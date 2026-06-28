package io.github.qishr.cascara.lang.yaml.processor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.LangDiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.annotation.Experimental;
import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.processor.Parser;
import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.lang.yaml.YamlOptions;
import io.github.qishr.cascara.lang.yaml.ast.CollectionStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlAliasNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchorNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlCommentNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlDirectiveNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocumentNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlStreamNode;
import io.github.qishr.cascara.lang.yaml.exception.YamlDiagnosticCode;
import io.github.qishr.cascara.lang.yaml.exception.YamlParserException;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

/// A recursive descent parser that transforms a stream of [YamlToken]s into a [YamlNode] AST.
///
/// This parser is designed for **high-fidelity AST construction**, meaning it preserves
/// comments, indentation styles, and quote styles for round-tripping.
///
/// ### Core Responsibilities
/// * **Structural Validation**: Enforces strict column alignment for map keys and sequence items.
/// * **Trivia Management**: Buffers comments using [pendingComments] and attaches them to
///   the next appropriate data node (Scalar, Map, or Sequence).
/// * **Indentation Lifecycle**: Manages block boundaries by consuming `INDENT` and `DEDENT`
///   tokens through the [parseValue] dispatcher.

public class YamlParser extends AbstractYamlProcessor<YamlParser> implements Parser<YamlNode, YamlToken> {

    private Tokenizer<YamlToken> tokenizer;
    private final List<YamlToken> tokenBuffer = new ArrayList<>(256);
    private int current = 0;
    private int depth = 0;

    /// Buffer to hold comments until a data node is created to claim them.
    private final List<YamlCommentNode> pendingComments = new ArrayList<>();
    private final Map<String, YamlNode> anchorRegistry = new HashMap<>();

    /// Empty default constructor for SPI.
    public YamlParser() {}

    @Override protected YamlParser self() { return this; }

    /// Entry point for parsing a full YAML source string.
    @Override
    public YamlNode parse(String text) {
        ensureTokenBufferFilled(text);
        return parseAndUnpack();
    }

    /// Entry point for parsing an InputStream.
    @Override
    public YamlNode parse(InputStream is) {
        ensureTokenBufferFilled(is);
        return parseAndUnpack();
    }

    /// Type-safe method specifically for multi-document scenarios.
    @Experimental
    public YamlStreamNode parseMulti(String text) {
        YamlOptions originalOptions = this.options;
        try {
            this.options = originalOptions.duplicate().setMultiDocument(true);
            return (YamlStreamNode) parse(text);
        } finally {
            this.options = originalOptions; // Safely restore original state
        }
    }

    /// Type-safe method specifically for multi-document scenarios.
    @Experimental
    public YamlStreamNode parseMulti(InputStream is) {
        YamlOptions originalOptions = this.options;
        try {
            this.options = originalOptions.duplicate().setMultiDocument(true);
            return (YamlStreamNode) parse(is);
        } finally {
            this.options = originalOptions; // Safely restore original state
        }
    }

    /// Primary parsing core driven directly by the Tokenizer interface structure.
    @Override
    public YamlNode parse(Tokenizer<YamlToken> tokenizer) {
        this.tokenizer = tokenizer;
        ensureTokenBufferFilled();
        return parseAndUnpack();
    }

    /// Entry point for parsing a list of tokens.
    @Override
    public YamlNode parse(List<YamlToken> tokens) {
        this.tokenizer = null;
        this.tokenBuffer.clear();
        if (tokens != null) {
            this.tokenBuffer.addAll(tokens);
        }
        this.current = 0;
        this.anchorRegistry.clear();
        this.pendingComments.clear();

        return parseAndUnpack();
    }

    //
    // Private Methods
    //

    /// Helper to execute internal parsing logic and unpack based on options.
    private YamlNode parseAndUnpack() {
        YamlStreamNode stream = parseInternal();

        // If the developer wants the full multi-document structure, hand over the stream node
        if (options.isMultiDocument()) {
            return stream;
        }

        // Otherwise, stay backward-compatible and return the naked first document body
        return stream.getDocuments().isEmpty()
            ? new YamlMapNode()
            : stream.getDocuments().get(0).getBody();
    }

