package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.processor.YamlParser;

class SingleFileTest {

    private final YamlOptions options = new YamlOptions().setStrict(true);

    // TODO: diagnostic level in one place for all tests?

    // private final YamlParser parser = new YamlParser().setOptions(options);
    private final YamlParser parser = new YamlParser()
            .setOptions(options)
            .setReporter(new StandardReporter().setLevel(Level.TRACE));

    @Test
    void testExplicitKeys() {
        String content = """
            mapping:
              ? foo
              : 1
              ? bar baz
              : 2
              ? "qux:quux"
              : 3
            tiles:
              ? X: -10
                Y: -10
              : 2
            """;
        YamlNode root = parser.parse(content);

        if (root instanceof YamlMapNode map) {
            YamlMapNode tiles = map.getMap("tiles");
            assertEquals(1, tiles.entrySet().size());
        }

        // assertDoesNotThrow(() -> parser.parse(content), "Should have parsed");
    }
}