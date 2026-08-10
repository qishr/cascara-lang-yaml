package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.lang.plain.PlainNode;
import io.github.qishr.cascara.common.lang.plain.PlainScalarNode;
import io.github.qishr.cascara.common.lang.plain.PlainSequenceNode;
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
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class SpecTests3 {
    private static final boolean DUMP_TOKENS = true;
    private static final boolean DEBUG = true;

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
        if (DUMP_TOKENS) {
            TestUtils.dumpTokens(
                reporter.getWriter(Level.DEBUG),
                tokens
            );
        }
    }

    @Test
    public void test4ABK() {
        String yaml = """
            {
            unquoted : "separate",
            http://foo.com,
            omitted value:,
            }
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = YamlNormalizer.normalize(doc.getBody());

        YamlMap map = (YamlMap) body;

        YamlMapEntry entry0 = map.getEntry(0);
        TestUtils.assertEquals("unquoted", entry0.getKeyString());
        TestUtils.assertEquals("separate", entry0.getValue().asString());

        YamlMapEntry entry1 = map.getEntry(1);
        TestUtils.assertEquals("http://foo.com", entry1.getKeyString());
        TestUtils.assertEquals(null, entry1.getValue().asString());

        YamlMapEntry entry2 = map.getEntry(2);
        TestUtils.assertEquals("omitted value", entry2.getKeyString());
        TestUtils.assertEquals(null, entry2.getValue().asString());

    }
}
