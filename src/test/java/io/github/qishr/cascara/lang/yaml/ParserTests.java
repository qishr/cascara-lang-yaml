package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.ast.YamlAliasNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchorNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.processor.YamlParser;

public class ParserTests {
    @Test
    void test_parser_anchoredScalar() {
        String yaml = "status: &val active\nlink: *val";
        YamlParser parser = new YamlParser();
        YamlDocument doc = parser.parse(yaml);

        assertInstanceOf(YamlMapNode.class, doc.getRoot());
        YamlMapNode map = (YamlMapNode) doc.getRoot();
        YamlNode statusValue = map.get("status");

        // Check Anchor
// Check Anchor
        assertEquals("val", statusValue.getAnchor());

        // We need to get the actual scalar content inside the anchor wrapper
        if (statusValue instanceof YamlAnchorNode wrapper) {
            YamlNode inner = wrapper.getInnerNode();
            assertInstanceOf(YamlScalarNode.class, inner);
            assertEquals("active", ((YamlScalarNode)inner).getValue()); // or .getString() if it exists there
        } else {
            // If it's not a wrapper, it must be the scalar itself
            assertEquals("active", ((YamlScalarNode)statusValue).getValue());
        }

        // Check Alias
        YamlNode linkValue = map.get("link");
        assertTrue(linkValue instanceof YamlAliasNode);
        assertEquals("val", ((YamlAliasNode)linkValue).getAlias());
    }

    @Test
    void test_parser_anchoredMap() {
        String yaml = "defaults: &settings\n" +
                      "  debug: true\n" +
                      "  level: 1\n" +
                      "current: *settings";
        YamlParser parser = new YamlParser();
        YamlDocument doc = parser.parse(yaml);

        YamlNode defaults = doc.get("defaults");

        // The entire MapNode should have the anchorName
        assertTrue(defaults instanceof YamlMapNode);
        assertEquals("settings", defaults.getAnchor());

        YamlNode current = doc.get("current");
        assertTrue(current instanceof YamlAliasNode);
    }
}
