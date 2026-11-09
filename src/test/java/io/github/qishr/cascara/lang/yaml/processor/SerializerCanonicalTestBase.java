package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.BeforeEach;

import io.github.qishr.cascara.lang.yaml.util.YamlConfig;

public abstract class SerializerCanonicalTestBase extends SerializerTestBase {
    @BeforeEach
    protected void setup() {
        super.setup();

        serializer = new YamlSerializer()
            .setReporter(serializerReporter)
            .setConfig(YamlConfig.CANONICAL);
    }
}
