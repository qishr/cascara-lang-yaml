package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.processor.Processor;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.util.Properties;
import io.github.qishr.cascara.lang.yaml.YamlOptions;

public abstract class AbstractYamlProcessor<P extends Processor> implements Processor {
    static final String YAML_CONTENT_TYPE_STRING = "text/yaml";

    static final ContentType YAML_CONTENT_TYPE =
        new ContentType("YAML")
            .withType("text/yaml")
            .withType("application/yaml")
            .withSuffix(".yaml")
            .withSuffix(".yml");

    protected YamlOptions options = new YamlOptions();
    protected Reporter reporter = new NoOpReporter();
    private Properties properties;

    protected abstract P self();

    @Override
    public Properties getServiceProperties() {
        if (properties == null) {
            properties = new Properties();
            properties.set("contentType", YAML_CONTENT_TYPE_STRING);
        }
        return properties;
    }

    @Override
    public ContentType getContentType() {
        return YAML_CONTENT_TYPE;
    }

    /// {@inheritDoc}
    @Override
    public P setReporter(Reporter reporter) {
        this.reporter = reporter;
        return self();
    }

    /// {@inheritDoc}
    @Override
    public P setOptions(LanguageOptions<?> options) {
        this.options = (YamlOptions) options;
        return self();
    }
}
