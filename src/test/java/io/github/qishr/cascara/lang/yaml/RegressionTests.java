package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.processor.YamlParser;

public class RegressionTests {
    private final YamlOptions options = new YamlOptions();
    private final YamlParser parser = new YamlParser().setOptions(options);

    // If we have a literal block where we want to preserve exact formatting (like a script or a snippet), your current code will turn key: value into key : value (adding spaces) or merge multiple tokens into a single line incorrectly.
    // A block scalar should ignore the "meaning" of tokens (like : or -) and just treat everything between the INDENT and DEDENT as raw text, only stripping the common indentation prefix.
    @Test
    void testLiteralBlockPreservesExactSpacing() {
        String yaml = """
            script: |
              line:one
              line:two
            """;
        YamlMapNode doc = (YamlMapNode)parser.parse(yaml);
        YamlMapNode root = (YamlMapNode) doc;
        YamlScalarNode script = (YamlScalarNode) root.get("script");

        // CURRENT EXPECTATION (Failing): "line : one \nline : two \n"
        // REAL YAML EXPECTATION: "line:one\nline:two\n"
        assertEquals("line:one\nline:two\n", script.asString());
    }
}
