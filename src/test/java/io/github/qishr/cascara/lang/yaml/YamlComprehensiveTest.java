package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.lang.yaml.YamlDocument;
import io.github.qishr.cascara.lang.yaml.YamlOptions;
import io.github.qishr.cascara.lang.yaml.ast.*;
import io.github.qishr.cascara.lang.yaml.processor.YamlEmitter;
import io.github.qishr.cascara.lang.yaml.processor.YamlParser;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

import java.util.List;

class YamlComprehensiveTest {

    private final YamlParser parser = new YamlParser();
    private final YamlTokenizer tokenizer = new YamlTokenizer();

    private void assertTokenTypes(List<YamlToken> tokens, YamlTokenType... expected) {
        for (int i = 0; i < expected.length; i++) {
            if (i >= tokens.size()) fail("Missing token at index " + i + ". Expected " + expected[i]);
            assertEquals(expected[i], tokens.get(i).getType(), "Mismatch at token " + i);
        }
    }

    // --- TOKENIZATION TESTS ---

    @Test
    void testTokenizerHandlesComplexIndentation() {
        String yaml = "key:\n  sub:\n    - item\n  next: val";
        List<YamlToken> tokens = tokenizer.tokenize(yaml);

        // Verify the Indent/Dedent balance
        long indents = tokens.stream().filter(t -> t.getType() == YamlTokenType.INDENT).count();
        long dedents = tokens.stream().filter(t -> t.getType() == YamlTokenType.DEDENT).count();

        assertEquals(2, indents, "Should have 2 indent levels");
        assertEquals(2, dedents, "Should have 2 dedent levels to return to root");
    }

    // --- PARSER EDGE CASES ---

    @Test
    void testImplicitNullValues() throws Exception {
        // Common in config: key followed by newline and another key
        String yaml = "empty_key:\nnext_key: value";
        YamlDocument doc = parser.parse(yaml);
        YamlNode val = doc.get("empty_key");
        assertTrue(val instanceof YamlScalarNode);
        assertNull(((YamlScalarNode)val).getString(), "Value-less key should result in null scalar");
    }

    @Test
    void testNestedFlowCollectionsInBlock() throws Exception {
        String yaml = "matrix: [[1, 2], [3, 4]]";
        YamlDocument doc = parser.parse(yaml);

        // 1. The root is a Map
        assertTrue(doc.getRoot() instanceof YamlMapNode);
        YamlMapNode rootMap = (YamlMapNode) doc.getRoot();

        // 2. Get the value for "matrix"
        YamlNode matrixNode = rootMap.get("matrix");
        assertTrue(matrixNode instanceof YamlSequenceNode);

        // 3. Now we are at the outer sequence: [[1, 2], [3, 4]]
        YamlSequenceNode outer = (YamlSequenceNode) matrixNode;
        assertEquals(2, outer.size());

        // 4. Get the first inner sequence: [1, 2]
        assertTrue(outer.get(0) instanceof YamlSequenceNode);
        YamlSequenceNode inner = (YamlSequenceNode) outer.get(0);
        assertEquals(2, inner.size());

        // 5. Verify a leaf value
        assertEquals("1", inner.get(0).toString());
    }

    @Test
    void testAnchorAndAliasResolution() throws Exception {
        String yaml = """
                default: &def "base"
                custom: *def
                """;
        YamlDocument doc = parser.parse(yaml);

        // Use the common MapAstNode 'get' to find the alias node
        YamlNode customVal = doc.get("custom");

        assertTrue(customVal instanceof YamlAliasNode);
        assertEquals("def", ((YamlAliasNode)customVal).getAnchor());
    }

    // --- THE "ROUND-TRIP" STABILITY TEST ---

    @Test
    void testRoundTripPreservesStructure() throws Exception {
        String original = "records:\n  -\n    id: \"1\"\n    tags:\n      -\n        a";

        // 1. Parse
        YamlDocument doc = parser.parse(original);


        YamlOptions options = new YamlOptions().setExpandedStyle(true);

        // 2. Emit
        String emitted = new YamlEmitter().setOptions(options).emit(doc);
        System.out.println("--- EMITTED START ---");
        System.out.println(emitted);
        System.out.println("--- EMITTED END ---");

        // 3. Re-Parse
        YamlDocument reParsedDoc = parser.parse(emitted);

        // 4. Verify logical equality
        YamlMapNode originalMap = (YamlMapNode) doc.getRoot();
        YamlMapNode reParsedMap = (YamlMapNode) reParsedDoc.getRoot();

        assertEquals(originalMap.getEntries().size(), reParsedMap.getEntries().size());

        // Compare the first record's ID:
        // root (map) -> records (seq) -> [0] (map) -> id (scalar)
        YamlSequenceNode seq = (YamlSequenceNode) reParsedMap.get("records");
        YamlMapNode record = (YamlMapNode) seq.get(0);

        // Use getString helper from MapAstNode/YamlMapNode
        assertEquals("1", record.getString("id"));
    }

    @Test
    void testTabIndentationFails() {
        // YAML spec forbids tabs for indentation
        String yaml = "key:\n\t- item";
        // Assuming YamlParserException extends ParserException
        assertThrows(ParserException.class, () -> parser.parse(yaml));
    }

    @Test
    void testMismatchedDedentFails() {
        String yaml = "parent:\n  child: val\n    - orphan_item";
        assertThrows(ParserException.class, () -> parser.parse(yaml));
    }}
