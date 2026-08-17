package io.github.qishr.cascara.lang.yaml.ast;

import java.util.Objects;

import io.github.qishr.cascara.lang.yaml.token.YamlToken;

public class YamlTag extends YamlNodeProperty {

    private String content;
    private String resolved;

	public YamlTag(String content) {
		super();
        this.content = content;
	}

	public YamlTag(YamlToken token) {
		super(token);
        content = token.getContent();
	}

    public String getContent() {
        return content;
    }

    public String getResolved() {
        return resolved;
    }

    public YamlTag setResolved(String resolved) {
        this.resolved = resolved;
        return this;
    }

    /// {@inheritDoc}
    @Override
    public String asString() {
        return content;
    }

    /// {@inheritDoc}
    @Override
    public String toString() {
        return asString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YamlAnchor that)) return false;

        return Objects.equals(this.asString(), that.asString());
    }

    /// {@inheritDoc}
    @Override
    public int hashCode() {
        return Objects.hash(asString());
    }

}
