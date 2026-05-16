package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.LanguageOptions;
import io.github.qishr.cascara.common.lang.processor.Processor;
import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.util.Properties;
import io.github.qishr.cascara.lang.yaml.YamlOptions;

public abstract class AbstractYamlProcessor<P extends Processor> implements Processor {
    static final ContentType YAML_CONTENT_TYPE = new ContentType("YAML")
            .withMimeType("text/yaml")
            .withSuffix(".yaml");

    protected YamlOptions options = new YamlOptions();
    protected Reporter reporter = new NoOpReporter();
    private Properties capabilities;

    protected abstract P self();

    public Properties getCapabilities() {
        if (capabilities == null) {
            capabilities = new Properties();
            capabilities.set("contentType", "text/yaml");
        }
        return capabilities;
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
