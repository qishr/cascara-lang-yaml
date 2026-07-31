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
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.common.lang.util.SourceBuffer;
import io.github.qishr.cascara.common.lang.util.SourceInputStreamBuffer;
import io.github.qishr.cascara.common.lang.util.SourceStringBuffer;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.ast.ChompingStyle;
import io.github.qishr.cascara.lang.yaml.ast.ScalarStyle;
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
    }

    Deque<Integer> indentationLevels = new ArrayDeque<>();
    private int flowDepth = 0; // Tracks nesting level of flow context [ ] and { }
    SourceBuffer buffer;
    private List<YamlToken> tokens = new ArrayList<>();
    private boolean isLegacyMode = false;
    private boolean inQuotedScalar = false;

    final Deque<YamlToken> pendingTokens = new ArrayDeque<>();
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

    // TODO: Add to interface
    public List<YamlToken> tokenize(byte[] bytes) {
        return tokenize(new String(bytes, StandardCharsets.UTF_8));
    }

    String debugSource;

    @Override
    public List<YamlToken> tokenize(String source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        this.debugSource = source;

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
            char shouldBeNewline = buffer.peek();
            int pos = buffer.offset();
            if (!buffer.isAtEnd()) {
                char again = buffer.charAt(pos);
                debug(StringUtils.debugString(this.debugSource, pos));
                if (buffer.peek() == '\r' || buffer.peek() == '\n') {
                    handleNewlineAndIndentation(buffer.advance());
                }
            }
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

        if (c == '|') {
            trace(method, "block scalar");
            scanBlockScalar(c);
            return;
        }

        if (c == '>') {
            scanBlockScalar('>');
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
            scanTag();
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
                int dashColumn = tokenStartColumn;
                int currentMargin = indentationLevels.peek();

                if (dashColumn > currentMargin) {
                    indentationLevels.push(dashColumn);
                    debug("scanToke INDENT-1");
                    addStructuralToken(YamlTokenType.INDENT, dashColumn);
                }

                addToken(YamlTokenType.SEQUENCE_ENTRY_INDICATOR);

                if (buffer.peek() == ' ') buffer.advance();

                if (!buffer.isAtEnd() && buffer.peek() != '\n' && buffer.peek() != '\r') {
                    if (willBeMappingKey()) {
                        if (buffer.column() > indentationLevels.peek()) {
                            indentationLevels.push(buffer.column());
                            debug("scanToke INDENT-2");
                            addStructuralToken(YamlTokenType.INDENT, buffer.column());
                        }
                    }
                }
                return;
            }
        }

        if (c == ':') {
            trace(method, "colon");
            if (isWhitespace(buffer.peek()) || buffer.isAtEnd()) {
                addStructuralToken(YamlTokenType.VALUE_INDICATOR, tokenStartColumn);
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
                addStructuralToken(YamlTokenType.KEY_INDICATOR, tokenStartColumn);
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

    /// Scans a [plain scalar](https://yaml.org/spec/1.2.2/#733-plain-style).
    /// Scans a plain scalar. Supports multi-line continuation only when the next
    /// line is a valid YAML continuation line:
    ///   - non-empty
    ///   - not starting a structural token
    ///   - indentation strictly greater than the current structural margin
    ///   - base indentation inferred from the first continuation line
    private void scanPlainScalar(char firstChar) {
        debug("----scanPlainScalar----");

        final int startLine   = buffer.windowStartLine();
        final int startColumn = buffer.windowStartColumn();
        final int startOffset = buffer.windowStartOffset();

        int margin = indentationLevels.peek();
        boolean isDocumentLevel = (margin == 1);
        boolean isKey = false;
        int blockIndent = - 1;
        boolean prevEmpty = true;
        boolean finished = false;

        StringBuilder lexeme = new StringBuilder();
        StringBuilder content = new StringBuilder();

        String line = "" + firstChar;
        String trailingWhitespace = "";
        int lineNum = 0;

        int endOfPrevLine = -1;
        int lastContentPos = -1;
        char prev = 0;

        while (!buffer.isAtEnd() && !finished) {

            int firstContentPos = -1;
            int pos = 0; // Within the current line
            char ch = 0;
            int lineOffset = buffer.offset();

            // TODO: Use StringBuffer

            // Consume a line
            while (!buffer.isAtEnd() && ch != '\n') {
                ch = buffer.peek();
                char next = buffer.peekAhead(1);
                debugString(Character.toString(ch));

                // ch == '*' || ch == '%' || ch == '!' || ch == '&' ||

                // TODO: For complex keys, there must be a space or newline after
                // the key indicator and the value indicator
                if (line.isEmpty() && (ch == '?' || ch == ':' || ch == '#')) { // TODO other chars?
                    finished = true;
                    break;
                }

                // TODO: Do tabs count as whitespace here?
                if ((ch == '#' && prev == ' ' || prev == '\r' || prev == '\n') ||
                    (ch == ':' && (next == ' ' || next == '\r' || next == '\n')) ||
                    (ch == '-' && (next == ' ' || next == '\r' || next == '\n')) ||
                    (flowDepth > 0 && (ch == ',' || ch == '{' || ch == '}' || ch == '[' || ch == ']')))
                {
                    finished = true;
                    if (endOfPrevLine > -1) {
                        debug("---key is next token: " + line);
                        debug(StringUtils.debugString(line));

                        int target;
                        if (ch == '#') {
                            target = buffer.windowStartOffset() + pos - 2;
                        } else if (lastContentPos == -1) {
                            target = buffer.windowStartOffset() + 1;
                        } else {
                            target = endOfPrevLine;
                        }

                        for (int i = buffer.offset(); i > target; i--) {
                            buffer.backup();
                        }

                        // int newPos = buffer.offset();
                        // if (!buffer.isAtEnd()) {
                        //     debug(StringUtils.debugString(this.debugSource, "newPos", newPos));
                        // }

                    } else {
                        // This line is the key
                        debug("---key is this token");
                        isKey = true;
                    }
                    break;
                }

                ch = buffer.advance();

                if (ch == '\n') {
                    endOfPrevLine = buffer.offset() - 1;
                    break;
                }

                if (ch != ' ' && ch != '\t') {

                    // End of document check
                    if (pos == 0 && ch == '.') {
                        char ahead0 = buffer.peekAhead(0);
                        char ahead1 = buffer.peekAhead(1);
                        char ahead2 = buffer.peekAhead(2);
                        if (ahead0 == '.' && ahead1 == '.' && (ahead2 == '\n' || ahead2 == '\r')) {
                            debug("Document end - exiting");
                            finished = true;

                            // Use backup() instead of setOffset() as not all buffers have the latter.
                            // SourceInputStreamBuffer should be amended to provide an efficient way
                            // of doing this.
                            // buffer.setOffset(lineOffset);
                            for (int i = buffer.offset(); i > lineOffset; i--) {
                                buffer.backup();
                            }

                            break;
                        }
                    }

                    // End of scalar check
                    if (pos < blockIndent) {
                        debug("Indent %d < %d - exiting", pos, blockIndent);
                        finished = true;

                        // Use backup() instead of setOffset() as not all buffers have the latter.
                        // SourceInputStreamBuffer should be amended to provide an efficient way
                        // of doing this.
                        // buffer.setOffset(lineOffset);
                        for (int i = buffer.offset(); i > lineOffset; i--) {
                            buffer.backup();
                        }
                        break;
                    }

                    // Start of content
                    if (firstContentPos == -1) {
                        debug("blockIndent detection");
                        // TODO: Does this only apply after the first newline ?
                        firstContentPos = pos;
                        if (lineNum > 0 && blockIndent == -1) {
                            blockIndent = firstContentPos;
                            debug("Block indent = %d", blockIndent);
                        }
                    }

                    if (lineNum == 0 || (blockIndent > -1 && pos >= blockIndent)) {
                        line += trailingWhitespace; // TODO: Do this in block scanner too.
                        line += ch;

                        trailingWhitespace = "";

                        lastContentPos = pos;
                        debug("MOL content (append %d, %d): %s", blockIndent, pos, StringUtils.debugString(line));
                    }
                } else {
                    // Whitespace

                    if (blockIndent > -1 && pos >= blockIndent) {
                        // Block indent has been found and we are at least that far in
                        if (firstContentPos == -1) {
                        //     if (!isDocumentLevel) {
                        //         startOfLine.append(ch);
                        //         debug("SOL whitespace (prepend)");
                        //     }
                        } else {
                            trailingWhitespace += ch;
                            // line += ch;
                            // lastContentPos = pos;
                            debug("MOL whitespace (append)");
                        }
                    } else {
                        // TODO: Clean this up
                        if (blockIndent == -1 && firstContentPos == 0 && lineNum == 0 && !isDocumentLevel && lastContentPos > -1) {
                            // Still on the first line
                            trailingWhitespace += ch;
                            // line += ch;
                            // lastContentPos = pos;
                        } else {
                            debug("blockindent whitespace (discard)");
                        }
                    }
                }
                pos++;
                prev = ch;
            }

            if (isKey) {
                debug("Key: " + StringUtils.debugString(line));
                content.append(line);
                lexeme.append(line);
                break;
            }

            if (!finished) {
                debug("Line: " + StringUtils.debugString(line));
                if (lineNum > 0) {
                    if ((prevEmpty && lineNum > 1)) {
                        content.append('\n');
                        lexeme.append('\n');
                    } else {
                        content.append(' ');
                        lexeme.append(' ');
                    }
                }
                content.append(line);
                lexeme.append(line);
            }

            prevEmpty = firstContentPos == -1;
            lineNum++;
            line = "";
        }

        String contentString = content.toString().stripTrailing();

        debug("Scalar: " + StringUtils.debugString(contentString));

        addToken(new YamlToken(
            startLine,
            startColumn,
            startOffset,
            YamlTokenType.SCALAR,
            lexeme.toString().stripTrailing(),
            contentString,
            ScalarStyle.PLAIN
        ));
    }


    /// Scans a single quoted scalar, handling escape sequences for double quotes.
    private void scanSingleQuotedScalar() {
        // https://yaml.org/spec/1.2.2/#732-single-quoted-style
        trace("scanSingleQuotedScalar");
        inQuotedScalar = true;

        // 1. Capture the starting coordinates using the buffer state
        int startLine = buffer.line();

        // The opening quote is the character before the current one
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
                // TODO: https://yaml.org/spec/1.2.2/#63-line-prefixes
                buffer.advance();
                String lexeme = buffer.getTokenWindowLexeme();
                String content = lexeme.length() >= 2 ? lexeme.substring(1, lexeme.length() - 1) : "";
                addToken(new YamlToken(startLine, startColumn, startOffset, YamlTokenType.SCALAR, lexeme, content, ScalarStyle.SINGLE_QUOTED));
                inQuotedScalar = false;
                return;
            }

            buffer.advance();
        }

        if (buffer.isAtEnd()) {
            error(YamlDiagnosticCode.UNEXPECTED_END_OF_BUFFER);
        }
    }

    /// Scans a quoted scalar, handling escape sequences for double quotes.
    private void scanDoubleQuotedScalar() {
        // https://yaml.org/spec/1.2.2/#731-double-quoted-style
        trace("scanDoubleQuotedScalar");
        inQuotedScalar = true;

        // Remaining tasks:
        // - EOL escapes
        // - Whitespace at end of line
        // - Carriage returns

        // 1. Capture the starting coordinates using the buffer state
        int startLine = buffer.line();

        // The opening quote is one character before the current position
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
                String raw = lexeme.length() >= 2 ? lexeme.substring(1, lexeme.length() - 1) : "";

                int rawLength = raw.length();
                int newlines = 0;
                boolean isLineBlank = true;
                boolean isFirstLine = true;

                int lineEndPos = -1;
                int lastNonWhitespacePos = -1;
                int pos;
                for (pos = 0; pos < rawLength && raw.charAt(pos) != '\n'; pos++) {
                    char potentialWhitespace = raw.charAt(pos);
                    if (potentialWhitespace != ' ' && potentialWhitespace != '\t') {
                        lastNonWhitespacePos = pos;
                    }
                }
                if (pos < rawLength && raw.charAt(pos) == '\n') {
                    lineEndPos = pos;
                }
                if (lastNonWhitespacePos > -1) {
                    debugString(raw, "lastNonWhitespacePos", lastNonWhitespacePos);
                }
                if (lineEndPos > -1) {
                    debugString(raw, "lineEndPos", lineEndPos);
                }

                StringBuilder folded = new StringBuilder();
                for (int i = 0; i < rawLength;) {
                    char ch = raw.charAt(i);

                    if (ch == '\n') {
                        newlines++;
                        isLineBlank = true;
                        isFirstLine = false;
                        lineEndPos = -1;
                        lastNonWhitespacePos = -1;
                        if (i + 1 < rawLength) {
                            for (pos = i + 1; pos < rawLength && raw.charAt(pos) != '\n'; pos++) {
                                char potentialWhitespace = raw.charAt(pos);
                                if (potentialWhitespace != ' ' && potentialWhitespace != '\t') {
                                    lastNonWhitespacePos = pos;
                                }
                            }
                            if (pos < rawLength && raw.charAt(pos) == '\n') {
                                lineEndPos = pos;
                            }
                            if (lastNonWhitespacePos > -1) {
                                debugString(raw, "lastNonWhitespacePos", lastNonWhitespacePos);
                            }
                            if (lineEndPos > -1) {
                                debugString(raw, "lineEndPos", lineEndPos);
                            }
                        }

                    } else {
                        // https://yaml.org/spec/1.2.2/#63-line-prefixes
                        if (isLineBlank && (ch == ' ' || ch == '\t')) {
                            // Leading whitespace is ignored beyond the first line
                            if (isFirstLine) {
                                folded.append(ch);
                            }
                        } else {

                            if (newlines == 1) {
                                folded.append(' ');
                                newlines = 0;
                            } else if (newlines > 1) {
                                folded.append('\n');
                                newlines = 0;
                            }
                            folded.append(ch);
                            isLineBlank = false;

                            // Skip trailing whitespace
                            if (i == lastNonWhitespacePos && i < rawLength - 1) {
                                if (lineEndPos == -1) {
                                    // TODO: This will only happen if the string ends in a newline
                                    // or perhaps if there is a newline in the trailing whitespace?

                                    // Append all whitespace and break
                                    for (i++; i < rawLength; i++) {
                                        folded.append(raw.charAt(i));
                                    }
                                } else {
                                    // Skip to the newline
                                    i = lineEndPos;
                                    continue;
                                }
                            }
                        }
                    }
                    i++;
                }

                if (newlines == 1) {
                    folded.append(' ');
                } else if (newlines > 1) {
                    folded.append('\n');
                }

                String content = folded.toString();

                addToken(new YamlToken(startLine, startColumn, startOffset, YamlTokenType.SCALAR, lexeme, content, ScalarStyle.DOUBLE_QUOTED));
                inQuotedScalar = false;
                return;
            }

            buffer.advance();
        }

        if (buffer.isAtEnd()) {
            error(YamlDiagnosticCode.UNEXPECTED_END_OF_BUFFER);
        }

        inQuotedScalar = false;
    }

    /// Scans a folded or literal block scalar
    public void scanBlockScalar(char headerChar) {
        // Remaining tasks:
        // - EOL escapes
        // - Whitespace at end of line
        // - Carriage returns

        int startLine = buffer.line();
        int startColumn = buffer.column();
        int startOffset = buffer.offset();

        ScalarStyle scalarStyle = (headerChar == '|')
            ? ScalarStyle.LITERAL
            : ScalarStyle.FOLDED;

        ChompingStyle chompingStyle = ChompingStyle.CLIP;
        int explicitIndent = -1;

        String headerString = buffer.getTokenWindowLexeme();

        // Parse chomping and indent indicator
        // https://yaml.org/spec/1.2.2/#8112-block-chomping-indicator
        while (!buffer.isAtEnd()) {
            char next = buffer.peek();
            if (next == '-') {
                chompingStyle = ChompingStyle.STRIP;
                buffer.advance();
            } else if (next == '+') {
                chompingStyle = ChompingStyle.KEEP;
                buffer.advance();
            } else if (next >= '1' && next <= '9') {
                // TODO: Can this be >9 ?
                explicitIndent = next - '0';
                buffer.advance();
            } else {
                break;
            }
        }

        // Skip to the end of the header line
        while (!buffer.isAtEnd() && buffer.peek() != '\n' && buffer.peek() != '\r') {
            // TODO: Comments
            buffer.advance();
        }

        int currentMargin = indentationLevels.peek();

        debug("Current margin = %d", currentMargin);
        debug("Explicit indent = %d", explicitIndent);

        // Line folding allows long lines to be broken for readability, while retaining
        // the semantics of the original long line. If a line break is followed by an
        // empty line, it is trimmed; the first line break is discarded and the rest are
        // retained as content.

        // Otherwise (the following line is not empty), the line break is converted to a
        // single space (x20).

        // In the folded block style, the final line break and trailing empty lines are
        // subject to chomping and are never folded. In addition, folding does not apply
        // to line breaks surrounding text lines that contain leading white space. Note
        // that such a more-indented line may consist only of such leading white space.

        // The combined effect of the block line folding rules is that each “paragraph”
        // is interpreted as a line, empty lines are interpreted as a line feed and the
        // formatting of more-indented lines is preserved.

        int blockIndent = explicitIndent == -1 ? -1 : explicitIndent + currentMargin - 1;
        boolean deeplyIndented = false;
        boolean prevDeeplyIndented = false;
        boolean prevEmpty = true;
        boolean finished = false;
        int lineNum = 0;
        int consecutiveNewlines = 0;

        // TODO: Include header details in header line - style, explicitIndent...
        StringBuilder lexeme = new StringBuilder().append(headerString).append('\n');

        StringBuilder content = new StringBuilder();

        while (!buffer.isAtEnd() && !finished) {
            int firstContentPos = -1;
            int pos = 0; // Within the current line
            char ch = 0;
            int lineOffset = buffer.offset();
            StringBuilder startOfLine = new StringBuilder();

            // For debugging only. Not used in token.
            String line = "";

            // Consume a line
            while (!buffer.isAtEnd() && ch != '\n') {
                ch = buffer.advance();
                debugString(Character.toString(ch));

                if (ch == '\n') {
                    consecutiveNewlines++;
                    break;
                }
                if (ch != ' ' && ch != '\t') {

                    // End of document
                    if (pos == 0 && ch == '.') {
                        char ahead0 = buffer.peekAhead(0);
                        char ahead1 = buffer.peekAhead(1);
                        char ahead2 = buffer.peekAhead(2);
                        if (ahead0 == '.' && ahead1 == '.' && (ahead2 == '\n' || ahead2 == '\r')) {
                            debug("Document end - exiting");
                            finished = true;

                            // Use backup() instead of setOffset() as not all buffers have the latter.
                            // SourceInputStreamBuffer should be amended to provide an efficient way
                            // of doing this.
                            // buffer.setOffset(lineOffset);
                            for (int i = buffer.offset(); i > lineOffset; i--) {
                                buffer.backup();
                            }

                            break;
                        }
                    }

                    // End of scalar
                    if (pos < blockIndent) {
                        debug("Indent %d < %d - exiting", pos, blockIndent);
                        finished = true;

                        // Use backup() instead of setOffset() as not all buffers have the latter.
                        // SourceInputStreamBuffer should be amended to provide an efficient way
                        // of doing this.
                        // buffer.setOffset(lineOffset);
                        for (int i = buffer.offset(); i > lineOffset; i--) {
                            buffer.backup();
                        }

                        break;
                    }

                    // Start of content
                    if (firstContentPos == -1) {
                        firstContentPos = pos;
                        if (lineNum > 0 && blockIndent == -1) {
                            blockIndent = firstContentPos;
                            debug("Block indent = %d", blockIndent);
                        }
                        if (pos == blockIndent) {
                            deeplyIndented = false;
                            debug("Indented = false");
                        }
                        else if (pos > blockIndent) {
                            deeplyIndented = true;
                        }

                        boolean foldedNewline = consecutiveNewlines > 1;
                        boolean deeplyNewline = deeplyIndented && lineNum > 1;
                        boolean literalNewline = scalarStyle == ScalarStyle.LITERAL && lineNum > 1;

                        if (foldedNewline || deeplyNewline || literalNewline) {

                            int newLines = consecutiveNewlines -
                                    (deeplyIndented || prevDeeplyIndented || literalNewline ? 0 : 1);

                            debug("Adding %d new lines %b %b %b",
                                newLines, foldedNewline, deeplyNewline, literalNewline);

                            for (int i = 0; i < newLines; i++) {
                                content.append('\n');
                                lexeme.append('\n');
                                line += '\n';
                                debug(">1 newline (append): " + StringUtils.debugString(line));
                            }
                        }
                        else if (consecutiveNewlines == 1 && !literalNewline) {
                            if (!prevEmpty) {
                                content.append(' ');
                                lexeme.append(' ');
                                line += ' ';
                                debug("=1 newline (append): " + StringUtils.debugString(line));
                            }
                        }
                        content.append(startOfLine);
                        lexeme.append(startOfLine.toString());
                        line += startOfLine.toString();
                        debug("SOC content (append): " + StringUtils.debugString(line));
                    }

                    if (blockIndent > -1 && pos >= blockIndent) {
                        content.append(ch);
                        lexeme.append(ch);
                        line += ch;
                        debug("MOL content (append %d, %d): %s", blockIndent, pos, StringUtils.debugString(line));
                    }
                    consecutiveNewlines = 0;
                } else {
                    // Whitespace

                    if (blockIndent > -1 && pos >= blockIndent) {

                        // https://yaml.org/spec/1.2.2/#literal-style
                        // Inside literal scalars, all (indented) characters are considered to be
                        // content, including white space characters.
                        if (scalarStyle == ScalarStyle.LITERAL) {
                            if (lineNum > 1) {
                                for (int i = 0; i < consecutiveNewlines; i++) {
                                    content.append('\n');
                                    lexeme.append('\n');
                                    line += '\n';
                                    debug(">LIT newline (append): " + StringUtils.debugString(line));
                                }
                            }

                            // Whitespace can start the content for a LITERAL if the block indent is explicit
                            if (firstContentPos == -1) {
                                firstContentPos = pos;
                                if (pos == blockIndent) {
                                    deeplyIndented = false;
                                    debug("Indented = false");
                                }
                                else if (pos > blockIndent) {
                                    deeplyIndented = true;
                                }
                            }
                            consecutiveNewlines = 0;
                        }

                        // Block indent has been found and we are at least that far in
                        // TODO: Don't append trailing spaces?
                        if (firstContentPos == -1) {
                            startOfLine.append(ch);
                            debug("SOL whitespace (prepend)");
                        } else {
                            content.append(ch);
                            lexeme.append(ch);
                            line += ch;
                            debug("MOL whitespace (append)");
                        }
                    } else {
                        debug("blockindent whitespace (discard)");
                    }
                }
                pos++;
            }

            debug("Line: " + StringUtils.debugString(line));
            prevDeeplyIndented = deeplyIndented;
            prevEmpty = firstContentPos == -1;
            lineNum++;
        }

        debug("Trailing newlines = %d", consecutiveNewlines);

        // Perform Chomping
        // https://yaml.org/spec/1.2.2/#8112-block-chomping-indicator
        if (chompingStyle == ChompingStyle.KEEP) {
            content.append("\n".repeat(consecutiveNewlines));
        }
        else if (chompingStyle == ChompingStyle.STRIP) {
            int contentLen = content.length();
            if (contentLen > 0 && content.charAt(contentLen - 1) == '\n' ) {
                content.setLength(contentLen - 1);
            }
        }
        else {
            if (consecutiveNewlines > 0) {
                content.append('\n');
            }
        }

        debug("Scalar: %s", StringUtils.debugString(content.toString()));

        addToken(new YamlToken(
            startLine, startColumn, startOffset,
            YamlTokenType.SCALAR,
            lexeme.toString(),
            content.toString(),
            scalarStyle
        ));
    }


    private void scanIdentifier(YamlTokenType type) {
        while (!buffer.isAtEnd() && (isAlphaNumeric(buffer.peek()) || buffer.peek() == '!')) {
            buffer.advance();
        }
        addToken(type);
    }


    private void scanTag() {
        while (!buffer.isAtEnd() &&
                (isAlphaNumeric(buffer.peek()) ||
                 buffer.peek() == '!' ||
                 buffer.peek() == '%'))
        {
            buffer.advance();
        }
        addToken(YamlTokenType.TAG);
    }

    private void handleNewlineAndIndentation(char c) {

        // TOOO: This method is not meant to be called from scan*leQuotedScalar

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
        addStructuralToken(YamlTokenType.NEWLINE, buffer.column() - lexeme.length() + 1);

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
                    addStructuralToken(YamlTokenType.NEWLINE, buffer.column() - nl.length() + 1);
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
                // debug("handleNewlineAndIndentation INDENT");
                addStructuralToken(YamlTokenType.INDENT, currentColumn);
            }
        }

        else if (currentColumn < expectedIndent) {
            while (indentationLevels.size() > 1 && indentationLevels.peek() > currentColumn) {
                indentationLevels.pop();
                if (flowDepth == 0) {
                    // debug("handleNewlineAndIndentation DEDENT");
                    addStructuralToken(YamlTokenType.DEDENT, currentColumn);
                }
            }
        }

        // 4. Sync the start pointer window for the next token discovery phase
        buffer.startTokenWindow();
    }


    //
    //
    //


    private void resetCommonState() {
        indentationLevels = new ArrayDeque<>();
        flowDepth = 0; // Tracks nesting level of flow context [ ] and { }
        tokens = new ArrayList<>();
        isLegacyMode = false;
        inQuotedScalar = false;
        streamStarted = false;
        streamEnded = false;

        this.flowDepth = 0;
        this.indentationLevels.clear();
        this.indentationLevels.push(1);

        pendingTokens.clear();

        // Handle UTF-8 BOM if present at start of stream/string
        if (buffer.peek() == '\uFEFF') {
            buffer.advance();
        }
    }

    /// Small interceptor ensuring that if someone runs the old tokenize() API,
    /// tokens get copied to the collection output array correctly.
    private YamlToken queueToken(YamlToken token) {
        if (isLegacyMode && token != null) {
            tokens.add(token);
        }
        return token;
    }

    // TODO: Why do we have both queueToken (above) and addToken (below) ?
    private YamlToken addToken(YamlToken token) {
        debug("addToken: " + token.getType());
        if (token != null) {
            pendingTokens.add(token); // Queue it up so nextToken() can yield it!
        }
        return token;
    }

    private YamlToken addToken(YamlTokenType type) {
        String text = buffer.getTokenWindowLexeme();
        // TODO: Is this meant to be PLAIN?
        return addToken(new YamlToken(buffer.windowStartLine(), buffer.windowStartColumn(), buffer.windowStartOffset(), type, text, text, ScalarStyle.PLAIN));
    }

    private YamlToken addToken(YamlTokenType type, String lexeme) {
        // TODO: Is this meant to be PLAIN?
        return addToken(new YamlToken(buffer.windowStartLine(), buffer.windowStartColumn(), buffer.windowStartOffset(), type, lexeme, lexeme, ScalarStyle.PLAIN));
    }

    private void addStructuralToken(YamlTokenType type, int tokenColumn) {
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
        boolean willBeMappingKey = buffer.peekAhead(steps) == ':';
        return willBeMappingKey;
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
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.TRACE)) return;

        char c = buffer.peek();
        reporter.trace("S=%03d C=%03d '%s' %03d:%03d %s",
            buffer.offset(), buffer.offset(), StringUtils.visibleChar(c), buffer.line(), buffer.column(), method);
    }

    private void trace(String method, String info) {
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.TRACE)) return;

        char c = buffer.peek();
        reporter.trace("S=%03d C=%03d '%s' %03d:%03d %s: %s",
            buffer.offset(), buffer.offset(), StringUtils.visibleChar(c), buffer.line(), buffer.column(), method, info);
    }

    private void debug(String message, Object... details) {
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.DEBUG)) return;
        reporter.debug(message, details);
    }

    private void debugString(String string) {
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.DEBUG)) return;

        reporter.debug(StringUtils.debugString(string));
    }

    private void debugString(String string, String name, int pos) {
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.DEBUG)) return;

        reporter.debug(StringUtils.debugString(string, name, pos));
    }
}