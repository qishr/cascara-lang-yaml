package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.lang.ast.AstNodeFactory;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

public class YamlNodeFactory implements AstNodeFactory<YamlNode,YamlScalarNode,YamlSequenceNode,YamlMapNode,YamlMapEntryNode,YamlNode> {

    @Override
    public YamlScalarNode createScalarNode(Object jvmValue) {
        return new YamlScalarNode(jvmValue);
    }

    @Override
    public YamlScalarNode createScalarNode(Object key, QuoteStyle quoteStyle) {
        return new YamlScalarNode(key, quoteStyle);
    }

	@Override
	public YamlScalarNode createScalarNode(Object jvmValue, QuoteStyle quoteStyle, LanguageOptions<?> options) {
        return new YamlScalarNode(jvmValue, quoteStyle, (YamlOptions)options);
	}

    @Override
    public YamlScalarNode createKey(Object key) {
        return new YamlScalarNode(key);
    }

    @Override
    public YamlSequenceNode createSequenceNode() {
        return new YamlSequenceNode();
    }

    @Override
    public YamlMapNode createMapNode() {
        return new YamlMapNode();
    }
}