    /// Helper to centralize tokenizer execution
    private void ensureTokenBufferFilled(String text) {
        YamlTokenizer tz = new YamlTokenizer();
        tz.setOptions(options);
        tz.setReporter(reporter);
        tz.open(text);
        fillBuffer(tz);
    }

    private void ensureTokenBufferFilled(InputStream is) {
        YamlTokenizer tz = new YamlTokenizer();
        tz.setOptions(options);
        tz.setReporter(reporter);
        tz.open(is);
        fillBuffer(tz);
    }

    private void ensureTokenBufferFilled() {
        if (this.tokenizer == null) return;
        fillBuffer(this.tokenizer);
    }

    private void fillBuffer(Tokenizer<YamlToken> tz) {
        this.tokenBuffer.clear();
        this.current = 0;
        this.anchorRegistry.clear();
        this.pendingComments.clear();

        YamlToken next;
        while ((next = tz.nextToken()) != null) {
            tokenBuffer.add(next);
            if (next.getType() == YamlTokenType.EOF || next.getType() == YamlTokenType.STREAM_END) {
                break;
            }
        }
    }

    private YamlStreamNode parseInternal() {
        if (this.tokenBuffer.isEmpty()) {
            return new YamlStreamNode();
        }

        consume(YamlTokenType.STREAM_START, LangDiagnosticCode.EXPECTED_STREAM_START);

        YamlStreamNode streamNode = new YamlStreamNode(peek().getStartLine(), peek().getStartColumn());

        // Multi-Document Processing Loop
        while (!isAtEnd() && !check(YamlTokenType.STREAM_END) && !check(YamlTokenType.EOF)) {
            int previousPosition = current;

            if (check(YamlTokenType.NEWLINE) || check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                advance();
                continue;
            }

            if (check(YamlTokenType.COMMENT)) {
                // If there are no documents yet, AND there is no upcoming explicit
                // document boundary marker or directive, let the first document body claim it.
                if (streamNode.getDocuments().isEmpty() && !lookAheadToExplicitMarker()) {
                    pendingComments.add(parseComment());
                } else {
                    streamNode.getComments().add(parseComment());
                }
                continue;
            }

            if (isAtEnd() || check(YamlTokenType.STREAM_END) || check(YamlTokenType.EOF)) {
                break;
            }

            // Guard: Malformed indentations that break out of blocks cannot start documents
            if (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR) || check(YamlTokenType.VALUE_INDICATOR)) {
                YamlToken badToken = peek();
                error(badToken, YamlDiagnosticCode.UNEXPECTED_TOKEN, badToken.getType());
            }

            YamlDocumentNode docNode = parseDocument();
            streamNode.addDocument(docNode);

            if (current == previousPosition) {
                break;
            }
        }

        // Attach any loose trailing comments collected during document parsing
        streamNode.getComments().addAll(pendingComments);
        pendingComments.clear();

        // Safely consume trailing layout artifacts and capture any trailing file footer comments
        while (!isAtEnd() && !check(YamlTokenType.STREAM_END) && !check(YamlTokenType.EOF)) {
            if (check(YamlTokenType.NEWLINE) || check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                advance();
            } else if (check(YamlTokenType.COMMENT)) {
                streamNode.getComments().add(parseComment());
            } else {
                break;
            }
        }

        if (check(YamlTokenType.STREAM_END)) {
            advance();
        } else if (check(YamlTokenType.EOF)) {
            advance();
        }

