package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.plain.PlainNode;
import io.github.qishr.cascara.common.lang.plain.PlainScalarNode;
import io.github.qishr.cascara.common.lang.plain.PlainSequenceNode;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;

import static org.junit.jupiter.api.Assertions.*;

public class AstParserSpecTests2 extends AstParserTestBase {

    @Test
    public void testColon() {
        String yaml = """
            a: {k: ::v}
            b: [::v,:v]
            c: ["k":v]
            d: { "k"
             :v }
            e: [ {k: v}:v ]
            :f: :v
            ::g: v
            """;
            // TODO: :h :v
            // should parse as {":h :v"}

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;
        assertEquals(7, map.size());

        YamlMap map0 = map.getMap("a");
        TestUtils.assertEquals("::v", map0.getString("k"));

        YamlSequence seq1 = map.getSequence("b");
        TestUtils.assertEquals("::v", seq1.getScalar(0).asString());
        TestUtils.assertEquals(":v", seq1.getScalar(1).asString());

        YamlSequence seq2 = map.getSequence("c");
        YamlMap map2 = seq2.getMap(0);
        TestUtils.assertEquals("v", map2.getString("k"));

        YamlMap map3 = map.getMap("d");
        TestUtils.assertEquals("v", map3.getString("k"));

        YamlSequence seq4 = map.getSequence("e");
        YamlMap mapkv = seq4.getMap(0);
        YamlMapEntry e = mapkv.getEntry(0);
        TestUtils.assertEquals("v", e.getValue().asString());
        // TODO: Better way of getting entry value as string/scalar/map etc

        YamlMap keymap = (YamlMap)e.getKey();
        TestUtils.assertEquals("v", keymap.getString("k"));

        TestUtils.assertEquals(":v", map.getString(":f"));
        TestUtils.assertEquals("v", map.getString("::g"));
    }

    @Test
    public void test9MMWa() {
        String yaml = """
            - [ YAML : separate ]
            - [ "JSON like":adjacent ]
            - [ {JSON: like}:adjacent ]
            """;

        tokenize(yaml);
        parser.parseMulti(yaml);
    }

    // The problem here is that fixing it breaks valid_16_keys_explicit and vice versa
    @Disabled("Come back to this")
    @Test
    public void test9MMWb() {
        String yaml = """
            - - YAML: separate
            - - "JSON like": adjacent
            - - ? JSON: like
                : adjacent
            """;

        tokenize(yaml);
        parser.parseMulti(yaml);
    }


    // Duplicate of testMixedKeysImplicitExplicitNull
    @Test
    public void testDFF7() {
        String yaml = """
            {
            ? explicit: entry,
            implicit: entry,
            ?
            }
            """;

        parserReporter.setLevel(Level.TRACE);

        YamlMap root = (YamlMap) parser.parse(yaml);

        assertEquals(3, root.size());

        YamlMapEntry explicitKeyEntry = root.getEntry(0);
        YamlMapEntry implicitKeyEntry = root.getEntry(1);
        YamlMapEntry nullKeyEntry = root.getEntry(2);

        assertEquals("explicit", explicitKeyEntry.getKey().asString());
        assertEquals("implicit", implicitKeyEntry.getKey().asString());
        assertEquals(null, nullKeyEntry.getKey().asString());
    }

    // TODO: This is not BU8L - what is it?
    @Test
    public void testBU8L_not() {
        String yaml = """
            {
                "key": {
                  "a": "b"
                }
              }
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

        YamlMap map2 = map.getMap("key");

        TestUtils.assertEquals("b", map2.getString("a"));
    }

    @Test
    public void testBU8La() {
        String yaml = """
            key: &anchor
             !!map
              a: b
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

        YamlMap map2 = map.getMap("key");

        TestUtils.assertEquals("b", map2.getString("a"));
    }

    @Test
    public void test57H4() {
        String yaml = """
            sequence: !!seq
            - entry
            - !!seq
             - nested
            mapping: !!map
             foo: bar
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

    }

    @Test
    public void testBU8Lb() {
        String yaml = """
            key: &anchor
                 !!map
                 a: b
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

        YamlMap map2 = map.getMap("key");

        TestUtils.assertEquals("b", map2.getString("a"));
    }

