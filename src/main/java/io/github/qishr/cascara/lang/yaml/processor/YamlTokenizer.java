package io.github.qishr.cascara.lang.yaml.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Deque;
import java.util.EnumSet;
import java.net.URI;
import java.util.ArrayDeque;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.LanguageOptions;
import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.lang.yaml.exception.YamlTokenierException;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

/// Processes raw YAML source text into a stream of [YamlToken] objects.
///
/// This tokenizer implements a stateful scan that tracks indentation levels
/// to produce structural tokens (`INDENT`, `DEDENT`) alongside standard
/// YAML indicators and scalars.
///
/// ### Indentation Rules
/// * Only spaces are permitted for indentation.
/// * Tabs encountered in a whitespace context will trigger a [RuntimeException].
/// * A `DEDENT` must return exactly to a previously established indentation column.
///
/// ### Scalar Constraints
/// * Plain scalars (unquoted) cannot contain a colon (`:`) unless it is a
///   valid value indicator followed by whitespace.
public class YamlTokenizer implements Tokenizer<YamlToken> {
    private URI uri;
    private Reporter reporter = null;

    private static final Map<Character, YamlTokenType> FLOW_CONTEXT_SINGLE_CHAR_TOKENS = new HashMap<>();
    static {
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put('[', YamlTokenType.SEQUENCE_START);
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put(']', YamlTokenType.SEQUENCE_END);
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put('{', YamlTokenType.MAP_START);
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put('}', YamlTokenType.MAP_END);
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put(',', YamlTokenType.COMMA);
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put('!', YamlTokenType.TAG);
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put('|', YamlTokenType.SCALAR);
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put('>', YamlTokenType.SCALAR);
    }

    private Deque<Integer> indentationLevels = new ArrayDeque<>();
    private String source;
    private List<YamlToken> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;
    private int column = 1;
    private char currentChar = 0;

    public YamlTokenizer() {
        // reporter = new StandardReporter().setLevel(Level.TRACE);
    }

    @Override
    public Set<YamlTokenType> getTokenTypes() {
        return EnumSet.allOf(YamlTokenType.class);
    }

    @Override
    public YamlTokenizer setReporter(Reporter reporter) {
        this.reporter = reporter;
        return this;
    }

    @Override
    public YamlTokenizer setOptions(LanguageOptions<?> options) {
        // TODO: Options
        return this;
    }

    @Override
    public List<YamlToken> tokenize(String source) {
        return tokenize(source, null);
    }

    /// Entry point for the tokenization process.
    ///
    /// @param source The YAML text to process.
    /// @param uri The source URI for diagnostic reporting.
    /// @return A list of tokens including structural start/end markers.
    @Override
    public List<YamlToken> tokenize(String source, URI uri) {
        this.uri = uri;
        this.source = source;
        this.tokens = new ArrayList<>();
        this.current = 0;
        this.start = 0;
        this.line = 1;
        this.column = 1;
        this.indentationLevels.clear();
        this.indentationLevels.push(0);

        if (source == null || source.isEmpty()) {
            return List.of();
        }

        if (!source.isEmpty() && source.charAt(0) == '\uFEFF') {
            this.current = 1;
            this.column = 1;
            this.start = 1;
        }
        return scanTokens();
    }

    /// Iterates through the source until the end of the stream, managing the final DEDENT stack.
    List<YamlToken> scanTokens() {
        addToken(YamlTokenType.STREAM_START, "");

        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        // Use the current coordinates for all trailing structural tokens
        int finalLine = line;
        int finalCol = column;
        int finalOffset = current;

        while (indentationLevels.size() > 1) {
            indentationLevels.pop();
            tokens.add(new YamlToken(YamlTokenType.DEDENT, "", null, finalOffset, finalLine, finalCol));
        }

        tokens.add(new YamlToken(YamlTokenType.EOF, "", null, finalOffset, finalLine, finalCol));
        tokens.add(new YamlToken(YamlTokenType.STREAM_END, "", "", finalOffset, finalLine, finalCol));

        return tokens;
    }

