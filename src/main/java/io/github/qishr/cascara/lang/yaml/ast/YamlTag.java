package io.github.qishr.cascara.lang.yaml.ast;

import java.util.Objects;

import io.github.qishr.cascara.lang.yaml.token.YamlToken;

public class YamlTag extends YamlNodeProperty {
    private String rawValue;
    private String resolvedValue;

    private String handle = "";
    private String handleName = "";
    private String suffix = "";


	public YamlTag(YamlToken token, String handle, String handleName, String suffix) {
		super(token);
        this.rawValue = token.getContent();
        this.handle = handle;
        this.handleName = handleName;
        this.suffix = suffix;
	}

	public YamlTag(YamlToken token) {
		super(token);
        rawValue = token.getContent();
	}

	public YamlTag() {
		super();
	}

    public String getRawValue() {
        return rawValue;
    }

    public String getResolvedValue() {
        return resolvedValue;
    }

    public YamlTag setResolvedValue(String resolved) {
        this.resolvedValue = resolved;
        return this;
    }

    public String getHandle() {
        return handle;
    }

    public String getHandleName() {
        return handleName;
    }

    public String getSuffix() {
        return suffix;
    }

    /// {@inheritDoc}
    @Override
    public String asString() {
        return rawValue;
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
