package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.Collectors;

import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

public abstract class YamlTokenizerTestBase {

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