    /// Dispatches the scan to specific handlers based on the current character.
    private void scanToken() {
        // If we have pending spaces from a previous newline, process them now
        // resolvePendingIndentation();

        final String method = "scanToken";
        int tokenStartColumn = column;
        char c = advance();

        if (c == '\n' || c == '\r') {
            trace("scanToken", "");
            handleNewlineAndIndentation(c);
            return;
        }

        if (c == ' ' || c == '\t') {
            trace(method, "space or tab");
            if (c == '\t') {
                // This ensures bad-tabs.yaml triggers an exception
                throw new YamlTokenierException("Tab characters are not allowed for indentation in YAML", line, column, uri);
            }
            return;
        }

        if (c == '-' && peek() == '-' && peekNext() == '-') {
            trace(method, "dash1");
            advance(); advance();
            addToken(YamlTokenType.DOCUMENT_START);
            return;
        }

        if (c == '.' && peek() == '.' && peekNext() == '.') {
            trace(method, "dot");
            advance(); advance();
            addToken(YamlTokenType.DOCUMENT_END);
            return;
        }

        if (c == '#') {
            trace(method, "hash");
            while (peek() != '\n' && peek() != '\r' && !isAtEnd()) {
                advance();
            }
            addToken(YamlTokenType.COMMENT);
            return;
        }

        if (FLOW_CONTEXT_SINGLE_CHAR_TOKENS.containsKey(c)) {
            addToken(FLOW_CONTEXT_SINGLE_CHAR_TOKENS.get(c));
            return;
        }

        if (c == '-') {
            if (isWhitespace(peek()) || isAtEnd()) {
                int dashColumn = column - 1;
                int currentMargin = indentationLevels.peek();

                if (dashColumn > currentMargin) {
                    indentationLevels.push(dashColumn);
                    addStructuralToken(YamlTokenType.INDENT, dashColumn - 1);
                }

                addToken(YamlTokenType.SEQUENCE_ENTRY_INDICATOR);

                if (peek() == ' ') advance();

                if (!isAtEnd() && peek() != '\n' && peek() != '\r') {
                    if (willBeMappingKey()) {
                        if (column > indentationLevels.peek()) {
                            indentationLevels.push(column);
                            addStructuralToken(YamlTokenType.INDENT, column - 1);
                        }
                    }
                }
                return;
            }
        }

        if (c == ':') {
            trace(method, "colon");
            if (isWhitespace(peek()) || isAtEnd()) {
                addExplicitToken(YamlTokenType.VALUE_INDICATOR, ":", tokenStartColumn);
                return;
            }
        }

        if (c == '\'' || c == '\"') {
            trace(method, "quote");
            scanQuotedScalar(c);
            return;
        }

        if (c == '&') {
            trace(method, "ampersand");
            scanIdentifier(YamlTokenType.ANCHOR);
            return;
        }

        if (c == '*') {
            trace(method, "asterisk");
            scanIdentifier(YamlTokenType.ALIAS);
            return;
        }

        scanPlainScalar();
    }

    /// Processes newlines and calculates the indentation level of the following line.
    ///
    /// This method compares the observed column of the first non-whitespace character
    /// against the [indentationLevels] stack to emit `INDENT` or `DEDENT` tokens.
    private void handleNewlineAndIndentation(char c) {
        // 1. First Newline
        String lexeme = (c == '\r' && peek() == '\n') ? "\r\n" : "\n";
        if (lexeme.length() == 2) advance();

        // Use column - lexeme.length() to point to the start of the newline
        addExplicitToken(YamlTokenType.NEWLINE, lexeme, column - lexeme.length());
        line++;
        column = 1;

        // Keep eating newlines and spaces as long as the line is "empty"
        while (!isAtEnd()) {
            char next = peek();

            if (next == ' ') {
                advance();
            } else if (next == '\n' || next == '\r') {
                // We hit another newline, so previous spaces on this line didn't matter.
                char nc = advance();
                String nl = (nc == '\r' && peek() == '\n') ? "\r\n" : "\n";
                if (nl.length() == 2) advance();

                addExplicitToken(YamlTokenType.NEWLINE, nl, column - nl.length());
                line++;
                column = 1;
            } else {
                // We hit actual content (or a comment)
                break;
            }
        }

        int currentColumn = column;
        int expectedIndent = indentationLevels.peek();

        // 4. Indentation Logic (The version that passes your tests)
        if (currentColumn > expectedIndent) {
            indentationLevels.push(currentColumn);
            addStructuralToken(YamlTokenType.INDENT, currentColumn - 1);
        } else if (currentColumn < expectedIndent) {
            while (indentationLevels.size() > 1 && indentationLevels.peek() > currentColumn) {
                indentationLevels.pop();
                addStructuralToken(YamlTokenType.DEDENT, currentColumn);
            }
        }

        // Sync the start pointer so the next token doesn't include the spaces
        this.start = this.current;
    }

    /// Scans a quoted scalar, handling escape sequences for double quotes.
    private void scanQuotedScalar(char quoteChar) {
        trace("scanQuotedScalar");
        // 1. Capture the starting position BEFORE the loop
        int startLine = line;
        int startColumn = column;

        while (!isAtEnd()) {
            char c = peek();
            if (quoteChar == '"' && c == '\\') {
                advance();
                if (!isAtEnd()) advance();
                continue;
            }
            if (c == quoteChar) {
                advance();
                String lexeme = source.substring(start, current);
                String content = source.substring(start + 1, current - 1);

                // 2. Use the captured startColumn instead of calculating backwards
                tokens.add(new YamlToken(YamlTokenType.SCALAR, lexeme, content, start, startLine, startColumn));
                return;
            }
            if (c == '\n' || c == '\r') {
                advance();
                if (c == '\r' && peek() == '\n') advance();
                line++;
                column = 1;
                continue;
            }
            advance();
        }
        if (isAtEnd()) addToken(YamlTokenType.ERROR);
    }

