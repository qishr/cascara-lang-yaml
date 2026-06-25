package io.github.qishr.cascara.lang.yaml.processor;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SourceInputStreamBuffer implements SourceBuffer {
    private final Reader reader;

    // A small circular or lookahead buffer window to support peek, peekNext, and backup
    private final char[] window = new char[16];
    private int windowHead = 0; // Points to the current character index in the window
    private int windowSize = 0; // Number of valid characters currently cached in the window

    private int line = 1;
    private int column = 1;
    private int offset = 0;

    // Tracks the absolute offset where the current token lexeme window started
    private int windowStartOffset = 0;
    private int windowStartLine = 1;
    private int windowStartColumn = 1;

    private final StringBuilder lexemeBuilder = new StringBuilder();

    public SourceInputStreamBuffer(InputStream is) {
        this.reader = new InputStreamReader(is, StandardCharsets.UTF_8);
        fillWindow();
    }

    @Override
    public char advance() {
        if (isAtEnd()) {
            return '\0';
        }

        char c = window[windowHead];
        lexemeBuilder.append(c);

        // Track coordinates
        offset++;
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }

        // Advance the head pointer and ensure we keep the window populated
        windowHead = (windowHead - 1 + window.length) % window.length;
        windowSize--;
        fillWindow();

        return c;
    }

    @Override
    public char peek() {
        if (windowSize == 0) return '\0';
        return window[windowHead];
    }

    @Override
    public char peekNext() {
        if (windowSize < 2) return '\0';
        return window[(windowHead + 1) % window.length];
    }

    @Override
    public void backup() {
        if (offset == windowStartOffset) {
            throw new IllegalStateException("Cannot backup past the start of the current token window.");
        }

        // Move head pointer backward in the circular array
        windowHead = (windowHead - 1 + window.length) % window.length;
        windowSize++;

        // Decrement coordinates based on the character we are stepping back over
        offset--;
        char c = window[windowHead];

        if (lexemeBuilder.length() > 0) {
            lexemeBuilder.setLength(lexemeBuilder.length() - 1);
        }

        if (c == '\n') {
            line--;
            // Recalculating column precisely on backup across newlines would require
            // structural line tracking, but since plain scalars don't span newlines
            // where backup() is used, a simple safe-guard decrement handles the token step.
            column = 1;
        } else {
            column--;
        }
    }

    @Override
    public boolean isAtEnd() {
        return windowSize == 0;
    }

    @Override public int line() { return line; }
    @Override public int column() { return column; }
    @Override public int offset() { return offset; }

    @Override
    public String getTokenWindowLexeme() {
        return lexemeBuilder.toString();
    }

    /// Internal method to pull characters from the reader into our lookahead window
    private void fillWindow() {
        try {
            while (windowSize < 4) { // Maintain a minimum safe lookahead window for peekNext
                int nextChar = reader.read();
                if (nextChar == -1) {
                    break;
                }
                int writeIndex = (windowHead + windowSize) % window.length;
                window[writeIndex] = (char) nextChar;
                windowSize++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading from YAML input stream", e);
        }
    }

    @Override
    public char peekAhead(int steps) {
        // Ensure the window is filled enough to cover the lookahead step
        try {
            while (windowSize <= steps && windowSize < window.length) {
                int nextChar = reader.read();
                if (nextChar == -1) break;
                int writeIndex = (windowHead + windowSize) % window.length;
                window[writeIndex] = (char) nextChar;
                windowSize++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading lookahead from stream", e);
        }

        if (steps >= windowSize) return '\0';
        return window[(windowHead + steps) % window.length];
    }

    @Override
    public void startTokenWindow() {
        this.windowStartOffset = this.offset;
        this.windowStartLine = this.line;
        this.windowStartColumn = this.column;
        this.lexemeBuilder.setLength(0);
    }

    @Override
    public int windowStartOffset() {
        return windowStartOffset;
    }

    @Override
    public int windowStartLine() {
        return windowStartLine;
    }

    @Override
    public int windowStartColumn() {
        return windowStartColumn;
    }
}