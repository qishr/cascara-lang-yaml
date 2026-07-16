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
import io.github.qishr.cascara.common.lang.streaming.StreamingEventType;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.token.Token;
import io.github.qishr.cascara.lang.yaml.exception.YamlDiagnosticCode;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.streaming.YamlStreamingEvent;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

public class YamlStreamEngine {
    private final YamlTokenizer tokenizer;
    private final Deque<Integer> indentStack = new ArrayDeque<>();
    private final Deque<StreamingEventType> contextStack = new ArrayDeque<>();

    private Token currentToken;
    private Token bufferedToken;

    private final boolean includeComments;

    private int targetDedentCount = 0;
    private boolean isDocumentEnded = false;
    private boolean rootOpened = false;
    private boolean insideExplicitKey = false;
    private boolean insideBlockScalar = false;
    private final StringBuilder blockScalarBuffer = new StringBuilder();

    public YamlStreamEngine(InputStream input, Reporter reporter, boolean includeComments) {
        this.tokenizer = new YamlTokenizer().setReporter(reporter);
        this.tokenizer.open(input);
        this.indentStack.push(0);
        this.contextStack.push(StreamingEventType.START_OBJECT);
        this.includeComments = includeComments;
    }

    public YamlStreamEngine setReporter(Reporter reporter) {
        return this;
    }

    public boolean hashNextEvent() {
        // If we are already done, do not claim to have events
        return !isDocumentEnded;
    }

    public  YamlStreamingEvent nextEvent() throws ParserException {
        if (isDocumentEnded) return null; // Or handle as appropriate

        if (!rootOpened) {
            rootOpened = true;
            return new YamlStreamingEvent(1, 1, StreamingEventType.START_OBJECT, "");
        }

        if (targetDedentCount > 0) {
            targetDedentCount--;
            StreamingEventType closedContext = contextStack.pop();
            indentStack.pop();
            StreamingEventType endType = (closedContext == StreamingEventType.START_ARRAY) ? StreamingEventType.END_ARRAY : StreamingEventType.END_OBJECT;
            return new YamlStreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), endType, "");
        }

        advanceToken();

        if (currentToken.getType() == YamlTokenType.DOCUMENT_START) {
            // If a document was already in progress, close it
            if (rootOpened) {
                // You might need to flush contexts here if not already done
                return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.END_DOCUMENT, "");
            }
            // Otherwise, this is the start of the first document
            rootOpened = true;
            return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.START_DOCUMENT, "");
        }

        if (currentToken == null || currentToken.getType() == YamlTokenType.EOF || currentToken.getType() == YamlTokenType.STREAM_END) {
            if (insideBlockScalar) {
                insideBlockScalar = false;
                blockScalarBuffer.append("\n");
                String finalContent = blockScalarBuffer.toString();
                blockScalarBuffer.setLength(0);
                return new YamlStreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), StreamingEventType.VALUE_SCALAR, finalContent);
            }
            if (contextStack.size() > 0) {
                targetDedentCount = contextStack.size();
                return nextEvent();
            }
            isDocumentEnded = true;
            return new YamlStreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), StreamingEventType.END_DOCUMENT, "");
        }

        // 1. Structural drops flush block scalars naturally
        if (currentToken.getType() == YamlTokenType.DEDENT || currentToken.getType() == YamlTokenType.BLOCK_END) {
            if (insideBlockScalar) {
                insideBlockScalar = false;
                blockScalarBuffer.append("\n");
                String finalContent = blockScalarBuffer.toString();
                blockScalarBuffer.setLength(0);

                // Buffer this token so the layout engine executes it on the next loop turn
                bufferedToken = currentToken;
                return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.VALUE_SCALAR, finalContent);
            }

            if (indentStack.size() > 1) {
                indentStack.pop();
                StreamingEventType closedContext = contextStack.pop();
                StreamingEventType endType = (closedContext == StreamingEventType.START_ARRAY) ? StreamingEventType.END_ARRAY : StreamingEventType.END_OBJECT;
                return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), endType, "");
            }
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.INDENT) {
            // If we are gathering a block scalar, ignore indentation increases
            // as they are just deep text line content inside the literal block.
            if (insideBlockScalar) {
                return nextEvent();
            }

            int indentWidth = currentToken.getStartColumn() - 1;

            if (indentWidth > indentStack.peek()) {
                indentStack.push(indentWidth);

                if (isNextTokenSequenceIndicator()) {
                    contextStack.push(StreamingEventType.START_ARRAY);
                    return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.START_ARRAY, "");
                } else {
                    contextStack.push(StreamingEventType.START_OBJECT);
                    return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.START_OBJECT, "");
                }
            }
            return nextEvent();
        }

        // ====================================================================
        // Flow Style Structural Interceptors
        // ====================================================================

        // Flow Sequences [...]
        if (currentToken.getType() == YamlTokenType.SEQUENCE_START) {
            contextStack.push(StreamingEventType.START_ARRAY);
            // Push a sentinel placeholder to protect the block indentation tracking
            indentStack.push(-1);
            return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.START_ARRAY, "");
        }

        if (currentToken.getType() == YamlTokenType.SEQUENCE_END) {
            if (contextStack.peek() == StreamingEventType.START_ARRAY) {
                contextStack.pop();
                indentStack.pop();
                return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.END_ARRAY, "");
            }
            throw new ParserException(currentToken, YamlDiagnosticCode.UNEXPECTED_CLOSE_BRACKET);
        }

        // Flow Maps {...}
        if (currentToken.getType() == YamlTokenType.MAP_START) {
            contextStack.push(StreamingEventType.START_OBJECT);
            indentStack.push(-1);
            return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.START_OBJECT, "");
        }

        if (currentToken.getType() == YamlTokenType.MAP_END) {
            if (contextStack.peek() == StreamingEventType.START_OBJECT) {
                contextStack.pop();
                indentStack.pop();
                return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.END_OBJECT, "");
            }
            throw new ParserException(currentToken, YamlDiagnosticCode.UNEXPECTED_CLOSE_BRACE);
        }

        if (currentToken.getType() == YamlTokenType.COMMA) { // ','
            // Comma separates elements/pairs explicitly; advance immediately
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.COMMENT) {
            if (includeComments) {
                return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.COMMENT, currentToken.getContent());
            }
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.KEY_INDICATOR) {
            insideExplicitKey = true;
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.SCALAR) {
            String value = currentToken.getContent();

            if (insideExplicitKey) {
                insideExplicitKey = false;
                return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.FIELD_NAME, value);
            }

            if (isNextTokenValueIndicator()) {
                return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.FIELD_NAME, value);
            }

            return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.VALUE_SCALAR, value);
        }

        if (currentToken.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR) {
            if (contextStack.peek() != StreamingEventType.START_ARRAY) {
                contextStack.push(StreamingEventType.START_ARRAY);
                indentStack.push(currentToken.getStartColumn() - 1);
                return new YamlStreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), StreamingEventType.START_ARRAY, "");
            }
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.VALUE_INDICATOR
                || currentToken.getType() == YamlTokenType.NEWLINE
                || currentToken.getType() == YamlTokenType.STREAM_START) {
            return nextEvent();
        }

        throw new ParserException(currentToken, YamlDiagnosticCode.UNEXPECTED_TOKEN, currentToken.getType());
        // return null;
    }

    private boolean isNextTokenValueIndicator() throws ParserException {
        if (bufferedToken == null) {
            bufferedToken = tokenizer.nextToken();
        }
        return bufferedToken != null && bufferedToken.getType() == YamlTokenType.VALUE_INDICATOR;
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