package io.github.qishr.cascara.lang.yaml.ast;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;

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
    private final URI originUri;
    private final List<CommentAstNode> comments = new ArrayList<>();
    private String anchor;

    protected YamlNode() {
        startLine = 0;
        startColumn = 0;
        originUri = null;
    }

    /// Constructs a new YamlNode with specific source coordinates.
    ///
    /// @param line   The 1-based line number in the source document.
    /// @param column The 1-based column number in the source document.
    /// @param uri    The URI of the source document.
    protected YamlNode(int line, int column, URI uri) {
        this.startLine = line;
        this.startColumn = column;
        this.originUri = uri;
    }

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
    @Override public int getStartLine() { return startLine; }

    /// {@inheritDoc}
    @Override public int getStartColumn() { return startColumn; }

    /// {@inheritDoc}
    @Override public int getEndLine() { return endLine; }

    /// {@inheritDoc}
    @Override public int getEndColumn() { return endColumn; }

    /// {@inheritDoc}
    @Override public URI getOriginUri() { return originUri; }

    /// {@inheritDoc}
    @Override public List<CommentAstNode> getComments() { return comments; }

    /// Associates a comment node with this specific AST node.
    ///
    /// @param comment The comment node to add.
    public void addComment(CommentAstNode comment) {
        this.comments.add(comment);
    }

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
