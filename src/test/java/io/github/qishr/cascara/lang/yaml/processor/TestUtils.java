package io.github.qishr.cascara.lang.yaml.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AssertionFailureBuilder;

import io.github.qishr.cascara.common.annotation.Nullable;
import io.github.qishr.cascara.common.data.TextualTable;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.common.util.TermUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.token.YamlErrorToken;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

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
        dumpTokens(new PrintWriter(System.out), tokens, -1);
    }

    public static void dumpTokens(List<YamlToken> tokens, int maxColumnWidth) {
        dumpTokens(new PrintWriter(System.out), tokens, maxColumnWidth);
    }

    public static void dumpTokens(Writer writer, List<YamlToken> tokens) {
        dumpTokens(writer, tokens, -1);
    }

    public static void dumpTokens(Writer writer, List<YamlToken> tokens, int maxColumnWidth) {
        try {
			writer.write("\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        // System.out.println("------------TOKENS-----------");
        TextualTable table = new TextualTable()
            .setStyle(TextualTable.Style.ROUNDED)
            .setMaxColumnWidth(maxColumnWidth)
            .setBorderColor(TermUtils.ANSI_WHITE)
            .addColumn("#")
            .addColumn("Token")
            .addColumn("Location")
            .addColumn("Lexeme")
            .addColumn("Content");

        int tokenIdent = 0;

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
                        "  ".repeat(tokenIdent) + t.getType().toString(),
                        String.format("L:%-3d C:%-3d", t.getStartLine(), t.getStartColumn()),
                        StringUtils.debugString(t.getLexeme()),
                        StringUtils.debugString(t.getContent())
                    );
                    break;
                default:
                    if (t.getType() == YamlTokenType.DEDENT && tokenIdent > 0) {
                        tokenIdent--;
                    }
                    table.addRow(
                        String.format("%2d", i),
                        "  ".repeat(tokenIdent) + t.getType().toString(),
                        String.format("L:%-3d C:%-3d", t.getStartLine(), t.getStartColumn()), "", ""
                    );
                    if (t.getType() == YamlTokenType.INDENT) {
                        tokenIdent++;
                    }
            }
        }

        // PrintWriter pw = new PrintWriter(System.out);
        try {
            table.render(writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
