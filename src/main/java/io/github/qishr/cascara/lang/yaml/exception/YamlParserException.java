package io.github.qishr.cascara.lang.yaml.exception;

import java.net.URI;

import io.github.qishr.cascara.common.lang.exception.ParserException;

public class YamlParserException extends ParserException {

    public YamlParserException(String message, Throwable cause) {
        super(message, cause, UNKNOWN_COORD, UNKNOWN_COORD, null);
    }

    public YamlParserException(String message, int line, int column, URI uri) {
        super(message, line, column, uri);
    }

    public YamlParserException(String message, Throwable cause, int line, int column, URI uri) {
        super(message, cause, line, column, uri);
    }
}
