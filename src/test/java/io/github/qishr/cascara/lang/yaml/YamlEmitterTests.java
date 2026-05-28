package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.lang.QuoteStyle;
import io.github.qishr.cascara.common.diagnostic.SimpleReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.processor.YamlEmitter;
import io.github.qishr.cascara.lang.yaml.processor.YamlParser;

public class YamlEmitterTests {
    @Test
    void testEmitterRoundTrip() {
        String original = "name: Cascara\nversion: 1.0\ntags:\n  - java\n  - yaml";
        YamlParser parser = new YamlParser();
        YamlNode root = parser.parse(original);

        YamlEmitter emitter = new YamlEmitter();
        String emitted = emitter.emit(root);

        assertEquals(original.trim(), emitted.trim());
    }

    @Test
    void testComplexCommentRoundTrip() {
        String original =
            "# Project Configuration\n" +
            "project: Cascara # Structural Editor\n" +
            "settings: # Global settings\n" +
            "  # Enable experimental features\n" +
            "  debug: true\n" +
            "  modes:\n" +
            "    - fast # High performance\n" +
            "    - safe";

        YamlParser parser = new YamlParser();

        parser.setReporter(new SimpleReporter((s) -> {
            System.err.print(s);
        }).setLevel(Level.TRACE));

        YamlDocument yaml = parser.parse(original);

        YamlEmitter emitter = new YamlEmitter();
        String result = emitter.emit(yaml);

        // Using trim to ignore trailing whitespace differences
        assertEquals(original.trim(), result.trim());
    }

    @Test
    void test_emitter_anchorRoundTrip() {
        String yaml = "key: &myAnchor value\ncopy: *myAnchor\n";
        YamlParser parser = new YamlParser();
        YamlNode root = parser.parse(yaml);

        YamlEmitter emitter = new YamlEmitter();
        String output = emitter.emit(root);

        assertEquals(yaml, output);
    }

    @Test
    void testExpandedStyleSequence() {
        // Create a simple sequence: ["java", "yaml"]
        YamlSequenceNode seq = new YamlSequenceNode();
        seq.add(new YamlScalarNode("java", QuoteStyle.PLAIN));
        seq.add(new YamlScalarNode("yaml", QuoteStyle.PLAIN));

        // Wrap it in a document for the emitter
        YamlMapNode root = new YamlMapNode();

        root.put("tags", seq);

        // Act: Use the new fluent expanded style
        YamlOptions options = new YamlOptions().setExpandedStyle(true);
        String output = new YamlEmitter()
                .setOptions(options)
                .emit(root);

        // Assert: Check for the characteristic newline-and-indent after the dash
        // Assert: tags: must be at column 0 for a root-level map
        String expected =
            "tags:\n" +
            "  -\n" +
            "    java\n" +
            "  -\n" +
            "    yaml";

        assertEquals(expected.trim(), output.trim(), "Expanded style should place scalars on a new indented line.");
    }
}
