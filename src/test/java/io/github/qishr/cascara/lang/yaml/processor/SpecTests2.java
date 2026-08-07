package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
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
    // @Disabled("Come back to this")
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

}
