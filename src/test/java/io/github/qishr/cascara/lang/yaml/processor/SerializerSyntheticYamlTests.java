package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;

public class SerializerSyntheticYamlTests extends SerializerTestBase {
    @Test
    void test_simpleKeys() {
        String expected = """
            mapping:
              foo: 1
              bar baz: 2
              "qux:quux": 3
            """;

        YamlMap tiles = new YamlMap().put(
            new YamlScalar("mapping"),
            new YamlMap()
                .put("foo", new YamlScalar(1))
                .put("bar baz", new YamlScalar(2))
                .put(
                    new YamlScalar("qux:quux").setScalarStyle(ScalarStyle.DOUBLE_QUOTED),
                    new YamlScalar(3)
                )
        );

        String actual = serializer.toString(tiles);
        if (!expected.equals(actual)) {
            reportMismatch(expected, actual);
        }
        assertStringEquals(expected, actual);
    }

    @Test
    void test_explicitKeys_a() {
        String expected = """
            mapping:
              ? foo
              : 1
              ? bar baz
              : 2
              ? "qux:quux"
              : 3
            """;

        YamlMap tiles = new YamlMap().put(
            new YamlScalar("mapping"),
            new YamlMap()
                .put(new YamlMapEntry(
                    new YamlScalar("foo"),
                    new YamlScalar(1),
                    true
                ))
                .put(new YamlMapEntry(
                    new YamlScalar("bar baz"),
                    new YamlScalar(2),
                    true
                ))
                .put(new YamlMapEntry(
                    new YamlScalar("qux:quux").setScalarStyle(ScalarStyle.DOUBLE_QUOTED),
                    new YamlScalar(3),
                    true
                ))
        );

        String actual = serializer.toString(tiles);
        if (!expected.equals(actual)) {
            reportMismatch(expected, actual);
        }
        assertStringEquals(expected, actual);
    }

    @Test
    void test_explicitKeys_b() {
        String expected = """
            tiles:
              ? x: -10
                y: -10
              : 2
            """;

        YamlMap tiles = new YamlMap().put(
            new YamlScalar("tiles"),
            new YamlMap().put(
                new YamlMap()
                    .put("x", new YamlScalar(-10))
                    .put("y", new YamlScalar(-10)),
                new YamlScalar(2)
            )
        );

        String actual = serializer.toString(tiles);
        if (!expected.equals(actual)) {
            reportMismatch(expected, actual);
        }
        assertStringEquals(expected, actual);
    }
}
