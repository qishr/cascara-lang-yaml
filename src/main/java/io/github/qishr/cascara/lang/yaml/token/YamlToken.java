package io.github.qishr.cascara.lang.yaml.token;

import io.github.qishr.cascara.common.lang.token.Token;

public class YamlToken implements Token {
    private YamlTokenType type;
    private String lexeme;
    private String content;
    private int offset;
    private int line;
    private int column;

    public YamlToken(YamlTokenType type, String lexeme, String value,  int offset, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.content = value;
        this.offset = offset;
        this.line = line;
        this.column = column;
    }

    @Override
    public YamlTokenType getType() {
        return type;
    }

    @Override
    public String getLexeme() {
        return lexeme;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public int getStartLine() {
        return line;
    }

    @Override
    public int getStartColumn() {
        return column;
    }

    public void setType(YamlTokenType type) { this.type = type; }

    @Override
    public String toString() {
        String displayLexeme = lexeme.replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
        String valuePart = (content != null) ? " (Value: " + content + ")" : "";

        return String.format("[%-20s | '%-15s'%s | L:%d C:%d]",
            type,
            displayLexeme,
            valuePart,
            line,
            column);
    }
}