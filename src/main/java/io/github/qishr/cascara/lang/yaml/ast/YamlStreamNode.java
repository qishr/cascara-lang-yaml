package io.github.qishr.cascara.lang.yaml.ast;

import java.util.ArrayList;
import java.util.List;

public class YamlStreamNode extends YamlNode {
    private final List<YamlDocumentNode> documents = new ArrayList<>();
    private final List<YamlCommentNode> comments = new ArrayList<>();

    public YamlStreamNode() {
        super(1, 1);
    }

    public YamlStreamNode(int line, int column) {
        super(line, column);
    }

    public void addDocument(YamlDocumentNode document) {
        if (document != null) {
            this.documents.add(document);
        }
    }

    public List<YamlDocumentNode> getDocuments() {
        return documents;
    }

    public List<YamlCommentNode> getComments() {
        return comments;
    }

    public boolean isEmpty() {
        return documents.isEmpty();
    }

    @Override
    public void accept(YamlVisitor visitor) {
        visitor.visit(this);
    }

	@Override
	public List<? extends YamlNode> getChildren() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getChildren'");
	}
}