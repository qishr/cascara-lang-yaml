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


package io.github.qishr.cascara.lang.yaml.exception;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;

public enum YamlDiagnosticCode implements DiagnosticCode {

    TAB_NOT_ALLOWED("YAML-101", "Tab characters are not allowed for indentation in YAML"),

    // Expected Tokens
    EXPECTED_COMMA_OR_CLOSE_BRACE("YAML-102", "Expected \",\" or \"}\" in flow map"),
    EXPECTED_SCALAR("YAML-104", "Expected scalar but got \"{0}\""),
    EXPECTED_MAP_KEY("YAML-105", "Expected map key"),
    EXPECTED_OPEN_BRACE_FLOW_MAP("YAML-206", "Expected \"{\" to start flow map"),
    EXPECTED_CLOSE_BRACKET("YAML-107", "Expected \"]\""),
    EXPECTED_OPEN_BRACKET("YAML-108", "Expected \"[\""),
    EXPECTED_COLON_MAP_KEY("YAML-109", "Expected \":\" after key"),
    EXPECTED_COLON_FLOW_MAP("YAML-110", "Expected \":\" after key in flow map"),

    // Unexpected Tokens
    UNEXPECTED_TOKEN("YAML-111", "Unexpected {0}"),
    UNEXPECTED_CLOSE_BRACKET("YAML-112", "Unexpected \"]\""),
    UNEXPECTED_CLOSE_BRACE("YAML-113", "Unexpected \"}\""),

    INCONSISTENT_INDENTATION("YAML-114", "Inconsistent indentation"),
    EXPECTED_INDENTATION_BLOCK_SCALAR("YAML-115", "Inconsistent indentation for block scalar"),
    EXPECTED_DEDENT_BLOCK_COMMENT("YAML-116", "Expected dedent after block content"),

    // Parser
    DEPTH_LIMIT("YAML-202", "Depth limit exceeded"),
    DUPLICATE_KEY("YAML-203", "Duplicate key found: \"{0}\"");

    private final String code;
    private final String message;

    YamlDiagnosticCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}