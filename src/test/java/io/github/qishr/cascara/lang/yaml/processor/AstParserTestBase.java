package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.BeforeEach;

public abstract class AstParserTestBase extends TestBase {
    protected YamlAstParser parser;

    @BeforeEach
    protected void setup() {
        super.setup();
        parser = new YamlAstParser()
            .setReporter(parserReporter);

        parser.getTokenizer().setReporter(tokenizerReporter);
    }
}
