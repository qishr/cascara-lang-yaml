package io.github.qishr.cascara.lang.yaml.ast;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public class YamlMapNode extends YamlNode implements MapAstNode<YamlNode, YamlMapEntryNode> {
    private CollectionStyle style = CollectionStyle.BLOCK;
    private final LinkedHashMap<YamlNode,YamlMapEntryNode> entriesByKey = new LinkedHashMap<>();

    public YamlMapNode() {
        // This method intentionally left blank
    }

    public YamlMapNode(int line, int column) {
        super(line, column);
    }

    /// {@inheritDoc}
    @Override
    public boolean isEmpty() {
        return entriesByKey.isEmpty();
    }

    /// {@inheritDoc}
    @Override
    public int size() {
        return entriesByKey.size();
    }

    /// {@inheritDoc}
    @Override
    public boolean containsKey(YamlNode key) {
        return getEntry(key) != null;
    }

    /// {@inheritDoc}
    @Override
    @Nullable
    public YamlNode get(YamlNode key) {
        YamlMapEntryNode value = getEntry(key);
        return value == null ? null : value.getValue();
    }

    /// {@inheritDoc}
    @Override
    public List<YamlMapEntryNode> getChildren() {
        return List.copyOf(entriesByKey.values());
    }

    /// {@inheritDoc}
    @Override
    @Nullable
    public YamlMapEntryNode getEntry(YamlNode key) {
        return entriesByKey.get(key);
    }

    /// {@inheritDoc}
    @Override
    public List<YamlMapEntryNode> getEntries() {
        return List.copyOf(entriesByKey.values());
    }

    /// {@inheritDoc}
    public CollectionStyle getStyle() { return style; }

    /// {@inheritDoc}
    @Override
    public Set<YamlNode> keySet() {
        return entriesByKey.keySet();
    }

    // TODO: PERFORMANCE: This is looking up the hash entry more than once
    /// {@inheritDoc}
    @Override
    public YamlMapNode put(YamlNode key, YamlNode value) {
        YamlMapEntryNode entry = getEntry(key);
        if (entry == null) {
            entry = new YamlMapEntryNode(0, 0, key, value);
            entriesByKey.put(key, entry);
            return this;
        }
        entry.setRaw(value);
        return this;
    }

    /// {@inheritDoc}
    @Override
    public YamlMapNode remove(YamlNode key) {
        entriesByKey.remove(key);
        return this;
    }

    /// {@inheritDoc}
    @Override
    public YamlMapNode remove(String key) {
        for (Map.Entry<YamlNode,YamlMapEntryNode> entry : entriesByKey.entrySet()) {
            if (entry.getKey() instanceof YamlScalarNode scalar) {
                if (scalar.asString().equals(key)) {
                    entriesByKey.remove(scalar);
                    return this;
                }
            }
        }
        return this;
    }

    /// {@inheritDoc}
    public YamlMapNode setStyle(CollectionStyle style) {
        this.style = style;
        return this;
    }

    //
    // Convenience Methods
    //

    /// {@inheritDoc}
    @Override
    public boolean containsKey(String key) {
        for (YamlNode keyNode : entriesByKey.keySet()) {
            if (keyNode instanceof YamlScalarNode scalar && key.equals(scalar.asString())) {
                return true;
            }
        }
        return false;
    }

    /// {@inheritDoc}
    @Override
    @Nullable
    public YamlNode get(String key) {
        if (key == null) return null;

        for (Map.Entry<YamlNode,YamlMapEntryNode> entry : entriesByKey.entrySet()) {
            YamlMapEntryNode entryNode = entry.getValue();

            YamlNode kNode = entryNode.getKey();
            String entryKey = null;
            if (kNode instanceof YamlScalarNode scalar) {
                entryKey = scalar.asString();
            } else {
                entryKey = kNode.toString();
            }

            if (key.equals(entryKey)) {
                YamlNode val = entryNode.getValue();
                return (val instanceof YamlAnchorNode a) ? a.getInnerNode() : val;
            }
        }
        return null;
    }

    /// {@inheritDoc}
    @Override
    @Nullable
    public YamlMapNode getMap(String key) {
        if (get(key) instanceof YamlMapNode map) {
            return map;
        }
        return null;
    }

    /// {@inheritDoc}
    @Override
    @Nullable
    public YamlSequenceNode getSequence(String key) {
        if (get(key) instanceof YamlSequenceNode seq) {
            return seq;
        }
        return null;
    }

    /// Associates the specified value with the specified string key.
    ///
    /// If the map previously contained a mapping for the key, the old value
    /// is replaced. This method automatically wraps the string in a PLAIN
    /// scalar node.
    ///
    /// @param key   The string key to be associated with the value.
    /// @param value The value node to be associated with the key.
    @Override
    public YamlMapNode put(String key, YamlNode value) {
        for (YamlMapEntryNode entry : entriesByKey.values()) {
            YamlNode kNode = entry.getKey();
            // Check if the existing key's string value matches the requested key
            if (kNode instanceof YamlScalarNode scalar && key.equals(scalar.asString())) {
                entry.setRaw(value);
                return this;
            }
        }

        // Only if not found, create the new entry
        YamlNode keyNode = new YamlScalarNode(0, 0, key, key, QuoteStyle.PLAIN);
        YamlMapEntryNode entry = new YamlMapEntryNode(0, 0, keyNode, value);
        entriesByKey.put(entry.getKey(), entry);
        return this;
    }

    /// {@inheritDoc}
    public YamlMapNode put(YamlMapEntryNode entry) {
        entriesByKey.put(entry.getKey(), entry);
        return this;
    }

    /// {@inheritDoc}
    @Override
    public Set<YamlMapEntryNode> entrySet() {
        return new HashSet<YamlMapEntryNode>(entriesByKey.values());
    }

    /// {@inheritDoc}
    @Override
    public List<YamlNode> values() {
        return entriesByKey.values().stream().map(YamlMapEntryNode::getValue).collect(Collectors.toList());
    }

    /// {@inheritDoc}
    @Override
    public YamlMapNode put(String key, String value) {
        // TODO: This should not be forcing double quotes
        return put(key, new YamlScalarNode(value));
    }

    @Override
    public void accept(YamlVisitor visitor) {
        visitor.visit(this);
    }

	@Override
	public Iterator<YamlMapEntryNode> iterator() {
        return entriesByKey.sequencedValues().iterator();
	}
}