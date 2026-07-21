// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


package io.github.qishr.cascara.lang.yaml.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Deque;
import java.util.EnumSet;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayDeque;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.util.SourceBuffer;
import io.github.qishr.cascara.common.lang.util.SourceInputStreamBuffer;
import io.github.qishr.cascara.common.lang.util.SourceStringBuffer;
import io.github.qishr.cascara.lang.yaml.exception.YamlDiagnosticCode;
import io.github.qishr.cascara.lang.yaml.token.YamlErrorToken;
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
public class YamlTokenizer extends AbstractYamlProcessor<YamlTokenizer> implements Tokenizer<YamlToken> {

    private static final Map<Character, YamlTokenType> FLOW_CONTEXT_SINGLE_CHAR_TOKENS = new HashMap<>();
    static {
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put('[', YamlTokenType.SEQUENCE_START);
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put(']', YamlTokenType.SEQUENCE_END);
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put('{', YamlTokenType.MAP_START);
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put('}', YamlTokenType.MAP_END);
        FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put(',', YamlTokenType.COMMA);
        // FLOW_CONTEXT_SINGLE_CHAR_TOKENS.put('!', YamlTokenType.TAG);
    }

    private Deque<Integer> indentationLevels = new ArrayDeque<>();
    private int flowDepth = 0; // Tracks nesting level of flow context [ ] and { }
    private SourceBuffer buffer;
    private List<YamlToken> tokens = new ArrayList<>();
    private boolean isLegacyMode = false;
    private boolean inQuotedScalar = false;

    private final Deque<YamlToken> pendingTokens = new ArrayDeque<>();
    private boolean streamStarted = false;
    private boolean streamEnded = false;


    /// Default constructor for SPI
    public YamlTokenizer() {}

    @Override protected YamlTokenizer self() { return this; }

    @Override
    public int getOffset() {
        return buffer.offset();
    }

    public int getLine() {
        return buffer.line();
    }

    public int getColumn() {
        return buffer.column();
    }

    @Override
    public void open(String text) {
        this.buffer = new SourceStringBuffer(text);
        this.isLegacyMode = false;
        resetCommonState();
    }

    @Override
    public void open(Reader reader) {
        buffer = new SourceInputStreamBuffer(reader);
        this.isLegacyMode = false;
        resetCommonState();
    }

    @Override
    public void open(InputStream is) {
        this.buffer = new SourceInputStreamBuffer(is);
        this.isLegacyMode = false;
        resetCommonState();
    }

    @Override
    public List<YamlToken> tokenize(String source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        this.tokens = new ArrayList<>();
        this.streamStarted = false;
        this.streamEnded = false;

        open(source);
        this.isLegacyMode = true;

        // Drain the stream using the sequential nextToken logic
        YamlToken token;
        while ((token = nextToken()) != null) {
            if (token.getType() == YamlTokenType.STREAM_END) {
                break;
            }
        }
        return this.tokens;
    }

    @Override
    public Set<YamlTokenType> getTokenTypes() {
        return EnumSet.allOf(YamlTokenType.class);
    }

    @Override
    public YamlToken nextToken() {
        if (bufferedToken != null) {
            YamlToken tok = bufferedToken;
            bufferedToken = null;
            return tok;
        }

        // 1. Flush any tokens queued up by structural blocks first
        if (!pendingTokens.isEmpty()) {
            return queueToken(pendingTokens.pollFirst());
        }

        if (streamEnded) {
            return null;
        }

        if (!streamStarted) {
            streamStarted = true;
            return queueToken(new YamlToken(buffer.line(), buffer.column(), buffer.offset(), YamlTokenType.STREAM_START));
        }

        // 2. Loop until we either find a token or hit the end of the input buffer
        while (!buffer.isAtEnd() && pendingTokens.isEmpty()) {
            buffer.startTokenWindow();
            scanToken();
        }

        // 3. If scanning populated tokens, return the first one
        if (!pendingTokens.isEmpty()) {
            return queueToken(pendingTokens.pollFirst());
        }

        // 4. Handle stream wrap-up structural tokens when the underlying stream drains
        if (buffer.isAtEnd()) {
            int finalLine = buffer.line();
            int finalCol = buffer.column();
            int finalOffset = buffer.offset();

            if (indentationLevels.size() > 1) {
                indentationLevels.pop();
                return queueToken(new YamlToken(finalLine, finalCol, finalOffset, YamlTokenType.DEDENT));
            }

            streamEnded = true;
            pendingTokens.add(new YamlToken(finalLine, finalCol, finalOffset, YamlTokenType.EOF));
            pendingTokens.add(new YamlToken(finalLine, finalCol, finalOffset, YamlTokenType.STREAM_END));
            return queueToken(pendingTokens.pollFirst());
        }

        return null;
    }

