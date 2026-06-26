package io.github.qishr.cascara.lang.yaml.ast;

import java.util.List;

public class YamlAnchorNode extends YamlNode {
    private final String anchorName;
    private final YamlNode innerNode;

    public YamlAnchorNode(int line, int column, String name, YamlNode node) {
        super(line, column);
        this.anchorName = name;
        this.innerNode = node;
        this.setAnchor(name);
        // Also ensure the inner node knows it's anchored
        if (node != null) {
            node.setAnchor(name);
        }
    }

    public String getAnchorName() { return anchorName; }
    public YamlNode getInnerNode() { return innerNode; }

    /// {@inheritDoc}
    @Override
    public List<YamlNode> getChildren() { return List.of(innerNode); }

    /// {@inheritDoc}
    @Override
    public String asString() {
        return innerNode == null ? "" : innerNode.toString();
    }

}