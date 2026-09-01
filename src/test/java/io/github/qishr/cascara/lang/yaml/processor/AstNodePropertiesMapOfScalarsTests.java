package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;

public class AstNodePropertiesMapOfScalarsTests extends AstNodePropertiesTestBase {

    @Test
    public void testTag() {
        String yaml = """
            x: !!str v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);
        YamlScalar scalar = root.getScalar("x");
        assertStringEquals("v", scalar.getContent());
        assertStringEquals("!!str", scalar.getTag());
        assertStringEquals(STR_TAG, scalar.getResolvedTag());
    }

    @Test
    public void testAnchor() {
        String yaml = """
            x: &a v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);
        YamlScalar scalar = root.getScalar("x");
        assertStringEquals("v", scalar.getContent());
        assertStringEquals("a", scalar.getAnchor());
    }

    @Test
    public void testTagAndAnchor() {
        String yaml = """
            x: !!str &a v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);
        YamlScalar scalar = root.getScalar("x");
        assertStringEquals("v", scalar.getContent());
        assertStringEquals("!!str", scalar.getTag());
        assertStringEquals(STR_TAG, scalar.getResolvedTag());
        assertStringEquals("a", scalar.getAnchor());
    }

    @Test
    public void testAnchorAndTag() {
        String yaml = """
            x: &a !!str v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);
        YamlScalar scalar = root.getScalar("x");
        assertStringEquals("v", scalar.getContent());
        assertStringEquals("a", scalar.getAnchor());
        assertStringEquals("!!str", scalar.getTag());
        assertStringEquals(STR_TAG, scalar.getResolvedTag());
    }
}