    @Disabled("Come back to this")
    @Test
    public void testFH7J() {
        String yaml = """
            - !!str
            - !!null : a
              b: !!str
            - !!str : !!null
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
        // PlainNode agnostic = new YamlConverter().toPlainAst(body);
        // PlainSequenceNode seq = (PlainMapNode)agnostic;

        YamlSequence seq = (YamlSequence) body;
        assertEquals(3, seq.size());
    }

    @Test
    public void testHMQ5() {
        String yaml = """
            !!str &a1 "foo":
              !!str bar
            &a2 baz : *a1
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlMap originalMap = (YamlMap)doc.getBody();
        YamlMapEntry originalEntry = originalMap.getEntry(1);
        YamlNode originalKey = originalEntry.getKey();
        YamlNode originalVal = originalEntry.getValue();

        YamlNode body = normalize(resolveAliases(doc.getBody()));

        YamlMap normalizedMap = (YamlMap) body;
        YamlMapEntry normalizedEntry = normalizedMap.getEntry(1);
        YamlNode normalizedKey = normalizedEntry.getKey();
        YamlNode normalizedVal = normalizedEntry.getValue();

        if (DEBUG) {
            System.out.println("O-key-type: " + originalKey.getClass().getSimpleName());
            System.out.println("O-key-string: " + originalKey.asString());
            System.out.println("O-val-type: " + originalVal.getClass().getSimpleName());
            System.out.println("O-val-string: " + originalVal.asString());
            if (originalVal instanceof YamlAlias alias) {
                System.out.println("O-val-alias-anchor: " + alias.getAnchor());
                System.out.println("O-val-alias-alias: " + alias.getName());
                YamlNode resolved = alias.getResolvedNode();
                System.out.println("O-val-resolved-type: " + (resolved == null ? "null" : resolved.getClass().getSimpleName()));
                System.out.println("O-val-resolved-string " + (resolved == null ? "n/a" : resolved.asString()));
            }
            System.out.println("N-key-type: " + normalizedKey.getClass().getSimpleName());
            System.out.println("N-key-string: " + normalizedKey.asString());
            System.out.println("N-val-type: " + (normalizedVal == null ? "null" : normalizedVal.getClass().getSimpleName()));
            System.out.println("N-val-string: " + (normalizedVal == null ? "n/a" : normalizedVal.asString()));
        }

        TestUtils.assertEquals("bar", normalizedMap.getString("foo"));
        TestUtils.assertEquals("foo", normalizedMap.getString("baz"));
    }

    @Test
    public void testCN3R() {
        String yaml = """
            &flowseq [
             a: b,
             &c c: d,
             { &e e: f },
             &g { g: h }
            ]
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlSequence seq = (YamlSequence) body;
        assertEquals(4, seq.size());

        YamlMap map0 = seq.getMap(0);
        YamlMap map1 = seq.getMap(1);
        YamlMap map2 = seq.getMap(2);
        YamlMap map3 = seq.getMap(3);

        assertEquals(1, map0.size());
        assertEquals(1, map1.size());
        assertEquals(1, map2.size());
        assertEquals(1, map3.size());

        TestUtils.assertEquals("b", map0.getString("a"));
        TestUtils.assertEquals("d", map1.getString("c"));
        TestUtils.assertEquals("f", map2.getString("e"));
        TestUtils.assertEquals("h", map3.getString("g"));
    }

    @Test
    public void testM5C3() {
        String yaml = """
            literal: |2
              value
            folded:
               !foo
              >1
             value
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

        TestUtils.assertEquals("value\n", map.getString("literal"));
        TestUtils.assertEquals("value\n", map.getString("folded"));
    }

