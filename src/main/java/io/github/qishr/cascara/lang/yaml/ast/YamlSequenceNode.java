// YamlSequence.java (Extends YamlNode)
package io.github.qishr.cascara.lang.yaml.ast;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.common.lang.simple.SimpleNode;

/// Represents a YAML sequence (a list of items).
public class YamlSequenceNode extends YamlNode implements SequenceAstNode<YamlNode> {
    private final List<YamlNode> elements = new ArrayList<>();
    private CollectionStyle style = CollectionStyle.BLOCK;
    private boolean isExpanded = false; // Default to compact


    public YamlSequenceNode() {
        // This method intentionally left blank
    }

    public YamlSequenceNode(int line, int column, URI uri) {
        super(line, column, uri);
    }

    @Override
    public YamlSequenceNode remove(int index) {
        if (index >= 0 && index < elements.size()) {
            elements.remove(index);
        }
        return this;
    }

    @Override
    public void clear() {
        elements.clear();
    }

    /// Appends an item to the sequence.
    @Override
    public YamlSequenceNode add(YamlNode item) { elements.add(item); return this; }

    /// {@inheritDoc}
    @Override public int size() { return elements.size(); }

    /// {@inheritDoc}
    @Override public YamlNode get(int index) { return elements.get(index); }

    /// {@inheritDoc}
    @Override public List<YamlNode> getElements() {
        return elements;
    }

    @Override
    public YamlSequenceNode remove(YamlNode node) {
        elements.remove(node);
        return this;
    }

    // /// {@inheritDoc}
    // @Override public Iterable<YamlNode> items() { return items; }

    /// {@inheritDoc}
    @Override public List<YamlNode> getChildren() { return elements; }

    public CollectionStyle getStyle() { return style; }
    public void setStyle(CollectionStyle style) { this.style = style; }

    public boolean isExpanded() { return isExpanded; }
    public void setExpanded(boolean expanded) { this.isExpanded = expanded; }

    /// Returns Iterator instance
    public Iterator<YamlNode> iterator() {
        return new SequenceIterator<SimpleNode>(this);
    }

    static class SequenceIterator<T> implements Iterator<YamlNode> {
        YamlSequenceNode list;
        int currentIndex = 0;

        // initialize pointer to head of the list for iteration
        public SequenceIterator(YamlSequenceNode list) {
            this.list = list;
        }

        // returns false if next element does not exist
        public boolean hasNext() {
            return currentIndex < list.size();
        }

        // return current data and update pointer
        public YamlNode next() {
            YamlNode data = list.get(currentIndex++);
            return data;
        }

        // implement if needed
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
