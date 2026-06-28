package io.github.qishr.cascara.lang.yaml.ast;

import java.util.List;

public class YamlDirectiveNode extends YamlNode {
    private final String content;

    public YamlDirectiveNode(int line, int column, String content) {
        super(line, column);
        this.content = content;
    }

    public String getContent() {
        return content;
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