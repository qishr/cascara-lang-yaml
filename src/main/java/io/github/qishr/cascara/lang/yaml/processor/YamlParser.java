package io.github.qishr.cascara.lang.yaml.processor;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.common.lang.ast.QuoteStyle;
import io.github.qishr.cascara.common.lang.processor.Parser;
import io.github.qishr.cascara.lang.yaml.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.CollectionStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlAliasNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlCommentNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.exception.YamlParserException;
import io.github.qishr.cascara.lang.yaml.exception.YamlTokenierException;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

/// A recursive descent parser that transforms a stream of [YamlToken]s into a [YamlDocument].
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
public class YamlParser extends AbstractYamlProcessor<YamlParser> implements Parser<YamlDocument, YamlToken> {
    private URI uri;
    private List<YamlToken> tokens;
    private int current = 0;
    private int depth = 0;

    /// Buffer to hold comments until a data node is created to claim them.
    private final List<YamlCommentNode> pendingComments = new ArrayList<>();

    private final Map<String, YamlNode> anchorRegistry = new HashMap<>();

    @Override protected YamlParser self() { return this; }

    /// {@inheritDoc}
    @Override
    public YamlDocument parse(String text) throws YamlParserException {
        return parse(text, null);
    }

    /// Entry point for parsing a full YAML source string.
    ///
    /// @param text The raw YAML source.
    /// @param uri Optional URI of the source file for error reporting.
    /// @return A [YamlDocument] representing the root of the AST.
    @Override
    public YamlDocument parse(String text, URI uri) throws YamlParserException {
        this.uri = uri;
        YamlTokenizer tokenizer = new YamlTokenizer();
        tokenizer.setOptions(options);
        tokenizer.setReporter(this.reporter);
        this.current = 0;
        try {
            this.tokens = tokenizer.tokenize(text, uri);
        } catch (YamlTokenierException e) {
            throw new YamlParserException(e.getMessage(), e);
        }
        return parseDocument();
    }

    /// {@inheritDoc}
    @Override
    public YamlDocument parse(List<YamlToken> tokens) throws YamlParserException {
        this.uri = null;
        this.tokens = tokens;
        this.current = 0;
        return parseDocument();
    }


    /// {@inheritDoc}
    @Override
    public YamlDocument parse(List<YamlToken> tokens, URI uri) throws YamlParserException {
        this.uri = uri;
        this.tokens = tokens;
        this.current = 0;
        return parseDocument();
    }

