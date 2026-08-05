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


package io.github.qishr.cascara.lang.yaml.internal;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.streaming.StreamingEventType;
import io.github.qishr.cascara.common.lang.token.Token;
import io.github.qishr.cascara.lang.yaml.exception.YamlDiagnosticCode;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.streaming.YamlStreamingEvent;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

public class YamlStreamEngine {
    private final YamlTokenizer tokenizer;
    private final Deque<Integer> indentStack = new ArrayDeque<>();
    private final Deque<StreamingEventType> contextStack = new ArrayDeque<>();

    private YamlToken currentToken;
    private YamlToken bufferedToken;

    private final boolean includeComments;

    private int targetDedentCount = 0;
    private boolean streamOpened = false;
    private boolean streamClosed = false;
    private boolean documentOpened = false;
    private boolean documentClosed = false;
    private boolean isDocumentEnded = false;
    private boolean insideBlockScalar = false;
    private final StringBuilder blockScalarBuffer = new StringBuilder();

    public YamlStreamEngine(InputStream input, Reporter reporter, boolean includeComments) {
        this.tokenizer = new YamlTokenizer().setReporter(reporter);
        this.tokenizer.open(input);
        this.indentStack.push(0);
        this.includeComments = includeComments;
    }

    public YamlStreamEngine setReporter(Reporter reporter) {
        // reporter already wired into tokenizer; kept for API symmetry
        return this;
    }

    public boolean hasNextEvent() {
        return !isDocumentEnded;
    }

