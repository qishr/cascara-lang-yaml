package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.streaming.Event;
import io.github.qishr.cascara.common.lang.streaming.EventType;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.token.Token;
import io.github.qishr.cascara.lang.yaml.exception.YamlDiagnosticCode;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

class YamlStreamEngine {
    private final YamlTokenizer tokenizer;
    private final Deque<Integer> indentStack = new ArrayDeque<>();
    private final Deque<EventType> contextStack = new ArrayDeque<>();

    private Token currentToken;
    private Token bufferedToken;

    private final boolean includeComments;

    private int targetDedentCount = 0;
    private boolean isDocumentEnded = false;
    private boolean rootOpened = false;
    private boolean insideExplicitKey = false;

    private boolean insideBlockScalar = false;
    private boolean isFoldedBlock = false;
    private final StringBuilder blockScalarBuffer = new StringBuilder();

    YamlStreamEngine(InputStream input, Reporter reporter, boolean includeComments) {
        this.tokenizer = new YamlTokenizer().setReporter(reporter);
        this.tokenizer.open(input);
        this.indentStack.push(0);
        this.contextStack.push(EventType.START_OBJECT);
        this.includeComments = includeComments;
    }

    boolean hashNextEvent() {
        return !isDocumentEnded || targetDedentCount > 0;
    }

    Event nextEvent() throws ParserException {
        if (!rootOpened) {
            rootOpened = true;
            return new StreamingEvent(1, 1, EventType.START_OBJECT, "");
        }

        if (targetDedentCount > 0) {
            targetDedentCount--;
            EventType closedContext = contextStack.pop();
            indentStack.pop();
            EventType endType = (closedContext == EventType.START_ARRAY) ? EventType.END_ARRAY : EventType.END_OBJECT;
            return new StreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), endType, "");
        }

        advanceToken();

        if (currentToken == null || currentToken.getType() == YamlTokenType.EOF) {
            if (insideBlockScalar) {
                insideBlockScalar = false;
                blockScalarBuffer.append("\n");
                String finalContent = blockScalarBuffer.toString();
                blockScalarBuffer.setLength(0);
                return new StreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), EventType.VALUE_SCALAR, finalContent);
            }

            if (contextStack.size() > 0) {
                targetDedentCount = contextStack.size();
                return nextEvent();
            }
            isDocumentEnded = true;
            return new StreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), EventType.END_DOCUMENT, "");
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
                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.VALUE_SCALAR, finalContent);
            }

            if (indentStack.size() > 1) {
                indentStack.pop();
                EventType closedContext = contextStack.pop();
                EventType endType = (closedContext == EventType.START_ARRAY) ? EventType.END_ARRAY : EventType.END_OBJECT;
                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), endType, "");
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
                    contextStack.push(EventType.START_ARRAY);
                    return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_ARRAY, "");
                } else {
                    contextStack.push(EventType.START_OBJECT);
                    return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_OBJECT, "");
                }
            }
            return nextEvent();
        }

        // ====================================================================
        // Flow Style Structural Interceptors
        // ====================================================================

        // Flow Sequences [...]
        if (currentToken.getType() == YamlTokenType.SEQUENCE_START) {
            contextStack.push(EventType.START_ARRAY);
            // Push a sentinel placeholder to protect the block indentation tracking
            indentStack.push(-1);
            return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_ARRAY, "");
        }

        if (currentToken.getType() == YamlTokenType.SEQUENCE_END) {
            if (contextStack.peek() == EventType.START_ARRAY) {
                contextStack.pop();
                indentStack.pop();
                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.END_ARRAY, "");
            }
            throw new ParserException(currentToken, YamlDiagnosticCode.UNEXPECTED_CLOSE_BRACKET);
        }

        // Flow Maps {...}
        if (currentToken.getType() == YamlTokenType.MAP_START) {
            contextStack.push(EventType.START_OBJECT);
            indentStack.push(-1);
            return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_OBJECT, "");
        }

        if (currentToken.getType() == YamlTokenType.MAP_END) {
            if (contextStack.peek() == EventType.START_OBJECT) {
                contextStack.pop();
                indentStack.pop();
                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.END_OBJECT, "");
            }
            throw new ParserException(currentToken, YamlDiagnosticCode.UNEXPECTED_CLOSE_BRACE);
        }

        if (currentToken.getType() == YamlTokenType.COMMA) { // ','
            // Comma separates elements/pairs explicitly; advance immediately
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.COMMENT) {
            if (includeComments) {
                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.COMMENT, currentToken.getContent());
            }
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.KEY_INDICATOR) {
            insideExplicitKey = true;
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.SCALAR) {
            String value = currentToken.getContent();

            if (insideBlockScalar) {
                // Check if this text line broke the scalar's block structure baseline
                if ("|".equals(value) || ">".equals(value)) {
                    // Fall through to parse structural indicator change
                } else {
                    if (blockScalarBuffer.length() > 0) {
                        blockScalarBuffer.append(isFoldedBlock ? " " : "\n");
                    }
                    blockScalarBuffer.append(value);
                    return nextEvent();
                }
            }

            if (insideExplicitKey) {
                insideExplicitKey = false;
                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.FIELD_NAME, value);
            }

            if (isNextTokenValueIndicator()) {
                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.FIELD_NAME, value);
            }

            if ("|".equals(value) || ">".equals(value)) {
                insideBlockScalar = true;
                isFoldedBlock = ">".equals(value);
                blockScalarBuffer.setLength(0);
                return nextEvent();
            }

            return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.VALUE_SCALAR, value);
        }

        if (currentToken.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR) {
            if (contextStack.peek() != EventType.START_ARRAY) {
                contextStack.push(EventType.START_ARRAY);
                indentStack.push(currentToken.getStartColumn() - 1);
                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_ARRAY, "");
            }
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.VALUE_INDICATOR
                || currentToken.getType() == YamlTokenType.NEWLINE
                || currentToken.getType() == YamlTokenType.STREAM_START) {
            return nextEvent();
        }

        return null;
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