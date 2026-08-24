package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Diagnostic;
import io.github.qishr.cascara.common.diagnostic.LocalizableIOException;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.lang.plain.PlainMapNode;
import io.github.qishr.cascara.common.lang.plain.PlainNode;
import io.github.qishr.cascara.common.lang.plain.PlainSequenceNode;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
import io.github.qishr.cascara.lang.yaml.diagnostic.YamlParserException;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;

public class AstParserSpecTests extends AstParserTestBase {

    @Test
    public void testLiteral0() {
        String yaml = """
            yaml: |
                --- |0
            """;

        if (DUMP_TOKENS) {
            YamlTokenizer tokenizer = new YamlTokenizer()
                .setReporter(new StandardReporter().setLevel(TOKENIZER_LEVEL).setAnsiColoringEnabled(true));
            List<YamlToken> tokens = tokenizer.tokenize(yaml);
            TestUtils.dumpTokens(tokens);
        }

        YamlNode root = parser.parse(yaml.getBytes());
        YamlMap map = (YamlMap)normalize(root);
        YamlScalar scalar = map.getScalar("yaml");

        assertNotNull(root);
        assertEquals("--- |0\n", scalar.asString());
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
        if (DUMP_TOKENS) {
            YamlTokenizer tokenizer = new YamlTokenizer()
                .setReporter(new StandardReporter().setLevel(TOKENIZER_LEVEL).setAnsiColoringEnabled(true));
            List<YamlToken> tokens = tokenizer.tokenize(yaml);
            TestUtils.dumpTokens(tokens);
        }

        YamlNode root = parser.parse(yaml);
        assertTrue(root instanceof YamlMap);

        YamlMap map = (YamlMap) root;

        // 1. Collect only ROOT keys
        List<String> rootKeys = map.getEntries().stream()
            .map(e -> {
                YamlNode k = e.getKey();
                if (k instanceof YamlScalar s) return s.asString();
                if (k instanceof YamlAlias a) return "*" + a.getName();
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
        // if (node instanceof YamlAnchor anchor) {
        //     // unwrap and continue into the anchored value
        //     collectAliasKeys(anchor.getInnerNode(), out);
        //     return;
        // }

        if (node instanceof YamlMap map) {
            for (YamlMapEntry e : map.getEntries()) {
                YamlNode k = e.getKey();
                if (k instanceof YamlAlias a) {
                    out.add("*" + a.getName());
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
            .setDiagnosticConsumer(d -> {
                diagnostics.add(d);
            });

        parser.setReporter(reporter);

        YamlStream stream = parser.parseMulti(yaml);


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


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

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

        if (DEBUG) {
            TestUtils.dumpTokens(tokenizer.tokenize(yaml));
        }

        YamlStream stream = parser.parseMulti(yaml);


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

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

        if (DEBUG) {
            YamlTokenizer tz = new YamlTokenizer().setReporter(new StandardReporter().setLevel(Level.TRACE));
            TestUtils.dumpTokens(tz.tokenize(yaml));
        }

        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));

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

        if (DEBUG) {
            YamlTokenizer tokenizer = new YamlTokenizer();
            tokenizer.setReporter(new StandardReporter().setLevel(Level.DEBUG).setAnsiColoringEnabled(true));
            List<YamlToken> tokens = tokenizer.tokenize(yaml);
            TestUtils.dumpTokens(tokens);
        }

        YamlStream stream = parser.parseMulti(yaml);


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlMap.class, body);
        YamlMap map = (YamlMap) body;

        TestUtils.assertEquals("safe",map.getScalar("a!\"#$%&'()*+,-./09:;<=>?@AZ[\\]^_`az{|}~").asString());
        TestUtils.assertEquals("safe question mark",map.getScalar("?foo").asString());
        TestUtils.assertEquals("safe colon",map.getScalar(":foo").asString());
        TestUtils.assertEquals("safe dash",map.getScalar("-foo").asString());
        TestUtils.assertEquals("a comment",map.getScalar("this is#not").asString());
    }

    @Test
    public void testBadIndentReported() {
        String yaml = """
            list:
              - first
             - second  # Only 1 space indent, should fail
            """;
        //
        List<YamlToken> tokens = tokenizer.tokenize(yaml);
        if (DEBUG) {
            TestUtils.dumpTokens(tokens);
        }

        assertThrows(YamlParserException.class, () -> parser.parse(yaml));
    }

    @Test
    public void test3MYT() {
        String yaml = """
            ---
            k:#foo
             &a !t s
            """;

        if (DEBUG) {
            YamlTokenizer tokenizer = new YamlTokenizer();
            List<YamlToken> tokens = tokenizer.tokenize(yaml);
            TestUtils.dumpTokens(tokens);
        }

        YamlStream stream = parser.parseMulti(yaml);


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        assertEquals("k:#foo &a !t s", scalar.asString());
    }


    @Test
    public void test2G84() {
        String yaml = "--- |1-";

        YamlStream stream = parser.parseMulti(yaml);


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlConverter converter = new YamlConverter();
        YamlNode normalized = normalize(doc.getBody());
        AstNode ast = converter.toPlainAst(normalized);

        assertInstanceOf(ScalarAstNode.class, ast);
        ScalarAstNode<?> scalar = (ScalarAstNode<?>) ast;

        assertEquals("", scalar.asString());
    }


    @Test
    public void test4Q9Fa() {
        String yaml = """
            --- >
             ab
             cd

             ef


             gh
            """;

        YamlStream stream = parser.parseMulti(yaml);


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        assertEquals("ab cd\nef\n\ngh\n", scalar.asString());
    }

   @Test
    public void test4QFQ() {
        String yaml = "- |\n detected\n- >\n \n  \n  # detected\n- |1\n  explicit\n- >\n detected\n";

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());

        YamlDocument doc = stream.getDocuments().get(0);

        YamlNode body = normalize(doc.getBody());

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



        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokenizer().tokenize(yaml));
        }

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());

        YamlDocument doc = stream.getDocuments().get(0);

        YamlNode body = normalize(doc.getBody());

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

        if (DUMP_TOKENS) {
            YamlTokenizer tokenizer = new YamlTokenizer();
            List<YamlToken> tokens = tokenizer.tokenize(yaml);
            TestUtils.dumpTokens(tokens);
        }

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());

        YamlDocument doc = stream.getDocuments().get(0);

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap)body;

        TestUtils.assertEquals("Empty line\nas a line feed", map.get("Folding").asString());
    }

    @Test
    void test_6CK3() {
        String yamlString = """
          %TAG !e! tag:example.com,2000:app/
          ---
          - !local foo
          - !!str bar
          - !e!tag%21 baz
          """;

        YamlTokenizer tokenizer = new YamlTokenizer();
        List<YamlToken> tokens = tokenizer.tokenize(yamlString);
        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(tokens);
        }

        YamlStream stream = parser.parseMulti(yamlString);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());

        YamlDocument doc = stream.getDocuments().get(0);

        YamlNode body = normalize(doc.getBody());

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

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        TestUtils.assertEquals("ab\n\n \n", scalar.asString());
    }

    @Test
    public void test6KGN() {
        String yaml = "---\na: &anchor\nb: *anchor";

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(resolveAliases(doc.getBody()));

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

        List<YamlToken> tokens = tokenizer.tokenize(yamlString);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(tokens);
        }

        YamlStream stream = parser.parseMulti(yamlString);


        assertEquals(1, stream.getDocuments().size());

        YamlDocument doc = stream.getDocuments().get(0);

        YamlNode body = normalize(doc.getBody());


        YamlMap map = (YamlMap)body;
        assertEquals(2, map.size());

        YamlMapEntry entry0 = map.getEntry(0);
        YamlMapEntry entry1 = map.getEntry(1);

        String key0 = "foo\nbar:baz\tx \\$%^&*()x";
        // String key0 = "foo\\nbar:baz\\tx \\\\$%^&*()x";
        String key1 = "x\\ny:z\\tx $%^&*()x";
        // String key1 = "x\\\\ny:z\\\\tx $%^&*()x";

        if (DEBUG) {
            System.out.println("Expected 1: " + StringUtils.debugString(key0));
            System.out.println("Actual 1  : " + StringUtils.debugString(entry0.getKeyString()));
            System.out.println("Expected 2: " + StringUtils.debugString(key1));
            System.out.println("Actual 2  : " + StringUtils.debugString(entry1.getKeyString()));
        }

        assertEquals(23, map.getScalar(key0).asInteger());
        assertEquals(24, map.getScalar(key1).asInteger());
    }

    @Test
    public void test6VJK() {
        String yaml = ">\n Sammy Sosa completed another\n fine season with great stats.\n\n   63 Home Runs\n   0.288 Batting Average\n\n What a year!\n";

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        String expected = "Sammy Sosa completed another fine season with great stats.\n\n  63 Home Runs\n  0.288 Batting Average\n\nWhat a year!\n";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));
        }

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void test6WPF() {
        String yaml = "---\n\"\n  foo \n \n    bar\n\n  baz\n\"";

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

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

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        String expected = " 1st non-empty\n2nd non-empty 3rd non-empty ";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));
        }

        TestUtils.assertEquals(expected, scalar.asString());
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

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(tokens);
        }

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

        assertEquals(7, map.size());

        YamlMap top1 = map.getMap("top1");
        YamlMap top2 = map.getMap("top2");
        YamlMap top3 = map.getMap("top3");
        YamlMap top4 = map.getMap("top4");
        YamlMap top5 = map.getMap("top5");

        YamlMapEntry entry = top1.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();

        assertEquals("k1", key.getAnchor());
        assertEquals("node1", top1.getAnchor());

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

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        String expected = "\nfolded line\nnext line\n  * bullet\n\n  * list\n  * lines\n\nlast line\n";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));
        }

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void test36F6() throws IOException {
        String yaml = """
            ---
            plain: a
             b

             c
            """;

        tokenize(yaml);
        YamlStream stream = parser.parseMulti(yaml);

        // reporter.getWriter(Level.INFO).write("Sample info\n");
        // reporter.getWriter(Level.WARN).write("Sample warning\n");

        // reporter.error(new LocalizableRuntimeException(
        //     new Exception("Sample error"), GenericDiagnosticCode.ERROR, "Sample error")
        // );

        if (DEBUG) {
            TestUtils.dumpTokens(parserReporter.getWriter(Level.DEBUG), parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
        YamlMap map = (YamlMap) body;

        YamlScalar scalar = map.getScalar("plain");
        TestUtils.assertEquals("a b\nc", scalar.asString());
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

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        String expected = "ab";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));
        }

        TestUtils.assertEquals(expected, scalar.asString());
    }

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

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlMap.class, body);
        YamlMap map = (YamlMap) body;

        YamlSequence nestedSequence = map.getSequence("nested sequences");
        assertNotNull(nestedSequence);
        assertEquals(2, nestedSequence.size());

        YamlSequence outer1 = nestedSequence.getSequence(0);
        assertNotNull(outer1);

        YamlSequence middle1 = outer1.getSequence(0);
        assertNotNull(middle1);

        YamlSequence inner1 = middle1.getSequence(0);
        assertNotNull(inner1);
        assertTrue(inner1.isEmpty());

        YamlSequence outer2 = nestedSequence.getSequence(1);
        assertNotNull(outer2);

        YamlSequence middle2 = outer2.getSequence(0);
        assertNotNull(middle2);

        YamlMap inner2 = middle2.getMap(0);
        assertNotNull(inner2);
        assertTrue(inner2.isEmpty());

        YamlSequence val1 = map.getSequence("key1");
        YamlMap val2= map.getMap("key2");

        assertTrue(val1.isEmpty());
        assertTrue(val2.isEmpty());
    }

    @Test
    void test565N() {
        String yaml = """
            canonical: !!binary "\\
             R0lGODlhDAAMAIQAAP//9/X17unp5WZmZgAAAOfn515eXvPz7Y6OjuDg4J+fn5\\
             OTk6enp56enmlpaWNjY6Ojo4SEhP/++f/++f/++f/++f/++f/++f/++f/++f/+\\
             +f/++f/++f/++f/++f/++SH+Dk1hZGUgd2l0aCBHSU1QACwAAAAADAAMAAAFLC\\
             AgjoEwnuNAFOhpEMTRiggcz4BNJHrv/zCFcLiwMWYNG84BwwEeECcgggoBADs="
            generic: !!binary |
             R0lGODlhDAAMAIQAAP//9/X17unp5WZmZgAAAOfn515eXvPz7Y6OjuDg4J+fn5
             OTk6enp56enmlpaWNjY6Ojo4SEhP/++f/++f/++f/++f/++f/++f/++f/++f/+
             +f/++f/++f/++f/++f/++SH+Dk1hZGUgd2l0aCBHSU1QACwAAAAADAAMAAAFLC
             AgjoEwnuNAFOhpEMTRiggcz4BNJHrv/zCFcLiwMWYNG84BwwEeECcgggoBADs=
            description:
             The binary value above is a tiny arrow encoded as a gif image.
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

        assertEquals(3, map.size());

        YamlScalar canonical = map.getScalar("canonical");
        YamlScalar generic = map.getScalar("generic");
        YamlScalar description = map.getScalar("description");

        String expectedCanonical = "R0lGODlhDAAMAIQAAP//9/X17unp5WZmZgAAAOfn515eXvPz7Y6OjuDg4J+fn5OTk6enp56enmlpaWNjY6Ojo4SEhP/++f/++f/++f/++f/++f/++f/++f/++f/++f/++f/++f/++f/++f/++SH+Dk1hZGUgd2l0aCBHSU1QACwAAAAADAAMAAAFLCAgjoEwnuNAFOhpEMTRiggcz4BNJHrv/zCFcLiwMWYNG84BwwEeECcgggoBADs=";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expectedCanonical));
            System.out.println("Actual  : " + StringUtils.debugString(canonical.asString()));
        }

        TestUtils.assertEquals(expectedCanonical, canonical.asString());
        TestUtils.assertEquals("R0lGODlhDAAMAIQAAP//9/X17unp5WZmZgAAAOfn515eXvPz7Y6OjuDg4J+fn5\nOTk6enp56enmlpaWNjY6Ojo4SEhP/++f/++f/++f/++f/++f/++f/++f/++f/+\n+f/++f/++f/++f/++f/++SH+Dk1hZGUgd2l0aCBHSU1QACwAAAAADAAMAAAFLC\nAgjoEwnuNAFOhpEMTRiggcz4BNJHrv/zCFcLiwMWYNG84BwwEeECcgggoBADs=\n", generic.asString());
        TestUtils.assertEquals("The binary value above is a tiny arrow encoded as a gif image.", description.asString());
    }

    @Test
    public void test6BCT() {
        String yaml = """
            - foo:	 bar
            - - baz
              -	baz
            """;

        if (DUMP_TOKENS) {
            YamlTokenizer tokenizer = new YamlTokenizer();
            List<YamlToken> tokens = tokenizer.tokenize(yaml);
            TestUtils.dumpTokens(tokens);
        }

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlSequence.class, body);
        YamlSequence bodySeq = (YamlSequence) body;

        YamlMap map = bodySeq.getMap(0);
        YamlSequence seq = bodySeq.getSequence(1);

        TestUtils.assertEquals("bar", map.getString("foo"));
        TestUtils.assertEquals("baz", seq.getFirst().asString());
        TestUtils.assertEquals("baz", seq.getLast().asString());
    }

    @Test
    public void test7FWL() {
        String yaml = """
            !<tag:yaml.org,2002:str> foo :
              !<!bar> baz
            """;

        YamlTokenizer tokenizer = new YamlTokenizer();
        List<YamlToken> tokens = tokenizer.tokenize(yaml);
        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(tokens);
        }

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
        YamlMap map = (YamlMap) body;

        TestUtils.assertEquals("baz", map.getString("foo"));
    }

    @Test
    public void test735Y() {
        String yaml = """
            -
              "flow in block"
            - >
             Block scalar
            - !!map # Block collection
              foo : bar
            """;

        if (DUMP_TOKENS) {
            YamlTokenizer tokenizer = new YamlTokenizer();
            List<YamlToken> tokens = tokenizer.tokenize(yaml);
            TestUtils.dumpTokens(tokens);
        }

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlSequence.class, body);
        YamlSequence seq = (YamlSequence) body;

        YamlMap map = seq.getMap(2);

        TestUtils.assertEquals("flow in block", seq.getScalar(0).asString());
        TestUtils.assertEquals("Block scalar\n", seq.getScalar(1).asString());
        TestUtils.assertEquals("bar", map.getScalar("foo").asString());
    }

    @Test
    public void test82AN() {
        String yaml = """
            ---word1
            word2
            """;

        tokenize(yaml);
        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlScalar.class, body);
        YamlScalar scalar = (YamlScalar) body;

        String expected = "---word1 word2";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));
        }

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void test8XYN() {
        String yaml = """
            ---
            - &😁 unicode anchor
            """;

        // parser.getTokenizer().setReporter(
        //     new StandardReporter()
        //         .setLevel(TOKENIZER_LEVEL)
        //         .setAnsiColoringEnabled(true)
        // );

        // parser.setReporter(
        //     new StandardReporter()
        //         .setLevel(Level.DEBUG)
        //         .setAnsiColoringEnabled(true)
        // );

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        assertInstanceOf(YamlSequence.class, body);
        YamlSequence seq = (YamlSequence)body;

        YamlScalar scalar = seq.getScalar(0);

        String expected = "unicode anchor";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));
        }

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void test96NN_00() {
        String yaml = """
            foo: |-
             \tbar
            """;

        // parser.getTokenizer().setReporter(
        //     new StandardReporter()
        //         .setLevel(TOKENIZER_LEVEL)
        //         .setAnsiColoringEnabled(true)
        // );

        // parser.setReporter(
        //     new StandardReporter()
        //         .setLevel(Level.DEBUG)
        //         .setAnsiColoringEnabled(true)
        // );

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap)body;

        YamlScalar scalar = map.getScalar("foo");

        String expected = "\tbar";

        // System.out.println("Expected: " + StringUtils.debugString(expected));
        // System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Disabled("Parses as expected. Compliance test seems wrong.")
    @Test
    public void test9MQT_01() {
        String yaml = """
            --- "a
            ... x
            b"
            """;

        // parser.getTokenizer().setReporter(
        //     new StandardReporter()
        //         .setLevel(TOKENIZER_LEVEL)
        //         .setAnsiColoringEnabled(true)
        // );

        // parser.setReporter(
        //     new StandardReporter()
        //         .setLevel(Level.DEBUG)
        //         .setAnsiColoringEnabled(true)
        // );

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlScalar scalar = (YamlScalar)body;

        String expected = "a ...x b";

        // System.out.println("Expected: " + StringUtils.debugString(expected));
        // System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void test9YRD() {
        String yaml = "a\nb  \n  c\nd\n\ne";

        Reporter reporter = new StandardReporter()
            .setLevel(TOKENIZER_LEVEL)
            .setAnsiColoringEnabled(true);

        parser.getTokenizer().setReporter(reporter);
        parser.setReporter(reporter);

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
            // TestUtils.dumpTokens(reporter, parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlScalar scalar = (YamlScalar)body;

        String expected = "a b c d\ne";

        // System.out.println("Expected: " + StringUtils.debugString(expected));
        // System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void testAB8U() {
        String yaml = """
            - single multiline
             - sequence entry
            """;

        // Reporter reporter =  new StandardReporter()
        //     .setLevel(TOKENIZER_LEVEL)
        //     .setAnsiColoringEnabled(true);

        // parser.getTokenizer().setReporter(reporter);
        // // parser.setReporter(reporter);

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {

            TestUtils.dumpTokens(parser.getTokens());
            // TestUtils.dumpTokens(reporter, parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlSequence seq = (YamlSequence)body;
        YamlScalar scalar = seq.getScalar(0);

        String expected = "single multiline - sequence entry";

        System.out.println("Expected: " + StringUtils.debugString(expected));
        System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void testC4HZ() {
        String yaml = """
            %TAG ! tag:clarkevans.com,2002:
            --- !shape
              # Use the ! handle for presenting
              # tag:clarkevans.com,2002:circle
            - !circle
              center: &ORIGIN {x: 73, y: 129}
              radius: 7
            - !line
              start: *ORIGIN
              finish: { x: 89, y: 102 }
            - !label
              start: *ORIGIN
              color: 0xFFEEBB
              text: Pretty vector drawing.
            """;

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {

            TestUtils.dumpTokens(parser.getTokens());
            // TestUtils.dumpTokens(reporter, parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        //
        // TODO: Something goes wrong here...
        // Actually something went wrong before here.
        // // The anchor &ORIGIN is not on the flow map node.
        //

        YamlNode body = normalize(
            resolveAliases(doc.getBody())
        );

        YamlSequence seq = (YamlSequence)body;

        YamlMap map0 = seq.getMap(0);
        YamlMap map0circle = map0.getMap("center");
        assertEquals(7, map0.getInteger("radius"));
        assertEquals(73, map0circle.getInteger("x"));
        assertEquals(129, map0circle.getInteger("y"));


        YamlMap map1 = seq.getMap(1);
        YamlMap map1start = map1.getMap("start");
        YamlMap map1finish = map1.getMap("finish");
        assertEquals(73, map1start.getInteger("x"));
        assertEquals(129, map1start.getInteger("y"));
        assertEquals(89, map1finish.getInteger("x"));
        assertEquals(102, map1finish.getInteger("y"));


        YamlMap map2 = seq.getMap(2);
        YamlMap map2start = map2.getMap("start");
        assertEquals(73, map2start.getInteger("x"));
        assertEquals(129, map2start.getInteger("y"));
        assertEquals(16772795, map2.getInteger("color"));
        assertEquals("Pretty vector drawing.", map2.getString("text"));
    }

    @Test
    public void testDBG4() {
        String yaml = """
            # Outside flow collection:
            - ::vector
            - ": - ()"
            - Up, up, and away!
            - -123
            - http://example.com/foo#bar
            # Inside flow collection:
            - [ ::vector,
              ": - ()",
              "Up, up and away!",
              -123,
              http://example.com/foo#bar ]
            """;

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlSequence seq = (YamlSequence)body;

        assertEquals(6, seq.size());

        assertEquals("::vector", seq.getScalar(0).asString());
        assertEquals(": - ()", seq.getScalar(1).asString());
        assertEquals("Up, up, and away!", seq.getScalar(2).asString());
        assertEquals(-123, seq.getScalar(3).asInteger());
        assertEquals("http://example.com/foo#bar", seq.getScalar(4).asString());

        YamlSequence seq2 = seq.getSequence(5);

        assertEquals(5, seq2.size());

        assertEquals("::vector", seq2.getScalar(0).asString());
        assertEquals(": - ()", seq2.getScalar(1).asString());
        assertEquals("Up, up and away!", seq2.getScalar(2).asString());
        assertEquals(-123, seq2.getScalar(3).asInteger());
        assertEquals("http://example.com/foo#bar", seq2.getScalar(4).asString());
    }

    @Disabled("The test seems wrong")
    @Test
    public void testDE56_02() {
        String yaml = "3 trailing\\\t\n    tab";

        Reporter reporter =  new StandardReporter()
            .setLevel(TOKENIZER_LEVEL)
            .setAnsiColoringEnabled(true);

        parser.getTokenizer().setReporter(reporter);
        // parser.setReporter(reporter);

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {

            TestUtils.dumpTokens(parser.getTokens());
            // TestUtils.dumpTokens(reporter, parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlScalar scalar = (YamlScalar)body;

        String expected = "3 trailing\t tab";

        System.out.println("Expected: " + StringUtils.debugString(expected));
        System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void testDWX9() {
        String yaml = "|\n \n  \n  literal\n   \n  \n  text\n\n # Comment";

        Reporter reporter =  new StandardReporter()
            .setLevel(TOKENIZER_LEVEL)
            .setAnsiColoringEnabled(true);

        parser.getTokenizer().setReporter(reporter);
        // parser.setReporter(reporter);

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlScalar scalar = (YamlScalar)body;

        String expected = "\n\nliteral\n \n\ntext\n";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));
        }

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void testG4RS() {
        String yaml = """
            unicode: "Sosa did fine.\\u263A"
            control: "\\b1998\\t1999\\t2000\\n"
            hex esc: "\\x0d\\x0a is \\r\\n"

            single: '"Howdy!" he cried.'
            quoted: ' # Not a ''comment''.'
            tie-fighter: '|\\-*-/|'
            """;

        Reporter reporter =  new StandardReporter()
            .setLevel(TOKENIZER_LEVEL)
            .setAnsiColoringEnabled(true);

        parser.getTokenizer().setReporter(reporter);
        parser.setReporter(reporter);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokenizer().tokenize(yaml));
            // TestUtils.dumpTokens(reporter, parser.getTokens());
        }

        YamlStream stream = parser.parseMulti(yaml);

        // if (DUMP_TOKENS) {
        //     TestUtils.dumpTokens(parser.getTokens());
        //     // TestUtils.dumpTokens(reporter, parser.getTokens());
        // }

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap)body;

        YamlScalar control = map.getScalar("control");
        assertEquals(ScalarStyle.DOUBLE_QUOTED, control.getScalarStyle());

        TestUtils.assertEquals("Sosa did fine.☺", map.getString("unicode"));
        TestUtils.assertEquals("\b1998\t1999\t2000\n", control.asString());
        TestUtils.assertEquals("\r\n is \r\n", map.getString("hex esc"));
        TestUtils.assertEquals("\"Howdy!\" he cried.", map.getString("single"));
        TestUtils.assertEquals(" # Not a 'comment'.", map.getString("quoted"));
        TestUtils.assertEquals("|\\-*-/|", map.getString("tie-fighter"));
    }

    @Test
    public void testHS5T() {
        String yaml = "1st non-empty\n\n 2nd non-empty \n\t3rd non-empty";

        // Reporter reporter =  new StandardReporter()
        //     .setLevel(TOKENIZER_LEVEL)
        //     .setAnsiColoringEnabled(true);

        // parser.getTokenizer().setReporter(reporter);
        // parser.setReporter(reporter);

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
            // TestUtils.dumpTokens(reporter, parser.getTokens());
        }

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlScalar scalar = (YamlScalar)body;

        String expected = "1st non-empty\n2nd non-empty 3rd non-empty";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(scalar.asString()));
        }

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void testJEF9_02() {
        String yaml = "- |+\n   ";

        // Reporter reporter =  new StandardReporter()
        //     .setLevel(Level.DEBUG)
        //     .setAnsiColoringEnabled(true);

        // parser.getTokenizer().setReporter(reporter);
        // parser.setReporter(reporter);

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
            // TestUtils.dumpTokens(reporter, parser.getTokens());
        }

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlSequence seq = (YamlSequence)body;

        TestUtils.assertEquals("\n", seq.getScalar(0).asString());
    }

    @Test
    public void testF6MC() {
        String yaml = """
            ---
            a: >2
               more indented
              regular1
            b: >2


               more indented
              regular2
            """;

        Reporter reporter =  new StandardReporter()
            .setLevel(TOKENIZER_LEVEL)
            .setAnsiColoringEnabled(true);

        parser.getTokenizer().setReporter(reporter);
        // parser.setReporter(reporter);

        // if (DUMP_TOKENS) {
        //     TestUtils.dumpTokens(parser.getTokenizer().tokenize(yaml));
        //     // TestUtils.dumpTokens(reporter, parser.getTokens());
        // }

        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
            // TestUtils.dumpTokens(reporter, parser.getTokens());
        }

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap)body;

        String expected1 = " more indented\nregular1\n";
        String expected2 = "\n\n more indented\nregular2\n";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected1));
            System.out.println("Actual  : " + StringUtils.debugString(map.getScalar("a").asString()));

            System.out.println("Expected: " + StringUtils.debugString(expected2));
            System.out.println("Actual  : " + StringUtils.debugString(map.getScalar("b").asString()));
        }

        TestUtils.assertEquals(expected1, map.getScalar("a").asString());
        TestUtils.assertEquals(expected2, map.getScalar("b").asString());
    }

    @Test
    public void testSM9W_00() {
        String yaml = "-";

        YamlStream stream = parser.parseMulti(yaml);

        if (DEBUG) {
            TestUtils.dumpTokens(parserReporter.getWriter(Level.DEBUG), parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlSequence seq = (YamlSequence) body;
        assertEquals(1, seq.size());
        YamlScalar scalar = seq.getScalar(0);
        assertEquals(PrimitiveType.NULL, scalar.getPrimitiveType());
    }

    @Test
    public void testSM9W_01() {
        String yaml = ":";

        YamlStream stream = parser.parseMulti(yaml);

        if (DEBUG) {
            TestUtils.dumpTokens(parserReporter.getWriter(Level.DEBUG), parser.getTokens());
        }

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;
        assertEquals(1, map.size());
        YamlMapEntry entry = map.getEntry(0);

        YamlScalar value = (YamlScalar)entry.getValue();

        TestUtils.assertEquals("", entry.getKeyString());
        TestUtils.assertEquals(null, value.asString());
    }

    @Test
    public void testK858() {
        String yaml = """
            strip: >-

            clip: >

            keep: |+

            """;


        tokenize(yaml);
        YamlStream stream = parser.parseMulti(yaml);

        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap)body;

        TestUtils.assertEquals("", map.getScalar("strip").asString());
        TestUtils.assertEquals("", map.getScalar("clip").asString());
        TestUtils.assertEquals("\n", map.getScalar("keep").asString());
    }

    @Test
    public void testF8F9() {
        String yaml = """
           # Strip
            # Comments:
          strip: |-
            # text1

           # Clip
            # comments:

          clip: |
            # text2

           # Keep
            # comments:

          keep: |+
            # text3

           # Trail
            # comments.
          """;

        YamlStream stream = parser.parseMulti(yaml);

        if (DEBUG) {
            TestUtils.dumpTokens(parserReporter.getWriter(Level.DEBUG), parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;
        assertEquals(3, map.size());

        TestUtils.assertEquals("# text1", map.getString("strip"));
        TestUtils.assertEquals("# text2\n", map.getString("clip"));
        TestUtils.assertEquals("# text3\n\n", map.getString("keep"));
    }

    @Test
    public void testJR7V() {
        String yaml = """
            - a?string
            - another ? string
            - key: value?
            - [a?string]
            - [another ? string]
            - {key: value? }
            - {key: value?}
            - {key?: value }
            """;

        YamlStream stream = parser.parseMulti(yaml);

        if (DEBUG) {
            TestUtils.dumpTokens(parserReporter.getWriter(Level.DEBUG), parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlSequence seq = (YamlSequence) body;
        assertEquals(8, seq.size());

        TestUtils.assertEquals("a?string", seq.getScalar(0).asString());
        TestUtils.assertEquals("another ? string", seq.getScalar(1).asString());

        YamlMap map2 = seq.getMap(2);
        TestUtils.assertEquals("value?", map2.getString("key"));

        YamlSequence seq3 = seq.getSequence(3);
        TestUtils.assertEquals("a?string", seq3.getScalar(0).asString());

        YamlSequence seq4 = seq.getSequence(4);
        TestUtils.assertEquals("another ? string", seq4.getScalar(0).asString());

        YamlMap map5 = seq.getMap(5);
        TestUtils.assertEquals("value?", map5.getString("key"));

        YamlMap map6 = seq.getMap(6);
        TestUtils.assertEquals("value?", map6.getString("key"));

        YamlMap map7 = seq.getMap(7);
        TestUtils.assertEquals("value", map7.getString("key?"));

    }



    @Test
    public void testM6YH() {
        String yaml = """
            - |
             x
            -
             foo: bar
            -
             - 42
            """;


        YamlStream stream = parser.parseMulti(yaml);

        if (DEBUG) {
            TestUtils.dumpTokens(parserReporter.getWriter(Level.DEBUG), parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
        PlainNode agnostic = new YamlConverter().toPlainAst(body);

        PlainSequenceNode seq = (PlainSequenceNode)agnostic;
        assertEquals(3, seq.size());

        TestUtils.assertEquals("x\n", seq.getScalar(0).asString());

        PlainMapNode map1 = seq.getMap(1);
        TestUtils.assertEquals("bar", map1.getScalar("foo").asString());

        PlainSequenceNode seq2 = seq.getSequence(2);
        assertEquals(42, seq2.getScalar(0).asInteger());

    }

    @Disabled("Come back to this")
    @Test
    public void testMJS9() {
        String yaml = ">\n  foo \n \n  \t bar\n\n  baz\n";


        YamlStream stream = parser.parseMulti(yaml);

        if (DEBUG) {
            TestUtils.dumpTokens(parserReporter.getWriter(Level.DEBUG), parser.getTokens());
        }


        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
        YamlScalar scalar = (YamlScalar)body;

        TestUtils.assertEquals("foo \n\n\t bar\n\nbaz\n", scalar.asString());

    }

    @Test
    public void test4aBK() {
        String yaml = """
            {
            unquoted : "separate",
            http://foo.com,
            omitted value:,
            }
            """;

        YamlStream stream = parser.parseMulti(yaml);

        if (DEBUG) {
            TestUtils.dumpTokens(parserReporter.getWriter(Level.DEBUG), parser.getTokens());
        }

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
        YamlMap map = (YamlMap)body;



        TestUtils.assertEquals(null, map.getString("Mark McGwire"));
        TestUtils.assertEquals(null, map.getString("Sammy Sosa"));
        TestUtils.assertEquals(null, map.getString("Ken Griff"));

    }

    @Test
    public void test2XXW() {
        String yaml = """
            # Sets are represented as a
            # Mapping where each key is
            # associated with a null value
            --- !!set
            ? Mark McGwire
            ? Sammy Sosa
            ? Ken Griff
            """;

        parser.getTokenizer().setReporter(parserReporter);
        parser.setReporter(parserReporter);

        YamlStream stream = parser.parseMulti(yaml);

        if (DEBUG) {
            TestUtils.dumpTokens(parserReporter.getWriter(Level.DEBUG), parser.getTokens());
        }

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
        YamlMap map = (YamlMap)body;



        TestUtils.assertEquals(null, map.getString("Mark McGwire"));
        TestUtils.assertEquals(null, map.getString("Sammy Sosa"));
        TestUtils.assertEquals(null, map.getString("Ken Griff"));

    }

    @Disabled("come back to this")
    @Test
    public void test4FJ6() {
        String yaml = """
            ---
            [
              [ a, [ [[b,c]]: d, e]]: 23
            ]
            """;

        if (DEBUG) {
            TestUtils.dumpTokens(
                parserReporter.getWriter(Level.DEBUG),
                tokenizer.tokenize(yaml)
            );
        }

        parser.setReporter(parserReporter);
        parser.parseMulti(yaml);
    }

    @Test
    public void test4MUZ_00() {
        String yaml = """
            {"foo"
            : "bar"}
            """;

        if (DEBUG) {
            TestUtils.dumpTokens(
                parserReporter.getWriter(Level.DEBUG),
                tokenizer.tokenize(yaml)
            );
        }

        parser.setReporter(parserReporter);
        parser.parseMulti(yaml);
    }

    // TODO: test_T833
    @Test
    public void testUT92() {
        String yaml = """
            ---
            { matches
            % : 20 }
            ...
            ---
            # Empty
            ...
            """;

        if (DEBUG) {
            TestUtils.dumpTokens(
                parserReporter.getWriter(Level.DEBUG),
                tokenizer.tokenize(yaml)
            );
        }

        parser.setReporter(parserReporter);
        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(2, stream.getDocuments().size());

        YamlMap map = (YamlMap) stream.getDocument(0).getBody();
        assertEquals(1, map.size());
        YamlMapEntry entry = map.getEntry(0);
        assertEquals("matches %", entry.getKeyString());
        assertEquals("20", entry.getValue().asString());


        YamlScalar scalar = (YamlScalar) stream.getDocument(1).getBody();
        assertEquals(PrimitiveType.NULL, scalar.getPrimitiveType());
    }

    @Test
    public void test6CA3() {
        String yaml = "\t[\n\t]";

        if (DEBUG) {
            TestUtils.dumpTokens(
                parserReporter.getWriter(Level.DEBUG),
                tokenizer.tokenize(yaml)
            );
        }

        parser.setReporter(parserReporter);
        parser.parseMulti(yaml);
    }

    @Test
    public void test6M2F() {
        String yaml = "&a a: &b b\n: *a\n";

        if (DEBUG) {
            TestUtils.dumpTokens(
                parserReporter.getWriter(Level.DEBUG),
                tokenizer.tokenize(yaml)
            );
        }

        parser.setReporter(parserReporter);
        parser.parseMulti(yaml);
    }

    @Test
    public void test74H7() {
        String yaml = """
            !!str a: b
            c: !!int 42
            e: !!str f
            g: h
            !!str 23: !!bool false
            """;

        if (DEBUG) {
            TestUtils.dumpTokens(
                parserReporter.getWriter(Level.DEBUG),
                tokenizer.tokenize(yaml)
            );
        }

        parser.setReporter(parserReporter);
        parser.parseMulti(yaml);
    }

    // Related to testUT92 and test_T833
    @Test
    public void test87E4() {
        String yaml = """
            'implicit block key' : [
                'implicit flow key' : value,
               ]
            """;

        if (DEBUG) {
            TestUtils.dumpTokens(
                parserReporter.getWriter(Level.DEBUG),
                tokenizer.tokenize(yaml)
            );
        }

        parser.setReporter(parserReporter);
        parser.parseMulti(yaml);
    }

    @Test
    public void testL383() {
        String yaml = """
            --- foo  # comment
            --- foo  # comment
            """;

        if (DEBUG) {
            TestUtils.dumpTokens(
                parserReporter.getWriter(Level.DEBUG),
                tokenizer.tokenize(yaml)
            );
        }

        parser.setReporter(parserReporter);
        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(2, stream.getDocuments().size());

        YamlScalar s0 = (YamlScalar) stream.getDocument(0).getBody();
        assertEquals("foo", s0.asString());
        YamlScalar s1 = (YamlScalar) stream.getDocument(1).getBody();
        assertEquals("foo", s1.asString());
    }

    @Test
    void test_Y79Y_010() {
        String yaml = "-\t-1\n";
        tokenize(yaml);
        parser.parseMulti(yaml);
    }

    @Test
    void test_8UB() {
        String yaml = """
            [
            "double
             quoted", 'single
                       quoted',
            plain
             text, [ nested ],
            single: pair,
            ]
            """;

        tokenize(yaml);
        parser.parseMulti(yaml);
    }
}

