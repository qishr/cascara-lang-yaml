package io.github.qishr.cascara.lang.yaml.ast;

import java.net.URI;
import java.util.List;

public class YamlAnchorNode extends YamlNode {
    private final String anchorName;
    private final YamlNode innerNode;

    public YamlAnchorNode(int line, int column, URI uri, String name, YamlNode node) {
        super(line, column, uri);
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
    public String getString() {
        return innerNode == null ? "" : innerNode.toString();
    }

}