package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.processor.YamlAstParser;

public class StreamTests {
    @Test
    void test_simpleKeyValue() {
        String yamlString = "key: value";

        InputStream stream = new ByteArrayInputStream(yamlString.getBytes(StandardCharsets.UTF_8));

        YamlAstParser parser = new YamlAstParser()
                .setReporter(new StandardReporter().setLevel(Level.TRACE));

        YamlNode node = parser.parse(stream);
        assertInstanceOf(YamlMapNode.class, node);
    }
}
