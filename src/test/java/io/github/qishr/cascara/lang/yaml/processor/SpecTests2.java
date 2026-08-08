package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
import io.github.qishr.cascara.lang.yaml.exception.YamlParserException;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class SpecTests2 {
    private static final boolean dumpTokens = true;

    private YamlTokenizer tokenizer;
    private YamlAstParser parser;
    private Reporter reporter;

    @BeforeEach
    void setup() {
        reporter = new StandardReporter()
            .setLevel(Level.DEBUG)
            .setAnsiColoringEnabled(true)
            .setFlushEnabled(false)
            .setStackTraceEnabled(true);

        parser = new YamlAstParser()
            .setReporter(reporter);

        tokenizer = new YamlTokenizer();
        tokenizer.setReporter(reporter);
    }

    private void tokenize(String yaml) {
        List<YamlToken> tokens = tokenizer.tokenize(yaml);
        if (dumpTokens) {
            TestUtils.dumpTokens(
                reporter.getWriter(Level.DEBUG),
                tokens
            );
        }
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


    // @Disabled("come back to this")
    @Test
    public void testDFF7() {
        String yaml = """
            {
            ? explicit: entry,
            implicit: entry,
            ?
            }
            """;

        tokenize(yaml);
        parser.parseMulti(yaml);
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

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

        YamlMap map2 = map.getMap("key");

        TestUtils.assertEquals("b", map2.getString("a"));
    }

    @Test
    public void testBU8L() {
        String yaml = """
            key: &anchor
             !!map
              a: b
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

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

        YamlNode body = YamlNormalizer.normalize(doc.getBody());
        // AgnosticNode agnostic = new YamlConverter().toPlainAst(body);
        // AgnosticSequenceNode seq = (AgnosticMapNode)agnostic;

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

        YamlNode body = YamlNormalizer.normalize(doc.getBody());
        YamlMap normalizedMap = (YamlMap) body;
        YamlMapEntry normalizedEntry = normalizedMap.getEntry(1);
        YamlNode normalizedKey = normalizedEntry.getKey();
        YamlNode normalizedVal = normalizedEntry.getValue();

        System.out.println("O-key-type: " + originalKey.getClass().getSimpleName());
        System.out.println("O-key-string: " + originalKey.asString());
        System.out.println("O-val-type: " + originalVal.getClass().getSimpleName());
        System.out.println("O-val-string: " + originalVal.asString());
        if (originalVal instanceof YamlAlias alias) {
            System.out.println("O-val-alias-anchor: " + alias.getAnchor());
            System.out.println("O-val-alias-alias: " + alias.getAlias());
            YamlNode resolved = alias.getResolvedNode();
            System.out.println("O-val-resolved-type: " + (resolved == null ? "null" : resolved.getClass().getSimpleName()));
            System.out.println("O-val-resolved-string " + (resolved == null ? "n/a" : resolved.asString()));

        }


        System.out.println("N-key-type: " + normalizedKey.getClass().getSimpleName());
        System.out.println("N-key-string: " + normalizedKey.asString());
        System.out.println("N-val-type: " + (normalizedVal == null ? "null" : normalizedVal.getClass().getSimpleName()));
        System.out.println("N-val-string: " + (normalizedVal == null ? "n/a" : normalizedVal.asString()));

        // TestUtils.assertEquals("bar", normalizedMap.getString("foo"));
        // TestUtils.assertEquals("foo", normalizedMap.getString("baz"));
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

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

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

        YamlNode body = YamlNormalizer.normalize(doc.getBody());

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
        YamlNode body = YamlNormalizer.normalize(doc.getBody());

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
        YamlNode body = YamlNormalizer.normalize(doc.getBody());

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
        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        // YamlSequence seq = (YamlSequence) body;
        // YamlMap map0 = seq.getMap(0);
        // YamlMap map1 = seq.getMap(1);

        // TestUtils.assertEquals("value", map0.getString("single line"));
        // TestUtils.assertEquals("value", map1.getString("multi line"));
    }
}
