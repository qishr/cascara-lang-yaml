package io.github.qishr.cascara.lang.yaml.ast;

import java.net.URI;
import java.util.List;

public class YamlErrorNode extends YamlNode {
    private final String message;

    public YamlErrorNode(URI uri, int line, int column, String message) {
        super(uri, line, column);
        this.message = message;
    }

    @Override
    public List<? extends YamlNode> getChildren() {
        return List.of();
    }

    public String getMessage() { return message; }
}
