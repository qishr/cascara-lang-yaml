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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

public class TokenizerTests extends TokenizerTestBase {

    @Test
    void testSimpleMap() {
        String yaml = """
                a: x
                b: y
                """;

        List<YamlToken> tokens = tokenizer.tokenize(yaml);

        assertTokensMatch(tokens,
            YamlTokenType.STREAM_START,

            YamlTokenType.SCALAR,
            YamlTokenType.VALUE_INDICATOR,
            YamlTokenType.SCALAR,
            YamlTokenType.NEWLINE,

            YamlTokenType.SCALAR,
            YamlTokenType.VALUE_INDICATOR,
            YamlTokenType.SCALAR,
            // YamlTokenType.NEWLINE,

            YamlTokenType.EOF,
            YamlTokenType.STREAM_END
        );
    }

    @Test
    void testSimpleSequence() {
        String yaml = """
                - a
                - b
                """;

        List<YamlToken> tokens = tokenizer.tokenize(yaml);

        assertTokensMatch(tokens,
            YamlTokenType.STREAM_START,

            YamlTokenType.SEQUENCE_ENTRY_INDICATOR,
            YamlTokenType.SCALAR,
            YamlTokenType.NEWLINE,

            YamlTokenType.SEQUENCE_ENTRY_INDICATOR,
            YamlTokenType.SCALAR,
            // YamlTokenType.NEWLINE,

            YamlTokenType.EOF,
            YamlTokenType.STREAM_END
        );
    }

    @Test
    void testTokenBalance() {
        String yaml = """
                root:
                level1:
                    - item1
                """;
        // Assuming YamlTokenizer exists and returns a List of YamlToken
        List<YamlToken> tokens = new YamlTokenizer().tokenize(yaml);

        long indents = tokens.stream().filter(t -> t.getType() == YamlTokenType.INDENT).count();
        long dedents = tokens.stream().filter(t -> t.getType() == YamlTokenType.DEDENT).count();

        assertEquals(indents, dedents, "Every INDENT must be matched by a DEDENT");
        assertEquals(YamlTokenType.STREAM_START, tokens.get(0).getType());
        assertEquals(YamlTokenType.STREAM_END, tokens.get(tokens.size() - 1).getType());
    }

    @Test
    void testContentRegistryIndentation() {
        String yaml = "records:\n" +
                    "  - canonicalId: \"text/markdown\"\n" +
                    "    canonicalName: \"Markdown\"";

        List<YamlToken> tokens = tokenizer.tokenize(yaml);

        // This is what the YAML spec REQUIRES for this structure:
        assertTokensMatch(tokens,
            YamlTokenType.STREAM_START,
            YamlTokenType.SCALAR,          // records
            YamlTokenType.VALUE_INDICATOR, // :
            YamlTokenType.NEWLINE,
            YamlTokenType.INDENT,          // Level 2 (The '-' starts at Col 3)
            YamlTokenType.SEQUENCE_ENTRY_INDICATOR, // -
            YamlTokenType.INDENT,          // The key level
            YamlTokenType.SCALAR,          // canonicalId
            YamlTokenType.VALUE_INDICATOR, // :
            YamlTokenType.SCALAR,          // "text/markdown"
            YamlTokenType.NEWLINE,
            YamlTokenType.SCALAR,          // canonicalName (NO INDENT HERE! SUCCESS!)
            YamlTokenType.VALUE_INDICATOR, // :
            YamlTokenType.SCALAR,          // "Markdown"
            YamlTokenType.DEDENT,          // Level 5 (content)
            YamlTokenType.DEDENT,          // Level 2 (records)
            YamlTokenType.EOF,
            YamlTokenType.STREAM_END
        );
    }

    @Test
    void testSequenceIndentationStability() {
        // This specific structure often triggers the "Double Indent" bug
        // because the spaces trigger one INDENT and the '-' triggers another.
        String yaml = """
                key:
                  - item
                """;
        List<YamlToken> tokens = tokenizer.tokenize(yaml);

        // Filter for structural tokens to see the "skeleton" of the document
        List<YamlTokenType> structure = tokens.stream()
                .map(YamlToken::getType)
                .filter(t -> t == YamlTokenType.INDENT || t == YamlTokenType.DEDENT)
                .toList();

        // EXPECTATION:
        // 1. One INDENT for the 2 spaces before the dash.
        // 2. One DEDENT at the end to return to root.
        List<YamlTokenType> expected = List.of(YamlTokenType.INDENT, YamlTokenType.DEDENT);

        assertEquals(expected, structure,
            "A single nested sequence item should only produce ONE indent/dedent pair.");
    }

