package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.streaming.Event;
import io.github.qishr.cascara.common.lang.streaming.EventType;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.token.Token;
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

    private int targetDedentCount = 0;
    private boolean isDocumentEnded = false;
    private boolean rootOpened = false;
    private final boolean includeComments;

    YamlStreamEngine(InputStream input, Reporter reporter, boolean includeComments) {
        this.tokenizer = new YamlTokenizer().setReporter(reporter);
        this.tokenizer.open(input);
        // Seed with 0 so root level content (width 0) doesn't push a new layer
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
            // Drain remaining open containers (including the root scope)
            if (contextStack.size() > 0) {
                targetDedentCount = contextStack.size();
                return nextEvent();
            }
            isDocumentEnded = true;
            return new StreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), EventType.END_DOCUMENT, "");
        }

        if (currentToken.getType() == YamlTokenType.INDENT) {
            int indentWidth = currentToken.getLexeme().length();

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

        if (currentToken.getType() == YamlTokenType.DEDENT || currentToken.getType() == YamlTokenType.BLOCK_END) {
            if (indentStack.size() > 1) {
                indentStack.pop();
                EventType closedContext = contextStack.pop();
                EventType endType = (closedContext == EventType.START_ARRAY) ? EventType.END_ARRAY : EventType.END_OBJECT;
                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), endType, "");
            }
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.COMMENT) {
            if (includeComments) {
                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.COMMENT, currentToken.getContent());
            }
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.SCALAR) {
            if (isNextTokenValueIndicator()) {
                int startLine = currentToken.getStartLine();
                int startColumn = currentToken.getStartColumn();
                String fieldName = currentToken.getContent();
                return new StreamingEvent(startLine, startColumn, EventType.FIELD_NAME, fieldName);
            }
            return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.VALUE_SCALAR, currentToken.getContent());
        }

        if (currentToken.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR) {
            // If we aren't inside an array context yet, this hyphen opens it!
            if (contextStack.peek() != EventType.START_ARRAY) {
                contextStack.push(EventType.START_ARRAY);
                // Push a placeholder indent tracker to stay in alignment with dedent drops
                indentStack.push(currentToken.getStartColumn() - 1);

                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_ARRAY, "");
            }
            // Sibling entry item—skip the layout dash and fetch the scalar value
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.KEY_INDICATOR
                || currentToken.getType() == YamlTokenType.VALUE_INDICATOR
                || currentToken.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR
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