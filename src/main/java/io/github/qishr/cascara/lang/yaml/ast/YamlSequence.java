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


// YamlSequence.java (Extends YamlNode)
package io.github.qishr.cascara.lang.yaml.ast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

/// Represents a YAML sequence (a list of items).
public class YamlSequence extends YamlNode implements SequenceAstNode<YamlNode> {
    private final List<YamlNode> elements = new ArrayList<>();
    // private NodeStyle style = NodeStyle.BLOCK;
    private boolean isExpanded = false; // Default to compact

    public YamlSequence() {
        // This method intentionally left blank
    }

    public YamlSequence(YamlToken token) {
        super(token);
    }

    /// {@inheritDoc}
    @Override
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /// {@inheritDoc}
    @Override
    public YamlSequence remove(int index) {
        if (index >= 0 && index < elements.size()) {
            elements.remove(index);
        }
        return this;
    }

    /// {@inheritDoc}
    @Override
    public YamlSequence clear() {
        elements.clear();
        return this;
    }

    /// Appends an item to the sequence.
    @Override
    public YamlSequence add(YamlNode item) { elements.add(item); return this; }

    /// {@inheritDoc}
    @Override
    public int size() { return elements.size(); }

    /// {@inheritDoc}
    @Override
    public YamlNode get(int index) { return elements.get(index); }

    @Override
    public YamlNode getFirst() {
        if (elements.isEmpty()) {
            throw new NoSuchElementException();
        }
        return elements.getFirst();
    }

    @Override
    public YamlNode getLast() {
        if (elements.isEmpty()) {
            throw new NoSuchElementException();
        }
        return elements.getLast();
    }

    public YamlMap getMap(int i) {
        if (get(i) instanceof YamlMap map) {
            return map;
        }
        return null;
    }

    public YamlSequence getSequence(int i) {
        if (get(i) instanceof YamlSequence sequence) {
            return sequence;
        }
        return null;
    }

    public YamlScalar getScalar(int i) {
        if (get(i) instanceof YamlScalar scalar) {
            return scalar;
        }
        return null;
    }

    // TODO: getString, getInteger, etc

    public String getString(int i) {
        if (getScalar(i) instanceof YamlScalar scalar) {
            return scalar.asString();
        }
        return null;
    }

    public int getInteger(int i) {
        if (getScalar(i) instanceof YamlScalar scalar) {
            return scalar.asInteger();
        }
        return 0;
    }

    /// {@inheritDoc}
    @Override
    public List<YamlNode> getElements() {
        return elements;
    }

    /// {@inheritDoc}
    @Override
    public YamlSequence remove(YamlNode node) {
        elements.remove(node);
        return this;
    }

    /// {@inheritDoc}
    @Override
    public List<YamlNode> getChildren() { return elements; }

    public boolean isExpanded() { return isExpanded; }
    public void setExpanded(boolean expanded) { this.isExpanded = expanded; }

    /// Returns Iterator instance
    public Iterator<YamlNode> iterator() {
        return new SequenceIterator<YamlNode>(this);
    }

    static class SequenceIterator<T> implements Iterator<YamlNode> {
        YamlSequence list;
        int currentIndex = 0;

        // initialize pointer to head of the list for iteration
        public SequenceIterator(YamlSequence list) {
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

    @Override
    public void accept(YamlVisitor visitor) {
        visitor.visit(this);
    }
}
