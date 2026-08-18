// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


package io.github.qishr.cascara.lang.yaml.ast;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.qishr.cascara.common.annotation.Nullable;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.NodeStyle;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;
import io.github.qishr.cascara.lang.yaml.util.YamlVisitor;

public class YamlMap extends YamlNode implements MapAstNode<YamlNode, YamlNode, YamlMapEntry> {
    private final LinkedHashMap<YamlNode,YamlMapEntry> entriesByKey = new LinkedHashMap<>();

    public YamlMap() {
        nodeStyle = NodeStyle.BLOCK;
    }

    public YamlMap(YamlToken token, YamlOptions options) {
        super(token, options);
        nodeStyle = NodeStyle.BLOCK;
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
    public boolean containsKey(Object key) {
        if (key instanceof YamlNode node) {
            return getEntry(node) != null;
        } else if (key instanceof String string) {
            return containsKeyString(string);
        } else {
            return false;
        }
    }

    // public boolean containsKey(YamlNode key) {
    //     return getEntry(key) != null;
    // }

    private boolean containsKeyString(String key) {
        for (YamlNode keyNode : entriesByKey.keySet()) {
            if (key == null) {
                if (keyNode == null) {
                    return true;
                }
            } else if (keyNode instanceof YamlScalar scalar && key.equals(scalar.asString())) {
                return true;
            }
        }
        return false;
    }


    /// {@inheritDoc}
    @Override
    @Nullable
    public YamlNode get(Object key) {
        if (key instanceof String string) {
            for (Map.Entry<YamlNode,YamlMapEntry> entry : entriesByKey.entrySet()) {
                YamlMapEntry entryNode = entry.getValue();

                YamlNode kNode = entryNode.getKey();
                String entryKey = null;
                if (kNode instanceof YamlScalar scalar) {
                    entryKey = scalar.asString();
                } else {
                    entryKey = kNode.toString();
                }

                if (string.equals(entryKey)) {
                    YamlNode val = entryNode.getValue();
                    // return (val instanceof YamlAnchor a) ? a.getInnerNode() : val;
                    return val;
                }
            }
        }
        YamlMapEntry value = getEntry(key);
        return value == null ? null : value.getValue();
    }

    /// {@inheritDoc}
    @Override
    public List<YamlMapEntry> getChildren() {
        return List.copyOf(entriesByKey.values());
    }

    /// {@inheritDoc}
    @Override
    @Nullable
    public YamlMapEntry getEntry(Object key) {
        return entriesByKey.get(key);
    }

    @Nullable
    @Override
    public YamlMapEntry getEntry(int i) {
        if (i < 0 || i > size()) throw new NoSuchElementException();
        return entriesByKey.sequencedValues().toArray(new YamlMapEntry[]{})[i];
    }

    /// {@inheritDoc}
    @Override
    public List<YamlMapEntry> getEntries() {
        return List.copyOf(entriesByKey.values());
    }

    /// {@inheritDoc}
    @Override
    public Set<YamlNode> keySet() {
        return entriesByKey.keySet();
    }

    // TODO: PERFORMANCE: This is looking up the hash entry more than once
    /// {@inheritDoc}
    @Override
    public YamlMap put(YamlNode key, YamlNode value) {
        YamlMapEntry entry = getEntry(key);
        if (entry == null) {
            entry = new YamlMapEntry(key, value);
            entriesByKey.put(key, entry);
            return this;
        }
        entry.setRaw(value);
        return this;
    }

    /// {@inheritDoc}
    @Override
    public YamlMap remove(YamlNode key) {
        entriesByKey.remove(key);
        return this;
    }

    /// {@inheritDoc}
    @Override
    public YamlMap remove(String key) {
        for (Map.Entry<YamlNode,YamlMapEntry> entry : entriesByKey.entrySet()) {
            if (entry.getKey() instanceof YamlScalar scalar) {
                if (scalar.asString().equals(key)) {
                    entriesByKey.remove(scalar);
                    return this;
                }
            }
        }
        return this;
    }

    //
    // Convenience Methods
    //

    // /// {@inheritDoc}
    // @Override
    // @Nullable
    // public YamlNode get(String key) {
    //     if (key == null) return null;

    //     for (Map.Entry<YamlNode,YamlMapEntry> entry : entriesByKey.entrySet()) {
    //         YamlMapEntry entryNode = entry.getValue();

    //         YamlNode kNode = entryNode.getKey();
    //         String entryKey = null;
    //         if (kNode instanceof YamlScalar scalar) {
    //             entryKey = scalar.asString();
    //         } else {
    //             entryKey = kNode.toString();
    //         }

    //         if (key.equals(entryKey)) {
    //             YamlNode val = entryNode.getValue();
    //             return (val instanceof YamlAnchor a) ? a.getInnerNode() : val;
    //         }
    //     }
    //     return null;
    // }

    /// {@inheritDoc}
    @Override
    @Nullable
    public YamlMap getMap(Object key) {
        if (get(key) instanceof YamlMap map) {
            return map;
        }
        return null;
    }

    /// {@inheritDoc}
    @Override
    @Nullable
    public YamlSequence getSequence(Object key) {
        if (get(key) instanceof YamlSequence seq) {
            return seq;
        }
        return null;
    }

    @Override
    @Nullable
    public YamlScalar getScalar(Object key) {
        if (get(key) instanceof YamlScalar scalar) {
            return scalar;
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
    public YamlMap put(String key, YamlNode value) {
        for (YamlMapEntry entry : entriesByKey.values()) {
            YamlNode kNode = entry.getKey();
            // Check if the existing key's string value matches the requested key
            if (kNode instanceof YamlScalar scalar && key.equals(scalar.asString())) {
                entry.setRaw(value);
                return this;
            }
        }

        // TODO: Don't pass null as delegate
        // Only if not found, create the new entry
        YamlNode keyNode = new YamlScalar(key, ScalarStyle.UNDETERMINED, null);
        YamlMapEntry entry = new YamlMapEntry(keyNode, value);
        entriesByKey.put(entry.getKey(), entry);
        return this;
    }

    /// {@inheritDoc}
    public YamlMap put(YamlMapEntry entry) {
        entriesByKey.put(entry.getKey(), entry);
        return this;
    }

    /// {@inheritDoc}
    @Override
    public Set<YamlMapEntry> entrySet() {
        return new HashSet<YamlMapEntry>(entriesByKey.values());
    }

    /// {@inheritDoc}
    @Override
    public List<YamlNode> values() {
        return entriesByKey.values().stream().map(YamlMapEntry::getValue).collect(Collectors.toList());
    }

    /// {@inheritDoc}
    @Override
    public YamlMap put(String key, String value) {
        // TODO: This should not be forcing double quotes
        return put(key, new YamlScalar(value));
    }

    @Override
    public void accept(YamlVisitor visitor) {
        visitor.visit(this);
    }

	@Override
	public Iterator<YamlMapEntry> iterator() {
        return entriesByKey.sequencedValues().iterator();
	}
}