    private YamlToken bufferedToken;

    public YamlToken peekToken() throws ParserException {
        if (bufferedToken == null) {
            bufferedToken = nextToken();
        }
        return bufferedToken;
    }

    //
    //
    //

    /// Dispatches the scan to specific handlers based on the current character.
    private void scanToken() {
        trace("scanToken");

        final String method = "scanToken";
        int tokenStartColumn = buffer.column();

        char c = buffer.advance();

        if (c == '|' || c == '>') {
            trace(method, "block scalar");
            scanBlockScalar(c);
            return;
        }

        if (c == '\n' || c == '\r') {
            trace("scanToken", "");
            handleNewlineAndIndentation(c);
            return;
        }

        if (c == ' ' || c == '\t') {
            trace(method, "space or tab");
            if (c == '\t') {
                error(YamlDiagnosticCode.TAB_NOT_ALLOWED);
            }
            return;
        }

        if (c == '!') {
            scanIdentifier(YamlTokenType.TAG);
            return;
        }

        if (c == '-' && buffer.peek() == '-' && buffer.peekNext() == '-') {
            trace(method, "dash1");
            buffer.advance(); buffer.advance();
            addToken(YamlTokenType.DOCUMENT_START);
            return;
        }

        if (c == '.' && buffer.peek() == '.' && buffer.peekNext() == '.') {
            trace(method, "dot");
            buffer.advance(); buffer.advance();
            addToken(YamlTokenType.DOCUMENT_END);
            return;
        }

        if (c == '#') {
            trace(method, "hash");
            while (buffer.peek() != '\n' && buffer.peek() != '\r' && !buffer.isAtEnd()) {
                buffer.advance();
            }
            addToken(YamlTokenType.COMMENT);
            return;
        }

        if (FLOW_CONTEXT_SINGLE_CHAR_TOKENS.containsKey(c)) {
            YamlTokenType type = FLOW_CONTEXT_SINGLE_CHAR_TOKENS.get(c);

            // Track entering/leaving flow context
            if (type == YamlTokenType.SEQUENCE_START || type == YamlTokenType.MAP_START) {
                flowDepth++;
            } else if (type == YamlTokenType.SEQUENCE_END || type == YamlTokenType.MAP_END) {
                flowDepth--;
            }

            addToken(type);
            return;
        }

        if (c == '-') {
            if (isWhitespace(buffer.peek()) || buffer.isAtEnd()) {
                int dashColumn = buffer.column() - 1;
                int currentMargin = indentationLevels.peek();

                if (dashColumn > currentMargin) {
                    indentationLevels.push(dashColumn);
                    addStructuralToken(YamlTokenType.INDENT, dashColumn - 1);
                }

                addToken(YamlTokenType.SEQUENCE_ENTRY_INDICATOR);

                if (buffer.peek() == ' ') buffer.advance();

                if (!buffer.isAtEnd() && buffer.peek() != '\n' && buffer.peek() != '\r') {
                    if (willBeMappingKey()) {
                        if (buffer.column() > indentationLevels.peek()) {
                            indentationLevels.push(buffer.column());
                            addStructuralToken(YamlTokenType.INDENT, buffer.column() - 1);
                        }
                    }
                }
                return;
            }
        }

        if (c == ':') {
            trace(method, "colon");
            if (isWhitespace(buffer.peek()) || buffer.isAtEnd()) {
                addExplicitToken(YamlTokenType.VALUE_INDICATOR, ":", tokenStartColumn);
                return;
            }
        }

        if (c == '\'') {
            trace(method, "single quote");
            scanSingleQuotedScalar();
            return;
        }

        if (c == '\"') {
            trace(method, "double quote");
            scanDoubleQuotedScalar();
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

        if (c == '?') {
            trace(method, "question mark");
            // A '?' is only a structural marker if followed by whitespace/newline/EOF
            if (isWhitespace(buffer.peek()) || buffer.isAtEnd()) {
                addExplicitToken(YamlTokenType.KEY_INDICATOR, "?", tokenStartColumn);
                return;
            }
        }

        if (c == '%') {
            trace(method, "directive");
            // Directives are line-oriented metadata (e.g., %YAML 1.2)
            while (buffer.peek() != '\n' && buffer.peek() != '\r' && !buffer.isAtEnd()) {
                buffer.advance();
            }
            addToken(YamlTokenType.DIRECTIVE);
            return;
        }

        scanPlainScalar(c);
    }

    private void handleNewlineAndIndentation(char c) {

        // if (inQuotedScalar) {

        //     // Advance over the first newline character
        //     buffer.advance();

        //     // Handle optional CRLF
        //     String lexeme;
        //     if (c == '\r' && !buffer.isAtEnd() && buffer.peek() == '\n') {
        //         buffer.advance();
        //         lexeme = "\r\n";
        //     } else {
        //         lexeme = "\n";
        //     }

        //     addExplicitToken(YamlTokenType.NEWLINE, lexeme, buffer.column() - lexeme.length());

        //     int spaces = 0;
        //     while (!buffer.isAtEnd() && buffer.peek() == ' ') {
        //         buffer.advance();
        //         spaces++;
        //     }

        //     if (spaces > 0) {
        //         addExplicitToken(YamlTokenType.SCALAR, " ".repeat(spaces), buffer.column());
        //     }

        //     buffer.startTokenWindow();
        //     return;
        // }

        if (inQuotedScalar) {
            // Consume the newline (CR or LF or CRLF)
            buffer.advance();
            if (c == '\r' && !buffer.isAtEnd() && buffer.peek() == '\n') {
                buffer.advance();
            }

            // Consume indentation spaces, but DO NOT emit tokens
            while (!buffer.isAtEnd() && buffer.peek() == ' ') {
                buffer.advance();
            }

            // No tokens, no startTokenWindow, just return
            return;
        }


        // 1. First Newline
        String lexeme = (c == '\r' && buffer.peek() == '\n') ? "\r\n" : "\n";
        if (lexeme.length() == 2) buffer.advance();

        // Use column - lexeme.length() to point to the start of the newline
        addExplicitToken(YamlTokenType.NEWLINE, lexeme, buffer.column() - lexeme.length());

        // 2. Keep eating newlines and spaces as long as the line is "empty"
        while (!buffer.isAtEnd()) {
            char next = buffer.peek();

            if (next == ' ') {
                buffer.advance();
            } else if (next == '\n' || next == '\r') {
                // We hit another newline, so previous spaces on this line didn't matter.
                char nc = buffer.advance();
                String nl = (nc == '\r' && buffer.peek() == '\n') ? "\r\n" : "\n";
                if (nl.length() == 2) buffer.advance();

                if (flowDepth == 0) {
                    addExplicitToken(YamlTokenType.NEWLINE, nl, buffer.column() - nl.length());
                }
            } else {
                // We hit actual content (or a comment)
                break;
            }
        }

        int currentColumn = buffer.column();
        int expectedIndent = indentationLevels.peek();

        // 3. Indentation Logic
        if (currentColumn > expectedIndent) {
            indentationLevels.push(currentColumn);
            if (flowDepth == 0) {
                addStructuralToken(YamlTokenType.INDENT, currentColumn - 1);
            }
        } else if (currentColumn < expectedIndent) {
            while (indentationLevels.size() > 1 && indentationLevels.peek() > currentColumn) {
                indentationLevels.pop();
                if (flowDepth == 0) {
                    // Restored the proper structural token generation helper pointing to the exact column index
                    addStructuralToken(YamlTokenType.DEDENT, currentColumn);
                }
            }
        }

        // 4. Sync the start pointer window for the next token discovery phase
        buffer.startTokenWindow();
    }

    /// Scans a quoted scalar, handling escape sequences for double quotes.
    private void scanSingleQuotedScalar() {
        trace("scanSingleQuotedScalar");
        inQuotedScalar = true;

        // 1. Capture the starting coordinates using the buffer state
        int startLine = buffer.line();
        int startColumn = buffer.column() - 1;
        int startOffset = buffer.offset();

        while (!buffer.isAtEnd()) {
            char c = buffer.peek();

            if (c == '\n' || c == '\r') {
                handleNewlineAndIndentation(c);
                continue;
            }

            if (c == '\'' && buffer.peekAhead(1) == '\'') {
                buffer.advance(); // consume first '
                buffer.advance(); // consume second '
                continue;
            }

            if (c == '\'') {
                buffer.advance();
                String lexeme = buffer.getTokenWindowLexeme();
                String content = lexeme.length() >= 2 ? lexeme.substring(1, lexeme.length() - 1) : "";
                addToken(new YamlToken(startLine, startColumn, startOffset, YamlTokenType.SCALAR, lexeme, content, QuoteStyle.SINGLE));
                inQuotedScalar = false;
                return;
            }

            buffer.advance();
        }

        if (buffer.isAtEnd()) {
            addToken(YamlTokenType.ERROR);
        }
    }

    /// Scans a quoted scalar, handling escape sequences for double quotes.
    private void scanDoubleQuotedScalar() {
        trace("scanDoubleQuotedScalar");
        inQuotedScalar = true;

        // 1. Capture the starting coordinates using the buffer state
        int startLine = buffer.line();
        int startColumn = buffer.column() - 1;
        int startOffset = buffer.offset();

        while (!buffer.isAtEnd()) {
            char c = buffer.peek();

            if (c == '\\') {
                buffer.advance();
                if (!buffer.isAtEnd()) buffer.advance();
                continue;
            }

            if (c == '\n' || c == '\r') {
                handleNewlineAndIndentation(c);
                continue;
            }

            if (c == '"') {
                buffer.advance();
                String lexeme = buffer.getTokenWindowLexeme();
                String content = lexeme.length() >= 2 ? lexeme.substring(1, lexeme.length() - 1) : "";
                addToken(new YamlToken(startLine, startColumn, startOffset, YamlTokenType.SCALAR, lexeme, content, QuoteStyle.DOUBLE));
                inQuotedScalar = false;
                return;
            }

            buffer.advance();
        }

        if (buffer.isAtEnd()) {
            addToken(YamlTokenType.ERROR);
        }
    }

    private void scanPlainScalar(char firstChar) {
        trace("scanPlainScalar");

        char prevChar = '\0';
        boolean isFirstChar = true;

        do {
            char c = buffer.peek();

            // 1. Stop at Newlines
            if (c == '\n' || c == '\r') break;

            // 2. Stop at Comments (Space + #)
            // A '#' is only a comment if it is preceded by whitespace, or if it's the very first char
            if (c == '#') {
                if (!isFirstChar && !isWhitespace(prevChar)) {
                    // It's part of the scalar value literal, keep consuming
                } else {
                    break;
                }
            }

            if (FLOW_CONTEXT_SINGLE_CHAR_TOKENS.containsKey(c) && flowDepth > 0) {
                break;
            }

            // 4. The Colon Rule: Stop ONLY if it's a value indicator
            char next = buffer.peekNext();

            if (c == ':' && (isWhitespace(next) || buffer.isAtEnd())) {
                break;
            }

            // 4b. The Explicit Key Rule: Stop if this is a block key indicator
            if (c == '?' && (isWhitespace(next) || buffer.isAtEnd())) {
                break;
            }

            prevChar = buffer.advance();
            isFirstChar = false;
        } while (!buffer.isAtEnd());

        // Get the accumulated text inside our window
        String rawLexeme = buffer.getTokenWindowLexeme();

        if (rawLexeme.isEmpty()) {
             addToken(YamlTokenType.ERROR);
             return;
        }

        // 5. Trim trailing whitespace
        String trimmedLexeme = rawLexeme.stripTrailing();

        // 6. Backup the pointer for every character trimmed
        int trimmedLength = rawLexeme.length() - trimmedLexeme.length();
        for (int i = 0; i < trimmedLength; i++) {
            buffer.backup();
        }

        addToken(YamlTokenType.SCALAR, trimmedLexeme);
    }

    private void scanBlockScalar(char headerChar) {
        int startLine = buffer.line();
        int startColumn = buffer.column() - 1;
        int startOffset = buffer.offset();

        // Parse optional chomping ('S'=Strip, 'K'=Keep, 'C'=Clip) and explicit indentation
        char chomping = 'C';
        int explicitIndent = -1;

        while (!buffer.isAtEnd()) {
            char next = buffer.peek();
            if (next == '-') {
                chomping = 'S';
                buffer.advance();
            } else if (next == '+') {
                chomping = 'K';
                buffer.advance();
            } else if (next >= '1' && next <= '9') {
                explicitIndent = next - '0';
                buffer.advance();
            } else {
                break;
            }
        }

        // Skip to the end of the header line (ignoring trailing comments/spaces)
        while (!buffer.isAtEnd() && buffer.peek() != '\n' && buffer.peek() != '\r') {
            buffer.advance();
        }

        // Consume header line separator
        if (!buffer.isAtEnd()) {
            char next = buffer.advance();
            if (next == '\r' && buffer.peek() == '\n') {
                buffer.advance();
            }
        }

        int currentMargin = indentationLevels.peek();
        int blockIndent = explicitIndent != -1 ? (currentMargin + explicitIndent) : -1;

        List<String> rawLines = new ArrayList<>();
        List<Boolean> isLineDeeplyIndented = new ArrayList<>();
        boolean baseIndentDetermined = (blockIndent != -1);

        while (!buffer.isAtEnd()) {
            int lineStartOffset = buffer.offset();
            int spaces = 0;

            while (!buffer.isAtEnd() && buffer.peek() == ' ') {
                spaces++;
                buffer.advance();
            }

            char next = buffer.peek();
            boolean isEmptyLine = (next == '\n' || next == '\r' || buffer.isAtEnd());

            if (!isEmptyLine) {
                if (!baseIndentDetermined) {
                    if (spaces <= currentMargin) {
                        int rollback = buffer.offset() - lineStartOffset;
                        for (int i = 0; i < rollback; i++) buffer.backup();
                        break;
                    }
                    blockIndent = spaces;
                    baseIndentDetermined = true;
                }

                if (spaces < blockIndent) {
                    int rollback = buffer.offset() - lineStartOffset;
                    for (int i = 0; i < rollback; i++) buffer.backup();
                    break;
                }
            }

            StringBuilder lineContent = new StringBuilder();
            if (baseIndentDetermined && spaces > blockIndent) {
                lineContent.append(" ".repeat(spaces - blockIndent));
            }

            while (!buffer.isAtEnd() && buffer.peek() != '\n' && buffer.peek() != '\r') {
                lineContent.append(buffer.advance());
            }

            rawLines.add(lineContent.toString());
            isLineDeeplyIndented.add(baseIndentDetermined && spaces > blockIndent);

            if (!buffer.isAtEnd()) {
                char ch = buffer.advance();
                if (ch == '\r' && buffer.peek() == '\n') {
                    buffer.advance();
                }
            }
        }

        // Process chomping and literal/folded lines
        StringBuilder result = new StringBuilder();
        int totalLines = rawLines.size();
        int trailingEmptyCount = 0;

        for (int i = totalLines - 1; i >= 0; i--) {
            if (rawLines.get(i).isEmpty()) trailingEmptyCount++;
            else break;
        }

        int contentLines = totalLines - trailingEmptyCount;

        for (int i = 0; i < contentLines; i++) {
            String currentLine = rawLines.get(i);
            result.append(currentLine);

            if (headerChar == '|') {
                result.append('\n');
            } else { // '>' Folded Style
                if (i == contentLines - 1) {
                    result.append('\n');
                } else {
                    String nextLine = rawLines.get(i + 1);
                    boolean currentDeep = isLineDeeplyIndented.get(i);
                    boolean nextDeep = isLineDeeplyIndented.get(i + 1);

                    if (currentLine.isEmpty() || nextLine.isEmpty() || currentDeep || nextDeep) {
                        result.append('\n');
                    } else {
                        result.append(' ');
                    }
                }
            }
        }

        if (chomping == 'K') {
            result.append("\n".repeat(trailingEmptyCount));
        } else if (chomping == 'S' && result.length() > 0 && result.charAt(result.length() - 1) == '\n') {
            result.setLength(result.length() - 1);
        }

        addToken(new YamlToken(startLine, startColumn, startOffset, YamlTokenType.SCALAR, buffer.getTokenWindowLexeme(), result.toString(), QuoteStyle.PLAIN));
    }

    private void scanIdentifier(YamlTokenType type) {
        // while (!buffer.isAtEnd() && isAlphaNumeric(buffer.peek())) {
        while (!buffer.isAtEnd() && (isAlphaNumeric(buffer.peek()) || buffer.peek() == '!')) {
            buffer.advance();
        }
        addToken(type);
    }

    //
    //
    //

    // Small interceptor ensuring that if someone runs the old tokenize() API,
    // tokens get copied to the collection output array correctly.
    private YamlToken queueToken(YamlToken token) {
        if (isLegacyMode && token != null) {
            tokens.add(token);
        }
        return token;
    }

    private void resetCommonState() {
        this.flowDepth = 0;
        this.indentationLevels.clear();
        this.indentationLevels.push(0);

        // Handle UTF-8 BOM if present at start of stream/string
        if (buffer.peek() == '\uFEFF') {
            buffer.advance();
        }
    }

    private YamlToken addToken(YamlToken token) {
        trace("addToken");
        if (token != null) {
            pendingTokens.add(token); // Queue it up so nextToken() can yield it!
        }
        return token;
    }

    private YamlToken addToken(YamlTokenType type) {
        String text = buffer.getTokenWindowLexeme();
        return addToken(new YamlToken(buffer.windowStartLine(), buffer.windowStartColumn(), buffer.windowStartOffset(), type, text, text, QuoteStyle.PLAIN));
    }

    private YamlToken addToken(YamlTokenType type, String lexeme) {
        return addToken(new YamlToken(buffer.windowStartLine(), buffer.windowStartColumn(), buffer.windowStartOffset(), type, lexeme, lexeme, QuoteStyle.PLAIN));
    }

    private void addExplicitToken(YamlTokenType type, String lexeme, int tokenColumn) {
        trace("addExplicitToken");
        addToken(new YamlToken(buffer.windowStartLine(), tokenColumn, buffer.windowStartOffset(), type, lexeme, lexeme, QuoteStyle.PLAIN));
    }

    private void addStructuralToken(YamlTokenType type, int tokenColumn) {
        trace("addStructuralToken");
        addToken(new YamlToken(buffer.windowStartLine(), tokenColumn, buffer.windowStartOffset(), type));
    }

    private boolean willBeMappingKey() {
        int steps = 0;

        // 1. Skip potential quotes
        char firstChar = buffer.peekAhead(steps);
        boolean isQuoted = (firstChar == '\'' || firstChar == '\"');

        if (isQuoted) {
            steps++;
            while (buffer.peekAhead(steps) != '\0' && buffer.peekAhead(steps) != firstChar) {
                // Handle escaped quotes
                if (buffer.peekAhead(steps) == '\\' && buffer.peekAhead(steps + 1) != '\0') {
                    steps++;
                }
                steps++;
            }
            if (buffer.peekAhead(steps) != '\0') steps++; // Consume closing quote
        } else {
            // 2. Skip plain scalar key characters
            while (buffer.peekAhead(steps) != '\0' && isAlphaNumeric(buffer.peekAhead(steps))) {
                steps++;
            }
        }

        // 3. Skip trailing spaces before the indicator
        while (buffer.peekAhead(steps) == ' ') {
            steps++;
        }

        // 4. Check for the value indicator
        return buffer.peekAhead(steps) == ':';
    }

    private boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private boolean isAlphaNumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    //
    // Errors & Diagnostics
    //

    private void error(YamlDiagnosticCode msgCode, Object... details) {
        YamlErrorToken errorToken = new YamlErrorToken(
            buffer.line(),
            buffer.column(),
            buffer.offset(),
            msgCode,
            details
        );
        addToken(errorToken);
        reporter.errorAt(errorToken, msgCode, details);
    }

    private void trace(String method) {
        if (reporter == null || reporter instanceof NoOpReporter) return;
        char c = buffer.peek();
        reporter.trace("S=%03d C=%03d '%s' %03d:%03d %s",
            buffer.offset(), buffer.offset(), currentChar(c), buffer.line(), buffer.column(), method);
    }

    private void trace(String method, String info) {
        if (reporter == null || reporter instanceof NoOpReporter) return;
        char c = buffer.peek();
        reporter.trace("S=%03d C=%03d '%s' %03d:%03d %s: %s",
            buffer.offset(), buffer.offset(), currentChar(c), buffer.line(), buffer.column(), method, info);
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