// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


package io.github.qishr.cascara.lang.yaml.diagnostic;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;

public enum YamlDiagnosticCode implements DiagnosticCode {

    ERROR("YAML-101", "{0}"),
    TAB_NOT_ALLOWED("YAML-102", "Tab characters are not allowed for indentation in YAML"),
    INVALID_ESCAPE("YAML-103", "Invalid escape sequence: {0}"),

    // Expected Tokens
    EXPECTED_COMMA_OR_CLOSE_BRACE("YAML-111", "Expected \",\" or \"}\" in flow map"),
    EXPECTED_SCALAR("YAML-112", "Expected scalar but got \"{0}\""),
    EXPECTED_MAP_KEY("YAML-113", "Expected map key"),
    EXPECTED_OPEN_BRACE_FLOW_MAP("YAML-114", "Expected \"{\" to start flow map"),
    EXPECTED_CLOSE_BRACKET("YAML-115", "Expected \"]\""),
    EXPECTED_OPEN_BRACKET("YAML-116", "Expected \"[\""),
    EXPECTED_COLON_MAP_KEY("YAML-117", "Expected \":\" after key"),
    EXPECTED_COLON_FLOW_MAP("YAML-118", "Expected \":\" after key in flow map"),
    EXPECTED_CLOSE_SINGLE_QUOTE("YAML-119", "Expected closing single quote"),
    EXPECTED_CLOSE_DOUBLE_QUOTE("YAML-120", "Expected closing double quote"),

    // Unexpected Tokens
    UNEXPECTED_TOKEN("YAML-132", "Unexpected {0}"),
    UNEXPECTED_CLOSE_BRACKET("YAML-132", "Unexpected \"]\""),
    UNEXPECTED_CLOSE_BRACE("YAML-133", "Unexpected \"}\""),
    BLOCK_SCALAR_HEADER_EXTRA("YAML-134", "Block scalar header includes extra characters: {0}"),

    // TODO: Move these to parser section
    INCONSISTENT_INDENTATION("YAML-141", "Inconsistent indentation"),
    EXPECTED_INDENTATION_BLOCK_SCALAR("YAML-142", "Inconsistent indentation for block scalar"),
    EXPECTED_DEDENT_BLOCK_COMMENT("YAML-143", "Expected dedent after block content"),

    UNEXPECTED_EMPTY_LEXEME("YAML-144", "Unexpected empty lexeme"),
    UNEXPECTED_END_OF_BUFFER("YAML-145", "Unexpected end of buffer"),

    // Parser
    DEPTH_LIMIT("YAML-202", "Depth limit exceeded"),
    DUPLICATE_KEY("YAML-203", "Duplicate key found: \"{0}\""),
    UNKNOWN_DIRECTIVE("YAML-204", "Unknown directive: '{0}'"),
    EXPECTED_INDENT("YAML-205", "Expected indent"),
    EXPECTED_DEDENT("YAML-206", "Expected dedent"),
    TOO_MANY_ANCHORS("YAML-207", "A node can have at most one anchor"),
    TOO_MANY_TAGS("YAML-208", "A node can have at most one tag"),
    MISSING_DIRECTIVES_END_INDICATOR("YAML-209", "Missing directives-end indicator line"),
    IMPLICIT_KEY_SINGLE_LINE("YAML-210", "Implicit keys need to be on a single line"),
    BLOCK_COLLECTION_SAME_LINE_AS_MARKER("YAML-211", "Block collection cannot start on same line with directives-end marker"),
    MAPPING_KEY_COLUMN("YAML-212", "All mapping items must start at the same column"),
    BLOCK_COLLECTION_INSIDE_FLOW("YAML-213", "Block collections are not allowed within flow collections"),
    TOO_MANY_PARTS("YAML-214", "%YAML directive should contain exactly one part"),
    UNSUPPORTED_VERSION("YAML-215", "Unsupported YAML version {0}"),
    CANNOT_RESOLVE_TAG("YAML-216", "Could not resolve tag {0}"),
    EXPLICIT_INDENTATION_INDICATOR_NEEDED("YAML-217", "Block scalars with more-indented leading empty lines must use an explicit indentation indicator"),
    DUPICATE_YAML_DIRECTIVE("YAML-218", "Duplicate YAML directive"),
    ALIAS_MUST_NOT_SPECIFY_PROPERTIES("YAML-219", "An alias node must not specify any properties"),
    COMMENT_NOT_SEPARATED("YAML-220", "Comments must be separated from other tokens by white space characters"),

    // TODO:
    // Anchor cannot be an empty string
    // Set items must all have null values
    // Block collection cannot start on same line with directives-end marker

    // Serializer / Emitter
    UNEXPECTED_NODE_TYPE("YAML-301", "Unexpected node type: {0}");

    private final String code;
    private final String message;

    YamlDiagnosticCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}