package io.github.qishr.cascara.lang.yaml.processor;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

public abstract class BaseParserTest {
    protected static final Level TOKENIZER_LEVEL = Level.INFO;
    protected static final Level PARSER_LEVEL = Level.INFO;
    protected static final boolean DUMP_TOKENS = false;
    protected static final boolean DEBUG = false;

    protected YamlTokenizer tokenizer;
    // protected YamlAstParser parser;
    protected YamlNormalizer normalizer;
    protected YamlAliasResolver resolver;
    protected YamlEmitter emitter;

    protected Reporter reporter;

    @BeforeEach
    protected void setup() {
        reporter = new StandardReporter()
            .setLevel(PARSER_LEVEL)
            .setAnsiColoringEnabled(true)
            .setFlushEnabled(false)
            .setStackTraceEnabled(true);

        // parser = new YamlAstParser()
        //     .setReporter(reporter);

        tokenizer = new YamlTokenizer()
            .setReporter(reporter);

        normalizer = new YamlNormalizer()
            .setReporter(reporter);

        resolver = new YamlAliasResolver()
            .setReporter(reporter);

        emitter = new YamlEmitter()
            .setReporter(reporter)
            .setOptions(YamlOptions.CANONICAL);
    }

    protected void tokenize(String yaml) {
        if (DUMP_TOKENS) {
            List<YamlToken> tokens = tokenizer.tokenize(yaml);
            TestUtils.dumpTokens(
                reporter.getWriter(Level.DEBUG),
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

}
