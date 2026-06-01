package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.SimpleReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.yaml.ast.*;
import io.github.qishr.cascara.lang.yaml.processor.YamlParser;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

class YamlParserTest {

    private final YamlParser parser = new YamlParser();

    @Test
    void testNewLineInsideNestedObject() throws Exception {
        String yaml = """
            a:
                b: 1

                c: 2
            """;

        Reporter reporter = new SimpleReporter().setLevel(Level.TRACE);
        YamlParser parser = new YamlParser().setReporter(reporter);
        parser.parse(yaml);

    }


    @Test
    void testIndentedScalarInSequence() throws Exception {
        String yaml = """
                mimeTypes:
                  -
                    "text/css"
                """;

        YamlDocument doc = parser.parse(yaml);

        // Access the root map via doc.getRoot()
        assertTrue(doc.getRoot() instanceof YamlMapNode);
        YamlMapNode rootMap = (YamlMapNode) doc.getRoot();

        // Use the get(String key) helper from MapAstNode
        YamlNode rootValue = rootMap.get("mimeTypes");

        assertTrue(rootValue instanceof YamlSequenceNode, "Expected a SequenceNode for mimeTypes");
        YamlSequenceNode seq = (YamlSequenceNode) rootValue;

        // SequenceAstNode uses get(index)
        YamlNode firstItem = seq.get(0);
        assertTrue(firstItem instanceof YamlScalarNode, "Expected a ScalarNode inside the sequence");

        YamlScalarNode scalar = (YamlScalarNode) firstItem;
        // ScalarAstNode uses getString() or getPrimitive()
        assertEquals("text/css", scalar.getString(), "Should parse indented scalar without quotes");
    }

    @Test
    void testEmptyFileDoesNotCrash() throws Exception {
        String yaml = "";
        assertDoesNotThrow(() -> {
            YamlDocument doc = parser.parse(yaml);
            // doc.getRoot() might be a MapNode with no entries
            if (doc.getRoot() instanceof YamlMapNode map) {
                assertTrue(map.getEntries().isEmpty());
            }
        });
    }

    @Test
    void testNestedBlockMap() throws Exception {
        String yaml = """
                records:
                  -
                    id: 1
                    name: "test"
                """;

        YamlDocument doc = parser.parse(yaml);
        YamlMapNode rootMap = (YamlMapNode) doc.getRoot();

        YamlSequenceNode seq = (YamlSequenceNode) rootMap.get("records");
        // Get the first item in sequence, then cast to map
        YamlMapNode innerMap = (YamlMapNode) seq.get(0);

        assertEquals(2, innerMap.getEntries().size());
        assertEquals(1, innerMap.getInteger("id"));
        assertEquals("test", innerMap.getString("name"));
    }

    @Test
    void testMixedStyles() throws Exception {
        String yaml = """
                compact: [1, 2, 3]
                expanded:
                  -
                    1
                  -
                    2
                """;
        YamlDocument doc = parser.parse(yaml);
        YamlMapNode root = (YamlMapNode) doc.getRoot();

        // Accessing values by key and checking style
        YamlSequenceNode compact = (YamlSequenceNode) root.get("compact");
        YamlSequenceNode expanded = (YamlSequenceNode) root.get("expanded");

        assertEquals(CollectionStyle.FLOW, compact.getStyle());
        assertEquals(CollectionStyle.BLOCK, expanded.getStyle());
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
}