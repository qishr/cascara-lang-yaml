package io.github.qishr.cascara.lang.yaml.processor;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

public abstract class ParserTestBase {
    // Old things that need to be removed...
    protected static final Level PARSER_LEVEL = Level.INFO;
    protected static final Level TOKENIZER_LEVEL = Level.DEBUG;
    protected static final boolean dumpTokens = false;



    protected static final boolean DUMP_TOKENS = true;
    protected static final boolean DEBUG = true;

    protected YamlTokenizer tokenizer;
    protected YamlAstParser parser;
    protected YamlNormalizer normalizer;
    protected YamlAliasResolver resolver;

    protected Reporter reporter;

    @BeforeEach
    protected void setup() {
        reporter = new StandardReporter()
            .setLevel(Level.DEBUG)
            .setAnsiColoringEnabled(true)
            .setFlushEnabled(false)
            .setStackTraceEnabled(true);

        parser = new YamlAstParser()
            .setReporter(reporter);

        tokenizer = new YamlTokenizer()
            .setReporter(reporter);

        normalizer = new YamlNormalizer()
            .setReporter(reporter);

        resolver = new YamlAliasResolver()
            .setReporter(reporter);
    }

    protected void tokenize(String yaml) {
        List<YamlToken> tokens = tokenizer.tokenize(yaml);
        if (DUMP_TOKENS) {
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
