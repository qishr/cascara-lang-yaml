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
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.common.lang.token.TokenCategory;
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
    private boolean lineHasTabs = false;

    final Deque<YamlToken> pendingTokens = new ArrayDeque<>();
    private boolean streamStarted = false;
    private boolean streamEnded = false;
    private YamlToken previousNonWhitespaceToken;


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
        this.debugSource = text;

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

        char c = buffer.peek();

        if (c == '|') {
            scanScalar(ScalarStyle.LITERAL);
            return;
        }

        if (c == '>') {
            scanScalar(ScalarStyle.FOLDED);
            return;
        }

        if (c == '\n' || c == '\r') {
            buffer.advance();
            trace("scanToken", "");
            handleNewlineAndIndentation(c);
            return;
        }

        if (c == ' ' || c == '\t') {
            buffer.advance();
            trace(method, "space or tab");
            return;
        }

        if (c == '!') {
            buffer.advance();
            scanTag();
            return;
        }

        if (c == '-' && buffer.peekNext() == '-' && buffer.peekAhead(2) == '-' &&
            (isWhitespace(buffer.peekAhead(3)))
        ) {
            buffer.advance();
            trace(method, "dash1");
            buffer.advance(); buffer.advance();
            addToken(YamlTokenType.DOCUMENT_START);
            return;
        }

        if (c == '.' && buffer.peekNext() == '.' && buffer.peekAhead(2) == '.') {
            buffer.advance();
            trace(method, "dot");
            buffer.advance(); buffer.advance();
            addToken(YamlTokenType.DOCUMENT_END);
            return;
        }

        if (c == '#') {
            buffer.advance();
            trace(method, "hash");
            while (buffer.peek() != '\n' && buffer.peek() != '\r' && !buffer.isAtEnd()) {
                buffer.advance();
            }
            addToken(YamlTokenType.COMMENT);
            return;
        }

        if (FLOW_CONTEXT_SINGLE_CHAR_TOKENS.containsKey(c)) {
            buffer.advance();
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
            if (lineHasTabs) {
                error(YamlDiagnosticCode.TAB_NOT_ALLOWED);
            }
            if (buffer.offset() + 1 >= buffer.length()) {
                buffer.advance();
                addToken(YamlTokenType.SEQUENCE_ENTRY_INDICATOR);
                return;
            }
            if (isWhitespace(buffer.peekNext())) {
                buffer.advance();
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
            if (lineHasTabs) {
                error(YamlDiagnosticCode.TAB_NOT_ALLOWED);
            }
            if (buffer.offset() + 1 >= buffer.length()) {
                buffer.advance();
                addToken(YamlTokenType.VALUE_INDICATOR);
                return;
            }


            // TODO: This space is only optional if it's inside a flow ?
            // This still breaks testDBG4

            if (isWhitespace(buffer.peekNext()) || (buffer.peekNext() != ':' && flowDepth > 0)) {
                buffer.advance();
                trace(method, "colon");
                addStructuralToken(YamlTokenType.VALUE_INDICATOR, tokenStartColumn);
                return;
            }

            // //  test2EBW and test9MMW and now testDBG4

            // // TODO: If there is another colon after the next token,
            // // do not treat this as a value indicator
            // buffer.advance();
            // // TODO: Should probably consume all whitespace here
            // if (isWhitespace(buffer.peekNext())) {
            //     buffer.advance();
            // }
            // trace(method, "colon");
            // addStructuralToken(YamlTokenType.VALUE_INDICATOR, tokenStartColumn);
            // return;



        }

        if (c == '\'') {
            scanScalar(ScalarStyle.SINGLE_QUOTED);
            return;
        }

        if (c == '\"') {
            scanScalar(ScalarStyle.DOUBLE_QUOTED);
            return;
        }

        if (c == '&') {
            buffer.advance();
            trace(method, "ampersand");
            scanIdentifier(YamlTokenType.ANCHOR);
            return;
        }

        if (c == '*') {
            buffer.advance();
            trace(method, "asterisk");
            scanIdentifier(YamlTokenType.ALIAS);
            return;
        }

        if (c == '?' &&  (isWhitespace(buffer.peekNext()) || buffer.isAtEnd())) {
            buffer.advance();
            // A '?' is only a structural marker if followed by whitespace/newline/EOF
            trace(method, "question mark");
            addStructuralToken(YamlTokenType.KEY_INDICATOR, tokenStartColumn);
            return;
        }

        if (c == '%') {
            buffer.advance();
            trace(method, "directive");
            // Directives are line-oriented metadata (e.g., %YAML 1.2)
            while (buffer.peek() != '\n' && buffer.peek() != '\r' && !buffer.isAtEnd()) {
                buffer.advance();
            }
            addToken(YamlTokenType.DIRECTIVE);
            return;
        }

        // buffer.advance();
        // scanPlainScalar(c);
        scanScalar(ScalarStyle.PLAIN);
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
            lexeme.append(buffer.advance()); // consume the opening quote
            isQuoted = true;
        } else if (scalarStyle == ScalarStyle.FOLDED || scalarStyle == ScalarStyle.LITERAL) {
            buffer.advance();
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
            while (!buffer.isAtEnd() && buffer.peek() != '\n') {
                // TODO: inline comments
                buffer.advance();
            }
            buffer.advance(); // consume the newline
            lexeme.append(buffer.getTokenWindowLexeme());
            isBlock = true;
        }

        // Amount of indentation spaces
        int blockIndent = explicitIndent == -1 ? -1 : explicitIndent + currentMargin;

        int lineNum = 0;
        boolean lineHasContent = false;
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

        boolean startsOnNewLine = previousNonWhitespaceToken == null
            ? true
            : startLine > previousNonWhitespaceToken.getStartLine();

        int addedLeadingWhitespace = 0;

        // 2. Scan the scalar

        while (!buffer.isAtEnd() && action == ScalarAction.CONTINUE) {
            prevNonWhitespaceOffset = -1;
            currFirstContentOffset = -1;
            lineHasContent = false;
            currLine = peekLine();




            // TODO: We need to make sure the extra spaces don't go into the lexeme.
            // They are just to keep scanning less complicated (!!)

            // This isnn't entirely right -
            // We don't want to add leading spacces if they a...??????
            addedLeadingWhitespace = 0;
            if (lineNum == 0 && startsOnNewLine && scalarStyle == ScalarStyle.PLAIN) {
                // If this is the first line of an unquoted string that
                // starts on a line by itself, the tokenizer has already
                // consumed the leading whitespace.

                debug("scalar starts on new line");
                addedLeadingWhitespace = startColumn - 1;
                // Add it back in so this line is intact.
                currLine = " ".repeat(addedLeadingWhitespace) + currLine;
            }




            debug("LINE " + lineNum + ": " + StringUtils.debugString(currLine));

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

                action = scalarAction(currLine, lineHasContent, charOffset, scalarStyle);
                if (action != ScalarAction.CONTINUE) {
                    break;
                }

                char c = currLine.charAt(charOffset);







                // if (c != ' ' && c != '\r' && c!= '\n') {
                //     if (!isQuoted || lineNum > 0) {
                //         if (blockIndent == -1) {
                //             // blockIndent is the number of spaces, charOffset is 0-based.
                //             blockIndent = charOffset;
                //             debug("Block indent set: " + blockIndent);
                //         } else if (isBlock && charOffset < blockIndent) {
                //             action = ScalarAction.STOP_BLOCKINDENT;
                //             break;
                //         }

                //         // Determine extra indent
                //         if (currFirstContentOffset == -1) {
                //             currFirstContentOffset = charOffset;
                //             if (charOffset > blockIndent) {
                //                 currHasExtraIndent = true;
                //             } else {
                //                 currHasExtraIndent = false;
                //             }
                //         }
                //     }
                //     if (c != '\t') {
                //         prevNonWhitespaceOffset = charOffset;
                //         lineHasContent = true;
                //     }
                // }





                if (c != ' ' && c != '\r' && c!= '\n') {

                    // TODO: plain scalar starting on same line as preceeding token - can't detect blockIndent until line 1

                    // and plain scalar that starts on new line - first char column - 1 is blockindent
                    if (blockIndent == -1) {

                        // if (startsOnNewLine && !isQuoted) {
                        //     // blockIndent is the number of spaces, startColumn is 1-based.
                        //     blockIndent = startColumn - 1;
                        //     debug("Block indent set: " + blockIndent);
                        // } else if (lineNum > 0) {
                            // blockIndent is the number of spaces, charOffset is 0-based.
                            blockIndent = charOffset;
                            debug("Block indent set: " + blockIndent);
                        // }
                    }
                    // if (!isQuoted || startsOnNewLine || lineNum > 0) {
                        // if (blockIndent == -1) {
                        //     // blockIndent is the number of spaces, charOffset is 0-based.
                        //     blockIndent = charOffset + firstCharIndent;
                        //     debug("Block indent set: " + blockIndent);

                        // TODO: If a plain scalar value is indented at the same level or less than the key that started it, then it's an error
                        // } else if (isBlock && charOffset < blockIndent) {
                        if (!isQuoted && blockIndent > -1 && charOffset < blockIndent) {
                            action = ScalarAction.STOP_BLOCKINDENT;
                            break;
                        } else {
                            debug("Block indent = " + blockIndent + " and charOffset = " + charOffset);
                        }

                    if (!isQuoted || startsOnNewLine || lineNum > 0) {
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
                        lineHasContent = true;
                    }
                }







                // Plain scalar whitespace handling
                if (scalarStyle == ScalarStyle.PLAIN) {
                    if (charOffset == finalNonWhitespaceOffset) {
                        break;
                    }
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
            boolean isEmpty;
            boolean isEolEscaped = false;

            if (isBlock) {
                isEmpty = currLine.isEmpty();
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
                if (!isQuoted || lineNum > 0) {
                    isEmpty = currLineTrimmed.isBlank(); // !!2
                    currLineTrimmed = currLineTrimmed.stripLeading();

                    if (currLineTrimmed.length() < currLine.length()) {
                        trimmedCharOffset -= (currLine.length() - currLineTrimmed.length());
                    }

                    currLineTrimmed = currLineTrimmed.trim();
                } else {
                    isEmpty = currLineTrimmed.isBlank(); // !!2
                }

                if (isQuoted && action == ScalarAction.CONTINUE) {
                    // TODO: Do this for plain too?
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
                debug("CONTINUE: " + StringUtils.debugString(currLine));

                // if (lineNum == 15) {
                //     debug("Debug LIne Num " + lineNum + " empty="+isEmpty);
                // }

                if (currLineTrimmed.isEmpty() && action != ScalarAction.STOP_QUOTE) {
                    if (!isEolEscaped) {
                        trailingBlankLines.add(currLineTrimmed + "\n");
                    }
                } else {
                    if (!trailingBlankLines.isEmpty()) {
                        if (isQuoted && trailingBlankLines.size() == 1 && !alreadyHadContent) {
                            content.append(' ');
                        } else {
                            for (int i = 0; i < trailingBlankLines.size(); i++) {
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
                                content.append(' ');
                            }
                        }
                    }

                    if (scalarStyle == ScalarStyle.FOLDED && alreadyHadContent) {
                        if (currHasExtraIndent || prevHasExtraIndent) {
                            content.append("\n");
                        } else {
                            if (trailingBlankLines.isEmpty() && !content.isEmpty()) {
                                content.append(' ');
                            }
                        }
                    }

                    trailingBlankLines.clear();

                    debug("TRIMMED: " + StringUtils.debugString(currLineTrimmed));
                    content.append(currLineTrimmed);
                    debug("CONTENT: " + StringUtils.debugString(content.toString()));
                }
                if (action != ScalarAction.STOP_QUOTE) {
                    advanceBufferToNextLine();
                }
            }

            prevHasExtraIndent = currHasExtraIndent;
            alreadyHadContent |= (!currLineTrimmed.isEmpty());
            prevEolWasEscaped = isEolEscaped;
            lineNum++;
        }

        debug("Final action: " + action);
        debug("Content: " + StringUtils.debugString(content.toString()));

        // 3. Last line and buffer positioning

        if (action == ScalarAction.STOP_EOF) {
            debug("EOF");
            while (!buffer.isAtEnd()) {
                buffer.advance();
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
        } else if (currLineTrimmed.isEmpty()) {
            trailingBlankLines.add(currLineTrimmed);
            advanceBufferToNextLine();
            currLineTrimmed = null;



        } else if (action == ScalarAction.CONTINUE) {
            // We exited due to reaching the end of the buffer.
            // This line is already in the string builders.
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


            // advanceBufferBy(charOffset + 1);
            advanceBufferBy(charOffset + 1 - addedLeadingWhitespace);


            debugString(debugSource, buffer.offset() - 1);
            currLineTrimmed = null;
        } else if (action == ScalarAction.STOP_MAP_VALUE) {
            if (!(content.isEmpty() && trailingBlankLines.isEmpty())) {
                if (isBlock) {
                    debug("Block");
                }
                // Go back to the end of the previous line
                debugString(debugSource, buffer.offset() - 1);
                backupBufferToEOL();
                debugString(debugSource, buffer.offset() - 1);
                currLineTrimmed = null;
            } else {
                // This is a key. Consume it but don't consume the colon
                debugString(debugSource, buffer.offset());
                char ch = buffer.peek();
                debug(StringUtils.debugString(""+ch));


                // advanceBufferBy(currFirstContentOffset + prevNonWhitespaceOffset + 1);
                advanceBufferBy(currFirstContentOffset + prevNonWhitespaceOffset + 1 - addedLeadingWhitespace);


                currLineTrimmed = currLineTrimmed.substring(0, prevNonWhitespaceOffset + 1);
                currLineLexeme = currLineLexeme.substring(0, currFirstContentOffset + prevNonWhitespaceOffset + 1 - addedLeadingWhitespace);
                debugString(debugSource, buffer.offset());
            }
        } else if (action == ScalarAction.STOP_MAP_KEY) {

            // This doesn't seem right, but it works
            if (!content.isEmpty()) {
                backupBufferToEOL();
                currLineTrimmed = null;
            } else {


                // advanceBufferBy(charOffset);
                advanceBufferBy(charOffset - addedLeadingWhitespace);


                currLineTrimmed = currLineTrimmed.substring(0, trimmedCharOffset);
            }

        } else if (action == ScalarAction.STOP_FLOW) {
            // For plain scalars only
            // Consume currLine up until prevNonWhitespaceOffset


            // advanceBufferBy(charOffset);
            advanceBufferBy(charOffset - addedLeadingWhitespace);


            if (prevNonWhitespaceOffset < 0) {
                debug("Debug comment");
                backupBufferToEOL();
                currLineTrimmed = null;
            } else {
                debugString(currLineTrimmed, "flow", trimmedCharOffset);
                currLineTrimmed = currLineTrimmed.substring(0, prevNonWhitespaceOffset + 1);
                currLineLexeme = currLineLexeme.substring(0, currFirstContentOffset + prevNonWhitespaceOffset + 1 - addedLeadingWhitespace);
            }
        } else if (action == ScalarAction.STOP_COMMENT) {
            // For plain scalars only
            // Consume currLine up until prevNonWhitespaceOffset
            if (prevNonWhitespaceOffset < 0) {
                debug("Debug comment");
                backupBufferToEOL();
                currLineTrimmed = null;
            } else {
                debugString(currLineTrimmed, "comment", trimmedCharOffset);


                // advanceBufferBy(currFirstContentOffset + prevNonWhitespaceOffset + 1);
                advanceBufferBy(currFirstContentOffset + prevNonWhitespaceOffset + 1 - addedLeadingWhitespace);


                currLineTrimmed = currLineTrimmed.substring(0, prevNonWhitespaceOffset + 1);
                currLineLexeme = currLineLexeme.substring(0, currFirstContentOffset + prevNonWhitespaceOffset + 1 - addedLeadingWhitespace);
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
            lexeme.toString().stripTrailing(), // TODO: I don't think stripTrailing is right here
            content.toString(),
            scalarStyle
        );
        addToken(token);
        debug("\n**************** END scanScalar **************** ");
    }

    private ScalarAction scalarAction(String line, boolean lineHasContent, int offset, ScalarStyle scalarStyle) {
        char ch = line.charAt(offset);
        char prev = offset > 0 ? line.charAt(offset - 1) : '\0';
        char next = offset + 1 < line.length() ? line.charAt(offset + 1) : '\0';

        if (ch == '\0') {
            return ScalarAction.STOP_EOF;
        }

        if (scalarStyle == ScalarStyle.PLAIN) {
            if (ch == '#' && (prev == '\0' || prev == ' ' || prev == '\t' || prev == '\r' || prev == '\n')) {
                return ScalarAction.STOP_COMMENT;
            }
            if (ch == '?' && !lineHasContent && (next == '\0' || next == ' ' || next == '\t' || next == '\r' || next == '\n')) {
                return ScalarAction.STOP_MAP_KEY;
            }
            if (ch == ':' && (next == '\0' || next == ' ' || next == '\t' || next == '\r' || next == '\n')) {
                return ScalarAction.STOP_MAP_VALUE;
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
            buffer.advance();
        }
        buffer.advance();
    }

    private void backupBufferToEOL() {
        while (buffer.offset() > 1 && buffer.peek() != '\n') {
            buffer.backup();
        }
    }

    private void advanceBufferBy(int n) {
        for (int i = 0; i < n; i++) {
            buffer.advance();
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
        if (!isIdentifierStartChar(buffer.peek())) {
            return;
        }
        buffer.advance();
        for (char ch = buffer.peek(); !buffer.isAtEnd() && !(ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n'); ch = buffer.peek()) {
            ch = buffer.peek();
            char next = buffer.peekNext();
            if ((ch == ':' && (next == ' ' || next == '\t' || next == '\r' || next == '\n')) ||
                (ch == '-' && (next == ' ' || next == '\t' || next == '\r' || next == '\n')) ||
                (flowDepth > 0 && (ch == ',' || ch == '{' || ch == '}' || ch == '[' || ch == ']'))) {
                break;
            }
            buffer.advance();
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
            buffer.advance(); // '!'
            buffer.advance(); // '<'

            while (!buffer.isAtEnd() && buffer.peek() != '>') {
                buffer.advance();
            }

            if (!buffer.isAtEnd()) {
                buffer.advance(); // consume '>'
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
            buffer.advance();
        }

        addToken(YamlTokenType.TAG);
    }

    private void handleNewlineAndIndentation(char c) {

        // if (c == '\t') {
        //     error(YamlDiagnosticCode.TAB_NOT_ALLOWED);
        // }
        if (buffer.peek() == '\t') {
            while (buffer.peek() == '\t' || buffer.peek() == ' ') {
                buffer.advance();
            }
            buffer.startTokenWindow();
            lineHasTabs = true;
            return;
        }

        // 1. First Newline
        String lexeme = (c == '\r' && buffer.peek() == '\n') ? "\r\n" : "\n";
        if (lexeme.length() == 2) buffer.advance();

        // Use column - lexeme.length() to point to the start of the newline
        addStructuralToken(YamlTokenType.NEWLINE, buffer.column() - lexeme.length() + 1);
        lineHasTabs = false;

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



        // if (buffer.peek() == '\t') {
        //     error(YamlDiagnosticCode.TAB_NOT_ALLOWED);
        // }
        if (buffer.peek() == '\t') {
            while (buffer.peek() == '\t' || buffer.peek() == ' ') {
                buffer.advance();
            }
            buffer.startTokenWindow();
            lineHasTabs = true;
            return;
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
        streamStarted = false;
        streamEnded = false;
        previousNonWhitespaceToken = null;

        this.flowDepth = 0;
        this.indentationLevels.clear();
        this.indentationLevels.push(1);

        pendingTokens.clear();

        // Handle UTF-8 BOM if present at start of stream/string
        if (buffer.peek() == '\uFEFF') {
            buffer.advance();
        }
    }

    private void updatePreviousNonWhitespaceToken(YamlToken token) {
        TokenCategory cat = token.getType().getCategory();
        if (cat != TokenCategory.WHITESPACE &&
            cat != TokenCategory.INDENTATION &&
            cat != TokenCategory.INTERNAL
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
        updatePreviousNonWhitespaceToken(token);
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

    private void debugString(String string, int pos) {
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.DEBUG)) return;

        reporter.debug(StringUtils.debugString(string, pos));
    }

    private void debugString(String string, String name, int pos) {
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.DEBUG)) return;

        reporter.debug(StringUtils.debugString(string, name, pos));
    }
}