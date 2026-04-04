// YamlSequence.java (Extends YamlNode)
package io.github.qishr.cascara.lang.yaml.ast;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;

/// Represents a YAML sequence (a list of items).
public class YamlSequenceNode extends YamlNode implements SequenceAstNode<YamlNode> {
    private final List<YamlNode> items = new ArrayList<>();
    private CollectionStyle style = CollectionStyle.BLOCK;
    private boolean isExpanded = false; // Default to compact


    public YamlSequenceNode() {
        // This method intentionally left blank
    }

    public YamlSequenceNode(int line, int column, URI uri) {
        super(line, column, uri);
    }

    @Override
    public void remove(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
    }

    @Override
    public void clear() {
        items.clear();
    }

    /// Appends an item to the sequence.
    @Override
    public void add(YamlNode item) { items.add(item); }

    /// {@inheritDoc}
    @Override public int size() { return items.size(); }

    /// {@inheritDoc}
    @Override public YamlNode get(int index) { return items.get(index); }

    /// {@inheritDoc}
    @Override public List<YamlNode> getElements() {
        return items;
    }

    /// {@inheritDoc}
    @Override public Iterable<YamlNode> items() { return items; }

    /// {@inheritDoc}
    @Override public List<YamlNode> getChildren() { return items; }

    public CollectionStyle getStyle() { return style; }
    public void setStyle(CollectionStyle style) { this.style = style; }

    public boolean isExpanded() { return isExpanded; }
    public void setExpanded(boolean expanded) { this.isExpanded = expanded; }

}
