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


package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.Collectors;

import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

public abstract class TokenizerTestBase {

    protected void assertTokenTypes(List<YamlToken> tokens, YamlTokenType... expectedTypes) {
        // Filter out STREAM_START/END if you want to focus on the meat
        List<YamlTokenType> actualTypes = tokens.stream()
                .map(YamlToken::getType)
                .filter(t -> t != YamlTokenType.STREAM_START && t != YamlTokenType.STREAM_END)
                .collect(Collectors.toList());

        for (int i = 0; i < expectedTypes.length; i++) {
            if (i >= actualTypes.size()) {
                fail("Expected " + expectedTypes[i] + " at index " + i + " but stream ended.");
            }
            assertEquals(expectedTypes[i], actualTypes.get(i),
                "Token mismatch at index " + i + ". Full stream: " + actualTypes);
        }
        assertEquals(expectedTypes.length, actualTypes.size(), "Token count mismatch.");
    }

    protected void assertNoUnexpectedIndents(List<YamlToken> tokens) {
        long indents = tokens.stream().filter(t -> t.getType() == YamlTokenType.INDENT).count();
        long dedents = tokens.stream().filter(t -> t.getType() == YamlTokenType.DEDENT).count();
        assertEquals(indents, dedents, "Indent/Dedent count mismatch! State stack was not cleared.");
    }

    protected void assertTokenAt(List<YamlToken> tokens, int index, YamlTokenType type, int line, int col, String lexeme) {
        assertTrue(index < tokens.size(), "Index " + index + " out of bounds.");
        YamlToken t = tokens.get(index);

        assertAll("Token at index " + index + " mismatch",
            () -> assertEquals(type, t.getType(), "Type mismatch"),
            () -> assertEquals(line, t.getStartLine(), "Line mismatch"),
            () -> assertEquals(col, t.getStartColumn(), "Column mismatch"),
            () -> assertEquals(lexeme, t.getLexeme(), "Lexeme mismatch")
        );
    }

    protected void debugTokenGrid(String yaml, List<YamlToken> tokens) {
        System.out.println("--- YAML RULER (10s) ---");
        System.out.println("123456789012345678901234567890");
        System.out.println(yaml);
        System.out.println("------------------------");

        System.out.printf("%-15s | %-4s | %-4s | %-10s%n", "TYPE", "LINE", "COL", "LEXEME");
        System.out.println("------------------------------------------------");

        for (YamlToken t : tokens) {
            String lexeme = t.getLexeme() == null ? "" : t.getLexeme().replace("\n", "\\n");
            System.out.printf("%-15s | %-4d | %-4d | %-10s%n",
                t.getType(), t.getStartLine(), t.getStartColumn(), lexeme);
        }
    }
}