        return streamNode;
    }

    /// Parses a single document context, safely handling layout tokens near explicit boundaries
    private YamlDocumentNode parseDocument() {
        trace("parseDocument");
        depth++;

        YamlToken startToken = peek();
        YamlDocumentNode document = new YamlDocumentNode(startToken.getStartLine(), startToken.getStartColumn());

        if (lookAheadToExplicitMarker()) {
            while (!isAtEnd() && check(YamlTokenType.NEWLINE)) {
                advance();
            }
        }

        while (check(YamlTokenType.DIRECTIVE)) {
            YamlToken dirToken = advance();
            document.addDirective(new YamlDirectiveNode(dirToken.getStartLine(), dirToken.getStartColumn(), dirToken.getContent()));
            skipTrivia();
        }

        // Clean layout indentation wrapping the explicit document boundaries
        if (check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
            ensureBuffered(1);
            if (current + 1 < tokenBuffer.size() && tokenBuffer.get(current + 1).getType() == YamlTokenType.DOCUMENT_START) {
                advance();
            }
        }

        if (match(YamlTokenType.DOCUMENT_START)) {
            skipTrivia();
        }

        if (check(YamlTokenType.DOCUMENT_END) || check(YamlTokenType.DOCUMENT_START) || isAtEnd()) {
            document.setBody(new YamlScalarNode(peek().getStartLine(), peek().getStartColumn(), "", "", QuoteStyle.PLAIN));
        } else {
            document.setBody(parseValue());
        }

        if (match(YamlTokenType.DOCUMENT_END)) {
            skipTrivia();
        }

        depth--;
        return document;
    }

    /// Safely looks ahead to check for an explicit document boundary or directive.
    /// Uses a clean index bound to prevent infinite spin conditions.
    private boolean lookAheadToExplicitMarker() {
        int index = current;
        int max = tokenBuffer.size();

        while (index < max) {
            YamlTokenType type = tokenBuffer.get(index).getType();
            if (type == YamlTokenType.DOCUMENT_START || type == YamlTokenType.DIRECTIVE) {
                return true;
            }
            if (type == YamlTokenType.INDENT || type == YamlTokenType.DEDENT ||
                type == YamlTokenType.NEWLINE || type == YamlTokenType.COMMENT) {
                index++; // Guarantee progression
            } else {
                break; // Exit immediately on any structural data token
            }
        }
        return false;
    }

    /// The primary dispatcher for all YAML values.
    ///
    /// This method is responsible for:
    /// 1. Handling anchors (`&`) and aliases (`*`).
    /// 2. Managing block indentation tokens (`INDENT`/`DEDENT`).
    /// 3. Determining the structural type (Map, Sequence, or Scalar) via lookahead.
    private YamlNode parseValue() {
        depth++;
        trace("parseValue");
        try {
            if (check(YamlTokenType.ERROR)) {
                error(peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, peek().getType());
            }

            skipTrivia();

            if (check(YamlTokenType.DEDENT) || check(YamlTokenType.EOF)) {
                return new YamlScalarNode(peek().getStartLine(), peek().getStartColumn(), "", null, QuoteStyle.PLAIN);
            }

            // 1. Capture Anchor if present
            String pendingAnchor = null;
            if (check(YamlTokenType.ANCHOR)) {
                YamlToken anchorTok = advance();
                String raw = anchorTok.getContent();
                pendingAnchor = raw.startsWith("&") ? raw.substring(1) : raw;
                skipTrivia();
            }

            YamlNode result;

            // 2. Dispatch to the appropriate structural parser
            if (check(YamlTokenType.INDENT)) {
                advance(); // Consume INDENT
                skipTrivia();

                if (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                    result = parseSequence();
                }
                // An explicit key marker inside an indented block implies a Map
                else if (check(YamlTokenType.KEY_INDICATOR)) {
                    result = parseMap();
                }
                else if (check(YamlTokenType.SCALAR) && lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {
                    result = parseMap();
                } else {
                    // It's just a single indented value (scalar)
                    result = parseValue();
                }

                skipTrivia();
                consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT_BLOCK_COMMENT);
            }
            else if (check(YamlTokenType.KEY_INDICATOR)) {
                // Root-level un-indented explicit key marker implies a Map
                result = parseMap();
            }
            else if (check(YamlTokenType.ALIAS)) {
                YamlToken tok = advance();
                String name = tok.getContent().replace("*", "");
                YamlAliasNode alias = new YamlAliasNode(tok.getStartLine(), tok.getStartColumn(), name);
                if (anchorRegistry.containsKey(name)) {
                    alias.setResolvedNode(anchorRegistry.get(name));
                }
                result = alias;
            }
            else if (check(YamlTokenType.MAP_START)) {
                result = parseFlowMap();
            }
            else if (check(YamlTokenType.SEQUENCE_START)) {
                result = parseFlowSequence();
            }
            else if (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                result = parseSequence();
            }
            else if (check(YamlTokenType.SCALAR)) {
                YamlToken tok = peek();
                String val = tok.getContent();

                if ("|".equals(val) || ">".equals(val)) {
                    result = parseBlockScalar("|".equals(val));
                }
                else if (lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {
                    result = parseMap();
                } else {
                    result = parseScalar();
                }
            }
            else {
                result = new YamlScalarNode(peek().getStartLine(), peek().getStartColumn(),"", null, QuoteStyle.PLAIN);
            }

            // 3. Apply the anchor to whatever node was produced by wrapping it
            if (pendingAnchor != null && result != null) {
                // Ensure the underlying node gets its property set
                result.setAnchor(pendingAnchor);

                // Construct the decorator node using the coordinates of the current result node
                YamlAnchorNode anchorNode = new YamlAnchorNode(
                    result.getStartLine(),
                    result.getStartColumn(),
                    pendingAnchor,
                    result
                );

                // Track the actual unwrapped value node in the registry for Alias resolution
                anchorRegistry.put(pendingAnchor, result);

                // Hand the wrapped decorator node to the comment attacher
                result = anchorNode;
            }

            return attachComments(result);

        } finally {
            depth--;
        }
    }

    /// Parses a block-level mapping and enforces strict key indentation.
    /// This method captures the column of the first key encountered and ensures
    /// all subsequent sibling keys in this map align perfectly.
    private YamlNode parseMap() {
        depth++;
        trace("parseMap");
        try {
            YamlToken startToken = peek();
            YamlMapNode map = new YamlMapNode(startToken.getStartLine(), startToken.getStartColumn());
            map.setStyle(CollectionStyle.BLOCK);

            Set<String> seenKeys = new HashSet<>();
            int mapColumn = -1;

            while (!isAtEnd()) {
                skipTrivia();

                if (check(YamlTokenType.INDENT)) {
                    if (lookAheadIgnoringComments(YamlTokenType.NEWLINE)) {
                        advance(); // Consume INDENT
                        skipTrivia(); // Consume NEWLINE
                        if (check(YamlTokenType.DEDENT)) {
                            advance(); // Consume the DEDENT matching the empty line
                        }
                        continue;
                    }
                }

                if (check(YamlTokenType.DEDENT)) break;

                if (check(YamlTokenType.INDENT)) {
                    advance();
                    skipTrivia();
                }

                // Peek at the token that will tell us if an entry is actually here
                YamlToken markerToken = peek();
                boolean isExplicitKey = check(YamlTokenType.KEY_INDICATOR);

                // A map entry must start with an explicit indicator '?' OR a standard key 'SCALAR'
                if (!isExplicitKey && !check(YamlTokenType.SCALAR)) {
                    break;
                }

                if (mapColumn == -1) {
                    mapColumn = markerToken.getStartColumn();
                } else if (markerToken.getStartColumn() != mapColumn) {
                    error(markerToken, YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                }

                // 1. Harvest any block comments sitting directly above this key before it parses
                List<YamlCommentNode> leadingBlockComments = new ArrayList<>();
                while (check(YamlTokenType.COMMENT)) {
                    leadingBlockComments.add((YamlCommentNode) parseComment());
                    if (check(YamlTokenType.NEWLINE)) {
                        advance();
                    }
                }

                YamlNode key;

                if (isExplicitKey) {
                    advance(); // Consume '?'
                    skipTrivia();

                    // Multi-line nested block keys drop down via INDENT or start on a fresh NEWLINE
                    if (check(YamlTokenType.INDENT) || check(YamlTokenType.NEWLINE)) {
                        key = parseValue();
                    } else {
                        // Inline key evaluation: check if a colon belongs to the same line context
                        if (hasInlineValueIndicator()) {
                            key = parseValue(); // Parse inline nested map/sequence
                        } else {
                            key = parseScalar(); // Parse inline simple scalar key
                        }
                    }
                } else {
                    // Standard implicit key
                    key = parseScalar();
                }

                // 2. Prepend the harvested block comments so they don't get lost
                if (key != null && !leadingBlockComments.isEmpty()) {
                    // Assuming key.getComments() returns a collection editable or addAll exists
                    key.getComments().addAll(0, leadingBlockComments);
                }

                attachComments(key);

                String keyString = (key instanceof YamlScalarNode scalarKey) ? scalarKey.asString() : key.toString();

                if (options.isStrict() && !seenKeys.add(keyString)) {
                    error(previous(), YamlDiagnosticCode.DUPLICATE_KEY, keyString);
                }

                int keyColumn = key.getStartColumn();

                skipTrivia();

                // Clear any leftover indentation noise caused by the complex key block structure
                while (check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                    advance();
                    skipTrivia();
                }

                consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_MAP_KEY);
                parseInlineComment(key);

                YamlNode value;
                if (check(YamlTokenType.NEWLINE) && !isIndentedDeeperThan(keyColumn)) {
                    value = new YamlScalarNode(peek().getStartLine(), peek().getStartColumn(), "", null, QuoteStyle.PLAIN);
                } else {
                    value = parseValue();
                }

                map.put(new YamlMapEntryNode(key.getStartLine(), key.getStartColumn(), key, value));

                skipTrivia();
            }
            return map;
        } finally {
            depth--;
        }
    }

    /// Parses a block sequence (list) indicated by leading dashes.
    private YamlSequenceNode parseSequence() {
        depth++;
        trace("parseSequence");
        try {
            YamlToken start = peek();
            YamlSequenceNode sequence = new YamlSequenceNode(start.getStartLine(), start.getStartColumn());
            sequence.setStyle(CollectionStyle.BLOCK);
            attachComments(sequence);

            while (!isAtEnd()) {
                skipTrivia();

                // STOP: Hand control back to the dispatcher
                if (check(YamlTokenType.DEDENT)) {
                    break;
                }

                if (!check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                    break;
                }

                advance(); // Consume the '-'

                // parseValue handles the content, including potential nested blocks
                sequence.add(parseValue());

                skipTrivia();
            }

            return sequence;
        } finally {
            depth--;
        }
    }

    /// Parses a flow sequence like [item1, item2].
    private YamlSequenceNode parseFlowSequence() {
        depth++;
        trace("parseFlowSequence");
        try {
            YamlToken start = consume(YamlTokenType.SEQUENCE_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACKET);
            YamlSequenceNode sequence = new YamlSequenceNode(start.getStartLine(), start.getStartColumn());
            sequence.setStyle(CollectionStyle.FLOW);

            while (!check(YamlTokenType.SEQUENCE_END) && !isAtEnd()) {
                skipTrivia();
                sequence.add(parseValue());

                skipTrivia();

                if (!match(YamlTokenType.COMMA)) break;
            }

            consume(YamlTokenType.SEQUENCE_END, YamlDiagnosticCode.EXPECTED_CLOSE_BRACKET);
            return attachComments(sequence);
        } finally {
            depth--;
        }
    }

    /// Parses a flow map like {key: value, key2: value2}.
    /// Parses a flow-style mapping (e.g., { key: value, key2: value2 }).
    /// This method handles the explicit MAP_START and MAP_END tokens.
    private YamlNode parseFlowMap() {
        depth++;
        trace("parseFlowMap");
        try {
            YamlToken startToken = consume(YamlTokenType.MAP_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACE_FLOW_MAP);
            YamlMapNode map = new YamlMapNode(startToken.getStartLine(), startToken.getStartColumn());

            // Clear any whitespace/newlines before checking for an empty map exit
            skipTrivia();

            // Handle empty flow map {}
            if (match(YamlTokenType.MAP_END)) {
                return map;
            }

            while (!isAtEnd()) {
                skipTrivia();

                // 1. Parse Key
                YamlScalarNode key = parseScalar();

                // 2. Consume Value Indicator
                consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP);

                // 3. Parse Value
                YamlNode value = parseValue();

                // 4. Store Entry
                map.put(new YamlMapEntryNode(key.getStartLine(), key.getStartColumn(), key, value));

                skipTrivia();

                // 5. Check for continuation or end
                if (match(YamlTokenType.COMMA)) {
                    // Allow trailing commas by checking for end after comma
                    if (check(YamlTokenType.MAP_END)) {
                        advance();
                        break;
                    }
                    continue;
                } else if (match(YamlTokenType.MAP_END)) {
                    break;
                } else {
                    error(peek(), YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP);
                }
            }
            return map;
        } finally {
            depth--;
        }
    }

    private YamlScalarNode parseScalar() {
        ++this.depth;
        this.trace("parseScalar");

        try {
            YamlToken token = this.consume(YamlTokenType.SCALAR, YamlDiagnosticCode.EXPECTED_SCALAR);

            // Determine the style cleanly based on the token lexeme
            String raw = token.getLexeme();
            QuoteStyle style = QuoteStyle.PLAIN;
            if (raw.startsWith("\"")) {
                style = QuoteStyle.DOUBLE;
            } else if (raw.startsWith("'")) {
                style = QuoteStyle.SINGLE;
            }

            // Let YamlPrimitive handle unescaping, coercing, and type resolution
            YamlScalarNode scalar = new YamlScalarNode(
                token.getStartLine(),
                token.getStartColumn(),
                raw,
                token.getContent(), // Passes the unescaped base token text
                style
            );
            scalar.setToken(token);

            if (this.check(YamlTokenType.COMMENT) && this.peek().getStartLine() == token.getStartLine()) {
                scalar.getComments().add(this.parseComment());
            }

            this.parseInlineComment(scalar);
            return scalar;
        } finally {
            --this.depth;
        }
    }

    private YamlNode parseBlockScalar(boolean isLiteral) {
        YamlToken indicator = advance(); // Consume '|' or '>'
        StringBuilder content = new StringBuilder();

        // 1. Clear the rest of the current line (comments/whitespace)
        // and move to the start of the indented block.
        skipTrivia();

        // 2. Structural Check: Block scalars MUST be indented.
        if (!check(YamlTokenType.INDENT)) {
            error(peek(), YamlDiagnosticCode.EXPECTED_INDENTATION_BLOCK_SCALAR);
        }
        advance(); // Consume the INDENT

        // 3. Content Collection Loop
        while (!isAtEnd() && !check(YamlTokenType.DEDENT)) {
            // Collect every token on the line as raw text
            while (!isAtEnd() && !check(YamlTokenType.NEWLINE) && !check(YamlTokenType.DEDENT)) {
                // Use getLexeme() to preserve the exact text (like 'line:one')
                content.append(advance().getLexeme());
            }

            if (match(YamlTokenType.NEWLINE)) {
                content.append("\n");
            }

            // If there are comments inside the block, skipTrivia will
            // handle them, but be careful: in literal blocks,
            // indented # might be content, not a comment.
            // For now, let's keep it simple:
            if (check(YamlTokenType.COMMENT)) {
                skipTrivia();
            }
        }

        // 4. Clean up
        if (check(YamlTokenType.DEDENT)) {
            advance();
        }

        String result = content.toString();
        if (!isLiteral) {
            // Folded logic: Replace single newlines with spaces, preserve double newlines
            result = result.replaceAll("(?<!\\n)\\n(?!\\n)", " ").trim() + "\n";
        }

        return new YamlScalarNode(
            indicator.getStartLine(), indicator.getStartColumn(),
            isLiteral ? "|" : ">", result, QuoteStyle.PLAIN
        );
    }

    // private YamlCommentNode parseComment() {
    //     YamlToken token = advance();
    //     // 1. Safe cast from Object to String
    //     String text = token.getContent() != null ? token.getContent().toString() : "";

    //     // 2. Parser-side cleaning (The "Double Hash" Fix)
    //     if (text.startsWith("#")) {
    //         text = text.substring(1);
    //         if (text.startsWith(" ")) {
    //             text = text.substring(1);
    //         }
    //     }

    //     return new YamlCommentNode(
    //         token.getStartLine(),
    //         token.getStartColumn(),
    //         text,
    //         false
    //     );
    // }

    private YamlCommentNode parseComment() {
        YamlToken token = advance();
        String text = token.getContent() != null ? token.getContent().toString() : "";

        // Strip the raw hash marker, but preserve the exact space fidelity for the AST
        if (text.startsWith("#")) {
            text = text.substring(1);
        }

        return new YamlCommentNode(
            token.getStartLine(),
            token.getStartColumn(),
            text,
            false
        );
    }

    private void parseInlineComment(YamlNode node) {
        // If the very next token is a comment on the same line, it belongs to THIS node
        if (check(YamlTokenType.COMMENT) && peek().getStartLine() == node.getStartLine()) {
            node.getComments().add(parseComment());
        }
    }

    //
    // Navigation & Streaming Lookahead Helpers
    //

    /// Ensures that the lookahead buffer has retrieved tokens up to the requested lookahead index offset.
    private void ensureBuffered(int offset) {
        if (tokenizer == null) return; // Running in fixed List fallback mode

        int targetIndex = current + offset;
        while (tokenBuffer.size() <= targetIndex) {
            YamlToken next = tokenizer.nextToken();
            if (next == null) break;
            tokenBuffer.add(next);
            if (next.getType() == YamlTokenType.EOF || next.getType() == YamlTokenType.STREAM_END) {
                break;
            }
        }
    }

    private boolean isIndentedDeeperThan(int parentColumn) {
        int i = 1;
        while (true) {
            int targetIndex = current + i;
            if (targetIndex >= tokenBuffer.size()) return false;

            YamlToken t = tokenBuffer.get(targetIndex);
            if (t.getType() == YamlTokenType.INDENT) {
                return t.getStartColumn() > parentColumn;
            }
            if (t.getType() != YamlTokenType.NEWLINE && t.getType() != YamlTokenType.COMMENT) {
                return false;
            }
            i++;
        }
    }

    private boolean lookAheadIgnoringComments(YamlTokenType targetType) {
        int lookahead = current + 1;
        while (lookahead < tokenBuffer.size()) {
            YamlTokenType type = tokenBuffer.get(lookahead).getType();
            if (type == targetType) return true;
            if (type == YamlTokenType.NEWLINE || type == YamlTokenType.COMMENT) {
                lookahead++;
                continue;
            }
            break;
        }
        return false;
    }

    private boolean hasInlineValueIndicator() {
        int offset = 0;
        while (true) {
            YamlTokenType type = peek(offset).getType();
            if (type == YamlTokenType.NEWLINE || type == YamlTokenType.EOF || type == YamlTokenType.STREAM_END) {
                return false;
            }
            if (type == YamlTokenType.VALUE_INDICATOR) {
                return true;
            }
            offset++;
        }
    }

    private YamlToken peek() {
        return tokenBuffer.get(current);
    }

    private YamlToken peek(int offset) {
        int targetIndex = current + offset;
        if (targetIndex >= tokenBuffer.size()) {
            return tokenBuffer.isEmpty() ? null : tokenBuffer.get(tokenBuffer.size() - 1);
        }
        return tokenBuffer.get(targetIndex);
    }

    private boolean isAtEnd() {
        if (current >= tokenBuffer.size()) return true;
        YamlTokenType type = tokenBuffer.get(current).getType();
        return type == YamlTokenType.EOF || type == YamlTokenType.STREAM_END;
    }

    private boolean check(YamlTokenType type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }

    private YamlToken advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private YamlToken previous() {
        return tokenBuffer.get(current - 1);
    }

    // /// Collects comments and skips newlines, storing comments in the buffer.
    // private void skipTrivia() {
    //     while (!isAtEnd()) {
    //         if (match(YamlTokenType.NEWLINE)) continue;
    //         if (check(YamlTokenType.COMMENT)) {
    //             // Use the cleaner helper instead of manual creation
    //             pendingComments.add(parseComment());
    //             continue;
    //         }
    //         break;
    //     }
    // }

    /// Collects comments and skips newlines, storing comments in the buffer.
    private void skipTrivia() {
        while (!isAtEnd()) {
            if (match(YamlTokenType.NEWLINE)) continue;
            if (check(YamlTokenType.COMMENT)) {
                // If it's a root-level comment at the end of the file, leave it for the stream
                if (peek().getStartColumn() == 1 && isTrailingStreamComment(current)) {
                    break;
                }
                // Use the cleaner helper instead of manual creation
                pendingComments.add(parseComment());
                continue;
            }
            break;
        }
    }

    private boolean isTrailingStreamComment(int startPos) {
        int idx = startPos + 1;
        while (idx < tokenBuffer.size()) {
            YamlTokenType type = tokenBuffer.get(idx).getType();
            if (type != YamlTokenType.NEWLINE && type != YamlTokenType.DEDENT
                && type != YamlTokenType.STREAM_END && type != YamlTokenType.EOF
                && type != YamlTokenType.COMMENT) {
                return false;
            }
            idx++;
        }
        return true;
    }

    /// Clears the [pendingComments] buffer by attaching them to the given node.
    private <T extends YamlNode> T attachComments(T node) {
        for (YamlCommentNode comment : pendingComments) {
            node.addComment(comment);
        }
        pendingComments.clear();
        return node;
    }

    @Nullable
    private YamlToken consume(YamlTokenType type, DiagnosticCode msgCode) {
        if (check(type)) return advance();
        YamlToken token = peek();
        error(peek(), msgCode, token.getType());
        return null; // TODO: Make this return non-null
    }

    private boolean match(YamlTokenType... types) {
        for (YamlTokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    //
    // Errors and Diagnostics
    //

    private void error(YamlToken token, DiagnosticCode code, Object... details) {
        reporter.errorAt(token, code, details);
        if (!reporter.collectsProblems()) {
            throw new YamlParserException(token, code, details);
        }
    }

    /// Log the current method name and upcoming tokens
    private void trace(String methodName) {
        if (reporter == null || reporter instanceof NoOpReporter) return;

        // Ensure at least the current token is loaded to grab safe coordinates
        ensureBuffered(0);
        if (current >= tokenBuffer.size()) return;

        // Create indentation based on recursion depth
        String indent = "  ".repeat(Math.max(0, depth));

        reporter.trace("L%3d C%3d I%3d %s%s: %s",
                tokenBuffer.get(current).getStartLine(),
                tokenBuffer.get(current).getStartColumn(),
                current,
                indent,
                methodName,
                upcomingTokens());
    }

    // Get next 4 tokens as a string.
    private String upcomingTokens() {
        StringBuilder sb = new StringBuilder();

        // Force up to 4 lookahead tokens into the buffer safely
        ensureBuffered(4);

        int distance = Math.min(tokenBuffer.size() - current, 4);
        for (int i = 0; i < distance; i++) {
            YamlToken token = tokenBuffer.get(current + i);
            sb.append(token.getType());
            sb.append("(");
            sb.append(token.getLexeme().replace("\n", "\\n").replace("\r", "\\r"));
            sb.append(") ");
        }
        return sb.toString();
    }
}