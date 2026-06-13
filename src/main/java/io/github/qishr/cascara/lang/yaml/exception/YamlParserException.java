package io.github.qishr.cascara.lang.yaml.exception;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

public class YamlParserException extends ParserException {

    /// Standard constructor for parser-detected logic errors.
    public YamlParserException(int line, int column, DiagnosticCode code, Object... details) {
        super(line, column, code, details);
    }

    /// Standard constructor for parser-detected logic errors.
    public YamlParserException(YamlToken token, DiagnosticCode code, Object... details) {
        super(token, code, details);
    }

    /// Constructor for I/O or Stream failures.
    public YamlParserException(Throwable cause, DiagnosticCode code, Object... details) {
        super(cause, code, details);
    }

}
