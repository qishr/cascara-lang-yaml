package io.github.qishr.cascara.lang.yaml.ast;

import java.net.URI;
import java.util.List;

import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;

/// Represents the structural pairing of a key and a value.
public class YamlMapEntryNode extends YamlNode implements MapEntryAstNode<YamlNode> {
    private final YamlNode key;
    private YamlNode value;

    public YamlMapEntryNode(int line, int column, URI uri, YamlNode key, YamlNode value) {
        super(line, column, uri);
        this.key = key;
        this.value = value;
    }

    public YamlMapEntryNode(YamlNode key, YamlNode value) {
        super(0, 0, null);
        this.key = key;
        this.value = value;
    }

    /// {@inheritDoc}
    @Override public YamlNode getKey() { return key; }

    /// {@inheritDoc}
    @Override public YamlNode getValue() { return value; }

    /// {@inheritDoc}
    @Override public void setValue(YamlNode value) {
        this.value = (YamlNode) value;
    }

    /// {@inheritDoc}
    @Override public List<YamlNode> getChildren() {
        return List.of(key, value);
    }
}