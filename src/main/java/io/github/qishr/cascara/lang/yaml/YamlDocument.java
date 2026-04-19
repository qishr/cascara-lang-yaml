package io.github.qishr.cascara.lang.yaml;

import java.net.URI;
import java.util.List;

import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.ast.QuoteStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;

/// The root node of a YAML document.
public class YamlDocument extends YamlNode implements StructuredDocument {
    private YamlNode root;
    private URI schemaUri = null;

    public YamlDocument(YamlNode root) {
        super(root.getStartLine(), root.getStartColumn(), root.getOriginUri());
        this.root = root;
    }

    /// {@inheritDoc}
    @Override public List<YamlNode> getChildren() { return List.of(root); }

    //
    // StructuredDocument Implementation
    //

    @Override public URI getSchemaUri() {
        return schemaUri;
    }

    /// Returns the primary content node of the document.
    @Override public YamlNode getRoot() { return root; }

    //
    // Convenience Methods
    //

    public YamlNode get(String key) {
        if (root instanceof YamlMapNode map) {
            return map.get(key);
        }
        return new YamlScalarNode("", QuoteStyle.DOUBLE);
    }

    public YamlMapNode getMap(String key) {
        if (root instanceof YamlMapNode map) {
            return map.getMap(key);
        }
        return new YamlMapNode();
    }

    public YamlSequenceNode getSequence(String key) {
        if (root instanceof YamlMapNode map) {
            return map.getSequence(key);
        }
        return new YamlSequenceNode();
    }

    public String getString(String key) {
        if (root instanceof YamlMapNode map) {
            return map.getString(key);
        }
        return null;
    }

    public void setRoot(YamlNode node) {
        root = node;
    }
}