package io.github.qishr.cascara.lang.yaml.ast;

import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.DirectiveType;

public class YamlTagDirective extends YamlDirective {
    private String name;
    private String value;

    public YamlTagDirective(YamlToken token, DirectiveType type, String content, String name, String value) {
        super(token, type, content);
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
