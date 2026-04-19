package io.github.qishr.cascara.lang.yaml;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;



class YamlIndentationRegressionTest extends YamlTokenizerTestBase {

    @Test
    void testDeeplyNestedSequenceReset() {
        String yaml =
            "studio:\n" +
            "  - jsonSchemas:\n" +
            "      - files:\n" +
            "          - filename1\n" +
            "        schema:\n" +
            "          filename2";

        YamlTokenizer tokenizer = new YamlTokenizer();
        List<YamlToken> tokens = tokenizer.tokenize(yaml);

        debugTokenGrid(yaml, tokens);

        assertTokenTypes(tokens,
            YamlTokenType.SCALAR, YamlTokenType.VALUE_INDICATOR, YamlTokenType.NEWLINE,
            YamlTokenType.INDENT, // Level 1 (Studio)
                YamlTokenType.SEQUENCE_ENTRY_INDICATOR,
                YamlTokenType.INDENT,
                YamlTokenType.SCALAR, YamlTokenType.VALUE_INDICATOR, YamlTokenType.NEWLINE,
                YamlTokenType.INDENT, // Level 2 (jsonSchemas)
                    YamlTokenType.SEQUENCE_ENTRY_INDICATOR,
                    YamlTokenType.INDENT,
                    YamlTokenType.SCALAR, YamlTokenType.VALUE_INDICATOR, YamlTokenType.NEWLINE,
                    YamlTokenType.INDENT, // Level 3 (files)
                        YamlTokenType.SEQUENCE_ENTRY_INDICATOR,
                        YamlTokenType.SCALAR, YamlTokenType.NEWLINE,
                    YamlTokenType.DEDENT,
                    YamlTokenType.SCALAR, YamlTokenType.VALUE_INDICATOR, YamlTokenType.NEWLINE,
                    YamlTokenType.INDENT,
                        YamlTokenType.SCALAR,
                    YamlTokenType.DEDENT,
                    YamlTokenType.DEDENT,
                YamlTokenType.DEDENT,
                YamlTokenType.DEDENT,
            YamlTokenType.DEDENT,
            YamlTokenType.EOF
        );

        assertNoUnexpectedIndents(tokens);
    }

    @Test
    void testNestedStructurePositions() {
        String yaml =
            "studio:\n" +           // L1
            "  - jsonSchemas:\n" +  // L2 (Col 3: '-')
            "      - files:\n" +    // L3 (Col 7: '-')
            "          - f1\n" +    // L4 (Col 11: '-')
            "        schema:\n" +   // L5 (Col 9: 's') <- CRITICAL ALIGNMENT
            "          f2";         // L6 (Col 11)

        YamlTokenizer tokenizer = new YamlTokenizer();
        List<YamlToken> tokens = tokenizer.tokenize(yaml);

        // Let's find the 'schema' token.
        // It should be at Line 5, Column 9.
        YamlToken schemaToken = tokens.stream()
                .filter(t -> "schema".equals(t.getLexeme()))
                .findFirst()
                .orElseThrow();

        debugTokenGrid(yaml, tokens);

        assertEquals(5, schemaToken.getStartLine(), "Schema should be on line 5");
        assertEquals(9, schemaToken.getStartColumn(), "Schema key must align with 'files' (Col 9)");
    }
}