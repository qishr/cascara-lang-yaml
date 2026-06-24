package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.lang.QuoteStyle;
import io.github.qishr.cascara.common.lang.processor.AstFactory;
import io.github.qishr.cascara.common.lang.type.Primitive;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;

public class YamlFactory implements AstFactory<YamlNode,YamlScalarNode,YamlSequenceNode,YamlMapNode,YamlMapEntryNode> {

    @Override
    public YamlScalarNode createScalarNode(Object primitiveValue) {
        // TODO: Should this parameter be called:
        // - `primitiveValue` (used in AstNode), or
        // - `jvmInstance` (used in Serializer) ?
        // Check coding standards and update if need be.
        return new YamlScalarNode(primitiveValue);
    }

    @Override
    public YamlScalarNode createScalarNode(Object key, QuoteStyle quoteStyle) {
        return new YamlScalarNode(key, quoteStyle);
    }

    @Override
    public YamlScalarNode createScalarNode(Primitive primitive) {
        return YamlScalarNode.fromPrimitive(primitive);
    }

    @Override
    public YamlScalarNode createScalarKeyNode(Object key) {
        return new YamlScalarNode(key, true);
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