    /// Parses the top-level document structure and handles stream boundaries.
    private YamlDocument parseDocument() {
        if (this.tokens == null || this.tokens.isEmpty()) {
            YamlMapNode map = new YamlMapNode();
            return new YamlDocument(map);
        }

        consume(YamlTokenType.STREAM_START, "Expected start of stream.");
        skipTrivia();

        List<CommentAstNode> headers = new ArrayList<>(pendingComments);
        pendingComments.clear();

        match(YamlTokenType.DOCUMENT_START);
        skipTrivia();

        YamlNode root = parseValue();

        // Consume any remaining structural trivia
        skipTrivia();
        while (!isAtEnd() && (check(YamlTokenType.DEDENT) || check(YamlTokenType.NEWLINE))) {
            advance();
        }

        if (!isAtEnd()) {
            throw error(peek(), "Expected end of stream.");
        }

        match(YamlTokenType.STREAM_END);

        YamlDocument doc = new YamlDocument(root);
        doc.getComments().addAll(headers);

        return doc;
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
            skipTrivia();

            if (check(YamlTokenType.DEDENT) || check(YamlTokenType.EOF)) {
                return new YamlScalarNode(peek().getStartLine(), peek().getStartColumn(), uri, "", null, QuoteStyle.PLAIN);
            }

            // 1. Capture Anchor if present
            String pendingAnchor = null;
            if (check(YamlTokenType.ANCHOR)) {
                YamlToken anchorTok = advance();
                String raw = (String) anchorTok.getValue();
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
                // Check if it's actually a map key before calling parseMap
                else if (check(YamlTokenType.SCALAR) && lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {
                    result = parseMap();
                } else {
                    // It's just a single indented value (scalar)
                    result = parseValue();
                }

                skipTrivia();
                consume(YamlTokenType.DEDENT, "Expected dedent after block content.");
            }
            else if (check(YamlTokenType.ALIAS)) {
                YamlToken tok = advance();
                String name = ((String) tok.getValue()).replace("*", "");
                YamlAliasNode alias = new YamlAliasNode(tok.getStartLine(), tok.getStartColumn(), uri, name);
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
                String val = (String) tok.getValue();

                if ("|".equals(val) || ">".equals(val)) {
                    result = parseBlockScalar("|".equals(val));
                }
                // Use a smarter lookahead that skips comments/newlines
                else if (lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {
                    result = parseMap();
                } else {
                    result = parseScalar();
                }
            }
            else {
                // Handle empty values / implicit nulls
                result = new YamlScalarNode(peek().getStartLine(), peek().getStartColumn(), uri, "", null, QuoteStyle.PLAIN);
            }

            // 3. Apply the anchor to whatever node was produced
            if (pendingAnchor != null && result != null) {
                result.setAnchor(pendingAnchor);
                anchorRegistry.put(pendingAnchor, result);
            }

            return attachComments(result);

        } finally {
            depth--;
        }
    }

    /// Parses a block-level mapping and enforces strict key indentation.
    ///
    ///
    ///
    /// This method captures the column of the first key encountered and ensures
    /// all subsequent sibling keys in this map align perfectly.
    ///
    /// @throws YamlParserException if a sibling key is found at an inconsistent indentation.
    private YamlMapNode parseMap() {
        depth++;
        trace("parseMap");
        try {
            YamlToken startToken = peek();
            YamlMapNode map = new YamlMapNode(startToken.getStartLine(), startToken.getStartColumn(), uri);
            map.setStyle(CollectionStyle.BLOCK);

            // attachComments(map);

            java.util.Set<String> seenKeys = new java.util.HashSet<>();
            boolean isFirstEntry = true;
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

                // 1. If we see a DEDENT, the map is definitely over.
                if (check(YamlTokenType.DEDENT)) break;

                // 2. If we see an INDENT, it means the NEXT key is indented.
                // This happens in block maps. We should consume it and stay in the loop.
                if (check(YamlTokenType.INDENT)) {
                    advance();
                    skipTrivia();
                }

                // Peek at the token that should be our key
                YamlToken keyToken = peek();

                // 3. Now check for the key
                if (!check(YamlTokenType.SCALAR)) break;



                if (mapColumn == -1) {
                    mapColumn = keyToken.getStartColumn();
                } else if (keyToken.getStartColumn() != mapColumn) {
                    // This will catch bad-key-indent.yaml
                    throw new YamlParserException("Inconsistent indentation for map key",
                        keyToken.getStartLine(), keyToken.getStartColumn(), uri);
                }



                YamlScalarNode key = parseScalar();



                // If it's the first entry, it should claim the
                // comments that were buffered before the map technically started.
                if (isFirstEntry) {
                    attachComments(key);
                    isFirstEntry = false;
                } else {
                    attachComments(key); // Standard attachment for subsequent keys
                }


                String keyString = key.getString();

                if (options.isStrict() && !seenKeys.add(keyString)) {
                    throw error(previous(), "Duplicate key: " + keyString);
                }
                int keyColumn = key.getStartColumn(); // Capture the column of the current key

                consume(YamlTokenType.VALUE_INDICATOR, "Expected ':' after key.");
                parseInlineComment(key);

                YamlNode value;
                // Is the next line indented DEEPER than the current key?
                if (check(YamlTokenType.NEWLINE) && !isIndentedDeeperThan(keyColumn)) {
                    value = new YamlScalarNode(peek().getStartLine(), peek().getStartColumn(), uri, "", null, QuoteStyle.PLAIN);
                    // Don't advance here, let the loop's skipTrivia handle the newline
                } else {
                    value = parseValue();
                }

                map.put(new YamlMapEntryNode(key.getStartLine(), key.getStartColumn(), uri, key, value));

                skipTrivia();
            }
            return map;
        } finally {
            depth--;
        }
    }

    /// Determines if the next non-trivia token is indented deeper than the [parentColumn].
    private boolean isIndentedDeeperThan(int parentColumn) {
        int i = 1;
        while (current + i < tokens.size()) {
            YamlToken t = tokens.get(current + i);
            if (t.getType() == YamlTokenType.INDENT) {
                // It's only a nested value if the indent column is > parent key column
                return t.getStartColumn() > parentColumn;
            }
            if (t.getType() != YamlTokenType.NEWLINE && t.getType() != YamlTokenType.COMMENT) {
                return false;
            }
            i++;
        }
        return false;
    }

