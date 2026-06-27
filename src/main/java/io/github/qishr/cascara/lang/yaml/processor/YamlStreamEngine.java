package io.github.qishr.cascara.lang.yaml.processor;

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

    private Token currentToken;
    private Token bufferedToken; // Single-token lookahead cache

    private int targetDedentCount = 0;
    private boolean isDocumentEnded = false;

    YamlStreamEngine(InputStream input) {
        this.tokenizer = new YamlTokenizer();
        this.tokenizer.open(input);
        this.indentStack.push(-1);
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
            return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.COMMENT, currentToken.getContent());
        }

        // Lookahead Check: Is this SCALAR actually a map key?
        if (currentToken.getType() == YamlTokenType.SCALAR) {
            peekToken(); // Fill bufferedToken

            if (bufferedToken != null && bufferedToken.getType() == YamlTokenType.KEY_INDICATOR) {
                // Consume the KEY_INDICATOR so it's skipped on the next iteration
                advanceToken();
                return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.FIELD_NAME, currentToken.getContent());
            }

            // Standalone scalar value
            return new StreamingEvent(currentToken.getStartLine(), currentToken.getStartColumn(), EventType.VALUE_SCALAR, currentToken.getContent());
        }

        // Skip structural punctuation that has been translated or handled implicitly
        if (currentToken.getType() == YamlTokenType.KEY_INDICATOR || currentToken.getType() == YamlTokenType.VALUE_INDICATOR) {
            return nextEvent();
        }

        return null;
    }

    private void advanceToken() throws ParserException {
        if (bufferedToken != null) {
            this.currentToken = bufferedToken;
            this.bufferedToken = null;
        } else {
            this.currentToken = tokenizer.nextToken();
        }
    }

    private void peekToken() throws ParserException {
        if (this.bufferedToken == null) {
            this.bufferedToken = tokenizer.nextToken();
        }
    }
}