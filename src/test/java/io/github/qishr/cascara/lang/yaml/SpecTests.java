package io.github.qishr.cascara.lang.yaml;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.data.Tree;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.LocalizableIOException;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.util.AstTreeData;
import io.github.qishr.cascara.lang.yaml.ast.YamlAliasNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchorNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlStreamNode;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import io.github.qishr.cascara.lang.yaml.processor.YamlAstParser;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;

public class SpecTests {
       private final YamlAstParser parser = new YamlAstParser();

    @Test
    public void testLiteral0() {
        String yaml = """
            yaml: |
                --- |0
            """;

        parser.setReporter(new StandardReporter().setLevel(Level.TRACE));
        YamlNode root = parser.parse(yaml.getBytes());

        assertNotNull(root);
        assertEquals("", root.asString());
    }

    // @Test
    // public void testLiteral10() {
    //     YamlNode root = parser.parse("""
    //         --- |10
    //         """.getBytes());
    //     assertNotNull(root);
    //     assertEquals("", root.asDocument().asScalar().value());
    // }

    // @Test
    // public void testLiteral1Minus() {
    //     YamlNode root = parser.parse("""
    //         --- |1-
    //         """.getBytes());
    //     assertNotNull(root);
    //     assertEquals("", root.asDocument().asScalar().value());
    // }

    // @Test
    // public void testLiteral1Plus() {
    //     YamlNode root = parser.parse("""
    //         --- |1+
    //         """.getBytes());
    //     assertNotNull(root);
    //     assertEquals("", root.asDocument().asScalar().value());
    // }


    // PrintWriter pw = new PrintWriter(System.err);
    // Tree<AstTreeData,AstNode> tree = new Tree<>();
    // tree.setRoot(new AstTreeData(root));
    // tree.render(pw);
    // pw.flush();

    // @Test
    // public void testAliasAndEmptyKeysAreParsed() throws LocalizableIOException {
    //     String yaml = """
    //         "top1" :
    //             "key1" : &alias1 scalar1
    //         'top2' :
    //             'key2' : &alias2 scalar2
    //         top3: &node3
    //             *alias1 : scalar3
    //         top4:
    //             *alias2 : scalar4
    //         top5   :
    //             scalar5
    //         top6:
    //             &anchor6 'key6' : scalar6
    //         """;

    //     YamlAstParser parser = new YamlAstParser()
    //         .setReporter(new StandardReporter().setLevel(Level.TRACE));
    //     YamlNode root = parser.parse(yaml);

    //     assertTrue(root instanceof YamlMapNode);
    //     YamlMapNode map = (YamlMapNode) root;

    //     // Extract keys as strings for easier comparison

    //     // List<String> keys = map.getEntries().stream()
    //     // .<String>map(e -> {
    //     //     YamlNode k = e.getKey();
    //     //     if (k instanceof YamlScalarNode s) return s.asString();
    //     //     if (k instanceof YamlAliasNode a) return "*" + a.getAlias();
    //     //     return "<complex>";
    //     // })
    //     // .toList();

    //     List<String> keys = new ArrayList<>();
    //     collectKeys(root, keys);


    //     assertEquals(
    //         List.of("top1", "top2", "top3", "*alias1", "top4", "*alias2", "top5", "top6"),
    //         keys
    //     );
    // }

    // private void collectKeys(YamlNode node, List<String> out) {
    //     if (node instanceof YamlMapNode map) {
    //         for (YamlMapEntryNode e : map.getEntries()) {
    //             YamlNode k = e.getKey();
    //             if (k instanceof YamlScalarNode s) out.add(s.asString());
    //             else if (k instanceof YamlAliasNode a) out.add("*" + a.getAlias());
    //             else out.add("<complex>");
    //             collectKeys(e.getValue(), out);
    //         }
    //     }
    // }

    @Test
    public void testAliasAndEmptyKeysAreParsed() throws LocalizableIOException {
        String yaml = """
            "top1" :
                "key1" : &alias1 scalar1
            'top2' :
                'key2' : &alias2 scalar2
            top3: &node3
                *alias1 : scalar3
            top4:
                *alias2 : scalar4
            top5   :
                scalar5
            top6:
                &anchor6 'key6' : scalar6
            """;

        YamlAstParser parser = new YamlAstParser()
            .setReporter(new StandardReporter().setLevel(Level.TRACE));

        YamlNode root = parser.parse(yaml);
        assertTrue(root instanceof YamlMapNode);

        YamlMapNode map = (YamlMapNode) root;



        // PrintWriter pw = new PrintWriter(System.err);
        // Tree<AstTreeData,AstNode> tree = new Tree<>();
        // tree.setRoot(new AstTreeData(root));
        // tree.render(pw);
        // pw.flush();



        // 1. Collect only ROOT keys
        List<String> rootKeys = map.getEntries().stream()
            .map(e -> {
                YamlNode k = e.getKey();
                if (k instanceof YamlScalarNode s) return s.asString();
                if (k instanceof YamlAliasNode a) return "*" + a.getAlias();
                return "<complex>";
            })
            .toList();

        assertEquals(
            List.of("top1", "top2", "top3", "top4", "top5", "top6"),
            rootKeys
        );

        // 2. Collect nested alias keys
        List<String> nestedAliasKeys = new ArrayList<>();
        collectAliasKeys(map, nestedAliasKeys);

        assertEquals(
            List.of("*alias1", "*alias2"),
            nestedAliasKeys
        );
    }

    // TODO CHeck that this is compatible with what yaml-test-suite does
    private void collectAliasKeys(YamlNode node, List<String> out) {
        if (node instanceof YamlAnchorNode anchor) {
            // unwrap and continue into the anchored value
            collectAliasKeys(anchor.getInnerNode(), out);
            return;
        }

        if (node instanceof YamlMapNode map) {
            for (YamlMapEntryNode e : map.getEntries()) {
                YamlNode k = e.getKey();
                if (k instanceof YamlAliasNode a) {
                    out.add("*" + a.getAlias());
                }
                collectAliasKeys(e.getValue(), out);
            }
        }
    }

    @Test
    public void test_sequence_with_tags_should_not_produce_two_empty_documents() {
        String yaml =
            "- !!str a\n" +
            "- b\n" +
            "- !!int 42\n" +
            "- d\n";

        YamlAstParser parser = new YamlAstParser()
                .setReporter(new StandardReporter().setLevel(Level.TRACE));
        YamlStreamNode stream = parser.parseMulti(yaml);

        // This assertion WILL fail with your current parser,
        // because you said the AST contains 2 empty documents.
        assertEquals(1, stream.getDocuments().size());
    }

}