    @Test
    public void testNJ66() {
        String yaml = """
            ---
            - { single line: value}
            - { multi
              line: value}
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlSequence seq = (YamlSequence) body;
        YamlMap map0 = seq.getMap(0);
        YamlMap map1 = seq.getMap(1);

        TestUtils.assertEquals("value", map0.getString("single line"));
        TestUtils.assertEquals("value", map1.getString("multi line"));
    }

    @Test
    public void testX38W() {
        String yaml = "{ &a [a, &b b]: *b, *a : [c, *b, d]}";

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        normalize(doc.getBody());

        // YamlSequence seq = (YamlSequence) body;
        // YamlMap map0 = seq.getMap(0);
        // YamlMap map1 = seq.getMap(1);

        // TestUtils.assertEquals("value", map0.getString("single line"));
        // TestUtils.assertEquals("value", map1.getString("multi line"));
    }

    @Test
    public void testPW8X() {
        String yaml = """
            - &a
            - a
            -
              &a : a
              b: &b
            -
              &c : &a
            -
              ? &d
            -
              ? &e
              : &a
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        normalize(doc.getBody());

        // YamlSequence seq = (YamlSequence) body;
        // YamlMap map0 = seq.getMap(0);
        // YamlMap map1 = seq.getMap(1);

        // TestUtils.assertEquals("value", map0.getString("single line"));
        // TestUtils.assertEquals("value", map1.getString("multi line"));
    }

