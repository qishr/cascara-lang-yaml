package io.github.qishr.cascara.lang.yaml.ast;

import java.net.URI;
import java.util.List;

public class YamlAnchorNode extends YamlNode {
    private final String anchorName;
    private final YamlNode innerNode;

    public YamlAnchorNode(URI uri, int line, int column, String name, YamlNode node) {
        super(uri, line, column);
        this.anchorName = name;
        this.innerNode = node;
        // This is the missing link!
        this.setAnchor(name);
        // Also ensure the inner node knows it's anchored
        if (node != null) {
            node.setAnchor(name);
        }
    }

    public String getAnchorName() { return anchorName; }
    public YamlNode getInnerNode() { return innerNode; }

    @Override public List<YamlNode> getChildren() { return List.of(innerNode); }

    @Override
    public String asString() {
        return innerNode == null ? "" : innerNode.toString();
    }

}