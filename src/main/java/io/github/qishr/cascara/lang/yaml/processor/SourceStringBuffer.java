package io.github.qishr.cascara.lang.yaml.processor;

public class SourceStringBuffer implements SourceBuffer {
    private final String source;
    private int line = 1;
    private int column = 1;
    private int offset = 0;
    private int windowStartOffset = 0;
    private int windowStartLine = 1;
    private int windowStartColumn = 1;


    public SourceStringBuffer(String source) {
        this.source = source != null ? source : "";
    }

    @Override
    public char advance() {
        if (isAtEnd()) {
            return '\0';
        }
        char c = source.charAt(offset++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    @Override
    public char peekAhead(int steps) {
        if (offset + steps >= source.length()) return '\0';
        return source.charAt(offset + steps);
    }

    @Override
    public char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(offset);
    }

    @Override
    public char peekNext() {
        if (offset + 1 >= source.length()) return '\0';
        return source.charAt(offset + 1);
    }

    @Override
    public void backup() {
        if (offset == windowStartOffset) {
            throw new IllegalStateException("Cannot backup past the start of the current token window.");
        }
        offset--;
        char c = source.charAt(offset);
        if (c == '\n') {
            line--;
            column = 1; // Safeguard fallback (same as stream buffer)
        } else {
            column--;
        }
    }

    @Override
    public boolean isAtEnd() {
        return offset >= source.length();
    }

    @Override public int line() { return line; }
    @Override public int column() { return column; }
    @Override public int offset() { return offset; }

    @Override
    public String getTokenWindowLexeme() {
        return source.substring(windowStartOffset, offset);
    }

    @Override
    public void startTokenWindow() {
        this.windowStartOffset = this.offset;
        this.windowStartLine = this.line;
        this.windowStartColumn = this.column;
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