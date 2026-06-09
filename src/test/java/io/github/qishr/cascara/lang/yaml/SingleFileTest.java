package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import io.github.qishr.cascara.lang.yaml.YamlOptions;
import io.github.qishr.cascara.lang.yaml.processor.YamlParser;

class SingleFileTest {

    // private Reporter reporter = new StandardReporter().setLevel(Level.TRACE);
    private final YamlOptions options = new YamlOptions().setStrict(true);
    // private final YamlParser parser = new YamlParser().setOptions(options).setReporter(reporter);

    // TODO: diagnostic level in one place for all tests?
    // YamlParser parser = new YamlParser().setReporter(new StandardReporter().setLevel(Level.TRACE));
    private final YamlParser parser = new YamlParser().setOptions(options);

    @Test
    void testValidFiles() throws IOException {
        String content = "a: b c";
        assertDoesNotThrow(() -> parser.parse(content), "Should have parsed");
    }
}