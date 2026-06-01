package io.github.qishr.cascara.lang.yaml.ast;

import java.net.URI;
import java.util.List;

public class YamlAliasNode extends YamlNode {
    private final String alias;
    private YamlNode resolvedNode; // This is what the parser needs

    public YamlAliasNode(URI uri, int line, int column, String alias) {
        super(uri, line, column);
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

    @Override public List<YamlNode> getChildren() {
        return resolvedNode != null ? List.of(resolvedNode) : List.of();
    }

    @Override
    public String getAnchor() {
        // In the context of an alias node, the 'anchor' it's interested in
        // is the string name it points to.
        return getAlias();
    }
}