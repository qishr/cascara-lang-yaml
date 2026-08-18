package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.diagnostic.YamlParserException;

public class AstParserExpectedFailureTests extends AstParserTestBase {

    @Test
    public void testZVH3() {
        String yaml = """
            - key: value
             - item1
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    public void test236B() {
        String yaml = """
            foo:
              bar
            invalid
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    public void testCQ3W() {
        String yaml = """
            ---
            key: "missing closing quote
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Disabled("come back to this")
    @Test
    public void test2CMS() {
        String yaml = """
            this
             is
              invalid: x
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }
}
