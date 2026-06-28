package io.github.qishr.cascara.lang.yaml.ast;

/// Defines a visitor pattern interface for traversing the Cascara YAML AST hierarchy.
public interface YamlVisitor {
    // Stream and Document containers
    void visit(YamlStreamNode node);
    void visit(YamlDocumentNode node);
    void visit(YamlDirectiveNode node);

    // Standard structural nodes
    void visit(YamlMapNode node);
    void visit(YamlMapEntryNode node);
    void visit(YamlSequenceNode node);
    void visit(YamlScalarNode node);
    void visit(YamlAliasNode node);
    void visit(YamlCommentNode node);
    void visit(YamlAnchorNode node);
}