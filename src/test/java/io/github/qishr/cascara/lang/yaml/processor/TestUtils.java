package io.github.qishr.cascara.lang.yaml.processor;

import java.io.PrintWriter;
import java.util.List;

import org.junit.jupiter.api.AssertionFailureBuilder;

import io.github.qishr.cascara.common.data.Table;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.token.YamlErrorToken;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

public class TestUtils {


    public static void assertEquals(String expected, String actual) {
        if (expected == actual) return;
        if (expected == null || !expected.equals(actual)) {
            AssertionFailureBuilder.assertionFailure()
                // .message(error(expected, actual))
				.expected(StringUtils.debugString(expected))
				.actual(StringUtils.debugString(actual))
				.buildAndThrow();
        }
    }

    public static void dumpTokens(List<YamlToken> tokens) {
        System.out.println();
        // System.out.println("------------TOKENS-----------");
        Table table = new Table();
        table.addColumn("#");
        table.addColumn("Token");
        table.addColumn("Location");
        table.addColumn("Lexeme");
        table.addColumn("Content");

        for (int i = 0; i < tokens.size(); i++) {
            YamlToken t = tokens.get(i);
            switch(t.getType()) {
                case ERROR:
                    YamlErrorToken et = (YamlErrorToken)t;
                    table.addRow(
                        String.format("%2d", i),
                        t.getType().toString(),
                        String.format("L:%-3d C:%-3d", t.getStartLine(), t.getStartColumn()),
                        "", et.getCode().getMessage()
                    );
                    break;
                case SCALAR, ALIAS, ANCHOR, TAG, COMMENT:
                    table.addRow(
                        String.format("%2d", i),
                        t.getType().toString(),
                        String.format("L:%-3d C:%-3d", t.getStartLine(), t.getStartColumn()),
                        StringUtils.debugString(t.getLexeme()),
                        StringUtils.debugString(t.getContent())
                    );
                    break;
                default:
                    table.addRow(
                        String.format("%2d", i),
                        t.getType().toString(),
                        String.format("L:%-3d C:%-3d", t.getStartLine(), t.getStartColumn()), "", ""
                    );
            }
        }

        PrintWriter pw = new PrintWriter(System.out);
        table.render(pw);
        // pw.flush();


        // for (int i = 0; i < tokens.size(); i++) {
        //     YamlToken t = tokens.get(i);
        //     System.out.printf("[%2d] %-20s | L:%-3d C:%-3d | Lexeme: '%s'%n",
        //         i, t.getType(), t.getStartLine(), t.getStartColumn(),
        //         t.getLexeme() == null
        //         ? "null"
        //         : t.getLexeme().replace("\n", "\\n").replace("\r", "\\r"));
        // }

        // System.out.println("-----------------------------\n");
    }

    public static void dumpYamlAst(YamlNode node, String indent) {
        if (node instanceof YamlScalar s) {
            System.out.println(indent + "Scalar:");
            System.out.println(indent + "  value      = " + s.asString());
            System.out.println(indent + "  quoteStyle = " + s.getQuoteStyle());
            System.out.println(indent + "  type       = " + s.getPrimitiveType());
            return;
        }

        if (node instanceof YamlSequence arr) {
            System.out.println(indent + "Array:");
            for (YamlNode child : arr.getElements()) {
                dumpYamlAst(child, indent + "  ");
            }
            return;
        }

        if (node instanceof YamlMap obj) {
            System.out.println(indent + "Object:");
            for (var entry : obj.getEntries()) {
                System.out.println(indent + "  key = " + entry.getKeyString());
                dumpYamlAst(entry.getValue(), indent + "    ");
            }
            return;
        }

        System.out.println(indent + node.getClass().getSimpleName());
    }

}