    public YamlStreamingEvent nextEvent() throws ParserException {
        // 1. Stream start
        if (!streamOpened) {
            streamOpened = true;
            return new YamlStreamingEvent(
                tokenizer.getLine(),
                tokenizer.getColumn(),
                StreamingEventType.START_STREAM,
                ""
            );
        }

        // 2. First document start (for inputs without explicit '---')
        if (!documentOpened) {
            documentOpened = true;
            documentClosed = false;
            return new YamlStreamingEvent(
                tokenizer.getLine(),
                tokenizer.getColumn(),
                StreamingEventType.START_DOCUMENT,
                ""
            );
        }

        // 3. Pending dedent collapses (END_OBJECT / END_ARRAY)
        if (targetDedentCount > 0) {
            targetDedentCount--;
            StreamingEventType closedContext = contextStack.pop();
            indentStack.pop();
            StreamingEventType endType =
                (closedContext == StreamingEventType.START_ARRAY)
                    ? StreamingEventType.END_ARRAY
                    : StreamingEventType.END_OBJECT;
            return new YamlStreamingEvent(
                tokenizer.getLine(),
                tokenizer.getColumn(),
                endType,
                ""
            );
        }

        // 4. Advance token
        advanceToken();

        // 5. EOF / STREAM_END handling
        if (currentToken == null ||
            currentToken.getType() == YamlTokenType.EOF ||
            currentToken.getType() == YamlTokenType.STREAM_END) {

            // Flush block scalar if active
            if (insideBlockScalar) {
                insideBlockScalar = false;
                blockScalarBuffer.append("\n");
                String finalContent = blockScalarBuffer.toString();
                blockScalarBuffer.setLength(0);
                return new YamlStreamingEvent(
                    tokenizer.getLine(),
                    tokenizer.getColumn(),
                    StreamingEventType.VALUE_SCALAR,
                    finalContent
                );
            }

            // Close open MAP/SEQ contexts
            if (!contextStack.isEmpty()) {
                StreamingEventType ctx = contextStack.pop();
                indentStack.pop();
                StreamingEventType endType =
                    (ctx == StreamingEventType.START_ARRAY)
                        ? StreamingEventType.END_ARRAY
                        : StreamingEventType.END_OBJECT;

                return new YamlStreamingEvent(
                    tokenizer.getLine(),
                    tokenizer.getColumn(),
                    endType,
                    ""
                );
            }

            // Close document if open
            if (documentOpened && !documentClosed) {
                documentClosed = true;
                return new YamlStreamingEvent(
                    tokenizer.getLine(),
                    tokenizer.getColumn(),
                    StreamingEventType.END_DOCUMENT,
                    ""
                );
            }

            // Close stream
            if (!streamClosed) {
                streamClosed = true;
                isDocumentEnded = true;
                return new YamlStreamingEvent(
                    tokenizer.getLine(),
                    tokenizer.getColumn(),
                    StreamingEventType.END_STREAM,
                    ""
                );
            }

            // Fully done; no more events
            // 5. Done — but do NOT return null while hasNextEvent() is true
            isDocumentEnded = true;
            return new YamlStreamingEvent(
                tokenizer.getLine(),
                tokenizer.getColumn(),
                StreamingEventType.END_STREAM,
                ""
            );
        }

        // 6. Explicit document markers (---)
        if (currentToken.getType() == YamlTokenType.DOCUMENT_START) {
            // Close previous document if one was open
            if (documentOpened && !documentClosed) {
                documentClosed = true;
                return new YamlStreamingEvent(
                    currentToken.getStartLine(),
                    currentToken.getStartColumn(),
                    StreamingEventType.END_DOCUMENT,
                    ""
                );
            }

            // Start a new document
            documentOpened = true;
            documentClosed = false;
            isDocumentEnded = false;
            return new YamlStreamingEvent(
                currentToken.getStartLine(),
                currentToken.getStartColumn(),
                StreamingEventType.START_DOCUMENT,
                ""
            );
        }

        // 7. Root mapping detection: open START_OBJECT before first key
        if (documentOpened &&
            !documentClosed &&
            contextStack.isEmpty() &&
            currentToken.getType() == YamlTokenType.SCALAR) {

            // Peek without consuming
            Token lookahead = tokenizer.peekToken();  // <-- NEW

            if (lookahead != null && lookahead.getType() == YamlTokenType.VALUE_INDICATOR) {

                contextStack.push(StreamingEventType.START_OBJECT);
                indentStack.push(0);

                // Buffer the current scalar so SCALAR handler sees it next
                bufferedToken = currentToken;
                currentToken = null;

                return new YamlStreamingEvent(
                    tokenizer.getLine(),
                    tokenizer.getColumn(),
                    StreamingEventType.START_OBJECT,
                    ""
                );
            }
        }

        // 7b. Root mapping detection for explicit keys: '? explicit_key'
        if (documentOpened &&
            !documentClosed &&
            contextStack.isEmpty() &&
            currentToken.getType() == YamlTokenType.KEY_INDICATOR) {

            contextStack.push(StreamingEventType.START_OBJECT);
            indentStack.push(0);

            // Buffer the '?' so the next call will see the SCALAR "explicit_key"
            bufferedToken = currentToken;
            currentToken = null;

            return new YamlStreamingEvent(
                tokenizer.getLine(),
                tokenizer.getColumn(),
                StreamingEventType.START_OBJECT,
                ""
            );
        }


        // 8. Structural drops (DEDENT / BLOCK_END)
        if (currentToken.getType() == YamlTokenType.DEDENT ||
            currentToken.getType() == YamlTokenType.BLOCK_END) {

            if (insideBlockScalar) {
                insideBlockScalar = false;
                blockScalarBuffer.append("\n");
                String finalContent = blockScalarBuffer.toString();
                blockScalarBuffer.setLength(0);

                bufferedToken = currentToken;
                return new YamlStreamingEvent(
                    currentToken.getStartLine(),
                    currentToken.getStartColumn(),
                    StreamingEventType.VALUE_SCALAR,
                    finalContent
                );
            }

            if (indentStack.size() > 1) {
                indentStack.pop();
                StreamingEventType closedContext = contextStack.pop();
                StreamingEventType endType =
                    (closedContext == StreamingEventType.START_ARRAY)
                        ? StreamingEventType.END_ARRAY
                        : StreamingEventType.END_OBJECT;
                return new YamlStreamingEvent(
                    currentToken.getStartLine(),
                    currentToken.getStartColumn(),
                    endType,
                    ""
                );
            }
            return nextEvent();
        }

        // 9. INDENT: open nested object/array
        if (currentToken.getType() == YamlTokenType.INDENT) {
            if (insideBlockScalar) {
                return nextEvent();
            }

            int indentWidth = currentToken.getStartColumn() - 1;

            if (indentWidth > indentStack.peek()) {
                indentStack.push(indentWidth);

                if (isNextTokenSequenceIndicator()) {
                    contextStack.push(StreamingEventType.START_ARRAY);
                    return new YamlStreamingEvent(
                        currentToken.getStartLine(),
                        currentToken.getStartColumn(),
                        StreamingEventType.START_ARRAY,
                        ""
                    );
                } else {
                    contextStack.push(StreamingEventType.START_OBJECT);
                    return new YamlStreamingEvent(
                        currentToken.getStartLine(),
                        currentToken.getStartColumn(),
                        StreamingEventType.START_OBJECT,
                        ""
                    );
                }
            }
            return nextEvent();
        }

        // ====================================================================
        // Flow style structural interceptors
        // ====================================================================

        // Flow sequences [...]
        if (currentToken.getType() == YamlTokenType.SEQUENCE_START) {
            contextStack.push(StreamingEventType.START_ARRAY);
            indentStack.push(-1);
            return new YamlStreamingEvent(
                currentToken.getStartLine(),
                currentToken.getStartColumn(),
                StreamingEventType.START_ARRAY,
                ""
            );
        }

        if (currentToken.getType() == YamlTokenType.SEQUENCE_END) {
            if (contextStack.peek() == StreamingEventType.START_ARRAY) {
                contextStack.pop();
                indentStack.pop();
                return new YamlStreamingEvent(
                    currentToken.getStartLine(),
                    currentToken.getStartColumn(),
                    StreamingEventType.END_ARRAY,
                    ""
                );
            }
            throw new ParserException(currentToken, YamlDiagnosticCode.UNEXPECTED_CLOSE_BRACKET);
        }

        // Flow maps {...}
        if (currentToken.getType() == YamlTokenType.MAP_START) {
            contextStack.push(StreamingEventType.START_OBJECT);
            indentStack.push(-1);
            return new YamlStreamingEvent(
                currentToken.getStartLine(),
                currentToken.getStartColumn(),
                StreamingEventType.START_OBJECT,
                ""
            );
        }

        if (currentToken.getType() == YamlTokenType.MAP_END) {
            if (contextStack.peek() == StreamingEventType.START_OBJECT) {
                contextStack.pop();
                indentStack.pop();
                return new YamlStreamingEvent(
                    currentToken.getStartLine(),
                    currentToken.getStartColumn(),
                    StreamingEventType.END_OBJECT,
                    ""
                );
            }
            throw new ParserException(currentToken, YamlDiagnosticCode.UNEXPECTED_CLOSE_BRACE);
        }

        // Comma in flow style: skip
        if (currentToken.getType() == YamlTokenType.COMMA) {
            return nextEvent();
        }

        // Comments
        if (currentToken.getType() == YamlTokenType.COMMENT) {
            if (includeComments) {
                return new YamlStreamingEvent(
                    currentToken.getStartLine(),
                    currentToken.getStartColumn(),
                    StreamingEventType.COMMENT,
                    currentToken.getContent()
                );
            }
            return nextEvent();
        }

        // Explicit key indicator '?'
        if (currentToken.getType() == YamlTokenType.KEY_INDICATOR) {
            return nextEvent();
        }

        // Scalars: keys vs values

        if (currentToken.getType() == YamlTokenType.SCALAR) {
            String value = currentToken.getContent();

            // Literal block: already has correct newlines from tokenizer
            // Folded block: already folded by tokenizer
            // Plain/quoted: unchanged

            if (isNextTokenValueIndicator()) {
                return new YamlStreamingEvent(
                    currentToken.getStartLine(),
                    currentToken.getStartColumn(),
                    StreamingEventType.FIELD_NAME,
                    value
                );
            }

            return new YamlStreamingEvent(
                currentToken.getStartLine(),
                currentToken.getStartColumn(),
                StreamingEventType.VALUE_SCALAR,
                value
            );
        }

        // Block sequence entry '-'
        if (currentToken.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR) {
            if (contextStack.peek() != StreamingEventType.START_ARRAY) {
                contextStack.push(StreamingEventType.START_ARRAY);
                indentStack.push(currentToken.getStartColumn() - 1);
                return new YamlStreamingEvent(
                    currentToken.getStartLine(),
                    currentToken.getStartColumn(),
                    StreamingEventType.START_ARRAY,
                    ""
                );
            }
            return nextEvent();
        }

        // Value indicator ':', newline, stream start: skip
        if (currentToken.getType() == YamlTokenType.VALUE_INDICATOR
            || currentToken.getType() == YamlTokenType.NEWLINE
            || currentToken.getType() == YamlTokenType.STREAM_START) {
            return nextEvent();
        }

        // Anchors & aliases as scalar values
        if (currentToken.getType() == YamlTokenType.ANCHOR) {
            return new YamlStreamingEvent(
                currentToken.getStartLine(),
                currentToken.getStartColumn(),
                StreamingEventType.VALUE_SCALAR,
                "&" + currentToken.getContent()
            );
        }

        if (currentToken.getType() == YamlTokenType.ALIAS) {
            return new YamlStreamingEvent(
                currentToken.getStartLine(),
                currentToken.getStartColumn(),
                StreamingEventType.VALUE_SCALAR,
                "*" + currentToken.getContent()
            );
        }

        throw new ParserException(currentToken, YamlDiagnosticCode.UNEXPECTED_TOKEN, currentToken.getType());
    }

    private boolean isNextTokenValueIndicator() throws ParserException {
        if (bufferedToken == null) {
            bufferedToken = tokenizer.nextToken();
        }

        while (bufferedToken != null &&
               (bufferedToken.getType() == YamlTokenType.NEWLINE ||
                bufferedToken.getType() == YamlTokenType.COMMENT ||
                bufferedToken.getType() == YamlTokenType.INDENT ||
                bufferedToken.getType() == YamlTokenType.DEDENT)) {

            bufferedToken = tokenizer.nextToken();
        }

        return bufferedToken != null &&
               bufferedToken.getType() == YamlTokenType.VALUE_INDICATOR;
    }

    private boolean isNextTokenSequenceIndicator() throws ParserException {
        if (bufferedToken == null) {
            bufferedToken = tokenizer.nextToken();
        }
        return bufferedToken != null && bufferedToken.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR;
    }

    private void advanceToken() throws ParserException {
        if (bufferedToken != null) {
            this.currentToken = bufferedToken;
            this.bufferedToken = null;
        } else {
            this.currentToken = tokenizer.nextToken();
        }
    }
}
