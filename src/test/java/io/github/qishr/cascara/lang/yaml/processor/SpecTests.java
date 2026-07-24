package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.data.Tree;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Diagnostic;
import io.github.qishr.cascara.common.diagnostic.LocalizableIOException;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceScalarNode;
import io.github.qishr.cascara.common.lang.util.AstTreeData;
import io.github.qishr.cascara.lang.yaml.ast.YamlAliasNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchorNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocumentNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlStreamNode;
import io.github.qishr.cascara.lang.yaml.exception.YamlParserException;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import io.github.qishr.cascara.lang.yaml.processor.YamlAstParser;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;
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
        assertEquals(1, stream.getDocuments().size());
    }

    @Test
    public void testUnknownDirectiveIsIgnored() {
        String yaml =
            "%FOO  bar baz #c\n" +   // unknown directive → must be ignored
            "              #d\n" +
            "---\n" +
            "\"foo\"\n";

        // Collect diagnostics
        List<Diagnostic> diagnostics = new ArrayList<>();
        StandardReporter reporter = new StandardReporter()
            // .setLevel(Level.TRACE)
            .setDiagnosticCollector(d -> {
                diagnostics.add(d);
            });

        // Parse YAML using the actual compliance parser
        YamlAstParser parser = new YamlAstParser()
            .setReporter(reporter)
            .setOptions(YamlOptions.DEFAULT.duplicate().setMultiDocument(true));

        YamlStreamNode stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());

        YamlDocumentNode doc = stream.getDocuments().get(0);

        // The document body must be a scalar node with value "foo"
        assertTrue(doc.getBody() instanceof YamlScalarNode);

        YamlScalarNode scalar = (YamlScalarNode) doc.getBody();

        // The scalar content must be "foo"
        assertEquals("foo", scalar.getContent());

        // Check for a warning
        assertEquals(1, diagnostics.size(), "Should have exactly 1 warning");
        assertEquals(Level.WARN, diagnostics.getFirst().getLevel());

        // The directive must NOT appear as a node
        // i.e., no null scalar, no extra nodes, no map/seq created
    }

    @Disabled // This test is testing non-valid YAML 1.2 semantics
    @Test
    public void testAlias2SXE() {
        String yaml = """
            &a: key: &a value
            foo:
              *a:

            """;

        StandardReporter reporter = new StandardReporter()
            .setLevel(Level.TRACE);

        // Parse YAML using the actual compliance parser
        YamlAstParser parser = new YamlAstParser()
            .setReporter(reporter)
            .setOptions(YamlOptions.DEFAULT.duplicate().setMultiDocument(true));

        YamlStreamNode stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocumentNode doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlMapNode.class, body);
        YamlMapNode map = (YamlMapNode) body;

        assertEquals("value", map.getString("key"));
        assertEquals("key", map.getString("foo"));
    }

    @Test
    public void test35KP() {
        String yaml = """
            --- !!str
            d
            e
            """;

        StandardReporter reporter = new StandardReporter()
            .setLevel(Level.TRACE);

        // Parse YAML using the actual compliance parser
        YamlAstParser parser = new YamlAstParser()
            .setReporter(reporter)
            .setOptions(YamlOptions.DEFAULT.duplicate().setMultiDocument(true));

        YamlStreamNode stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocumentNode doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalarNode.class, body);
        YamlScalarNode scalar = (YamlScalarNode) body;

        assertEquals("d e", scalar.asString());
    }

    @Test
    public void test35KP2() {
        String yaml = """
            --- !!str
            d
            e
            f: g
            """;

        StandardReporter reporter = new StandardReporter()
            .setLevel(Level.TRACE);

        // Parse YAML using the actual compliance parser
        YamlAstParser parser = new YamlAstParser()
            .setReporter(reporter)
            .setOptions(YamlOptions.DEFAULT.duplicate().setMultiDocument(true));

        YamlStreamNode stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocumentNode doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalarNode.class, body);
        YamlScalarNode scalar = (YamlScalarNode) body;

        assertEquals("d e", scalar.asString());
    }

    @Test
    public void test2EBW() {
        String yaml = """
            a!"#$%&'()*+,-./09:;<=>?@AZ[\\]^_`az{|}~: safe
            ?foo: safe question mark
            :foo: safe colon
            -foo: safe dash
            this is#not: a comment
            """;

        StandardReporter reporter = new StandardReporter()
            .setLevel(Level.TRACE);

        // Parse YAML using the actual compliance parser
        YamlAstParser parser = new YamlAstParser()
            .setReporter(reporter)
            .setOptions(YamlOptions.DEFAULT.duplicate().setMultiDocument(true));

        YamlStreamNode stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocumentNode doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlMapNode.class, body);
        YamlMapNode map = (YamlMapNode) body;

    }

    @Test
    public void testIndent() {
        String yaml = """
            list:
              - first
             - second  # Only 1 space indent, should fail
            """;

        StandardReporter reporter = new StandardReporter()
            .setLevel(Level.TRACE);

        // Parse YAML using the actual compliance parser
        YamlAstParser parser = new YamlAstParser()
            .setReporter(reporter)
            .setOptions(YamlOptions.DEFAULT.duplicate().setMultiDocument(true));

        assertThrows(YamlParserException.class, () -> parser.parse(yaml));
    }

    @Test
    public void test3MYT() {
        String yaml = """
            ---
            k:#foo
             &a !t s
            """;

        StandardReporter reporter = new StandardReporter()
            .setLevel(Level.TRACE);

        // Parse YAML using the actual compliance parser
        YamlAstParser parser = new YamlAstParser()
            .setReporter(reporter)
            .setOptions(YamlOptions.DEFAULT.duplicate().setMultiDocument(true));

        YamlStreamNode stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocumentNode doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalarNode.class, body);
        YamlScalarNode scalar = (YamlScalarNode) body;

        assertEquals("k:#foo &a !t s", scalar.asString());
    }


    @Test
    public void test2G84() {
        String yaml = "--- |1-";

        StandardReporter reporter = new StandardReporter()
            .setLevel(Level.TRACE);

        // Parse YAML using the actual compliance parser
        YamlAstParser parser = new YamlAstParser()
            .setReporter(reporter)
            .setOptions(YamlOptions.DEFAULT.duplicate().setMultiDocument(true));

        YamlStreamNode stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocumentNode doc = stream.getDocuments().getFirst();

        YamlConverter converter = new YamlConverter();
        YamlNode normalized = YamlNormalizer.normalize(doc.getBody());
        AstNode ast = converter.toPlainAst(normalized);

        assertInstanceOf(ScalarAstNode.class, ast);
        ScalarAstNode<?> scalar = (ScalarAstNode<?>) ast;

        assertEquals("", scalar.asString());
    }


    @Test
    public void test4Q9F() {
        String yaml = """
            --- >
             ab
             cd

             ef


             gh
            """;

        StandardReporter reporter = new StandardReporter()
            .setLevel(Level.TRACE);

        // Parse YAML using the actual compliance parser
        YamlAstParser parser = new YamlAstParser()
            .setReporter(reporter)
            .setOptions(YamlOptions.DEFAULT.duplicate().setMultiDocument(true));

        YamlStreamNode stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocumentNode doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalarNode.class, body);
        YamlScalarNode scalar = (YamlScalarNode) body;

        assertEquals("ab cd\nef\n\ngh\n", scalar.asString());
    }

   @Test
    public void test4QFQ() {
        String yaml = """
            - |
             detected
            - >


              # detected
            - |1
              explicit
            - >
             detected
            """;

        StandardReporter reporter = new StandardReporter()
            .setLevel(Level.TRACE);

        YamlAstParser parser = new YamlAstParser()
            .setReporter(reporter)
            .setOptions(YamlOptions.DEFAULT.duplicate().setMultiDocument(true));

        YamlStreamNode stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());

        YamlDocumentNode doc = stream.getDocuments().get(0);

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlSequenceNode.class, body);
        YamlSequenceNode seq = (YamlSequenceNode) body;

        assertEquals(4, seq.size());

        Util.assertEquals("detected\n", seq.get(0).asString());
        Util.assertEquals("\n\n# detected\n", seq.get(1).asString());
        Util.assertEquals(" explicit\n", seq.get(2).asString());
        Util.assertEquals("detected\n", seq.get(3).asString());
    }

    @Test
    public void test4WA9() {
        String yaml = """
            - aaa: |2
                xxx
              bbb: |
                xxx
            """;

        StandardReporter reporter = new StandardReporter()
            .setLevel(Level.TRACE);

        YamlAstParser parser = new YamlAstParser()
            .setReporter(reporter)
            .setOptions(YamlOptions.DEFAULT.duplicate().setMultiDocument(true));

        YamlStreamNode stream = parser.parseMulti(yaml);

        Util.dumpTokens(parser.getTokens());

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());

        YamlDocumentNode doc = stream.getDocuments().get(0);

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlSequenceNode.class, body);
        YamlSequenceNode seq = (YamlSequenceNode) body;

        assertEquals(1, seq.size());

        YamlMapNode map = (YamlMapNode)seq.get(0);

        Util.assertEquals("xxx\n", map.get("aaa").asString());
        Util.assertEquals("xxx\n", map.get("bbb").asString());
    }

    @Test
    public void test5GBFa() {
        String yaml = """
            Folding:
              "Empty line

              as a line feed"
            """;

        StandardReporter reporter = new StandardReporter()
            .setLevel(Level.TRACE);

        YamlAstParser parser = new YamlAstParser()
            .setReporter(reporter)
            .setOptions(YamlOptions.DEFAULT.duplicate().setMultiDocument(true));

        YamlStreamNode stream = parser.parseMulti(yaml);

        Util.dumpTokens(parser.getTokens());

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());

        YamlDocumentNode doc = stream.getDocuments().get(0);

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        YamlMapNode map = (YamlMapNode)body;

        Util.assertEquals("Empty line\nas a line feed", map.get("Folding").asString());
    }

}
