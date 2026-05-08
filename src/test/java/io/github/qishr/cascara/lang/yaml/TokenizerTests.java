package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.processor.YamlParser;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

public class TokenizerTests {

    /// Asserts that the sequence of token types matches the expected types.
    ///
    /// This helper is used to verify the internal state of the [YamlTokenizer]
    /// without involving the [YamlParser].
    ///
    /// @param tokens The list of actual tokens produced by the tokenizer.
    /// @param expectedTypes A varargs list of the expected [YamlTokenType]s.
    private void assertTokensMatch(List<YamlToken> tokens, YamlTokenType... expectedTypes) {
        List<YamlTokenType> actualTypes = tokens.stream()
                .map(YamlToken::getType)
                .toList();

        if (actualTypes.size() != expectedTypes.length) {
            dumpTokens(tokens);
            org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Arrays.asList(expectedTypes),
                actualTypes,
                "Token stream length mismatch."
            );
        }

        for (int i = 0; i < expectedTypes.length; i++) {
            if (actualTypes.get(i) != expectedTypes[i]) {
                dumpTokens(tokens);
                org.junit.jupiter.api.Assertions.assertEquals(
                    expectedTypes[i],
                    actualTypes.get(i),
                    "Mismatch at token index " + i
                );
            }
        }
    }

    /// Helper to print tokens in a readable format when a test fails.
    private void dumpTokens(List<YamlToken> tokens) {
        System.out.println("\n--- Captured Token Stream ---");
        for (int i = 0; i < tokens.size(); i++) {
            YamlToken t = tokens.get(i);
            System.out.printf("[%2d] %-20s | L:%-3d C:%-3d | Lexeme: '%s'%n",
                i, t.getType(), t.getStartLine(), t.getStartColumn(),
                t.getLexeme().replace("\n", "\\n").replace("\r", "\\r"));
        }
        System.out.println("-----------------------------\n");
    }

    YamlTokenizer tokenizer;

    @BeforeEach
    void setupEach() {
        tokenizer = new YamlTokenizer();
    }

    //
    //
    //

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
}
