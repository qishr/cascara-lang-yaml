package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.lang.yaml.ast.*;
import io.github.qishr.cascara.lang.yaml.ast.CollectionStyle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YamlFlowParserTest {

    private final YamlParser parser = new YamlParser();

    @Test
    void testEmptyFlowContainers() {
        // Test empty flow map
        YamlNode emptyMapRoot = parser.parse("{}");
        assertTrue(emptyMapRoot instanceof YamlMapNode);
        assertTrue(((YamlMapNode) emptyMapRoot).isEmpty());

        // Test empty flow sequence
        YamlNode emptySeqRoot = parser.parse("[]");
        assertTrue(emptySeqRoot instanceof YamlSequenceNode);
        assertEquals(0, ((YamlSequenceNode) emptySeqRoot).size());
    }

    @Test
    void testStandardFlowSequence() {
        String yaml = "[macos, windows, linux]";
        YamlNode root = parser.parse(yaml);

        assertTrue(root instanceof YamlSequenceNode);
        YamlSequenceNode seq = (YamlSequenceNode) root;

        assertEquals(3, seq.size());
        assertEquals("macos", ((YamlScalarNode) seq.get(0)).asString());
        assertEquals("windows", ((YamlScalarNode) seq.get(1)).asString());
        assertEquals("linux", ((YamlScalarNode) seq.get(2)).asString());
    }

    @Test
    void testFlowMapWithTrailingComma() {
        String yaml = "{ name: Cascara, version: 0.4.0, }";
        YamlNode root = parser.parse(yaml);

        assertTrue(root instanceof YamlMapNode);
        YamlMapNode map = (YamlMapNode) root;

        assertEquals(2, map.size());
        // Verify entries map properly even with trailing comma block execution
        assertNotNull(map.get("name"));
        assertNotNull(map.get("version"));
    }

    @Test
    void testNestedFlowContainers() {
        String yaml = "{ platform: macos, dependencies: [java25, javafx] }";
        YamlNode root = parser.parse(yaml);

        assertTrue(root instanceof YamlMapNode);
        YamlMapNode map = (YamlMapNode) root;

        YamlNode deps = map.get("dependencies");
        assertTrue(deps instanceof YamlSequenceNode);
        assertEquals(2, ((YamlSequenceNode) deps).size());
    }
}