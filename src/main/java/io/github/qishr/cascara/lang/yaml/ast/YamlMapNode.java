package io.github.qishr.cascara.lang.yaml.ast;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.QuoteStyle;

public class YamlMapNode extends YamlNode implements MapAstNode<YamlNode, YamlMapEntryNode> {
    private CollectionStyle style = CollectionStyle.BLOCK;
    private final List<YamlMapEntryNode> entries = new ArrayList<>();

    public YamlMapNode() {
        // This method intentionally left blank
    }

    public YamlMapNode(int line, int column, URI uri) {
        super(line, column, uri);
    }

    @Override public boolean containsKey(YamlNode key) {
        return getEntry(key) != null;
    }

    @Override
    public YamlNode get(YamlNode key) {
        YamlMapEntryNode value = getEntry(key);
        return value == null ? null : value.getValue();
    }

    /// {@inheritDoc}
    @Override
    public List<YamlMapEntryNode> getChildren() {
        return entries;
    }

    @Override
    @Nullable
    public YamlMapEntryNode getEntry(YamlNode key) {
        for (YamlMapEntryNode entry : entries) {
            if (entry.getKey().equals(key)) return entry;
        }
        return null;
    }

    /// {@inheritDoc}
    /// We use an unmodifiable view or a cast to satisfy the wildcard contract.
    @Override
    public List<YamlMapEntryNode> getEntries() {
        return entries;
    }

    public CollectionStyle getStyle() { return style; }

    @Override
    public Set<YamlNode> keySet() {
        return Set.copyOf(entries.stream().map(e -> e.getKey()).toList());
    }

    @Override
    public YamlMapNode put(YamlNode key, YamlNode value) {
        for (YamlMapEntryNode entry : entries) {
            if (entry.getKey().equals(key)) {
                entry.setValue(value);
                return this;
            }
        }
        entries.add(new YamlMapEntryNode(0, 0, getOriginUri(), key, value));
        return this;
    }

    @Override
    public void remove(YamlNode key) {
        entries.removeIf(e -> e.getKey().equals(key));
    }

    @Override
    public void remove(String key) {
        entries.removeIf(e -> {
            if (e.getKey() instanceof YamlScalarNode scalar) {
                if (scalar.getString().equals(key)) {
                    return true;
                }
            }
            return false;
        });
    }

    public void setStyle(CollectionStyle style) { this.style = style; }

    //
    // Convenience Methods
    //

    @Override
    public boolean containsKey(String key) {
        for (YamlMapEntryNode entry : entries) {
            if (entry.getKey() instanceof YamlScalarNode scalar && key.equals(scalar.getString())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public YamlNode get(String key) {
        if (key == null) return null;
        for (YamlMapEntryNode entry : entries) {
            YamlNode kNode = entry.getKey();
            String entryKey = null;
            if (kNode instanceof YamlScalarNode scalar) {
                entryKey = scalar.getString();
            } else {
                entryKey = kNode.toString();
            }

            if (key.equals(entryKey)) {
                YamlNode val = entry.getValue();
                return (val instanceof YamlAnchorNode a) ? a.getInnerNode() : val;
            }
        }
        return null;
    }

    @Override
    public YamlMapNode getMap(String key) {
        YamlNode node = this.get(key);
        if (node instanceof YamlMapNode map) {
            return map;
        }
        return new YamlMapNode();
    }

    @Override
    public YamlSequenceNode getSequence(String key) {
        YamlNode node = this.get(key);
        if (node instanceof YamlSequenceNode seq) {
            return seq;
        }
        return new YamlSequenceNode();
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
        for (YamlMapEntryNode entry : entries) {
            YamlNode kNode = entry.getKey();
            // Check if the existing key's string value matches the requested key
            if (kNode instanceof YamlScalarNode scalar && key.equals(scalar.getString())) {
                entry.setValue(value);
                return this;
            }
        }
        // Only if not found, create the new entry
        YamlNode keyNode = new YamlScalarNode(0, 0, getOriginUri(), key, key, QuoteStyle.PLAIN);
        entries.add(new YamlMapEntryNode(0, 0, getOriginUri(), keyNode, value));
        return this;
    }

    public YamlMapNode put(YamlMapEntryNode entry) {
        for (YamlMapEntryNode candidate : entries) {
            if (candidate.getKey().equals(entry.getKey())) {
                candidate.setValue(entry.getValue());
                return this;
            }
        }
        entries.add(entry);
        return this;
    }

    @Override
    public Set<YamlMapEntryNode> entrySet() {
        return new HashSet<YamlMapEntryNode>(entries);
    }

    @Override
    public Collection<YamlNode> values() {
        return entries.stream().map(YamlMapEntryNode::getValue).collect(Collectors.toList());
    }

    @Override
    public YamlNode put(String key, String value) {
        return put(key, new YamlScalarNode(value, QuoteStyle.DOUBLE));
    }
}