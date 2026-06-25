package io.github.qishr.cascara.lang.yaml.processor;

interface SourceBuffer {
    char advance();
    char peek();
    char peekNext();
    char peekAhead(int steps);
    void backup();
    boolean isAtEnd();

    int line();
    int column();
    int offset();

    // The window tracking state metrics needed by token factories
    void startTokenWindow();
    String getTokenWindowLexeme();
    int windowStartOffset();
    int windowStartLine();
    int windowStartColumn();
}