    private void scanPlainScalar() {
        trace("scanPlainScalar");

        while (!isAtEnd()) {
            char c = peek();

            // 1. Stop at Newlines
            if (c == '\n' || c == '\r') break;

            // 2. Stop at Comments (Space + #)
            // Note: In YAML, a # is only a comment if preceded by whitespace
            if (c == '#' && (current == start || isWhitespace(source.charAt(current - 1)))) break;

            // 3. Stop at Flow Indicators
            if (FLOW_CONTEXT_SINGLE_CHAR_TOKENS.containsKey(c)) break;

            // 4. The Colon Rule: Stop ONLY if it's a value indicator
            if (c == ':' && (isWhitespace(peekNext()) || isAtEnd())) {
                break;
            }

            advance();
        }

        if (start == current) {
             addToken(YamlTokenType.ERROR);
             return;
        }

        // 5. Trim trailing whitespace
        String rawLexeme = source.substring(start, current);
        String trimmedLexeme = rawLexeme.stripTrailing();

        // 6. Backup the pointer for every character trimmed
        int trimmedLength = rawLexeme.length() - trimmedLexeme.length();
        for (int i = 0; i < trimmedLength; i++) {
            backup();
        }

        addToken(YamlTokenType.SCALAR, trimmedLexeme);
    }

    private void backup() {
        if (current > 0) {
            current--;
            column--;
            // Update currentChar to the new 'current' position if needed for tracing
            currentChar = source.charAt(current > 0 ? current - 1 : 0);
        }
    }

    private void scanIdentifier(YamlTokenType type) {
        while (!isAtEnd() && isAlphaNumeric(peek())) {
            advance();
        }
        addToken(type);
    }

    //
    //
    //

    private void addToken(YamlTokenType type) {
        String text = source.substring(start, current);
        // If we finished 'schema' at col 15, 15 - 6 = 9.
        int tokenColumn = column - text.length();
        addToken(new YamlToken(type, text, text, start, line, tokenColumn));
    }

    private void addToken(YamlTokenType type, String lexeme) {
        int tokenColumn = column - lexeme.length();
        addToken(new YamlToken(type, lexeme, lexeme, start, line, tokenColumn));
    }

    private void addExplicitToken(YamlTokenType type, String lexeme, int tokenColumn) {
        trace("addExplicitToken");
        addToken(new YamlToken(type, lexeme, lexeme, start, line, tokenColumn));
    }

    private void addStructuralToken(YamlTokenType type, int tokenColumn) {
        trace("addStructuralToken");
        addToken(new YamlToken(type, "", null, start, line, tokenColumn));
    }

    private void addToken(YamlToken token) {
        trace("addToken");
        tokens.add(token);
    }

    private char advance() {
        currentChar = source.charAt(current++);
        column++;
        return currentChar;
    }

    public char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    public char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean willBeMappingKey() {
        int lookahead = current;

        // 1. Skip potential quotes
        char firstChar = (lookahead < source.length()) ? source.charAt(lookahead) : '\0';
        boolean isQuoted = (firstChar == '\'' || firstChar == '\"');

        if (isQuoted) {
            lookahead++;
            while (lookahead < source.length() && source.charAt(lookahead) != firstChar) {
                // Handle escaped quotes
                if (source.charAt(lookahead) == '\\' && lookahead + 1 < source.length()) lookahead++;
                lookahead++;
            }
            if (lookahead < source.length()) lookahead++; // Consume closing quote
        } else {
            // 2. Skip plain scalar key characters
            while (lookahead < source.length() && isAlphaNumeric(source.charAt(lookahead))) {
                lookahead++;
            }
        }

        // 3. Skip trailing spaces before the indicator
        while (lookahead < source.length() && source.charAt(lookahead) == ' ') {
            lookahead++;
        }

        // 4. Check for the value indicator
        return lookahead < source.length() && source.charAt(lookahead) == ':';
    }

    public boolean isAtEnd() {
        return current >= source.length();
    }

    private boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private boolean isAlphaNumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    //
    // Diagnostics
    //

    private void trace(String method) {
        if (reporter == null) return;
        reporter.trace("S=%03d C=%03d '%s' %03d:%03d %s", start, current, currentChar(currentChar), line, column, method);
    }

    private void trace(String method, String info) {
        if (reporter == null) return;
        reporter.trace("S=%03d C=%03d '%s' %03d:%03d %s: %s", start, current, currentChar(currentChar), line, column, method, info);
    }

    private String currentChar(char c) {
        switch (c) {
            case ' ':
                return "␣";
            case '\t':
                return "⇥";
            case '\r':
                return "↵";
            case '\n':
                return "↩";
            default:
                return Character.toString(c);
        }
    }
}