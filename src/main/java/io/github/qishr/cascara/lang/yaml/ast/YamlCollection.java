package io.github.qishr.cascara.lang.yaml.ast;

import io.github.qishr.cascara.lang.yaml.util.NodeStyle;

public interface YamlCollection {
    boolean isEmpty();
    int size();
    NodeStyle getNodeStyle();
    int getStartLine();
    int getStartColumn();
    int getSemanticColumn();
}
