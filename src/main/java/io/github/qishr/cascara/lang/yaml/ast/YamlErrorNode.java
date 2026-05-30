package io.github.qishr.cascara.lang.yaml.ast;

import java.net.URI;
import java.util.List;

public class YamlErrorNode extends YamlNode {
    private final String message;

    public YamlErrorNode(int line, int column, URI uri, String message) {
        super(line, column, uri);
        this.message = message;
    }

    @Override
    public List<? extends YamlNode> getChildren() {
        return List.of();
    }

    public String getMessage() { return message; }
}
