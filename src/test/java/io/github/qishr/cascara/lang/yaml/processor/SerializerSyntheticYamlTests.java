package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;

public class SerializerSyntheticYamlTests extends SerializerTestBase {

    // @Test
    // void test_explicitKeys_a() {
    //     testIntegrity("""
    //         mapping:
    //           ? foo
    //           : 1
    //           ? bar baz
    //           : 2
    //           ? "qux:quux"
    //           : 3
    //         """);
    // }

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
        TestUtils.assertEquals(expected, actual);
    }
}
