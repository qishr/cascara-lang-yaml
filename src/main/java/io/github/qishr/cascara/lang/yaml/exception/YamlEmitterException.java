package io.github.qishr.cascara.lang.yaml.exception;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.LocatableException;

public class YamlEmitterException extends LocatableException {

    public YamlEmitterException(Throwable cause, DiagnosticCode code, Object... details) {
        super(null, UNKNOWN_COORD, UNKNOWN_COORD, cause, code, details);
    }

    public YamlEmitterException(DiagnosticCode code, Object... details) {
        this(null, code, details);
    }

    // public YamlEmitterException(String message, Throwable cause, int line, int column, URI uri) {
    //     super(message, cause, line, column, uri);
    // }
}
