package io.github.qishr.cascara.lang.yaml.ast;

import java.util.List;

import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.YamlVisitor;

public class YamlNodeProperty extends YamlNode {

    public YamlNodeProperty() {
        super();
    }

    public YamlNodeProperty(YamlToken token) {
        super(token, null);
    }

	@Override
	public void accept(YamlVisitor visitor) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'accept'");
	}

	@Override
	public List<? extends YamlNode> getChildren() {
        return List.of();
	}

}
