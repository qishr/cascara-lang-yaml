package io.github.qishr.cascara.lang.yaml.exception;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;

public enum YamlDiagnosticCode implements DiagnosticCode {

    TAB_NOT_ALLOWED("YAML-101", "Tab characters are not allowed for indentation in YAML"),

    EXPECTED_COMMA_OR_CLOSE_BRACE("YAML-201", "Expected ',' or '}' in flow map"),
    EXPECTED_EOS("YAML-203", "Expected end of stream"),
    EXPECTED_SCALAR("YAML-204", "Expected scalar"),
    EXPECTED_COLON_FLOW_MAP("YAML-205", "Expected ':' after key in flow map"),
    EXPECTED_OPEN_BRACE_FLOW_MAP("YAML-206", "Expected '{' to start flow map"),
    EXPECTED_CLOSE_BRACKET("YAML-207", "Expected ']'"),
    EXPECTED_OPEN_BRACKET("YAML-208", "Expected '['"),
    EXPECTED_COLON_MAP_KEY("YAML-209", "Expected ':' after key"),

    MAP_KEY_INDENTATION("YAML-302", "Inconsistent indentation for map key"),
    EXPECTED_INDENTATION_BLOCK_SCALAR("YAML-303", "Inconsistent indentation for block scalar"),
    EXPECTED_DEDENT_BLOCK_COMMENT("YAML-304", "Expected dedent after block content"),

    DUPLICATE_KEY("YAML-403", "Duplicate key found: '{0}'"),

    FAILED_TO_MAP_TYPE("YAML-501", "Failed to map {0} to YAML AST: {1}"),
    FAILED_TO_MAP_AST("YAML-502", "Failed to map YAML AST to {0}: {1}"),

    CLASS_NOT_SERIALIZABLE("YAML-503", "Class {0} is not serializable"),
    FIELD_NOT_ACCESSIBLE("", "Field {0} is not accessible"),
    NO_SUCH_METHOD("YAML-", "No such method: {0}"),
    INVOCATION_TARGET_EXCEPTION("", "Method {0} threw an exception"),

    EXPECTED_MAP_STRUCTURE("YAML-", "Expected a map structure for class {0}"),
    FAILED_SERIALIZE("YAML-", "Failed to serialize: {0}"),
    FAILED_DESERIALIZE("YAML-", "Failed to deserialize: {0}: {1}."),
    EXPECTED_YAML_NODE("YAML-", "Expected YamlNode for serializable type: {0}"),
    INCOMPATIBLE_TYPES("YAML-", "Incompatible types: Cannot map {0} to Java type {1}"),
    FAILED_DESERIALIZE_SCALAR("YAML-", "Failed to deserialize scalar to {0}: {1}"),
    UNSUPPORTED_TYPE("YAML-", "Unsupported field type: {0}"),
    EXPECTED_SEQUENCE("YAML-", "Expected a sequence for field: {0}");

    private final String code;
    private final String message;

    YamlDiagnosticCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}