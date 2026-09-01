package io.github.qishr.cascara.lang.yaml.processor;

import java.util.List;

import org.junit.jupiter.api.AssertionFailureBuilder;
import org.junit.jupiter.api.BeforeEach;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.YamlAliasResolver;

public abstract class TestBase {
    protected static final Level TOKENIZER_LEVEL = Level.INFO;
    // protected static final Level TOKENIZER_LEVEL = Level.DEBUG;

    protected static final Level PARSER_LEVEL = Level.INFO;
    // protected static final Level PARSER_LEVEL = Level.TRACE;

    protected static final Level EMITTER_LEVEL = Level.INFO;

    protected static final Level SERIALIZER_LEVEL = Level.INFO;
    // protected static final Level SERIALIZER_LEVEL = Level.TRACE;

    protected static boolean DEBUG = false;
    // protected static boolean DEBUG = true;

    protected static boolean DUMP_TOKENS = DEBUG;

    /// Reporter for use by tests
    protected Reporter reporter;

    protected Reporter tokenizerReporter;
    protected Reporter parserReporter;

    protected YamlTokenizer tokenizer;
    protected YamlNormalizer normalizer;
    protected YamlAliasResolver resolver;


    @BeforeEach
    protected void setup() {
        reporter = new StandardReporter()
            .setLevel(Level.DEBUG)
            .setAnsiColoringEnabled(true)
            .setFlushEnabled(true)
            .setStackTraceEnabled(true);

        tokenizerReporter = new StandardReporter()
            .setLevel(TOKENIZER_LEVEL)
            .setAnsiColoringEnabled(true)
            .setFlushEnabled(true)
            .setStackTraceEnabled(true);

        parserReporter = new StandardReporter()
            .setLevel(PARSER_LEVEL)
            .setAnsiColoringEnabled(true)
            .setFlushEnabled(true)
            .setStackTraceEnabled(true);

        // emitterReporter = new StandardReporter()
        //     .setLevel(EMITTER_LEVEL)
        //     .setAnsiColoringEnabled(true)
        //     .setFlushEnabled(true)
        //     .setStackTraceEnabled(true);

        tokenizer = new YamlTokenizer()
            .setReporter(tokenizerReporter);

        normalizer = new YamlNormalizer()
            .setReporter(parserReporter);

        resolver = new YamlAliasResolver()
            .setReporter(parserReporter);
    }

    protected void tokenize(String yaml) {
        if (DUMP_TOKENS) {
            List<YamlToken> tokens = tokenizer.tokenize(yaml);
            TestUtils.dumpTokens(
                parserReporter.getWriter(Level.DEBUG),
                tokens
            );
        }
    }

    protected YamlNode normalize(YamlNode root) {
        return normalizer.normalize(root);
    }

    protected YamlNode resolveAliases(YamlNode root) {
        return resolver.resolve(root);
    }

    protected void assertStringEquals(String expected, String actual) {
        assertStringEquals(null, expected, actual);
    }

    protected void assertStringEquals(String input, String expected, String actual) {
        if (expected == actual) return;
        if (expected == null || !expected.equals(actual)) {

            if (input != null) {
                reporter.debug("Input: " + StringUtils.debugString(input));
                reporter.debug("Input:");
                reporter.getWriter(Level.DEBUG).write(2, input);
            }

            reporter.debug("Expected: " + StringUtils.debugString(expected));
            reporter.debug("Emitted : " + StringUtils.debugString(actual));
            reporter.debug("Expected:");
            reporter.getWriter(Level.DEBUG).write(2, expected);
            reporter.debug("Emitted:");
            reporter.getWriter(Level.DEBUG).write(2, actual);

            AssertionFailureBuilder.assertionFailure()
                // .message(error(expected, actual))
				.expected(StringUtils.debugString(expected))
				.actual(StringUtils.debugString(actual))
				.buildAndThrow();
        }
    }
}
