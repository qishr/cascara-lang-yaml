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

    @Test
    void test_yamlDirectiveOnly() {
        String yaml ="%YAML 1.2\n";
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_dmg6() {
        String yaml ="""
            key:
              ok: 1
             wrong: 2
            """;
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    // this" should not be part of the "value" scalar because
    // it has no leading whitespace. Unless it's document-level, it
    // needs at least one space.
    @Test
    void test_GDY7() {
        String yaml ="""
            key: value
            this is #not a: key
            """;
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_7LBH() {
        String yaml ="""
            "a\nb": 1
            "c
             d": 1
            """;
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_G5U8() {
        String yaml ="""
            ---
            - [-, -]
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_H7TQ() {
        String yaml ="""
            %YAML 1.2 foo
            ---
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_S98Z() {
        String yaml ="empty block scalar: >\n \n  \n   \n # comment";

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_SF5V() {
        String yaml = """
            %YAML 1.2
            %YAML 1.2
            ---
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    // An alias node must not specify any properties
    // YAML-219
    // ALIAS_MUST_NOT_SPECIFY_PROPERTIES
    @Test
    void test_SR86() {
        String yaml = """
            key1: &a value
            key2: &b *a
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_SU74() {
        String yaml = """
            key1: &alias value1
            &b *alias : value2
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    // COMMENT_NOT_SEPARATED
    @Test
    void test_SU5Z() {
        String yaml = "key: \"value\"# invalid comment\n";

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    // Anchor before sequence entry on same line
    @Test
    void test_SY6V() {
        String yaml = "&anchor - sequence entry\n";

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    // Implicit keys need to be on a single line
    // Block collections are not allowed within flow collections
    // Flow mapping missing a separating comma

    // TODO: testUT92
    @Test
    void test_T833() {
        String yaml = """
            ---
            {
             foo: 1
             bar: 2 }
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_VJP3() {
        String yaml = """
            k: {
            k
            :
            v
            }
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_X4QW() {
        String yaml = """
            block: ># comment
              scalar
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    // Block scalar values in collections must be indented by a SPACE
    @Test
    void test_Y79Y_000() {
        String yaml = "foo: |\n\t\nbar: 1\n";
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    // Flow sequence in block collection must be sufficiently indented and end with a ]
    @Test
    void test_Y79Y_003() {
        String yaml = "- [\n\tfoo,\n foo\n ]\n";
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_Y79Y_004() {
        String yaml = "-\t-\n";
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_Y79Y_006() {
        String yaml = "?\t-\n";
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    // Tab cannot appear directly after value indicator in entry with explicit key
    @Test
    void test_Y79Y_009() {
        String yaml = "? key:\n:\tkey:\n";
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_S4GJ() {
        String yaml = """
            ---
            folded: > first line
              second line
            """;
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    // NESTED_MAPPING_IN_COMPACT_MAPPING
    @Test
    void test_ZCZ6() {
        String yaml = "a: b: c: d\n";
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    // Unexpected block-seq-ind on same line with key
    @Test
    void test_5U3A() {
        String yaml = """
            key: - a
                 - b
            """;
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_6S55() {
        String yaml = """
            key:
             - bar
             - baz
             invalid
            """;
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    void test_9MAG() {
        String yaml = "[ , a, b, c ]";
        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }
}
