package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocumentNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlStreamNode;

public class Yts3RLNTests {
    @Test
    public void test3RLN_00() {

        String yaml = "\"1 leading\n    \\ttab\"";

        // My original
        // String expected = "1 leading \ttab";

        // Copilot (14:30-ish)
        // String expected = "1 leading \\ttab";

        // Copilot 14:42
        // String expected = "1 leading \ttab";

        // Copilot 14:47
        String expected = "1 leading \\ttab";

        String actual = convert(yaml);
        debug(yaml, expected, actual);
        assertEquals(expected, actual, error(expected, actual));
    }

    @Test
    public void test3RLN_01() {
        String yaml = "\"2 leading\n    \\\ttab\"";
        String expected = "2 leading \ttab";
        String actual = convert(yaml);
        debug(yaml, expected, actual);
        assertEquals(expected, actual, error(expected, actual));
    }

    @Test
    public void test3RLN_02() {
        String yaml = "\"3 leading\n    \ttab\"";
        String expected = "3 leading tab";
        String actual = convert(yaml);
        debug(yaml, expected, actual);
        assertEquals(expected, actual, error(expected, actual));
    }

    @Test
    public void test3RLN_03() {
        // Copilot original (14:30-ish)
        // String yaml = "\"4 leading\n    \\ tab\"";

        // Copilot 15:25
        String yaml = "\"4 leading\n    \\t  tab\"";

        // Copilot (14:30-ish)
        // String expected = "4 leading \\ tab";

        // Copilot 14:42
        // String expected = "4 leading \t  tab";

        // Copilot 14:47
        // String expected = "4 leading \\t  tab";

        // Copilot 14:51
        // String expected = "4 leading \\ tab";

        // Copilot 15:25
        String expected = "4 leading \\t  tab";

        String actual = convert(yaml);
        debug(yaml, expected, actual);
        assertEquals(expected, actual, error(expected, actual));
    }

    @Test
    public void test3RLN_04() {
        String yaml = "\"5 leading\n    \\ \t tab\"";
        String expected = "5 leading \\ \t tab";
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
        assertEquals(expected, actual, error(expected, actual));
    }

    //
    // Helpers
    //

    private void debug(String yaml, String expected, String actual) {
        System.out.println("YAML: " + debugString(yaml));
        System.out.println("Converted: " + debugString(actual));
        System.out.println("Expected: " + debugString(expected));
    }

    private String convert(String yaml) {
        YamlAstParser parser = new YamlAstParser();

        YamlStreamNode stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocumentNode doc = stream.getDocuments().getFirst();

        YamlConverter converter = new YamlConverter();
        YamlNode normalized = YamlNormalizer.normalize(doc.getBody());
        AstNode ast = converter.toPlainAst(normalized);

        assertInstanceOf(ScalarAstNode.class, ast);
        ScalarAstNode<?> scalar = (ScalarAstNode<?>) ast;
        return scalar.asString();
    }

    private String error(String expected, String actual) {
        return String.format(
            "Expected: %s  Actual: %s",
            debugString(expected),
            debugString(actual)
        );
    }

    private String debugString(String input) {
        StringBuilder sb = new StringBuilder();
        for (int codePoint : input.codePoints().toArray()) {
            sb.append(currentChar( codePoint ));
        }
        return sb.toString();
    }

    private String currentChar(int c) {
        switch (c) {
            case ' ':
                return "␣";
            case '\t':
                return "⇥";
            case '\r':
                return "↵";
            case '\n':
                return "↩";
            default:
                return Character.toString(c);
        }
    }
}
