package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
import io.github.qishr.cascara.lang.yaml.diagnostic.YamlParserException;

public class StructuralTests extends BaseAstParserTest {

    @Test
    public void testStartsOnNewLine_key_and_scalar_valid1() {
        String yaml = """
            a:
              x
            b:
              y:
            """;

        tokenize(yaml);
        YamlNode doc = parser.parse(yaml);

        YamlMap map = (YamlMap) doc;
        assertEquals(2, map.size());

        assertEquals("x", map.getString("a"));
        assertInstanceOf(YamlMap.class, map.get("b"));
    }

    // THis works by pure luck
    // The indentation of x and b that the scalar scanner sees does not match.
    // x's indentation was consumed before scalar scanning
    @Test
    public void testStartsOnNewLine_key_and_scalar_valid2() {
        String yaml = """
            a:
              x
              b
            """;

        tokenize(yaml);
        YamlNode doc = parser.parse(yaml);

        YamlMap map = (YamlMap) doc;
        assertEquals(1, map.size());

        assertEquals("x b", map.getString("a"));
    }

    @Test
    public void testStartsOnNewLine_key_and_scalar_valid3() {
        String yaml = """
            --- >
             x
             b
            """;

        tokenize(yaml);

        // YamlNode doc = parser.parse(yaml);
        // YamlScalar scalar = (YamlScalar) doc;

        YamlStream stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlScalar scalar = (YamlScalar) normalize(doc.getBody());

        TestUtils.assertEquals("x b\n", scalar.asString());
    }

    // This fails
    @Test
    public void testStartsOnNewLine_key_and_scalar_invalid() {
        String yaml = """
            a:
              x
            b
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parse(yaml));
    }

}
