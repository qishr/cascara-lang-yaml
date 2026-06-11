package io.github.qishr.cascara.lang.yaml.ast;

import java.util.List;

import io.github.qishr.cascara.common.lang.ast.CommentAstNode;

/// Represents a comment within the YAML source.
public class YamlCommentNode extends YamlNode implements CommentAstNode {
    private final String text;
    private final boolean multiLine;

    public YamlCommentNode(int line, int column, String text, boolean multiLine) {
        super(line, column);
        this.text = text;
        this.multiLine = multiLine;
    }

    /// {@inheritDoc}
    @Override public String asString() { return text; }

    /// {@inheritDoc}
    @Override public boolean isMultiLine() { return multiLine; }

    /// {@inheritDoc}
    @Override public List<YamlNode> getChildren() { return List.of(); }

    @Override
    public String getRaw() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRawValue'");
    }
}