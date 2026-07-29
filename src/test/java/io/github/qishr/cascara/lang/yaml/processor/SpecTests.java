package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Diagnostic;
import io.github.qishr.cascara.common.diagnostic.LocalizableIOException;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchor;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
import io.github.qishr.cascara.lang.yaml.exception.YamlParserException;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.lang.yaml.token.YamlToken;

public class SpecTests {
    private static final Level PARSER_LEVEL = Level.INFO;
    private static final Level TOKENIZER_LEVEL = Level.DEBUG;
    private static final boolean dumpTokens = false;

    private YamlAstParser parser;
    private Reporter reporter;

    @BeforeEach
    void setup() {
        reporter = new StandardReporter()
            .setLevel(PARSER_LEVEL)
            .setAnsiColoringEnabled(true)
            .setFlushEnabled(false);

        parser = new YamlAstParser()
            .setReporter(reporter);
    }

    @Test
    public void testLiteral0() {
        String yaml = """
            yaml: |
                --- |0
            """;

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
    //         .setReporter(new StandardReporter().setLevel(LEVEL));
    //     YamlNode root = parser.parse(yaml);

    //     assertTrue(root instanceof YamlMap);
    //     YamlMap map = (YamlMap) root;

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
    //     if (node instanceof YamlMap map) {
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

        //
        if (true|dumpTokens) {
            YamlTokenizer tokenizer = new YamlTokenizer()
                .setReporter(new StandardReporter().setLevel(TOKENIZER_LEVEL).setAnsiColoringEnabled(true));
            List<YamlToken> tokens = tokenizer.tokenize(yaml);
            TestUtils.dumpTokens(tokens);
        }

        reporter.setLevel(Level.DEBUG);

        YamlNode root = parser.parse(yaml);
        assertTrue(root instanceof YamlMap);

        YamlMap map = (YamlMap) root;



        // PrintWriter pw = new PrintWriter(System.err);
        // Tree<AstTreeData,AstNode> tree = new Tree<>();
        // tree.setRoot(new AstTreeData(root));
        // tree.render(pw);
        // pw.flush();



        // 1. Collect only ROOT keys
        List<String> rootKeys = map.getEntries().stream()
            .map(e -> {
                YamlNode k = e.getKey();
                if (k instanceof YamlScalar s) return s.asString();
                if (k instanceof YamlAlias a) return "*" + a.getAlias();
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
        if (node instanceof YamlAnchor anchor) {
            // unwrap and continue into the anchored value
            collectAliasKeys(anchor.getInnerNode(), out);
            return;
        }

        if (node instanceof YamlMap map) {
            for (YamlMapEntry e : map.getEntries()) {
                YamlNode k = e.getKey();
                if (k instanceof YamlAlias a) {
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

        YamlStream stream = parser.parseMulti(yaml);
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
            // .setLevel(LEVEL)
            .setAnsiColoringEnabled(true)
            .setDiagnosticCollector(d -> {
                diagnostics.add(d);
            });

        parser.setReporter(reporter);

        YamlStream stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());

        YamlDocument doc = stream.getDocuments().get(0);

        // The document body must be a scalar node with value "foo"
        assertTrue(doc.getBody() instanceof YamlScalar);

        YamlScalar scalar = (YamlScalar) doc.getBody();

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

        YamlStream stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlMap.class, body);
        YamlMap map = (YamlMap) body;

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

        // parser.getTokenizer().setReporter(new StandardReporter().setLevel(Level.TRACE));
        YamlTokenizer tz = new YamlTokenizer().setReporter(new StandardReporter().setLevel(Level.TRACE));
        TestUtils.dumpTokens(tz.tokenize(yaml));

        YamlStream stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

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

        parser.getReporter().setLevel(Level.DEBUG); // TODO: REMOVE THIS
        // // parser.getTokenizer().setReporter(new StandardReporter().setLevel(Level.TRACE)); // TODO: REMOVE THIS
        YamlTokenizer tz = new YamlTokenizer().setReporter(new StandardReporter().setLevel(Level.TRACE));
        TestUtils.dumpTokens(tz.tokenize(yaml));

        YamlStream stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

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

        YamlStream stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlMap.class, body);
        // YamlMap map = (YamlMap) body;

    }

    @Test
    public void testBadIndentReported() {
        String yaml = """
            list:
              - first
             - second  # Only 1 space indent, should fail
            """;

        assertThrows(YamlParserException.class, () -> parser.parse(yaml));
    }

    @Test
    public void test3MYT() {
        String yaml = """
            ---
            k:#foo
             &a !t s
            """;

        YamlTokenizer tokenizer = new YamlTokenizer();
        tokenizer.setReporter(new StandardReporter().setLevel(Level.DEBUG).setAnsiColoringEnabled(true));
        List<YamlToken> tokens = tokenizer.tokenize(yaml);
        TestUtils.dumpTokens(tokens);

        YamlStream stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        assertEquals("k:#foo &a !t s", scalar.asString());
    }


    @Test
    public void test2G84() {
        String yaml = "--- |1-";

        parser.getTokenizer().setReporter(new StandardReporter().setLevel(Level.DEBUG));

        YamlStream stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

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

        YamlStream stream = parser.parseMulti(yaml);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        assertEquals("ab cd\nef\n\ngh\n", scalar.asString());
    }

   @Test
    public void test4QFQ() {
        String yaml = "- |\n detected\n- >\n \n  \n  # detected\n- |1\n  explicit\n- >\n detected\n";

        parser.getTokenizer().setReporter(new StandardReporter().setLevel(Level.DEBUG));

        YamlStream stream = parser.parseMulti(yaml);

        if (dumpTokens) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());

        YamlDocument doc = stream.getDocuments().get(0);

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlSequence.class, body);
        YamlSequence seq = (YamlSequence) body;

        assertEquals(4, seq.size());

        TestUtils.assertEquals("detected\n", seq.get(0).asString());
        TestUtils.assertEquals("\n\n# detected\n", seq.get(1).asString());
        TestUtils.assertEquals(" explicit\n", seq.get(2).asString());
        TestUtils.assertEquals("detected\n", seq.get(3).asString());
    }

