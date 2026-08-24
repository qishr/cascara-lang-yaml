package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;

public class AstParserYts3RLNTests extends AstParserTestBase {
    @Test
    public void test3RLN_00() {
        String yaml = "\"1 leading\n    \\ttab\"";
        String expected = "1 leading \ttab";
        String actual = convert(yaml);
        debug(yaml, expected, actual);
        TestUtils.assertEquals(expected, actual);
    }

    @Test
    public void test3RLN_01() {
        String yaml = "\"2 leading\n    \\\ttab\"";
        String expected = "2 leading \ttab";
        String actual = convert(yaml);
        debug(yaml, expected, actual);
        TestUtils.assertEquals(expected, actual);
    }

    @Test
    public void test3RLN_02() {
        String yaml = "\"3 leading\n    \ttab\"";
        String expected = "3 leading tab";
        String actual = convert(yaml);
        debug(yaml, expected, actual);
        TestUtils.assertEquals(expected, actual);
    }

    @Test
    public void test3RLN_03() {
        String yaml = "\"4 leading\n    \\t  tab\"";
        String expected = "4 leading \t  tab";
        String actual = convert(yaml);
        debug(yaml, expected, actual);
        TestUtils.assertEquals(expected, actual);
    }

    @Test
    public void test3RLN_04() {
        String yaml = "\"5 leading\n    \\\t  tab\"";
        String expected = "5 leading \t  tab";
        String actual = convert(yaml);
        debug(yaml, expected, actual);
        assertEquals(expected, actual, error(expected, actual));
    }

    @Test
    public void test3RLN_05() {
        String yaml = "\"6 leading\n    \t   tab\"";
        String expected = "6 leading tab";
        String actual = convert(yaml);
        debug(yaml, expected, actual);
        TestUtils.assertEquals(expected, actual);
    }

    //
    // Helpers
    //

    private void debug(String yaml, String expected, String actual) {
        if (DEBUG) {
            System.out.println("YAML: " + StringUtils.debugString(yaml));
            System.out.println("Converted: " + StringUtils.debugString(actual));
            System.out.println("Expected: " + StringUtils.debugString(expected));
        }
    }

    private String convert(String yaml) {
        YamlAstParser parser = new YamlAstParser();

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlConverter converter = new YamlConverter();
        YamlNode normalized = normalize(doc.getBody());
        AstNode ast = converter.toPlainAst(normalized);

        assertInstanceOf(ScalarAstNode.class, ast);
        ScalarAstNode<?> scalar = (ScalarAstNode<?>) ast;
        return scalar.asString();
    }

    private String error(String expected, String actual) {
        return String.format(
            "Expected: %s  Actual: %s",
            StringUtils.debugString(expected),
            StringUtils.debugString(actual)
        );
    }
}
