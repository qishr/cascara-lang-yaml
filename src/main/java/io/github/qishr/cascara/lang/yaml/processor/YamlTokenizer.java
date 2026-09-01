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

import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.common.lang.token.TokenCategory;
import io.github.qishr.cascara.common.lang.util.SourceBuffer;
import io.github.qishr.cascara.common.lang.util.SourceInputStreamBuffer;
import io.github.qishr.cascara.common.lang.util.SourceStringBuffer;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.diagnostic.YamlDiagnosticCode;
import io.github.qishr.cascara.lang.yaml.internal.AbstractYamlProcessor;
import io.github.qishr.cascara.lang.yaml.token.YamlErrorToken;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;
import io.github.qishr.cascara.lang.yaml.util.ChompingStyle;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;

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

    private boolean isLegacyMode = false;

    Deque<Integer> indentationLevels = new ArrayDeque<>();
    SourceBuffer buffer;
    final Deque<YamlToken> pendingTokens = new ArrayDeque<>();

    private boolean streamStarted = false;
    private boolean streamEnded = false;
    private YamlToken previousNonWhitespaceToken;
    private int flowDepth = 0; // Tracks nesting level of flow context [ ] and { }
    private List<YamlToken> tokens = new ArrayList<>();
    private boolean lineHasTabs = false;
    private boolean isDocumentLevel;
    private String leadingWhitespace = "";
    private boolean lineHasContent;

    String debugSource;

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
        this.isLegacyMode = false;

        this.debugSource = text; // TODO: Remove this

        this.buffer = new SourceStringBuffer();
        this.buffer.open(text);
        setup();
    }

    @Override
    public void open(Reader reader) {
        this.isLegacyMode = false;
        buffer = new SourceInputStreamBuffer();
        this.buffer.open(reader);
        setup();
    }

    @Override
    public void open(InputStream is) {
        this.isLegacyMode = false;
        this.buffer = new SourceInputStreamBuffer();
        this.buffer.open(is);
        setup();
    }

    // TODO: Add to interface
    public List<YamlToken> tokenize(byte[] bytes) {
        return tokenize(new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public List<YamlToken> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        this.debugSource = text;

        this.tokens = new ArrayList<>();
        this.streamStarted = false;
        this.streamEnded = false;

        open(text);
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
            // Handle UTF-8 BOM if present at start of stream
            if (buffer.peek() == '\uFEFF') {
                advance();
            }
            return queueToken(new YamlToken(buffer.line(), buffer.column(), buffer.offset(), YamlTokenType.STREAM_START));
        }

        // 2. Loop until we either find a token or hit the end of the input buffer
        while (!buffer.isAtEnd() && pendingTokens.isEmpty()) {
            buffer.startTokenWindow();
            scanToken();

            // char shouldBeNewline = buffer.peek();
            int pos = buffer.offset();

            if (!buffer.isAtEnd()) {

                // char again = buffer.charAt(pos);
                debug(StringUtils.debugString(this.debugSource, pos));

                if (buffer.peek() == '\r' || buffer.peek() == '\n') {
                    handleNewlineAndIndentation(advance());
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

            String fileEnding = Character.toString(buffer.previous());
            YamlToken eofToken = new YamlToken(finalLine, finalCol, finalOffset, YamlTokenType.EOF, fileEnding);

            pendingTokens.add(eofToken);
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

        char c = buffer.peek();

        if (c == '\n' || c == '\r') {
            advance();
            trace("scanToken", "");
            handleNewlineAndIndentation(c);
            return;
        }

        if (c == ' ' || c == '\t') {
            if (!lineHasContent) {
                leadingWhitespace += c;
            }
            if (c == '\t') {
                lineHasTabs = true;
            }
            advance();
            trace(method, "space or tab");
            return;
        }

        if (c == '|') {
            scanScalar(ScalarStyle.LITERAL);
            isDocumentLevel = false;
            return;
        }

        if (c == '>') {
            scanScalar(ScalarStyle.FOLDED);
            isDocumentLevel = false;
            return;
        }

        if (c == '!') {
            advance();
            scanTag();
            lineHasContent = true;
            return;
        }

        if (c == '-' && buffer.peekNext() == '-' && buffer.peekAhead(2) == '-' &&
            (isWhitespace(buffer.peekAhead(3)))
        ) {
            advance();
            trace(method, "dash1");
            advance(); advance();
            addToken(YamlTokenType.DOCUMENT_START);
            isDocumentLevel = true;
            lineHasContent = true;
            return;
        }

        if (c == '.' && buffer.peekNext() == '.' && buffer.peekAhead(2) == '.') {
            advance();
            trace(method, "dot");
            advance(); advance();
            addToken(YamlTokenType.DOCUMENT_END);
            isDocumentLevel = true;
            lineHasContent = true;
            return;
        }

        if (c == '#') {

            if (!isWhitespace(buffer.previous()) && buffer.offset() != 0) {
                error(YamlDiagnosticCode.COMMENT_NOT_SEPARATED);
            }

            advance();

            trace(method, "hash");
            while (buffer.peek() != '\n' && buffer.peek() != '\r' && !buffer.isAtEnd()) {
                advance();
            }
            addToken(YamlTokenType.COMMENT);
            lineHasContent = true;
            return;
        }

        if (FLOW_CONTEXT_SINGLE_CHAR_TOKENS.containsKey(c)) {
            advance();
            YamlTokenType type = FLOW_CONTEXT_SINGLE_CHAR_TOKENS.get(c);

            // Track entering/leaving flow context
            if (type == YamlTokenType.SEQUENCE_START || type == YamlTokenType.MAP_START) {
                flowDepth++;
            } else if (type == YamlTokenType.SEQUENCE_END || type == YamlTokenType.MAP_END) {
                flowDepth--;
            }

            addToken(type);
            lineHasContent = true;
            return;
        }

        if (c == '-') {
            if (lineHasTabs) {
                error(YamlDiagnosticCode.TABS_NOT_ALLOWED_AS_INDENTATION);
            }
            if (buffer.offset() + 1 >= buffer.length()) {
                isDocumentLevel = false;
                advance();
                addToken(YamlTokenType.SEQUENCE_ENTRY_INDICATOR);
                lineHasContent = true;
                return;
            }
            YamlTokenType type = FLOW_CONTEXT_SINGLE_CHAR_TOKENS.get(buffer.peekNext());
            boolean followedByFlowContextCharacter = type != null;
            if (isWhitespace(buffer.peekNext()) || followedByFlowContextCharacter) {
                isDocumentLevel = false;
                advance();
                int dashColumn = tokenStartColumn;
                int currentMargin = indentationLevels.peek();

                if (dashColumn > currentMargin) {
                    indentationLevels.push(dashColumn);
                    debug("scanToke INDENT-1");
                    addStructuralToken(YamlTokenType.INDENT, dashColumn);
                }

                YamlToken indicator = new YamlToken(
                    buffer.windowStartLine(),
                    buffer.windowStartColumn(),
                    buffer.windowStartOffset(),
                    YamlTokenType.SEQUENCE_ENTRY_INDICATOR
                );

                if (buffer.peek() == ' ') {
                    advance();
                } else if (buffer.peek() == '\t') {
                    advance();
                    indicator.setFollowedByTab(true);
                }

                addToken(indicator);

                if (!buffer.isAtEnd() && buffer.peek() != '\n' && buffer.peek() != '\r') {
                    if (willBeMappingKey()) {
                        if (buffer.column() > indentationLevels.peek()) {
                            indentationLevels.push(buffer.column());
                            debug("scanToke INDENT-2");
                            addStructuralToken(YamlTokenType.INDENT, buffer.column());
                        }
                    }
                }
                lineHasContent = true;
                return;
            }
        }

        if (c == ':') {
            char next = buffer.peekAhead(1);

            if (lineHasTabs) {
            // if (lineHasTabs || next == '\t') {
                error(YamlDiagnosticCode.TABS_NOT_ALLOWED_AS_INDENTATION);
            }

            boolean isAtEnd = buffer.offset() + 1 == buffer.length();
            boolean prevTokenWasColon = (previousNonWhitespaceToken != null && previousNonWhitespaceToken.getType() == YamlTokenType.VALUE_INDICATOR);

            boolean nextCharIsWhitespace = isWhitespace(next);

            boolean prevTokenWasScalar = (previousNonWhitespaceToken != null &&
                previousNonWhitespaceToken.getType() == YamlTokenType.SCALAR);
            boolean prevTokenWasFlowEnd = (previousNonWhitespaceToken != null &&
                (previousNonWhitespaceToken.getType() == YamlTokenType.MAP_END ||
                previousNonWhitespaceToken.getType() == YamlTokenType.SEQUENCE_END));

            boolean cannotBeScalar = isAtEnd || nextCharIsWhitespace ||
                    ((prevTokenWasScalar || prevTokenWasFlowEnd) && flowDepth > 0);

            // if (cannotBeScalar && !prevTokenWasColon) {
            if (cannotBeScalar) {
                isDocumentLevel = false;
                addToken(YamlTokenType.VALUE_INDICATOR);
                advance();
                trace(method, "colon");
                lineHasContent = true;
                return;
            }
        }

        if (c == '\'') {
            scanScalar(ScalarStyle.SINGLE_QUOTED);
            isDocumentLevel = false;
            return;
        }

        if (c == '\"') {
            scanScalar(ScalarStyle.DOUBLE_QUOTED);
            isDocumentLevel = false;
            return;
        }

        if (c == '&') {
            advance();
            trace(method, "ampersand");
            scanIdentifier(YamlTokenType.ANCHOR);
            isDocumentLevel = false;
            lineHasContent = true;
            return;
        }

        if (c == '*') {
            advance();
            trace(method, "asterisk");
            scanIdentifier(YamlTokenType.ALIAS);
            isDocumentLevel = false;
            lineHasContent = true;
            return;
        }

        if (c == '?' &&  (isWhitespace(buffer.peekNext()) || buffer.isAtEnd())) {
            advance();
            // A '?' is only a structural marker if followed by whitespace/newline/EOF
            trace(method, "question mark");
            addStructuralToken(YamlTokenType.KEY_INDICATOR, tokenStartColumn);
            isDocumentLevel = false;
            lineHasContent = true;
            return;
        }

        if (c == '%') {
            advance();
            trace(method, "directive");
            // Directives are line-oriented metadata (e.g., %YAML 1.2)
            while (buffer.peek() != '\n' && buffer.peek() != '\r' && !buffer.isAtEnd()) {
                advance();
            }
            addToken(YamlTokenType.DIRECTIVE);
            return;
        }

        // advance();
        // scanPlainScalar(c);
        scanScalar(ScalarStyle.PLAIN);
        isDocumentLevel = false;
        lineHasContent = true;
    }

    void scanScalar(final ScalarStyle scalarStyle) {
        debug("\n**************** scanScalar " + scalarStyle + " **************** ");

        debug(StringUtils.debugString(this.debugSource, buffer.offset()));

        StringBuilder content = new StringBuilder();
        StringBuilder lexeme = new StringBuilder();

        int startLine = buffer.line();
        int startColumn = buffer.column();
        int startOffset = buffer.offset();

        int currentMargin = indentationLevels.peek() - 1;
        int explicitIndent = -1;
        boolean isBlock = false;
        boolean isQuoted = false;
        ChompingStyle chompingStyle = ChompingStyle.CLIP;

        // 1. Handle header or opening quote

        if (scalarStyle == ScalarStyle.SINGLE_QUOTED || scalarStyle == ScalarStyle.DOUBLE_QUOTED) {
            lexeme.append(advance()); // consume the opening quote
            isQuoted = true;
        } else if (scalarStyle == ScalarStyle.FOLDED || scalarStyle == ScalarStyle.LITERAL) {
            advance();
            // Parse chomping and indent indicator
            // https://yaml.org/spec/1.2.2/#8112-block-chomping-indicator
            char prev = '\0';
            while (!buffer.isAtEnd()) {
                char next = buffer.peek();
                debug("Header char: " + StringUtils.debugString(""+next));
                if (next == '-') {
                    chompingStyle = ChompingStyle.STRIP;
                    advance();
                } else if (next == '+') {
                    chompingStyle = ChompingStyle.KEEP;
                    advance();
                } else if (next >= '1' && next <= '9') {
                    // TODO: Can this be >9 ?
                    explicitIndent = next - '0';
                    advance();
                // } else if (next ==' ' || next == '\t' || next =='#' || next == '\r' || next == '\n') {
                } else if (next =='#' || next == '\r' || next == '\n') {
                    break;
                } else if (next ==' ' || next == '\t') {
                    advance();
                } else {
                    error(YamlDiagnosticCode.BLOCK_SCALAR_HEADER_EXTRA, next);
                    break;
                }
                prev = next;
            }

            char c = buffer.peek();
            if (c == '#') {
                if (!(prev == ' ' || prev == '\t')) {
                    error(YamlDiagnosticCode.COMMENT_NOT_SEPARATED);
                }
                // TODO: inline comments
                advance();
                while (!buffer.isAtEnd() && buffer.peek() != '\n') {
                    advance();
                }
            } else if (c != '\n') {
                while (!buffer.isAtEnd() && buffer.peek() != '\n') {
                    if (buffer.peek() != ' ' && buffer.peek() != '\t' && buffer.peek() != '\r' ) {
                        error(YamlDiagnosticCode.BLOCK_SCALAR_HEADER_EXTRA, buffer.peek());
                    }
                    advance();
                }
            }

            // while (!buffer.isAtEnd() && buffer.peek() != '\n') {
            // }
            advance(); // consume the newline
            lexeme.append(buffer.getTokenWindowLexeme());
            isBlock = true;
        }

        int firstLineIndent = 0;
        // Amount of indentation spaces
        int blockIndent = explicitIndent == -1 ? -1 : explicitIndent + currentMargin;

        int lineNum = 0;
        boolean foundContent = false;
        String currLine = "";
        String currLineTrimmed = "";
        String currLineLexeme = "";

        int charOffset = 0;
        int trimmedCharOffset = 0;
        int currFirstContentOffset = -1;
        int prevNonWhitespaceOffset = -1;

        ScalarAction action = ScalarAction.CONTINUE;
        List<String> trailingBlankLines = new ArrayList<>();

        boolean currHasExtraIndent = false;
        boolean prevHasExtraIndent = false;
        boolean alreadyHadContent = false;
        boolean prevEolWasEscaped = false;
        boolean prevLineWasBlank = false;
        boolean doNotTrimLeading = false;

        boolean startsOnNewLine = previousNonWhitespaceToken == null
            ? true
            : startLine > previousNonWhitespaceToken.getStartLine();

        // spaces that were consumed already but were part of the
        // line that is about to be scanned.
        int addedLeadingWhitespace = 0;
        boolean isFirstChar = true;
        boolean isCharEscaped = false;
        int removedChars = 0;

        int mostSpacesInBlankLine = 0;

        // 2. Scan the scalar

        while (!buffer.isAtEnd() && action == ScalarAction.CONTINUE) {
            prevNonWhitespaceOffset = -1;
            currFirstContentOffset = -1;
            foundContent = false;
            isCharEscaped = false;
            currLine = peekLine();

            debug("U LINE " + lineNum + ": " + StringUtils.debugString(currLine));

            // Test NP9H
            // If there's a backslash before the content, remove it and the whitespace preceeding it.
            if (scalarStyle == ScalarStyle.DOUBLE_QUOTED) {
                for (int i = 0; i < currLine.length() - 1; i++) {
                    char c = currLine.charAt(i);
                    if (c == '\\') {
                        c = currLine.charAt(i + 1);
                        // TODO: Does this backslash have to be followed by whitespace?
                        if (c == ' ' || c == '\t') {
                            doNotTrimLeading = true;
                            currLine = currLine.substring(i + 1);
                            removedChars+=i + 1;
                            break;
                        }
                    } else if (c != ' ' && c != '\t') {
                        break;
                    }
                }
            }

            if (lineNum == 0 && startsOnNewLine && !leadingWhitespace.isEmpty()) {
                firstLineIndent = leadingWhitespace.length();
                for (int i = 0; i < leadingWhitespace.length(); i++) {
                    char ch = leadingWhitespace.charAt(i);
                    if (ch == '\r' || ch == '\n') {
                        break;
                    }
                    if (ch != ' ') {
                        firstLineIndent = i;
                        break;
                    }
                }
            }

            // Make sure the extra spaces don't go into the lexeme.
            // They are just to keep scanning less complicated (!!)

            // This isn't entirely right -
            // We don't want to add leading spaces if they ...??????
            // TODO: Track leading whitespace and add it as-is. Don't just add spaces.

            addedLeadingWhitespace = 0;
            if (lineNum == 0 && startsOnNewLine && scalarStyle == ScalarStyle.PLAIN) {
                // If this is the first line of an unquoted scalar that
                // starts on a line by itself, the tokenizer has already
                // consumed the leading whitespace.

                debug("scalar starts on new line");
                // addedLeadingWhitespace = startColumn - 1;
                addedLeadingWhitespace = leadingWhitespace.length();
                // // Add it back in so this line is intact.
                // currLine = " ".repeat(addedLeadingWhitespace) + currLine;
                currLine = leadingWhitespace + currLine;
            }

            debug("M LINE " + lineNum + ": " + StringUtils.debugString(currLine));

            if (currLine.length() > 3 && isWhitespace(currLine.charAt(3))) {
                if (currLine.startsWith("...")) {
                    action = ScalarAction.STOP_BLOCKINDENT;
                    break;
                }
                if (currLine.startsWith("---")) {
                    action = ScalarAction.STOP_BLOCKINDENT;
                    break;
                }
            }

            // Find final non-whitespace character
            int finalNonWhitespaceOffset = currLine.length() - 1;
            while (finalNonWhitespaceOffset > 0 && isWhitespace(currLine.charAt(finalNonWhitespaceOffset))) {
                finalNonWhitespaceOffset--;
            }

            // 2a. Line scanning

            for (charOffset = 0; charOffset < currLine.length(); charOffset++) {
                debugString(currLine, charOffset);

                char c = currLine.charAt(charOffset);

                action = scalarAction(currLine, foundContent, charOffset, scalarStyle, lineNum == 0, isFirstChar);
                if (action != ScalarAction.CONTINUE) {
                    break;
                }

                // Subsequent lines of a plain scalar must be indented at least one space
                // unless it is document level.
                if ((scalarStyle == ScalarStyle.PLAIN || isQuoted) &&
                    !isDocumentLevel && lineNum > 0 && charOffset == 0 &&
                    c != ' ' && c != '\r' && c != '\n') { // && c != '\t'
                    // debug("plain scalar finished - no ident");
                    action = ScalarAction.STOP_BLOCKINDENT;
                    break;
                }

                if (c != ' ' && c != '\r' && c!= '\n') {
                    if (isCharEscaped) {
                        if (scalarStyle != ScalarStyle.PLAIN &&
                            scalarStyle != ScalarStyle.SINGLE_QUOTED &&
                            scalarStyle != ScalarStyle.LITERAL &&
                            !isValidEscape(c, scalarStyle)) {
                            error(YamlDiagnosticCode.INVALID_ESCAPE, "\\" + c);
                        }
                        isCharEscaped = false;
                    } else {
                        if (c == '\\') {
                            isCharEscaped = true;
                        }
                    }

                    if (blockIndent == -1) {
                        if (isBlock) {
                            blockIndent = charOffset;
                            debug("Block indent set: " + blockIndent);

                            // TODO: set mostSpacesInBlankLine

                            if (isBlock && mostSpacesInBlankLine > blockIndent) {
                                error(YamlDiagnosticCode.EXPLICIT_INDENTATION_INDICATOR_NEEDED);
                            }
                        } else {
                            if (startsOnNewLine || lineNum > 0) {
                                blockIndent = charOffset;
                            }
                        }
                    }

                    if (!isQuoted && blockIndent > -1 && charOffset < blockIndent) {
                        action = ScalarAction.STOP_BLOCKINDENT;
                        break;
                    } else {
                        debug("Block indent = " + blockIndent + " and charOffset = " + charOffset);
                    }

                    if (c != '\t' && (!isQuoted || startsOnNewLine || lineNum > 0)) {
                        // Determine extra indent
                        if (currFirstContentOffset == -1) {
                            currFirstContentOffset = charOffset;
                            if (charOffset > blockIndent) {
                                currHasExtraIndent = true;
                            } else {
                                currHasExtraIndent = false;
                            }
                        }
                    }
                    if (c != '\t') {
                        prevNonWhitespaceOffset = charOffset;
                        foundContent = true;
                    }
                }

                // Plain scalar whitespace handling
                if (scalarStyle == ScalarStyle.PLAIN) {
                    if (charOffset == finalNonWhitespaceOffset) {
                        break;
                    }
                }

                isFirstChar = false;
            }

            if (isBlock && !foundContent) { // && lineNum > 0
                if (charOffset - 1 > mostSpacesInBlankLine) {
                    mostSpacesInBlankLine = charOffset - 1;
                }
            }

            // 2b. Line trimming

            if (action == ScalarAction.STOP_EOF && currLine.endsWith("\0")) {
                currLine = currLine.substring(0, currLine.length() - 1);
            }

            currLineLexeme = addedLeadingWhitespace > 0 ? currLine.substring(addedLeadingWhitespace) : currLine;
            trimmedCharOffset = charOffset;
            currLineTrimmed = currLine;
            if (currFirstContentOffset > 0) { // !!1
                prevNonWhitespaceOffset -= currFirstContentOffset;
            }
            boolean isEolEscaped = false;

            if (isBlock) {
                if (blockIndent > -1 && currLine.length() > blockIndent) {
                    currLineTrimmed = currLineTrimmed.substring(blockIndent);
                    trimmedCharOffset -= blockIndent;
                } else {
                    currLineTrimmed = currLineTrimmed.trim();
                    trimmedCharOffset -= currFirstContentOffset;
                }
                if (currLineTrimmed.endsWith("\r\n")) { // !!3
                    currLineTrimmed = currLineTrimmed.substring(0, currLineTrimmed.length() - 2);
                } else if (currLineTrimmed.endsWith("\n")) {
                    currLineTrimmed = currLineTrimmed.substring(0, currLineTrimmed.length() - 1);
                }
            } else {
                if (!doNotTrimLeading && (!isQuoted || lineNum > 0)) {
                    currLineTrimmed = currLineTrimmed.stripLeading();
                    if (currLineTrimmed.length() < currLine.length()) {
                        trimmedCharOffset -= (currLine.length() - currLineTrimmed.length());
                    }
                }

                // We need to strip trailing whitespace apart
                // from tabs that have a backslash in front of them.
                currLineTrimmed = stripTrailingLeavingTabs(currLineTrimmed);

                if (isQuoted && action == ScalarAction.CONTINUE) {

                    // TODO: Do this for plain too?
                    // TODO: Surely this is never used since currLineTrimmed is already stripped?
                    if (currLineTrimmed.endsWith("\r\n")) { // !!3
                        currLineTrimmed = currLineTrimmed.substring(0, currLineTrimmed.length() - 2);
                    } else if (currLineTrimmed.endsWith("\n")) {
                        currLineTrimmed = currLineTrimmed.substring(0, currLineTrimmed.length() - 1);
                    }

                    if (currLineTrimmed.endsWith("\\")) {
                        // TODO: Only if this is an odd number of backslashes
                        isEolEscaped = true;
                        currLineTrimmed = currLineTrimmed.substring(0, currLineTrimmed.length() - 1);
                    }
                }

                if (isQuoted && action == ScalarAction.STOP_QUOTE) {
                    currLineTrimmed = currLineTrimmed.substring(0, trimmedCharOffset);
                    currLineLexeme = currLineLexeme.substring(0, trimmedCharOffset - addedLeadingWhitespace);
                }
            }

            // 2c. Appending and folding

            if (action == ScalarAction.CONTINUE || action == ScalarAction.STOP_QUOTE || action == ScalarAction.STOP_EOF) {
                lexeme.append(currLineLexeme);
                debug("currLine: " + StringUtils.debugString(currLine));
                debug("currLineTrimmed: " + StringUtils.debugString(currLineTrimmed));
                debug("content0: " + StringUtils.debugString(content.toString()));

                if (currLineTrimmed.isEmpty() && action != ScalarAction.STOP_QUOTE) {
                    if (!isEolEscaped) {
                        trailingBlankLines.add(currLineTrimmed + "\n");
                    }
                } else {


                    // TODO: This feels hacky
                    // THIS SECTION ----------------=======================
                    if (currLineTrimmed.isEmpty() && action == ScalarAction.STOP_QUOTE) {
                        if (content.isEmpty() && lineNum > 0) {
                            if (trailingBlankLines.size() < 2) {
                                content.append(' ');
                            }
                        }
                    }


                    if (!trailingBlankLines.isEmpty()) {
                        if (isQuoted && trailingBlankLines.size() == 1 && !alreadyHadContent) {

                            // THIS LINE ----------------=======================
                            if (!currLineTrimmed.isEmpty()) {
                                content.append(' ');
                            }

                        } else {
                            int i = 0;



                            // TODO: This feels hacky
                            // Without this block, NAT4 fails but 7A4E passes
                            if (isQuoted &&
                                !(currHasExtraIndent||prevHasExtraIndent) &&

                                // Without this line, 7A4E fails.
                                !(alreadyHadContent || startsOnNewLine)
                            ) {
                                i++;
                            }



                            for (; i < trailingBlankLines.size(); i++) {
                                content.append(trailingBlankLines.get(i));
                            }
                            if (scalarStyle == ScalarStyle.LITERAL && alreadyHadContent) {
                                content.append('\n');
                            }
                        }
                    } else if (!content.isEmpty()) {
                        if (scalarStyle == ScalarStyle.LITERAL) {
                            content.append('\n');
                        } else if (scalarStyle == ScalarStyle.FOLDED) {
                            // Handled later
                        } else {
                            if (!(scalarStyle == ScalarStyle.DOUBLE_QUOTED && prevEolWasEscaped)) {
                                if (alreadyHadContent) {
                                    content.append(' ');
                                }
                            }
                        }
                    }

                    if (scalarStyle == ScalarStyle.FOLDED && alreadyHadContent) {
                        if (currHasExtraIndent || prevHasExtraIndent) {
                            content.append("\n");
                        } else {
                            if (trailingBlankLines.isEmpty() && !content.isEmpty()) {



                                // TODO: Is this only for isBlock? or only for FOLDED? or...?
                                // if (prevLineWasBlank && isBlock) {
                                if (prevLineWasBlank) {
                                    content.append('\n');
                                } else {
                                    content.append(' ');
                                }
                                // content.append(' ');



                            }
                        }
                    }
                    trailingBlankLines.clear();

                    if (isQuoted && !foundContent) {
                        if (doNotTrimLeading) {
                            currLineTrimmed = currLineTrimmed.stripTrailing();
                        } else {
                            currLineTrimmed = currLineTrimmed.trim();
                        }
                        // currLineTrimmed = currLineTrimmed.trim();
                    }

                    debug("currLineTrimmed: " + StringUtils.debugString(currLineTrimmed));
                    content.append(currLineTrimmed);
                    // debug("CONTENT: " + StringUtils.debugString(content.toString()));
                }
                if (action != ScalarAction.STOP_QUOTE) {
                    advanceBufferToNextLine();
                }
                debug("content1: " + StringUtils.debugString(content.toString()));
            }

            prevLineWasBlank = currLineTrimmed.isBlank();
            prevHasExtraIndent = currHasExtraIndent;
            alreadyHadContent |= (!currLineTrimmed.isEmpty());
            prevEolWasEscaped = isEolEscaped;
            lineNum++;
            isFirstChar = false;
        }

        debug("Final action: " + action);
        debug("Content: " + StringUtils.debugString(content.toString()));

        // 3. Last line and buffer positioning

        if (action == ScalarAction.STOP_EOF) {
            debug("EOF");
            while (!buffer.isAtEnd()) {
                advance();
            }
            currLineTrimmed = null;
        } else if (action == ScalarAction.STOP_SEQ) {
            // We exited due to a sequence indicator.
            // Don't include this line.
            debugString(debugSource, "seq", buffer.offset());
            if (!content.isEmpty()) {
                backupBufferToEOL();
                debugString(debugSource, "nl", buffer.offset());
            }
            currLineTrimmed = null;
        // } else if (currLineTrimmed.isEmpty()) {
        //     trailingBlankLines.add(currLineTrimmed);
        //     advanceBufferToNextLine();
        //     currLineTrimmed = null;
        } else if (action == ScalarAction.CONTINUE) {
            // We exited due to reaching the end of the buffer.
            // This line is already in the string builders.
            if (scalarStyle == ScalarStyle.SINGLE_QUOTED) {
                error(YamlDiagnosticCode.EXPECTED_CLOSE_SINGLE_QUOTE);
                return;
            } else if (scalarStyle == ScalarStyle.DOUBLE_QUOTED) {
                error(YamlDiagnosticCode.EXPECTED_CLOSE_DOUBLE_QUOTE);
                return;
            }
            currLineTrimmed = null;
        } else if (action == ScalarAction.STOP_BLOCKINDENT) {
            if (!content.isEmpty()) {
                debugString(debugSource, buffer.offset() - 1);
                backupBufferToEOL();
                debugString(debugSource, buffer.offset() - 1);
            }
            currLineTrimmed = null;
        } else if (action == ScalarAction.STOP_QUOTE) {
            // charOffset is the last content character
            debugString(debugSource, buffer.offset() - 1);
            debugString(currLine, charOffset);

            advanceBufferBy(charOffset + 1 - addedLeadingWhitespace + removedChars);

            debugString(debugSource, buffer.offset() - 1);
            currLineTrimmed = null;
        } else if (action == ScalarAction.STOP_MAP_VALUE) {
            if (currFirstContentOffset == -1) {
                currFirstContentOffset = 0;
            }

            if (flowDepth > 0) {

                // This feels is a bit hacky.
                // It works for multi-line plain scalar keys inside flow structures
                // but doesn't cover the same keys not inside flow structures.

                debugString(debugSource, buffer.offset());

                advanceBufferBy(currFirstContentOffset + prevNonWhitespaceOffset + 1 - addedLeadingWhitespace);
                currLineTrimmed = currLineTrimmed.substring(0, prevNonWhitespaceOffset + 1);

                // System.out.println("currFirstContentOffset  = " + currFirstContentOffset);
                // System.out.println("prevNonWhitespaceOffset = " + prevNonWhitespaceOffset);
                // System.out.println("addedLeadingWhitespace  = " + addedLeadingWhitespace);

                currLineLexeme = currLineLexeme.substring(0, currFirstContentOffset + prevNonWhitespaceOffset + 1 - addedLeadingWhitespace);

                debugString(debugSource, buffer.offset());
                if (!content.isEmpty() && !currLineTrimmed.isEmpty()) {
                    currLineTrimmed = " " + currLineTrimmed;
                }

            } else if (!(content.isEmpty() && trailingBlankLines.isEmpty())) {
                // Go back to the end of the previous line
                debugString(debugSource, buffer.offset() - 1);
                backupBufferToEOL();
                debugString(debugSource, buffer.offset() - 1);
                currLineTrimmed = null;
            } else {
                // This is a key. Consume it but don't consume the colon
                debugString(debugSource, buffer.offset());
                advanceBufferBy(currFirstContentOffset + prevNonWhitespaceOffset + 1 - addedLeadingWhitespace);
                currLineTrimmed = currLineTrimmed.substring(0, prevNonWhitespaceOffset + 1);

                // System.out.println("currFirstContentOffset  = " + currFirstContentOffset);
                // System.out.println("prevNonWhitespaceOffset = " + prevNonWhitespaceOffset);
                // System.out.println("addedLeadingWhitespace  = " + addedLeadingWhitespace);

                currLineLexeme = currLineLexeme.substring(0, currFirstContentOffset + prevNonWhitespaceOffset + 1 - addedLeadingWhitespace);
                debugString(debugSource, buffer.offset());
            }
        } else if (action == ScalarAction.STOP_MAP_KEY) {
            // This doesn't seem right, but it works
            if (!content.isEmpty()) {
                backupBufferToEOL();
                currLineTrimmed = null;
            } else {
                advanceBufferBy(charOffset - addedLeadingWhitespace);
                currLineTrimmed = currLineTrimmed.substring(0, trimmedCharOffset);
            }
        } else if (action == ScalarAction.STOP_FLOW) {
            // Consume currLine up until prevNonWhitespaceOffset
            advanceBufferBy(charOffset - addedLeadingWhitespace);

            if (prevNonWhitespaceOffset < 0) {
                // debug("Debug comment");
                backupBufferToEOL();
                currLineTrimmed = null;
            } else {
                debugString(currLineTrimmed, "flow", trimmedCharOffset);
                currLineTrimmed = currLineTrimmed.substring(0, prevNonWhitespaceOffset + 1);
                currLineLexeme = currLineLexeme.substring(0, currFirstContentOffset + prevNonWhitespaceOffset + 1 - addedLeadingWhitespace);

                if (!content.isEmpty() && !currLineTrimmed.isEmpty()) {
                    currLineTrimmed = " " + currLineTrimmed;
                }
            }

        } else if (action == ScalarAction.STOP_COMMENT) {
            // Consume currLine up until prevNonWhitespaceOffset
            if (prevNonWhitespaceOffset < 0) {
                // debug("Debug comment");
                backupBufferToEOL();
                currLineTrimmed = null;
            } else {
                debugString(currLineTrimmed, "comment", trimmedCharOffset);
                advanceBufferBy(currFirstContentOffset + prevNonWhitespaceOffset + 1 - addedLeadingWhitespace);
                currLineTrimmed = currLineTrimmed.substring(0, prevNonWhitespaceOffset + 1);
                currLineLexeme = currLineLexeme.substring(0, currFirstContentOffset + prevNonWhitespaceOffset + 1 - addedLeadingWhitespace);
                if (!content.isEmpty() && !currLineTrimmed.isEmpty()) {
                    currLineTrimmed = " " + currLineTrimmed;
                }
            }
        }

        if (currLineTrimmed != null) {
            lexeme.append(currLineLexeme);
            content.append(currLineTrimmed);
        }

        if (scalarStyle == ScalarStyle.SINGLE_QUOTED) lexeme.append('\'');
        if (scalarStyle == ScalarStyle.DOUBLE_QUOTED) lexeme.append('"');

        // 4. Trailing new lines

        if (scalarStyle == ScalarStyle.FOLDED || scalarStyle == ScalarStyle.LITERAL) {
            // Perform Chomping
            // https://yaml.org/spec/1.2.2/#8112-block-chomping-indicator
            if (chompingStyle == ChompingStyle.KEEP) {
                if (!content.isEmpty()) {
                    content.append('\n');
                }
                for (String blankLine : trailingBlankLines) {
                    content.append(blankLine);
                }
            }
            else if (chompingStyle == ChompingStyle.STRIP) {
                int contentLen = content.length();
                if (contentLen > 0 && content.charAt(contentLen - 1) == '\n' ) {
                    content.setLength(contentLen - 1);
                }
            }
            else {
                if (!content.isEmpty()) {
                    content.append('\n');
                }
            }
        }

        debug(scalarStyle + " SCALAR: " + StringUtils.debugString(content.toString()));

        // 5. Create the token

        YamlToken token = new YamlToken(
            startLine,
            startColumn,
            startOffset,
            YamlTokenType.SCALAR,
            lexeme.toString().stripTrailing(),
            content.toString(),
            scalarStyle,
            firstLineIndent,
            blockIndent
        );
        token.setStartsOnNewLine(!lineHasContent);
        addToken(token);
        lineHasContent = true;
        debug("\n**************** END scanScalar **************** ");
    }

    // TODO: This is horrendously inefficient
    private String stripTrailingLeavingTabs(String s) {
        while (true) {
            if (s.endsWith("\r") ||
                s.endsWith("\n") ||
                s.endsWith(" ")) {
                s = s.substring(0, s.length() - 1);
            } else if (s.endsWith("\t")) {
                if (s.endsWith("\\\t")) {
                    break;
                }
                s = s.substring(0, s.length() - 1);
            } else {
                break;
            }
        }
        return s;
    }

    private ScalarAction scalarAction(String line, boolean lineHasContent, int offset, ScalarStyle scalarStyle, boolean isFirstLine, boolean isFirstChar) {
        char ch = line.charAt(offset);
        char prev = offset > 0 ? line.charAt(offset - 1) : '\0';
        char next = offset + 1 < line.length() ? line.charAt(offset + 1) : '\0';

        if (ch == '\0') {
            return ScalarAction.STOP_EOF;
        }

        if (scalarStyle == ScalarStyle.PLAIN) {
            if (isFirstChar && ch == ':') {
                return ScalarAction.CONTINUE;
            }
            // testAB8U: if c is a dash and the previous token was a dash
            // c is paert of this string if it's indented more than the previous token.
            boolean prevTokenWasSequenceIndicator = previousNonWhitespaceToken != null &&
                previousNonWhitespaceToken.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR;
            // if (ch == '-' &&
            //     prevTokenWasSequenceIndicator &&
            //     previousNonWhitespaceToken.getStartColumn() < offset + 1
            // ) {
            //     return ScalarAction.CONTINUE;
            // }
            if (prevTokenWasSequenceIndicator) {
                if (ch == '-' && previousNonWhitespaceToken.getStartColumn() < offset + 1) {
                    return ScalarAction.CONTINUE;
                } else {
                    if (!isFirstLine && ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') {
                        // If a non-whitespace character is indented less than or same as
                        // a preceeding sequence indicator, it's not part of this scalar.
                        if (offset + 1 <= previousNonWhitespaceToken.getStartColumn()) {
                            return ScalarAction.STOP_MAP_KEY;
                        }
                    }
                }
            }
            if (ch == '#' && (prev == '\0' || prev == ' ' || prev == '\t' || prev == '\r' || prev == '\n')) {
                return ScalarAction.STOP_COMMENT;
            }
            if (ch == '?' && !lineHasContent && (next == '\0' || next == ' ' || next == '\t' || next == '\r' || next == '\n')) {
                return ScalarAction.STOP_MAP_KEY;
            }
            if (ch == ':') {
                if (next == '\0' || next == ' ' || next == '\t' || next == '\r' || next == '\n') {
                    return ScalarAction.STOP_MAP_VALUE;
                }
                if (flowDepth > 0) {
                    if (next == ',' || next == '}' || next ==']') {
                        return ScalarAction.STOP_MAP_VALUE;
                    }
                }
            }
            if (ch == '-' && !lineHasContent && (next == '\0' || next == ' ' || next == '\t' || next == '\r' || next == '\n')) {
                return ScalarAction.STOP_SEQ;
            }
            if (flowDepth > 0 && (ch == ',' || ch == '{' || ch == '}' || ch == '[' || ch == ']')) {
                return ScalarAction.STOP_FLOW;
            }
        }
        else if (scalarStyle == ScalarStyle.SINGLE_QUOTED) {
            if (ch == '\'' && prev != '\'' && next != '\'') {
                return ScalarAction.STOP_QUOTE;
            }
        }
        else if (scalarStyle == ScalarStyle.DOUBLE_QUOTED) {
            if (ch == '"' && prev != '\\') {
                return ScalarAction.STOP_QUOTE;
            }
        } else  if (scalarStyle == ScalarStyle.FOLDED) {
            if (ch == '?' && (next == '\0' || next == ' ' || next == '\t' || next == '\r' || next == '\n')) {
                return ScalarAction.STOP_MAP_KEY;
            }
            if (ch == ':' && (next == '\0' || next == ' ' || next == '\t' || next == '\r' || next == '\n')) {
                return ScalarAction.STOP_MAP_VALUE;
            }
            if (ch == '-' && prev != '-'  && (next == '\0' || next == ' ' || next == '\t' || next == '\r' || next == '\n')) {
                return ScalarAction.STOP_SEQ;
            }
            if (flowDepth > 0 && (ch == ',' || ch == '{' || ch == '}' || ch == '[' || ch == ']')) {
                return ScalarAction.STOP_FLOW;
            }
        } else  if (scalarStyle == ScalarStyle.LITERAL) {
            if (ch == '?' && (next == '\0' || next == ' ' || next == '\t' || next == '\r' || next == '\n')) {
                return ScalarAction.STOP_MAP_KEY;
            }
            if (ch == ':' && (next == '\0' || next == ' ' || next == '\t' || next == '\r' || next == '\n')) {
                return ScalarAction.STOP_MAP_VALUE;
            }
            if (ch == '-' && prev != '-'  && (next == '\0' || next == ' ' || next == '\t' || next == '\r' || next == '\n')) {
                return ScalarAction.STOP_SEQ;
            }
            if (flowDepth > 0 && (ch == ',' || ch == '{' || ch == '}' || ch == '[' || ch == ']')) {
                return ScalarAction.STOP_FLOW;
            }
        }
        return ScalarAction.CONTINUE;
    }

    // private boolean isValidEscape(char c, ScalarStyle scalarStyle) {
    //     if (scalarStyle == ScalarStyle.PLAIN) {
    //     } else if (scalarStyle == ScalarStyle.SINGLE_QUOTED) {
    //     } else if (scalarStyle == ScalarStyle.DOUBLE_QUOTED) {
    //     } else if (scalarStyle == ScalarStyle.LITERAL) {
    //     } else if (scalarStyle == ScalarStyle.FOLDED) {
    //     }
    //     return true;
    // }
    private boolean isValidEscape(char c, ScalarStyle scalarStyle) {
        if (scalarStyle == ScalarStyle.PLAIN) {
            // Plain scalars do not support escape sequences; '\' is literal text
            return false;

        } else if (scalarStyle == ScalarStyle.SINGLE_QUOTED) {
            // Single-quoted scalars ONLY escape standard single quotes using double-quote syntax ('')
            // Backslash '\' is treated as a literal character, not an escape prefix
            return false;

        } else if (scalarStyle == ScalarStyle.DOUBLE_QUOTED) {
            // Standard YAML 1.2 double-quoted escape characters
            return switch (c) {
                // Null, Bell, Backspace, Tab, Line Feed, Vertical Tab, Form Feed, Carriage Return, Escape
                case '0', 'a', 'b', 't', '\t', 'n', 'v', 'f', 'r', 'e' -> true;
                // Space, Double Quote, Slash, Backslash, Next Line (NEL), Non-breaking Space, Line Separator, Paragraph Separator
                case ' ', '"', '/', '\\', 'N', '_', 'L', 'P' -> true;
                // Hex / Unicode escapes (xXX, uXXXX, UXXXXXXXX)
                case 'x', 'u', 'U' -> true;
                default -> false;
            };

        } else if (scalarStyle == ScalarStyle.LITERAL || scalarStyle == ScalarStyle.FOLDED) {
            // Block scalars (Literal '|' and Folded '>') do not interpret backslash escapes;
            // '\' is treated as literal text
            return false;
        }

        return false;
    }
    private static enum ScalarAction {
        CONTINUE,
        STOP_BLOCKINDENT,
        STOP_QUOTE,
        STOP_MAP_KEY,
        STOP_MAP_VALUE,
        STOP_SEQ,
        STOP_FLOW,
        STOP_COMMENT,
        STOP_EOF
    }

    private void advanceBufferToNextLine() {
        while (!buffer.isAtEnd() && buffer.peek() != '\n') {
            advance();
        }
        advance();
    }

    private void backupBufferToEOL() {
        while (buffer.offset() > 1 && buffer.peek() != '\n') {
            buffer.backup();
        }
    }

    private void advanceBufferBy(int n) {
        for (int i = 0; i < n; i++) {
            advance();
        }
    }

    private String peekLine() {
        int bufferOffset = buffer.offset();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        char ch = '\0';

        for (; buffer.peekAhead(i) != '\n' && bufferOffset + i < buffer.length(); i++) {
            ch = buffer.peekAhead(i);
            sb.append(ch);
        }

        ch = buffer.peekAhead(i);
        if (ch == '\n') {
            sb.append(ch);
        }
        return sb.toString();
    }

    private void scanIdentifier(YamlTokenType type) {
        boolean isAnchorName = type != YamlTokenType.ANCHOR || type != YamlTokenType.ALIAS;
        if (!isIdentifierStartChar(buffer.peek()) && !isAnchorName) {
            return;
        }
        advance();
        for (char ch = buffer.peek(); !buffer.isAtEnd() && !(ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n'); ch = buffer.peek()) {
            ch = buffer.peek();
            char next = buffer.peekNext();
            if ((ch == ':' && !isAnchorName && (next == ' ' || next == '\t' || next == '\r' || next == '\n')) ||
                (ch == '-' && (next == ' ' || next == '\t' || next == '\r' || next == '\n')) ||
                (flowDepth > 0 && (ch == ',' || ch == '{' || ch == '}' || ch == '[' || ch == ']'))) {
                break;
            }
            advance();
        }
        addToken(type);
    }

    private boolean isIdentifierStartChar(char c) {
        // ns-anchor-char: any non-space, non-flow-indicator, non-comment char
        return !isWhitespace(c)
            && c != ':'
            && c != ','
            && c != '['
            && c != ']'
            && c != '{'
            && c != '}'
            && c != '&'
            && c != '*'
            && c != '#'
            && c != '?'
            && c != '|'
            && c != '-'
            && c != '<'
            && c != '>'
            && c != '!';
    }

    private void scanTag() {
        // URI tag: !<...>
        // if (buffer.peek() == '!' && buffer.peekAhead(1) == '<') {
        if (buffer.peek()  == '<') {
            advance(); // '!'
            advance(); // '<'

            while (!buffer.isAtEnd() && buffer.peek() != '>') {
                advance();
            }

            if (!buffer.isAtEnd()) {
                advance(); // consume '>'
            }

            addToken(YamlTokenType.TAG);
            return;
        }

        // Non‑URI tag: !foo, !!str, !e!tag%21, etc.
        while (!buffer.isAtEnd() &&
               (isAlphaNumeric(buffer.peek()) ||
                buffer.peek() == '!' ||
                buffer.peek() == '%' ||
                buffer.peek() == '-' ||
                buffer.peek() == '_' ||
                buffer.peek() == ':' ||
                buffer.peek() == '/'))
        {
            advance();
        }

        addToken(YamlTokenType.TAG);
    }

    private void handleNewlineAndIndentation(char c) {
        lineHasContent = false;
        leadingWhitespace = "";
        if (buffer.peek() == '\t') {
            while (buffer.peek() == '\t' || buffer.peek() == ' ') {
                leadingWhitespace += buffer.peek();
                advance();
            }
            // for (char ch = buffer.peek(); ch == '\t' || ch == ' '; ch = advance()) {
            //     leadingWhitespace += ch;
            // }
            buffer.startTokenWindow();
            lineHasTabs = true;
            return;
        }

        // 1. First Newline
        String lexeme = (c == '\r' && buffer.peek() == '\n') ? "\r\n" : "\n";
        if (lexeme.length() == 2) advance();

        // Use column - lexeme.length() to point to the start of the newline
        addStructuralToken(YamlTokenType.NEWLINE, buffer.column() - lexeme.length() + 1);
        lineHasTabs = false;

        // 2. Keep eating newlines and spaces as long as the line is "empty"
        while (!buffer.isAtEnd()) {
            char next = buffer.peek();

            if (next == ' ') {
                leadingWhitespace += " ";
                advance();
            } else if (next == '\n' || next == '\r') {
                // We hit another newline, so previous spaces on this line didn't matter.
                lineHasContent = false;
                leadingWhitespace = "";
                char nc = advance();
                String nl = (nc == '\r' && buffer.peek() == '\n') ? "\r\n" : "\n";
                if (nl.length() == 2) advance();

                if (flowDepth == 0) {
                    addStructuralToken(YamlTokenType.NEWLINE, buffer.column() - nl.length() + 1);
                }
            } else {
                // We hit actual content (or a comment)
                break;
            }
        }

        while (buffer.peek() == '\t' || buffer.peek() == ' ') {
            leadingWhitespace += buffer.peek();
            advance();
        }
        // for (char ch = buffer.peek(); ch == '\t' || ch == ' '; ch = advance()) {
        //     leadingWhitespace += ch;
        // }

        int currentColumn = buffer.column();
        int expectedIndent = indentationLevels.peek();

        // 3. Indentation Logic
        if (currentColumn > expectedIndent) {
            if (flowDepth == 0) {
                indentationLevels.push(currentColumn);
                addStructuralToken(YamlTokenType.INDENT, currentColumn);
            }
        }

        else if (currentColumn < expectedIndent) {
            while (indentationLevels.size() > 1 && indentationLevels.peek() > currentColumn) {
                if (flowDepth == 0) {
                    indentationLevels.pop();
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

    private char advance() {
        return buffer.advance();
    }

    private void setup() {
        indentationLevels = new ArrayDeque<>();
        flowDepth = 0; // Tracks nesting level of flow context [ ] and { }
        tokens = new ArrayList<>();
        isLegacyMode = false;
        streamStarted = false;
        streamEnded = false;
        previousNonWhitespaceToken = null;
        isDocumentLevel = true;
        leadingWhitespace = "";
        lineHasContent = false;

        flowDepth = 0;
        indentationLevels.clear();
        indentationLevels.push(1);

        pendingTokens.clear();

        // // Handle UTF-8 BOM if present at start of stream/string
        // if (buffer.peek() == '\uFEFF') {
        //     advance();
        // }
    }

    private void updatePreviousNonWhitespaceToken(YamlToken token) {
        YamlTokenType type = token.getType();
        TokenCategory cat = token.getType().getCategory();
        if (cat != TokenCategory.WHITESPACE &&
            cat != TokenCategory.INDENTATION &&

            // cat != TokenCategory.INTERNAL &&
            type != YamlTokenType.STREAM_START &&

            cat != TokenCategory.NEWLINE &&
            cat != TokenCategory.COMMENT
        ) {
            previousNonWhitespaceToken = token;
        }
    }

    /// Small interceptor ensuring that if someone runs the old tokenize() API,
    /// tokens get copied to the collection output array correctly.
    private YamlToken queueToken(YamlToken token) {
        if (isLegacyMode && token != null) {
            tokens.add(token);
        }
        updatePreviousNonWhitespaceToken(token);
        return token;
    }

    // TODO: Why do we have both queueToken (above) and addToken (below) ?
    private YamlToken addToken(YamlToken token) {
        debug("addToken: " + token.getType());
        if (token != null) {
            pendingTokens.add(token); // Queue it up so nextToken() can yield it!
        }
        if (previousNonWhitespaceToken != null && previousNonWhitespaceToken.getStartLine() < token.getStartLine()) {
            token.setHasPreceedingNewLine(true);
        }
        updatePreviousNonWhitespaceToken(token);
        return token;
    }

    private YamlToken addToken(YamlTokenType type) {
        String text = buffer.getTokenWindowLexeme();
        // TODO: Is this meant to be PLAIN?
        return addToken(new YamlToken(buffer.windowStartLine(), buffer.windowStartColumn(), buffer.windowStartOffset(), type, text, text, ScalarStyle.PLAIN, -1, -1));
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
        if (!reporter.reportsTrace()) return;
        char c = buffer.peek();
        reporter.trace("S=%03d C=%03d '%s' %03d:%03d %s",
            buffer.offset(), buffer.offset(), StringUtils.visibleChar(c), buffer.line(), buffer.column(), method);
    }

    protected void trace(String method, String info) {
        if (!reporter.reportsTrace()) return;
        char c = buffer.peek();
        reporter.trace("S=%03d C=%03d '%s' %03d:%03d %s: %s",
            buffer.offset(), buffer.offset(), StringUtils.visibleChar(c), buffer.line(), buffer.column(), method, info);
    }

    private void debugString(String string, int pos) {
        if (!reporter.reportsDebug()) return;
        reporter.debug(StringUtils.debugString(string, pos));
    }

    private void debugString(String string, String name, int pos) {
        if (!reporter.reportsDebug()) return;
        reporter.debug(StringUtils.debugString(string, name, pos));
    }
}