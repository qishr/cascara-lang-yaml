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

import java.util.List;

import io.github.qishr.cascara.lang.yaml.token.YamlToken;

public class YamlAliasNode extends YamlNode {

    // TODO: Should this be `alias` or `name`?
    private final String alias;

    private YamlNode resolvedNode; // This is what the parser needs

    public YamlAliasNode(YamlToken token, String alias) {
        super(token);
        this.alias = alias;
    }

    public String getAlias() { return alias; }

    /// Put this back to fix the Parser
    public void setResolvedNode(YamlNode node) {
        this.resolvedNode = node;
    }

    public YamlNode getResolvedNode() {
        return resolvedNode;
    }

    /// {@inheritDoc}
    @Override
    public List<YamlNode> getChildren() {
        return resolvedNode != null ? List.of(resolvedNode) : List.of();
    }

    /// {@inheritDoc}
    @Override
    public String getAnchor() {
        // In the context of an alias node, the 'anchor' it's interested in
        // is the string name it points to.
        return getAlias();
    }

    @Override
    public void accept(YamlVisitor visitor) {
        visitor.visit(this);
    }
}