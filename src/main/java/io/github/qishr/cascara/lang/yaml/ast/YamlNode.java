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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.qishr.cascara.common.diagnostic.Diagnostic;
import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

/// Base implementation for all YAML AST nodes.
///
/// This class provides the foundational structure for YAML nodes, including
/// source coordinates (line and column), the source URI, and support for
/// YAML anchors and comments.
public abstract class YamlNode implements AstNode {
    private final int startLine;
    private final int startColumn;
    private final int endLine = 0;
    private final int endColumn = 0;
    private List<YamlComment> comments = null;
    private String anchor;
    private String tag;
    protected YamlToken token;
    protected YamlOptions options;
    protected NodeStyle nodeStyle;

    protected YamlNode() {
        startLine = 0;
        startColumn = 0;
    }

    public abstract void accept(YamlVisitor visitor);

    /// Constructs a new YamlNode with specific source coordinates
    /// obtained from a YamLToken
    ///
    /// @param token The YAML token.
    protected YamlNode(YamlToken token) {
        this(token, YamlOptions.DEFAULT);
    }

    /// Constructs a new YamlNode with specific source coordinates
    /// obtained from a YamLToken and a set of YAML options.
    ///
    /// @param token   The YAML token.
    /// @param options The YAML options.
    protected YamlNode(YamlToken token, YamlOptions options) {
        int line = token == null ? Diagnostic.UNKNOWN_COORD : token.getStartLine();
        int column = token == null ? Diagnostic.UNKNOWN_COORD : token.getStartColumn();
        this(line, column, options);
        this.token = token;
    }

    /// Constructs a new YamlNode with specific source coordinates
    /// and a set of YAML options.
    ///
    /// @param line    The line number (1-based).
    /// @param column  The column number (1-based).
    /// @param options The YAML options.
    protected YamlNode(int line, int column, YamlOptions options) {
        this.startLine = line;
        this.startColumn = column;
        this.options = options;
    }

    public YamlOptions getOptions() {
        return options;
    }

    public NodeStyle getNodeStyle() {
        return nodeStyle;
    }

    public YamlNode setNodeStyle(NodeStyle nodeStyle) {
        this.nodeStyle = nodeStyle;
        return this;
    }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    /// Gets the YAML anchor associated with this node (e.g., &anchorName).
    ///
    /// @return The anchor string, or `null` if no anchor is defined.
    public String getAnchor() { return anchor; }

    /// Sets the YAML anchor for this node.
    ///
    /// @param anchor The anchor string to associate with this node.
    public void setAnchor(String anchor) { this.anchor = anchor; }

    /// {@inheritDoc}
    ///
    /// Implementation-specific nodes must return their constituent children.
    /// For example, a Map node returns its entries.
    @Override
    public abstract List<? extends YamlNode> getChildren();

    /// {@inheritDoc}
    @Override
    public int getStartLine() { return startLine; }

    /// {@inheritDoc}
    @Override
    public int getStartColumn() { return startColumn; }

    /// {@inheritDoc}
    @Override
    public int getEndLine() { return endLine; }

    /// {@inheritDoc}
    @Override
    public int getEndColumn() { return endColumn; }

    /// {@inheritDoc}
    @Override
    public List<YamlComment> getComments() {
        if (comments == null) {
            comments = new ArrayList<>();
        }
        return comments;
    }

    /// Associates a comment node with this specific AST node.
    ///
    /// @param comment The comment node to add.
    public void addComment(YamlComment comment) {
        if (this.comments == null) {
            this.comments = new ArrayList<>();
        }
        this.comments.add(comment);
    }

    /// Associates a comment node with this specific AST node.
    ///
    /// @param comment The comment node to add.
    public void addComments(int pos, List<YamlComment> comments) {
        if (this.comments == null) {
            this.comments = new ArrayList<>();
        }
        this.comments.addAll(pos, comments);
    }

    /// {@inheritDoc}
    @Override
    @Nullable
    public YamlToken getToken() { return token; }

    public void setToken(YamlToken token) { this.token = token; }

    /// Compares this node with another for equality based on its content.
    ///
    /// Note: Source coordinates (line and column) are intentionally excluded
    /// from equality checks to allow programmatically created nodes to match
    /// parsed nodes during map lookups.
    ///
    /// @param o The object to compare with.
    /// @return `true` if the nodes represent logically equivalent data.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YamlNode other)) return false;

        return Objects.equals(anchor, other.anchor) &&
               Objects.equals(getChildren(), other.getChildren());
    }

    /// Generates a hash code based on the node's logical content.
    ///
    /// @return The hash code.
    @Override
    public int hashCode() {
        return Objects.hash(anchor, getChildren());
    }
}
