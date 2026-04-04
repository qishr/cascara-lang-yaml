package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.SimpleReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.yaml.processor.YamlParser;

class SingleFileTest {

    private Reporter reporter = new SimpleReporter().setLevel(Level.TRACE);
    private final YamlOptions options = new YamlOptions().setStrict(true);
    private final YamlParser parser = new YamlParser().setOptions(options).setReporter(reporter);

    @Test
    void testValidFiles() throws IOException {
        String content = "a: b c";
        assertDoesNotThrow(() -> parser.parse(content), "Should have parsed");
    }
}