    /// Parses a block sequence (list) indicated by leading dashes.
    private YamlSequenceNode parseSequence() {
        depth++;
        trace("parseSequence");
        try {
            YamlToken start = peek();
            YamlSequenceNode sequence = new YamlSequenceNode(start.getStartLine(), start.getStartColumn(), uri);
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
            YamlToken start = consume(YamlTokenType.SEQUENCE_START, "Expected '['");
            YamlSequenceNode sequence = new YamlSequenceNode(start.getStartLine(), start.getStartColumn(), null);
            sequence.setStyle(CollectionStyle.FLOW);

            while (!check(YamlTokenType.SEQUENCE_END) && !isAtEnd()) {
                skipTrivia();
                sequence.add(parseValue());

                if (!match(YamlTokenType.COMMA)) break;
            }

            consume(YamlTokenType.SEQUENCE_END, "Expected ']'");
            return attachComments(sequence);
        } finally {
            depth--;
        }
    }

    /// Parses a flow map like {key: value, key2: value2}.
    /// Parses a flow-style mapping (e.g., { key: value, key2: value2 }).
    /// This method handles the explicit MAP_START and MAP_END tokens.
    private YamlMapNode parseFlowMap() {
        depth++;
        trace("parseFlowMap");
        try {
            YamlToken startToken = consume(YamlTokenType.MAP_START, "Expected '{' to start flow map.");
            YamlMapNode map = new YamlMapNode(startToken.getStartLine(), startToken.getStartColumn(), uri);

            // Handle empty flow map {}
            if (match(YamlTokenType.MAP_END)) {
                return map;
            }

            while (!isAtEnd()) {
                skipTrivia();

                // 1. Parse Key
                YamlScalarNode key = parseScalar();

                // 2. Consume Value Indicator
                consume(YamlTokenType.VALUE_INDICATOR, "Expected ':' after key in flow map.");

                // 3. Parse Value
                YamlNode value = parseValue();

                // 4. Store Entry
                map.put(new YamlMapEntryNode(key.getStartLine(), key.getStartColumn(), uri, key, value));

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
                    throw error(peek(), "Expected ',' or '}' in flow map.");
                }
            }
            return map;
        } finally {
            depth--;
        }
    }

    /// Parses a scalar value and checks for same-line (inline) comments.
    private YamlScalarNode parseScalar() {
        ++this.depth;
        this.trace("parseScalar");

        try {
            YamlToken token = this.consume(YamlTokenType.SCALAR, "Expected scalar.");
            String raw = token.getLexeme();
            String value = (String) token.getValue();

            QuoteStyle style;
            if (raw.startsWith("\"")) {
                style = QuoteStyle.DOUBLE;
                value = this.unescapeDoubleQuotes(value);
            } else if (raw.startsWith("'")) {
                style = QuoteStyle.SINGLE;
                value = this.unescapeSingleQuotes(value);
            } else {
                style = QuoteStyle.PLAIN;
                // Handle boolean/null/number conversion for plain scalars
                Object coercedValue = this.coercePlainScalar(value);

                // We need to decide if YamlScalarNode holds Object or String.
                // If it holds String, we'll fix it in the Compiler,
                // but usually, it's better to store the typed value here.
                value = coercedValue.toString();
                // Note: If YamlScalarNode supports a 'TypedValue', set it here.
            }

            YamlScalarNode scalar = new YamlScalarNode(
                token.getStartLine(), token.getStartColumn(), this.uri, raw, value, style
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

    /// Simple unescaper for double-quoted YAML strings
    private String unescapeDoubleQuotes(String input) {
        if (input == null) return null;
        return input.replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r");
    }

    /// YAML single quotes unescape by replacing doubled single quotes with one.
    private String unescapeSingleQuotes(String input) {
        return input == null ? null : input.replace("''", "'");
    }

    /// Evaluates plain scalars for boolean values to avoid type-bleeding.
    private Object coercePlainScalar(String input) {
        if (input == null) return null;
        String lowered = input.toLowerCase().trim();

        // Standard YAML 1.2 boolean set
        if (lowered.equals("true") || lowered.equals("yes") || lowered.equals("on")) return Boolean.TRUE;
        if (lowered.equals("false") || lowered.equals("no") || lowered.equals("off")) return Boolean.FALSE;

        // Add number parsing here if needed later
        return input;
    }

    private YamlNode parseBlockScalar(boolean isLiteral) {
        YamlToken indicator = advance(); // Consume '|' or '>'
        StringBuilder content = new StringBuilder();

        // 1. Clear the rest of the current line (comments/whitespace)
        // and move to the start of the indented block.
        skipTrivia();

        // 2. Structural Check: Block scalars MUST be indented.
        if (!check(YamlTokenType.INDENT)) {
            throw error(peek(), "Expected indentation for block scalar.");
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
            indicator.getStartLine(), indicator.getStartColumn(), uri,
            isLiteral ? "|" : ">", result, QuoteStyle.PLAIN
        );
    }

    private YamlCommentNode parseComment() {
        YamlToken token = advance();
        // 1. Safe cast from Object to String
        String text = token.getValue() != null ? token.getValue().toString() : "";

        // 2. Parser-side cleaning (The "Double Hash" Fix)
        if (text.startsWith("#")) {
            text = text.substring(1);
            if (text.startsWith(" ")) {
                text = text.substring(1);
            }
        }

        return new YamlCommentNode(
            token.getStartLine(),
            token.getStartColumn(),
            uri,
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
    // Helpers
    //

    /// Peeks ahead to find a specific token type while ignoring irrelevant trivia.
    private boolean lookAheadIgnoringComments(YamlTokenType targetType) {
        int lookahead = current + 1;
        while (lookahead < tokens.size()) {
            YamlTokenType type = tokens.get(lookahead).getType();
            if (type == targetType) return true;
            if (type == YamlTokenType.NEWLINE || type == YamlTokenType.COMMENT) {
                lookahead++;
                continue;
            }
            // If we hit anything else (like a Scalar or Alias), the lookahead fails
            break;
        }
        return false;
    }

    /// Collects comments and skips newlines, storing comments in the buffer.
    private void skipTrivia() {
        while (!isAtEnd()) {
            if (match(YamlTokenType.NEWLINE)) continue;
            if (check(YamlTokenType.COMMENT)) {
                // Use the cleaner helper instead of manual creation
                pendingComments.add(parseComment());
                continue;
            }
            break;
        }
    }

    /// Clears the [pendingComments] buffer by attaching them to the given node.
    private <T extends YamlNode> T attachComments(T node) {
        for (YamlCommentNode comment : pendingComments) {
            node.addComment(comment);
        }
        pendingComments.clear();
        return node;
    }

    //
    // Navigation Helpers
    //

    private YamlToken consume(YamlTokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
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

    private boolean check(YamlTokenType type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }

    private YamlToken advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private YamlToken peek() { return tokens.get(current); }

    private YamlToken previous() { return tokens.get(current - 1); }

    private boolean isAtEnd() {
        if (current >= tokens.size()) return true;
        YamlTokenType type = peek().getType();
        return type == YamlTokenType.EOF || type == YamlTokenType.STREAM_END;
    }


    //
    // Errors and Diagnostics
    //

    private YamlParserException error(YamlToken token, String message) {
        if (reporter != null) {
            reporter.errorAt(token.getStartLine(), token.getStartColumn(), uri, message);
        }
        return new YamlParserException(message, token.getStartLine(), token.getStartColumn(), uri);
    }

    /// Log the current method name and upcoming tokens
    private void trace(String methodName) {
        if (reporter == null) return;

        // Create indentation based on recursion depth
        String indent = "  ".repeat(Math.max(0, depth));

        reporter.trace("L%3d C%3d I%3d %s%s: %s",
                tokens.get(current).getStartLine(),
                tokens.get(current).getStartColumn(),
                current,
                indent,
                methodName,
                upcomingTokens());
    }

    // Get next 4 tokens as a string.
    private String upcomingTokens() {
        StringBuilder sb = new StringBuilder();
        int distance = Math.min(tokens.size() - current, 4);
        for (int i = 0; i < distance; i++) {
            YamlToken token = tokens.get(current + i);
            sb.append(token.getType());
            sb.append("(");
            sb.append(token.getLexeme().replace("\n", "\\n").replace("\r", "\\r"));
            sb.append(") ");
        }
        return sb.toString();
    }

    public void dumpAST(YamlNode node, int indent) {
        String spacing = "  ".repeat(indent);

        if (node instanceof YamlMapNode map) {
            System.out.println(spacing + "YamlMapNode");
            for (YamlMapEntryNode entry : map.getEntries()) {
                // In the API, the key is a YamlNode (could be a scalar, map, etc.)
                YamlNode keyNode = entry.getKey();
                YamlNode valueNode = entry.getValue();
                String keyDesc = (keyNode instanceof YamlScalarNode s) ? s.getString() : "Complex Key";
                System.out.println(spacing + "  Key: " + keyDesc);

                dumpAST(valueNode, indent + 4);
            }
        } else if (node instanceof YamlSequenceNode seq) {
            List<YamlNode> items = seq.getElements();
            System.out.println(spacing + "YamlSequenceNode (size: " + items.size() + ")");
            for (YamlNode item : items) {
                dumpAST(item, indent + 2);
            }
        } else if (node instanceof YamlScalarNode scalar) {
            System.out.println(spacing + "YamlScalarNode: [" + scalar.getString() + "]");
        }
    }
}