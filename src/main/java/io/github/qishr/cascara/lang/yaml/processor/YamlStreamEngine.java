package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.lang.streaming.Event;
import io.github.qishr.cascara.common.lang.streaming.EventType;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.token.Token;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

class YamlStreamEngine {
    private final YamlTokenizer tokenizer;
    private final Deque<Integer> indentStack = new ArrayDeque<>();

    private Token currentToken;
    private Token bufferedToken;

    private int targetDedentCount = 0;
    private boolean isDocumentEnded = false;
    private final boolean includeComments;

    YamlStreamEngine(InputStream input, Reporter reporter, boolean includeComments) {
        this.tokenizer = new YamlTokenizer().setReporter(reporter);
        this.tokenizer.open(input);
        this.indentStack.push(-1);
        this.includeComments = includeComments;
    }

    boolean hashNextEvent() {
        return !isDocumentEnded || targetDedentCount > 0;
    }

    Event nextEvent() throws ParserException {
        if (targetDedentCount > 0) {
            targetDedentCount--;
            indentStack.pop();
            return new StreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), EventType.END_OBJECT, "");
        }

        advanceToken();

        if (currentToken == null || currentToken.getType() == YamlTokenType.EOF) {
            if (indentStack.size() > 1) {
                targetDedentCount = indentStack.size() - 1;
                return nextEvent();
            }
            isDocumentEnded = true;
            return new StreamingEvent(tokenizer.getLine(), tokenizer.getColumn(), EventType.END_DOCUMENT, "");
        }

        if (currentToken.getType() == YamlTokenType.INDENT) {
            int indentWidth = currentToken.getLexeme().length();
            indentStack.push(indentWidth);
            return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.START_OBJECT, "");
        }

        if (currentToken.getType() == YamlTokenType.DEDENT || currentToken.getType() == YamlTokenType.BLOCK_END) {
            indentStack.pop();
            return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.END_OBJECT, "");
        }

        if (currentToken.getType() == YamlTokenType.COMMENT) {
            if (includeComments) {
                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.COMMENT, currentToken.getContent());
            }
            return nextEvent();
        }

        if (currentToken.getType() == YamlTokenType.SCALAR) {
            // Check if a KEY_INDICATOR follows this scalar, ignoring formatting syntax
            if (isNextContentTokenValueIndicator()) {
                int startLine = currentToken.getStartLine();
                int startColumn = currentToken.getStartColumn();
                String fieldName = currentToken.getContent();

                // Advance past the scalar itself; the subsequent loops will drain the indicators safely
                return new StreamingEvent(startLine, startColumn, EventType.FIELD_NAME, fieldName);
            }

            return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.VALUE_SCALAR, currentToken.getContent());
        }

        // Safely skip standalone structural indicators since they are resolved by context shifts
        if (currentToken.getType() == YamlTokenType.KEY_INDICATOR
            || currentToken.getType() == YamlTokenType.VALUE_INDICATOR
            || currentToken.getType() == YamlTokenType.NEWLINE
            || currentToken.getType() == YamlTokenType.STREAM_START) {
            return nextEvent();
        }

        return null;
    }

    private boolean isNextContentTokenValueIndicator() throws ParserException {
        if (bufferedToken == null) {
            bufferedToken = tokenizer.nextToken();
        }

        return bufferedToken != null && bufferedToken.getType() == YamlTokenType.VALUE_INDICATOR;
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