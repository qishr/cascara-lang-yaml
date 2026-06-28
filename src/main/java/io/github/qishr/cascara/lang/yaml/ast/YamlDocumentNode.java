package io.github.qishr.cascara.lang.yaml.ast;

import java.util.ArrayList;
import java.util.List;

public class YamlDocumentNode extends YamlNode {
    private final List<YamlDirectiveNode> directives = new ArrayList<>();
    private YamlNode body;

    public YamlDocumentNode(int line, int column) {
        super(line, column);
    }

    public void addDirective(YamlDirectiveNode directive) {
        if (directive != null) {
            this.directives.add(directive);
        }
    }

    public List<YamlDirectiveNode> getDirectives() {
        return directives;
    }

    public YamlNode getBody() {
        return body;
    }

    public void setBody(YamlNode body) {
        this.body = body;
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