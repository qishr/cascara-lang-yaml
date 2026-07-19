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

        // TODO: Make this configurable
        // debugTokenGrid(yaml, tokens);

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

        // TODO: Make this configurable
        // debugTokenGrid(yaml, tokens);

        assertEquals(5, schemaToken.getStartLine(), "Schema should be on line 5");
        assertEquals(9, schemaToken.getStartColumn(), "Schema key must align with 'files' (Col 9)");
    }
}