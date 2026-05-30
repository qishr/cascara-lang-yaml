package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.SimpleReporter;
import io.github.qishr.cascara.lang.yaml.ast.*;
import io.github.qishr.cascara.lang.yaml.processor.YamlParser;

class YamlStandardComplianceTest {

    private YamlOptions options;
    private YamlParser parser;
    private Reporter reporter;
    private List<Diagnostic> diagnostics;

    public void collect(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public void clear(URI uri) {
        diagnostics.clear();
    }


    @BeforeEach
    void init() {
        diagnostics = new ArrayList<>();
        reporter = new SimpleReporter().setProblemCollector(this::collect);
        options = new YamlOptions().setStrict(true);
        parser = new YamlParser()
            .setOptions(options)
            .setReporter(reporter);
    }

    // 1. NESTED EMPTY COLLECTIONS
    @Test
    void testNestedEmptyCollections() throws Exception {
        String yaml = "empty_map: {}\nempty_seq: []\nnested: [[]]";
        YamlDocument doc = parser.parse(yaml);
        assertNotNull(doc);
        YamlMapNode root = (YamlMapNode) doc.getRoot();

        assertTrue(((YamlMapNode)root.get("empty_map")).getEntries().isEmpty());
        assertEquals(0, ((YamlSequenceNode)root.get("empty_seq")).size());
    }

    // 2. MIXED BLOCK AND FLOW
    @Test
    void testMixedBlockAndFlow() throws Exception {
        String yaml = """
            block_map:
              flow_seq: [a, b, c]
              inner_block:
                - item
            """;
        YamlDocument doc = parser.parse(yaml);
        YamlMapNode root = (YamlMapNode) doc.getRoot();

        // The root contains one key "block_map" which is itself a map
        YamlMapNode blockMap = (YamlMapNode) root.get("block_map");
        assertEquals(2, blockMap.getEntries().size());
    }

    // 3. MULTI-LINE SCALARS (LITERAL)
    @Test
    void testLiteralBlockScalar() throws Exception {
        String yaml = "content: |\n  line one\n  line two";
        YamlDocument doc = parser.parse(yaml);
        YamlMapNode root = (YamlMapNode) doc.getRoot();

        YamlScalarNode scalar = (YamlScalarNode) root.get("content");
        assertTrue(scalar.getString().contains("\n"));
    }

    // 4. THE "RECORDS" REGRESSION (EXPANDED STYLE)
    @Test
    void testExpandedScalarRegression() throws Exception {
        String yaml = "key:\n  -\n    indented_value";
        YamlDocument doc = parser.parse(yaml);
        YamlMapNode root = (YamlMapNode) doc.getRoot();

        YamlSequenceNode seq = (YamlSequenceNode) root.get("key");
        assertEquals("indented_value", ((YamlScalarNode)seq.get(0)).getString());
    }

    // 5. TRAILING COMMENTS AT END OF FILE
    @Test
    void testTrailingComments() throws Exception {
        String yaml = "key: value\n# This comment is at the very end\n  # and indented weirdly";
        assertDoesNotThrow(() -> parser.parse(yaml));
    }

    // 6. DEEP NESTING LIMITS
    @Test
    void testDeepNesting() throws Exception {
        String yaml = "a: { b: { c: { d: { e: final } } } }";
        YamlDocument doc = parser.parse(yaml);
        assertNotNull(doc);
        YamlMapNode root = (YamlMapNode) doc.getRoot();
        assertNotNull(root.get("a"));
    }

    // 7. KEY WITH SPECIAL CHARACTERS
    @Test
    void testComplexKeys() throws Exception {
        String yaml = "\"quoted key\": value\n'single quoted': value";
        YamlDocument doc = parser.parse(yaml);
        YamlMapNode root = (YamlMapNode) doc.getRoot();

        assertEquals(2, root.getEntries().size());
        assertEquals("value", root.getString("quoted key"));
        assertEquals("value", root.getString("single quoted"));
    }

    // 8. DUPLICATE KEY HANDLING
    @Test
    void testDuplicateKeys() throws Exception {
        String yaml = "dup: first\ndup: second";

        // Assert that the parser fails on duplicate keys
        // assertThrows(YamlParserException.class, () -> {
            parser.parse(yaml);
        // });

        assertFalse(diagnostics.isEmpty());
    }

    // 9. TYPE INFERENCE
    @Test
    void testTypeInference() throws Exception {
        String yaml = "bool: true\nnum: 123.45";
        YamlDocument doc = parser.parse(yaml);
        YamlMapNode root = (YamlMapNode) doc.getRoot();

        assertTrue(root.getBoolean("bool"));
        assertEquals(123.45, root.getDouble("num"), 0.001);
    }

    // 10. EMPTY LINE NOISE
    @Test
    void testEmptyLinesAndTabs() throws Exception {
        String yaml = "key: value\n\n\n    \nnext: value";
        assertDoesNotThrow(() -> {
            YamlDocument doc = parser.parse(yaml);
            YamlMapNode root = (YamlMapNode) doc.getRoot();
            assertEquals(2, root.getEntries().size());
        });
    }
}