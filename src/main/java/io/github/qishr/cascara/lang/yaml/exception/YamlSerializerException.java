package io.github.qishr.cascara.lang.yaml.exception;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.exception.SerializerException;

public class YamlSerializerException extends SerializerException {
    //
    // Without Location
    //

    public YamlSerializerException(Throwable cause, DiagnosticCode code, Object... details) {
        super(cause, code, details);
    }

    public YamlSerializerException(DiagnosticCode code, Object... details) {
        super(null, code, details);
    }

    //
    // With Location
    //

    public YamlSerializerException(AstNode node, Throwable cause, DiagnosticCode code, Object... details) {
        super(cause, code, details);
    }

    public YamlSerializerException(AstNode node, DiagnosticCode code, Object... details) {
        super(node, null, code, details);
    }
}