    @Test
    public void testV9D5() {
        String yaml = """
            - sun: yellow
            - ? earth: blue
              : moon: white
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        normalize(doc.getBody());

        // YamlSequence seq = (YamlSequence) body;
        // YamlMap map0 = seq.getMap(0);
        // YamlMap map1 = seq.getMap(1);

        // TestUtils.assertEquals("value", map0.getString("single line"));
        // TestUtils.assertEquals("value", map1.getString("multi line"));
    }

    @Test
    public void testW5VH() {
        String yaml = """
            a: &:@*!$"<foo>: scalar a
            b: *:@*!$"<foo>:
            """;

        // tokenizer.getReporter().setLevel(Level.TRACE);
        tokenize(yaml);
        // tokenizer.getReporter().setLevel(Level.DEBUG);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        normalize(doc.getBody());

        // YamlSequence seq = (YamlSequence) body;
        // YamlMap map0 = seq.getMap(0);
        // YamlMap map1 = seq.getMap(1);

        // TestUtils.assertEquals("value", map0.getString("single line"));
        // TestUtils.assertEquals("value", map1.getString("multi line"));
    }

    @Test
    public void test4MUZ_02() {
        String yaml = """
            {foo
            : bar}
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());
        YamlMap map = (YamlMap) body;
        TestUtils.assertEquals("bar", map.getString("foo"));
    }

    @Test
    public void testW42U() {
        String yaml = """
            - # Empty
            - |
             block node
            - - one # Compact
              - two # sequence
            - one: two # Compact mapping
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlSequence seq = (YamlSequence) body;
        assertEquals(4, seq.size());

        TestUtils.assertEquals(null, seq.getScalar(0).asString());
        TestUtils.assertEquals("block node\n", seq.getScalar(1).asString());

        YamlSequence seq2 = seq.getSequence(2);
        YamlMap map3 = seq.getMap(3);

        assertEquals(2, seq2.size());
        assertEquals(1, map3.size());

        TestUtils.assertEquals("two", map3.getString("one"));
    }

    @Test
    public void test58MP() {
        String yaml = "{x: :x}";

        // tokenizer.getReporter().setLevel(Level.TRACE);
        tokenize(yaml);
        // tokenizer.getReporter().setLevel(Level.DEBUG);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());
        YamlMap map = (YamlMap) body;
        TestUtils.assertEquals(":x", map.getString("x"));
    }

    @Test
    public void test5MUD() {
        String yaml = "{ \"foo\"\n  :bar }";

        // tokenizer.getReporter().setLevel(Level.TRACE);
        tokenize(yaml);
        // tokenizer.getReporter().setLevel(Level.DEBUG);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;
        TestUtils.assertEquals("bar", map.getString("foo"));
    }

    @Test
    public void test5T43() {
        String yaml = """
            - { "key":value }
            - { "key"::value }
            """;

        // tokenizer.getReporter().setLevel(Level.TRACE);
        tokenize(yaml);
        // tokenizer.getReporter().setLevel(Level.DEBUG);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlSequence seq = (YamlSequence) body;
        assertEquals(2, seq.size());

        YamlMap map0 = seq.getMap(0);
        YamlMap map1 = seq.getMap(1);

        TestUtils.assertEquals("value", map0.getString("key"));
        TestUtils.assertEquals(":value", map1.getString("key"));
    }

    @Test
    public void test8KB6() {
        String yaml = """
            ---
            - { single line, a: b}
            - { multi
              line, a: b}
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlSequence seq = (YamlSequence) body;
        assertEquals(2, seq.size());

        YamlMap map0 = seq.getMap(0);

        TestUtils.assertEquals("b", map0.getString("a"));
        TestUtils.assertEquals(null, map0.getString("single line"));

        YamlMap map1 = seq.getMap(1);

        TestUtils.assertEquals("b", map1.getString("a"));
        TestUtils.assertEquals(null, map1.getString("multi line"));
    }

    // There are some assumptions going around online:
    // "YAML 1.2 does not allow the use of a question mark inside flow collections"
    // "YAML 1.2 does not allow ? inside flow collections"

    // The YAML 1.2 spec contains this exact YAML as an example:
    // https://yaml.org/spec/1.2.2/#example-single-pair-explicit-entry

    @Disabled("come back to this. it is valid, but online normalizers and validators disagree.")
    @Test
    public void testCT4Q() {
        String yaml = """
            [
            ? foo
             bar : baz
            ]
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlSequence seq = (YamlSequence) body;
        assertEquals(1, seq.size());

        YamlMap map0 = seq.getMap(0);

        TestUtils.assertEquals("baz", map0.getString("foo bar"));
    }

    @Test
    public void testA2M4() {
        String yaml = """
            ? a
            : -	b
              -  -	c
                 - d
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;
        assertEquals(1, map.size());

        YamlSequence seq = map.getSequence("a");
        assertEquals(2, seq.size());

        YamlScalar b = seq.getScalar(0);
        TestUtils.assertEquals("b", b.asString());

        YamlSequence inner = seq.getSequence(1);
        TestUtils.assertEquals("c", inner.getString(0));
        TestUtils.assertEquals("d", inner.getString(1));
    }

    @Test
    public void testK3WX() {
        String yaml = """
            ---
            { "foo" # comment
              :bar }
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;
        assertEquals(1, map.size());

        TestUtils.assertEquals("bar", map.getString("foo"));
    }

    @Test
    public void testMJS9() {
        String yaml = ">\n  foo \n \n  \t bar\n\n  baz";

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlScalar scalar = (YamlScalar) body;

        String actual = scalar.asString();
        String expected = "foo \n\n\t bar\n\nbaz\n";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(actual));
        }

        TestUtils.assertEquals(expected, actual);
    }

    @Test
    public void testNAT4() {
        String yaml = "---\na: '\n  '\nb: '  \n  '\nc: \"  \n  \"\nd: \"\n  \"\ne: '\n\n  '\nf: \"\n\n  \"\ng: '\n\n\n  '\nh: \"\n\n\n  \"";

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;
        assertEquals(8, map.size());

        TestUtils.assertEquals(" ", map.getString("a"));
        TestUtils.assertEquals(" ", map.getString("b"));
        TestUtils.assertEquals(" ", map.getString("c"));
        TestUtils.assertEquals(" ", map.getString("d"));
        TestUtils.assertEquals("\n", map.getString("e"));
        TestUtils.assertEquals("\n", map.getString("f"));
        TestUtils.assertEquals("\n\n", map.getString("g"));
        TestUtils.assertEquals("\n\n", map.getString("h"));

        MapAstNode<?,?,?> plain = (MapAstNode<?,?,?>) new YamlConverter().toPlainAst(body);
        assertEquals(8, plain.size());
    }

    @Test
    public void testNAT4a() {
        String yaml = "a: '\n  '";

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

        TestUtils.assertEquals(" ", map.getString("a"));
    }

    @Test
    public void testNAT4b() {
        String yaml = "b: '  \n  '";

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

        TestUtils.assertEquals(" ", map.getString("b"));
    }

    @Test
    public void testNAT4e() {
        String yaml = "e: '\n\n  '";

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

        TestUtils.assertEquals("\n", map.getString("e"));
    }

    @Test
    public void testSKE5() {
        String yaml = """
            ---
            seq:
             &anchor
            - a
            - b
            """;

        tokenize(yaml);

        // parser.getReporter().setLevel(Level.TRACE);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;
        YamlSequence seq = map.getSequence("seq");

        TestUtils.assertEquals("a", seq.getString(0));
        TestUtils.assertEquals("b", seq.getString(1));
    }

    @Test
    public void testP76L() {
        String yaml = """
            %TAG !! tag:example.com,2000:app/
            ---
            !!int 1 - 3 # Interval, not integer
            """;;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        AstNode node = new YamlConverter().toPlainAst(body);


        // TODO: Rename Plain -> Plain (or Intermediate)



        YamlScalar scalar = (YamlScalar) body;
        TestUtils.assertEquals("1 - 3", scalar.asString());

        ScalarAstNode<?> plainScalar = (ScalarAstNode<?>) node;
        TestUtils.assertEquals("1 - 3", plainScalar.asString());
    }

    //
    // Tests below still have compliance errors.
    // Most likely due to JSON conversion.
    //

    @Test
    public void testWZ62() {
        String yaml = """
            {
                foo : !!str,
                !!str : bar,
            }
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;
        TestUtils.assertEquals("", map.getString("foo"));
        TestUtils.assertEquals("bar", map.getString(""));
    }

    @Test
    public void testS4JQ() {
        String yaml = """
            # Assuming conventional resolution:
            - "12"
            - 12
            - ! 12
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        YamlSequence seq = (YamlSequence) body;
        assertEquals("12", seq.getString(0));
        assertEquals(12, seq.getInteger(1));
        assertEquals("12", seq.getString(2));
    }

    @Test
    public void testLE5A() {
        String yaml = """
            - !!str "a"
            - 'b'
            - &anchor "c"
            - *anchor
            - !!str
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(resolveAliases(doc.getBody()));
        PlainNode agnostic = new YamlConverter().toPlainAst(body);
        PlainSequenceNode seq = (PlainSequenceNode)agnostic;

        assertEquals(5, seq.size());

        TestUtils.assertEquals("a", seq.getScalar(0).asString());
        TestUtils.assertEquals("b", seq.getScalar(1).asString());
        TestUtils.assertEquals("c", seq.getScalar(2).asString());
        TestUtils.assertEquals("c", seq.getScalar(3).asString());
        TestUtils.assertEquals("", seq.getScalar(4).asString());

    }

    @Test
    public void testR4YG() {
        String yaml = "- |\n detected\n- >\n\n\n # detected\n- |1\n  explicit\n- >\n \t\n detected\n";

        tokenizerReporter.setLevel(Level.TRACE);
        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
        YamlSequence seq = (YamlSequence) body;

        assertEquals(4, seq.size());

        TestUtils.assertEquals("detected\n", seq.getScalar(0).asString());
        TestUtils.assertEquals("\n\n# detected\n", seq.getScalar(1).asString());
        TestUtils.assertEquals(" explicit\n", seq.getScalar(2).asString());
        TestUtils.assertEquals("\t\ndetected\n", seq.getScalar(3).asString());
    }

    @Test
    public void testNP9H() {
        String yaml = "\"folded \nto a space,\t\n \nto a line feed, or \t\\\n \\ \tnon-content\"";

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
        YamlScalar scalar = (YamlScalar) body;

        String actual = scalar.asString();
        String expected = "folded to a space,\nto a line feed, or \t \tnon-content";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(actual));
        }

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void testNP9Hb() {
        String yaml = "\"folded \nto\"";

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
        YamlScalar scalar = (YamlScalar) body;

        String actual = scalar.asString();
        String expected = "folded to";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(actual));
        }

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void testKH5V_00() {
        String yaml = "\"2 inline\\ttab\"\n";

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        PlainNode agnostic = new YamlConverter().toPlainAst(body);
        PlainScalarNode scalar = (PlainScalarNode)agnostic;

        String actual = scalar.asString();
        String expected = "2 inline\ttab";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(actual));
        }

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void testKH5V_01() {
        String yaml = "\"2 inline\\\ttab\"";

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());

        PlainNode agnostic = new YamlConverter().toPlainAst(body);
        PlainScalarNode scalar = (PlainScalarNode)agnostic;

        String actual = scalar.asString();
        String expected = "2 inline\ttab";

        if (DEBUG) {
            System.out.println("Expected: " + StringUtils.debugString(expected));
            System.out.println("Actual  : " + StringUtils.debugString(actual));
        }

        TestUtils.assertEquals(expected, scalar.asString());
    }

    @Test
    public void testUGM3() {
        String yaml = """
            --- !<tag:clarkevans.com,2002:invoice>
            invoice: 34843
            date   : 2001-01-23
            bill-to: &id001
                given  : Chris
                family : Dumars
                address:
                    lines: |
                        458 Walkman Dr.
                        Suite #292
                    city    : Royal Oak
                    state   : MI
                    postal  : 48046
            ship-to: *id001
            product:
                - sku         : BL394D
                  quantity    : 4
                  description : Basketball
                  price       : 450.00
                - sku         : BL4438H
                  quantity    : 1
                  description : Super Hoop
                  price       : 2392.00
            tax  : 251.42
            total: 4443.52
            comments:
                Late afternoon is best.
                Backup contact is Nancy
                Billsmer @ 338-4338.
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;
        assertEquals(8, map.size());

        assertEquals(34843, map.getInteger("invoice"));
        assertEquals("2001-01-23", map.getString("date"));

        YamlSequence product = map.getSequence("product");

        YamlMap product0 = product.getMap(0);
        assertEquals("BL394D", product0.getString("sku"));
        assertEquals(4, product0.getInteger("quantity"));
        assertEquals("Basketball", product0.getString("description"));
        assertEquals(450.00, product0.getDouble("price"));

        YamlMap product1 = product.getMap(1);
        assertEquals("BL4438H", product1.getString("sku"));
        assertEquals(1, product1.getInteger("quantity"));
        assertEquals("Super Hoop", product1.getString("description"));
        assertEquals(2392.00, product1.getDouble("price"));

        assertEquals(251.42, map.getDouble("tax"));
        assertEquals(4443.52, map.getDouble("total"));
    }


    @Test
    public void test_EHF6() {
        String yaml = """
            !!map {
              k: !!seq
              [ a, !!str b]
            }
            """;
        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
    }

    @Test
    public void test_VJP3() {
        String yaml = """
            k: {
             k
             :
             v
             }
            """;
        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
    }

    @Test
    public void test_M5DY() {
        String yaml = """
            ? - Detroit Tigers
              - Chicago cubs
            :
              - 2001-07-23

            ? [ New York Yankees,
                Atlanta Braves ]
            : [ 2001-07-02, 2001-08-12,
                2001-08-14 ]
            """;
        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
    }

    @Test
    public void test_FH7J() throws Exception {
        String yaml = """
            - !!str
            -
              !!null : a
              b: !!str
            - !!str : !!null
            """;

        tokenize(yaml);
        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = normalize(doc.getBody());
    }

    @Test
    public void test_P76L() throws Exception {
        String yaml = """
            %TAG !! tag:example.com,2000:app/
            ---
            !!int 1 - 3 # Interval, not integer
            """;

        tokenize(yaml);
        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlScalar scalar = (YamlScalar) doc.getBody();

        assertEquals("!!int", scalar.getTag());
        assertEquals("tag:example.com,2000:app/int", scalar.getResolvedTag());
    }

    @Test
    public void test_52DL() throws Exception {
        String yaml = """
            ---
            ! a
            """;

        tokenize(yaml);
        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlScalar scalar = (YamlScalar) doc.getBody();

        assertEquals("!", scalar.getTag());
        assertEquals("!", scalar.getResolvedTag());
    }
}