    @Test
    public void test4WA9() {
        String yaml = """
            - aaa: |2
                xxx
              bbb: |
                xxx
            """;

        parser.getTokenizer().setReporter(new StandardReporter().setLevel(Level.DEBUG));

        YamlStream stream = parser.parseMulti(yaml);

        if (dumpTokens) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());

        YamlDocument doc = stream.getDocuments().get(0);

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlSequence.class, body);
        YamlSequence seq = (YamlSequence) body;

        assertEquals(1, seq.size());

        YamlMap map = (YamlMap)seq.get(0);

        TestUtils.assertEquals("xxx\n", map.get("aaa").asString());
        TestUtils.assertEquals("xxx\n", map.get("bbb").asString());
    }

    @Test
    public void test5GBFa() {
        String yaml = """
            Folding:
              "Empty line

              as a line feed"
            """;

        YamlTokenizer tokenizer = new YamlTokenizer();
        List<YamlToken> tokens = tokenizer.tokenize(yaml);
        if (true|dumpTokens) {
            TestUtils.dumpTokens(tokens);
        }

        // parser.getTokenizer().setReporter(new StandardReporter().setLevel(Level.DEBUG));
        reporter.setLevel(Level.DEBUG);

        YamlStream stream = parser.parseMulti(yaml);

        if (dumpTokens) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());

        YamlDocument doc = stream.getDocuments().get(0);

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        YamlMap map = (YamlMap)body;

        TestUtils.assertEquals("Empty line\nas a line feed", map.get("Folding").asString());
    }

    @Test
    void test_tags() {
        String yamlString = """
          %TAG !e! tag:example.com,2000:app/
          ---
          - !local foo
          - !!str bar
          - !e!tag%21 baz
          """;

        YamlTokenizer tokenizer = new YamlTokenizer();
        List<YamlToken> tokens = tokenizer.tokenize(yamlString);
        if (dumpTokens) {
            TestUtils.dumpTokens(tokens);
        }

        YamlStream stream = parser.parseMulti(yamlString);

        if (dumpTokens) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());

        YamlDocument doc = stream.getDocuments().get(0);

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlSequence.class, body);
        YamlSequence seq = (YamlSequence) body;

        assertEquals(3, seq.size());

        TestUtils.assertEquals("foo", seq.get(0).asString());
        TestUtils.assertEquals("bar", seq.get(1).asString());
        TestUtils.assertEquals("baz", seq.get(2).asString());
    }

    @Test
    public void test6FWR() {
        String yaml = "--- |+\nab\n\n \n...\n";

        parser.getTokenizer().setReporter(new StandardReporter().setLevel(Level.DEBUG));

        YamlStream stream = parser.parseMulti(yaml);

        if (dumpTokens) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        TestUtils.assertEquals("ab\n\n \n", scalar.asString());
    }

    @Test
    public void test6KGN() {
        String yaml = "---\na: &anchor\nb: *anchor";

        parser.getTokenizer().setReporter(
            new StandardReporter()
                .setLevel(TOKENIZER_LEVEL)
                .setAnsiColoringEnabled(true)
        );

        parser.setReporter(
            new StandardReporter()
                .setLevel(TOKENIZER_LEVEL)
                .setAnsiColoringEnabled(true));

        YamlStream stream = parser.parseMulti(yaml);

        if (true|dumpTokens) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        YamlMap map = (YamlMap)body;
        assertEquals(2, map.size());

        TestUtils.assertEquals(null, map.get("a").asString());
        TestUtils.assertEquals(null, map.get("b").asString());
    }

    @Test
    void test6SLA() {
        String yamlString = """
            "foo\\nbar:baz\\tx \\\\$%^&*()x": 23
            'x\\ny:z\\tx $%^&*()x': 24
            """;

        YamlTokenizer tokenizer = new YamlTokenizer();
        List<YamlToken> tokens = tokenizer.tokenize(yamlString);

        if (dumpTokens) {
            TestUtils.dumpTokens(tokens);
        }

        YamlStream stream = parser.parseMulti(yamlString);

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());

        YamlDocument doc = stream.getDocuments().get(0);

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        YamlMap map = (YamlMap)body;
        assertEquals(2, map.size());

        assertEquals(23, map.getScalar("foo\nbar:baz\tx \\$%^&*()x").asInteger());
        assertEquals(24, map.getScalar("x\\ny:z\\tx $%^&*()x").asInteger());
    }

    @Test
    public void test6VJK() {
        String yaml = ">\n Sammy Sosa completed another\n fine season with great stats.\n\n   63 Home Runs\n   0.288 Batting Average\n\n What a year!\n";

        // parser.getTokenizer().setReporter(new StandardReporter().setLevel(Level.DEBUG));

        YamlStream stream = parser.parseMulti(yaml);

        if (dumpTokens) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        String expected = "Sammy Sosa completed another fine season with great stats.\n\n  63 Home Runs\n  0.288 Batting Average\n\nWhat a year!\n";

        System.out.println("Expected: " + StringUtils.debugString(expected));
        System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void test6WPF() {
        String yaml = "---\n\"\n  foo \n \n    bar\n\n  baz\n\"";

        // parser.getTokenizer().setReporter(
        //     new StandardReporter()
        //         .setLevel(TOKENIZER_LEVEL)
        //         .setAnsiColoringEnabled(true)
        // );

        YamlStream stream = parser.parseMulti(yaml);

        if (dumpTokens) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        TestUtils.assertEquals(" foo\nbar\nbaz ", scalar.asString());
    }

    @Test
    public void test7A4E() {
        String yaml = "\" 1st non-empty\n" +
                      "\n" +
                      " 2nd non-empty \n" +
                      "\t3rd non-empty \"\n";

        parser.getTokenizer().setReporter(
            new StandardReporter()
                .setLevel(TOKENIZER_LEVEL)
                .setAnsiColoringEnabled(true)
        );

        YamlStream stream = parser.parseMulti(yaml);

        if (dumpTokens) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        TestUtils.assertEquals(" 1st non-empty\n2nd non-empty 3rd non-empty ", scalar.asString());
    }

    @Test
    void test7BMT() {
        String yaml = """
            ---
            top1: &node1
              &k1 key1: one
            top2: &node2 # comment
              key2: two
            top3:
              &k3 key3: three
            top4: &node4
              &k4 key4: four
            top5: &node5
              key5: five
            top6: &val6
              six
            top7:
              &val7 seven
            """;

        YamlTokenizer tokenizer = new YamlTokenizer();
        List<YamlToken> tokens = tokenizer.tokenize(yaml);

        if (true|dumpTokens) {
            TestUtils.dumpTokens(tokens);
        }

        parser.setReporter(new StandardReporter().setLevel(Level.DEBUG).setAnsiColoringEnabled(true)); // TODO: REMOVE THIS

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

        assertEquals(7, map.size());

        YamlMap top1 = map.getMap("top1");
        YamlMap top2 = map.getMap("top2");
        YamlMap top3 = map.getMap("top3");
        YamlMap top4 = map.getMap("top4");
        YamlMap top5 = map.getMap("top5");

        assertEquals("one", top1.getScalar("key1").asString());
        assertEquals("two", top2.getScalar("key2").asString());
        assertEquals("three", top3.getScalar("key3").asString());
        assertEquals("four", top4.getScalar("key4").asString());
        assertEquals("five", top5.getScalar("key5").asString());

        assertEquals("six", map.getScalar("top6").asString());
        assertEquals("seven", map.getScalar("top7").asString());
    }

    @Test
    public void test7T8X() {
        String yaml = """
            >

             folded
             line

             next
             line
               * bullet

               * list
               * lines

             last
             line

            # Comment
            """;;

        // parser.getTokenizer().setReporter(
        //     new StandardReporter()
        //         .setLevel(TOKENIZER_LEVEL)
        //         .setAnsiColoringEnabled(true)
        // );

        // parser.getTokenizer().setReporter(new StandardReporter().setLevel(Level.DEBUG));

        YamlStream stream = parser.parseMulti(yaml);

        if (dumpTokens) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        String expected = "\nfolded line\nnext line\n  * bullet\n\n  * list\n  * lines\n\nlast line\n";

        System.out.println("Expected: " + StringUtils.debugString(expected));
        System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void test753E() {
        String yaml = """
            --- |-
            ab


           ...

           """;

        // parser.getTokenizer().setReporter(new StandardReporter().setLevel(Level.DEBUG));

        YamlStream stream = parser.parseMulti(yaml);

        if (dumpTokens) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // The stream must contain exactly one document
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        String expected = "ab";

        System.out.println("Expected: " + StringUtils.debugString(expected));
        System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));

        TestUtils.assertEquals(expected, scalar.asString());
    }

    // TODO: Enable this
    @Disabled
    @Test
    public void test7ZZ5() {
        String yaml = """
            ---
            nested sequences:
            - - - []
            - - - {}
            key1: []
            key2: {}
            """;

        YamlTokenizer tokenizer = new YamlTokenizer();
        List<YamlToken> tokens = tokenizer.tokenize(yaml);
        if (true|dumpTokens) {
            TestUtils.dumpTokens(tokens);
        }

        // parser.getTokenizer().setReporter(new StandardReporter().setLevel(Level.DEBUG));
        reporter.setLevel(Level.DEBUG);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        assertInstanceOf(YamlMap.class, body);
        YamlMap map = (YamlMap) body;

        YamlSequence nestedSequence = map.getSequence("nested sequences");
        assertNotNull(nestedSequence);
        assertEquals(4, nestedSequence.size());

        YamlSequence outer1 = nestedSequence.getFirst().asSequence();
        assertNotNull(outer1);

        YamlSequence middle1 = outer1.getFirst().asSequence();
        assertNotNull(middle1);

        YamlSequence inner1 = middle1.getFirst().asSequence();
        assertNotNull(inner1);
        assertTrue(inner1.isEmpty());

        // TODO: outer2 to inner2 (map)

        // {
        //     "nested sequences": [
        //       [
        //         [
        //           []
        //         ]
        //       ],
        //       [
        //         [
        //           {}
        //         ]
        //       ]
        //     ],
        //     "key1": [],
        //     "key2": {}
        //   }

    }
}
