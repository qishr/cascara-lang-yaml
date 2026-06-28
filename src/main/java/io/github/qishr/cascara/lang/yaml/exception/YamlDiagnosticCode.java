package io.github.qishr.cascara.lang.yaml.exception;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;

public enum YamlDiagnosticCode implements DiagnosticCode {

    TAB_NOT_ALLOWED("YAML-101", "Tab characters are not allowed for indentation in YAML"),

    // Expected Tokens
    EXPECTED_COMMA_OR_CLOSE_BRACE("YAML-201", "Expected ',' or '}' in flow map"),
    EXPECTED_SCALAR("YAML-204", "Expected scalar but got: {0}"),
    EXPECTED_MAP_KEY("YAML-205", "Expected map key"),
    EXPECTED_OPEN_BRACE_FLOW_MAP("YAML-206", "Expected '{' to start flow map"),
    EXPECTED_CLOSE_BRACKET("YAML-207", "Expected ']'"),
    EXPECTED_OPEN_BRACKET("YAML-208", "Expected '['"),
    EXPECTED_COLON_MAP_KEY("YAML-209", "Expected ':' after key"),
    EXPECTED_COLON_FLOW_MAP("YAML-210", "Expected ':' after key in flow map"),

    // Unexpected Tokens
    UNEXPECTED_TOKEN("YAML-301", "Unexpected '{0}'"),
    UNEXPECTED_CLOSE_BRACKET("YAML-302", "Unexpected ']'"),
    UNEXPECTED_CLOSE_BRACE("YAML-303", "Unexpected '}'"),

    INCONSISTENT_INDENTATION("YAML-401", "Inconsistent indentation"),
    EXPECTED_INDENTATION_BLOCK_SCALAR("YAML-402", "Inconsistent indentation for block scalar"),
    EXPECTED_DEDENT_BLOCK_COMMENT("YAML-403", "Expected dedent after block content"),

    DUPLICATE_KEY("YAML-403", "Duplicate key found: '{0}'");

    private final String code;
    private final String message;

    YamlDiagnosticCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}