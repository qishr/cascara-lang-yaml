package io.github.qishr.cascara.lang.yaml.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.LangDiagnosticCode;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.QuoteStyle;
import io.github.qishr.cascara.common.lang.processor.Parser;
import io.github.qishr.cascara.lang.yaml.ast.CollectionStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlAliasNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlCommentNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.exception.YamlDiagnosticCode;
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
    private List<YamlToken> tokens;
    private int current = 0;
    private int depth = 0;

    /// Buffer to hold comments until a data node is created to claim them.
    private final List<YamlCommentNode> pendingComments = new ArrayList<>();

    private final Map<String, YamlNode> anchorRegistry = new HashMap<>();

    /// Default constructor for SPI
    public YamlParser() {}

    @Override protected YamlParser self() { return this; }

    /// Entry point for parsing a full YAML source string.
    ///
    /// @param text The raw YAML source.
    /// @return A [YamlNode] representing the root of the AST.
    @Override
    public YamlNode parse(String text) {
        YamlTokenizer tokenizer = new YamlTokenizer();
        tokenizer.setOptions(options);
        tokenizer.setReporter(reporter);
        return parse(tokenizer.tokenize(text));
    }

    /// {@inheritDoc}
    @Override
    public YamlNode parse(List<YamlToken> tokens) {
        this.tokens = tokens;
        this.current = 0;

        if (this.tokens == null || this.tokens.isEmpty()) {
            return new YamlMapNode();
        }

        consume(YamlTokenType.STREAM_START, LangDiagnosticCode.EXPECTED_STREAM_START);
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
            // throw error(peek(), "Expected end of stream.");
            error(peek(), LangDiagnosticCode.UNEXPECTED_STREAM_END);
        }

        match(YamlTokenType.STREAM_END);

        root.getComments().addAll(headers);

        return root;
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
                // Check if it's actually a map key before calling parseMap
                else if (check(YamlTokenType.SCALAR) && lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {
                    result = parseMap();
                } else {
                    // It's just a single indented value (scalar)
                    result = parseValue();
                }

                skipTrivia();
                consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT_BLOCK_COMMENT, "Expected dedent after block content.");
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
                // Use a smarter lookahead that skips comments/newlines
                else if (lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {
                    result = parseMap();
                } else {
                    result = parseScalar();
                }
            }
            else {
                // Handle empty values / implicit nulls
                result = new YamlScalarNode( peek().getStartLine(), peek().getStartColumn(),"", null, QuoteStyle.PLAIN);
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
                    error(peek(), YamlDiagnosticCode.MAP_KEY_INDENTATION, "Inconsistent indentation for map key");
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


                String keyString = key.asString();

                if (options.isStrict() && !seenKeys.add(keyString)) {
                    error(previous(), YamlDiagnosticCode.DUPLICATE_KEY, "Duplicate key: {0}", keyString);
                }
                int keyColumn = key.getStartColumn(); // Capture the column of the current key

                consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_MAP_KEY, "Expected ':' after key.");
                parseInlineComment(key);

                YamlNode value;
                // Is the next line indented DEEPER than the current key?
                if (check(YamlTokenType.NEWLINE) && !isIndentedDeeperThan(keyColumn)) {
                    value = new YamlScalarNode(peek().getStartLine(), peek().getStartColumn(), "", null, QuoteStyle.PLAIN);
                    // Don't advance here, let the loop's skipTrivia handle the newline
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
            YamlToken start = consume(YamlTokenType.SEQUENCE_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACKET, "Expected '['");
            YamlSequenceNode sequence = new YamlSequenceNode(start.getStartLine(), start.getStartColumn());
            sequence.setStyle(CollectionStyle.FLOW);

            while (!check(YamlTokenType.SEQUENCE_END) && !isAtEnd()) {
                skipTrivia();
                sequence.add(parseValue());

                if (!match(YamlTokenType.COMMA)) break;
            }

            consume(YamlTokenType.SEQUENCE_END, YamlDiagnosticCode.EXPECTED_CLOSE_BRACKET, "Expected ']'");
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
            YamlToken startToken = consume(YamlTokenType.MAP_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACE_FLOW_MAP, "Expected '{' to start flow map.");
            YamlMapNode map = new YamlMapNode(startToken.getStartLine(), startToken.getStartColumn());

            // Handle empty flow map {}
            if (match(YamlTokenType.MAP_END)) {
                return map;
            }

            while (!isAtEnd()) {
                skipTrivia();

                // 1. Parse Key
                YamlScalarNode key = parseScalar();

                // 2. Consume Value Indicator
                consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP, "Expected ':' after key in flow map.");

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
                    error(peek(), YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP, "Expected ',' or '}' in flow map.");
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
            YamlToken token = this.consume(YamlTokenType.SCALAR, YamlDiagnosticCode.EXPECTED_SCALAR, "Expected scalar.");

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
            error(peek(), YamlDiagnosticCode.EXPECTED_INDENTATION_BLOCK_SCALAR, "Expected indentation for block scalar.");
            // return new YamlErrorNode(peek().getStartLine(), peek().getStartColumn(), "Expected indentation for block scalar.");
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

    private YamlCommentNode parseComment() {
        YamlToken token = advance();
        // 1. Safe cast from Object to String
        String text = token.getContent() != null ? token.getContent().toString() : "";

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

    private YamlToken consume(YamlTokenType type, DiagnosticCode msgCode, Object... details) {
        if (check(type)) return advance();
        error(peek(), msgCode, details);
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

    private void error(YamlToken token, DiagnosticCode code, Object... details) {
        reporter.errorAt(token, code, details);
        if (!reporter.collectsProblems()) {
            throw new ParserException(token, code, details);
        }
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
}