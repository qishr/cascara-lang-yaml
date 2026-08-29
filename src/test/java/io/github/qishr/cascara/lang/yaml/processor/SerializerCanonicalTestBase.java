package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.BeforeEach;

import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

public abstract class SerializerCanonicalTestBase extends SerializerTestBase {
    @BeforeEach
    protected void setup() {
        super.setup();

        serializer = new YamlSerializer()
            .setReporter(serializerReporter)
            .setOptions(YamlOptions.CANONICAL);
    }
}