    @Test
    void testTokenizerIndentationHybrid() {
        String yaml = """
                standard:
                  - item
                compact: - item
                """;

        List<YamlToken> tokens = tokenizer.tokenize(yaml);

        // Filter to see the structural 'skeleton'
        List<YamlTokenType> structure = tokens.stream()
                .map(YamlToken::getType)
                .filter(t -> t == YamlTokenType.INDENT || t == YamlTokenType.DEDENT || t == YamlTokenType.SEQUENCE_ENTRY_INDICATOR)
                .toList();

        // EXPECTATION:
        // 1. INDENT (for '  -')
        // 2. SEQUENCE_ENTRY_INDICATOR ('-')
        // 3. DEDENT (back to root)
        // 4. INDENT (for the compact '- item' after 'compact:')
        // 5. SEQUENCE_ENTRY_INDICATOR ('-')
        // 6. DEDENT (at EOF)

        List<YamlTokenType> expected = List.of(
            YamlTokenType.INDENT, YamlTokenType.SEQUENCE_ENTRY_INDICATOR, YamlTokenType.DEDENT,
            YamlTokenType.INDENT, YamlTokenType.SEQUENCE_ENTRY_INDICATOR, YamlTokenType.DEDENT
        );

        assertEquals(expected, structure);
    }

    // TODO: This doesn't seem like valid YAML
    // Should probably result in "Unexpected block-seq-ind on same line with key"
    @Test
    void testCompactMappingSequence() {
        // The input represents a key followed immediately by a sequence on the same line
        String source = "records: - item1\n         - item2";
        List<YamlToken> tokens = tokenizer.tokenize(source);

        // Logic check:
        // 1. 'records:' is scanned.
        // 2. Dash is detected. 'isCompact' is true because last token was VALUE_INDICATOR.
        // 3. Dash logic pushes Column 12 (content start) and emits an INDENT.
        // 4. Line 2 has 10 spaces + dash, which puts content at Column 12.
        //    Since 12 matches the stack top, no INDENT is emitted on Line 2.

        assertTokensMatch(tokens,
            YamlTokenType.STREAM_START,
            YamlTokenType.SCALAR,                  // 'records'
            YamlTokenType.VALUE_INDICATOR,         // ':'
            YamlTokenType.INDENT,                  // Structural indent from compact dash
            YamlTokenType.SEQUENCE_ENTRY_INDICATOR, // '-'
            YamlTokenType.SCALAR,                  // 'item1'
            YamlTokenType.NEWLINE,
            YamlTokenType.SEQUENCE_ENTRY_INDICATOR, // '-'
            YamlTokenType.SCALAR,                  // 'item2'
            YamlTokenType.DEDENT,                  // Closing the compact block
            YamlTokenType.EOF,
            YamlTokenType.STREAM_END
        );
    }

    @Test
    void testDeeplyNestedDash() {
        String yaml = """
                sub:
                    - item
                """;
        List<YamlToken> tokens = tokenizer.tokenize(yaml);

        long indents = tokens.stream().filter(t -> t.getType() == YamlTokenType.INDENT).count();

        // If this is 2, we've found the bug. It should only be 1.
        assertEquals(1, indents, "Nesting a dash deeper than its parent should only trigger ONE indent.");
    }

    @Test
    void testFoldedScalarTokenization() {
        String yaml = "--- >\n ab\n cd\n\n ef\n\n\n gh\n";

        List<YamlToken> tokens = tokenizer.tokenize(yaml);

        assertTokensMatch(tokens,
            YamlTokenType.STREAM_START,
            YamlTokenType.DOCUMENT_START,
            YamlTokenType.SCALAR,
            // YamlTokenType.NEWLINE,
            // YamlTokenType.SCALAR,
            // YamlTokenType.NEWLINE,
            // YamlTokenType.NEWLINE,
            // YamlTokenType.SCALAR,
            // YamlTokenType.NEWLINE,
            // YamlTokenType.NEWLINE,
            // YamlTokenType.NEWLINE,
            // YamlTokenType.SCALAR,
            // YamlTokenType.NEWLINE,
            YamlTokenType.EOF,
            YamlTokenType.STREAM_END
        );
    }

}
