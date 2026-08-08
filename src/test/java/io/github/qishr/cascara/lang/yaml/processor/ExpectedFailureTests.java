package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.exception.YamlParserException;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

public class ExpectedFailureTests {
    private static final boolean DUMP_TOKENS = true;

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

        tokenizer = new YamlTokenizer()
            .setReporter(reporter);
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
    public void testZVH3() {
        String yaml = """
            - key: value
             - item1
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    public void test236B() {
        String yaml = """
            foo:
              bar
            invalid
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Test
    public void testCQ3W() {
        String yaml = """
            ---
            key: "missing closing quote
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }

    @Disabled("come back to this")
    @Test
    public void test2CMS() {
        String yaml = """
            this
             is
              invalid: x
            """;

        tokenize(yaml);
        assertThrows(YamlParserException.class, () -> parser.parseMulti(yaml));
    }
}
