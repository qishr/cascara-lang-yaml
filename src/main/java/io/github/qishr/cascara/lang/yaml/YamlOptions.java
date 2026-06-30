package io.github.qishr.cascara.lang.yaml;

import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.util.Duplicable;

public class YamlOptions extends LanguageOptions<YamlOptions> implements Duplicable<YamlOptions> {
    private boolean allowUnicode = true;
    private boolean explicitStart = false; // Writes '---' if true
    private boolean expandedStyle = false;
    private boolean strict = false;
    private boolean includeComments = false;
    private boolean multiDocument = false;

    /// Sets whether unicode characters are allowed in scalars.
    public YamlOptions setAllowUnicode(boolean val) {
        this.allowUnicode = val;
        return this;
    }

    /// Sets whether to always output the '---' document start marker.
    public YamlOptions setExplicitStart(boolean val) {
        this.explicitStart = val;
        return this;
    }

    public YamlOptions setExpandedStyle(boolean val) {
        this.expandedStyle = val;
        return this;
    }

    public YamlOptions setStrict(boolean val) {
        this.strict = val;
        return this;
    }

    public YamlOptions setIncludeComments(boolean val) {
        this.includeComments = val;
        return this;
    }

    public YamlOptions setMultiDocument(boolean val) {
        this.multiDocument = val;
        return this;
    }

    public boolean isAllowUnicode() { return allowUnicode; }
    public boolean isExplicitStart() { return explicitStart; }
    public boolean isExpandedStyle() { return expandedStyle; }
    public boolean isStrict() { return strict; }
    public boolean isIncludeComments() { return includeComments; }
    public boolean isMultiDocument() { return multiDocument; }

    @Override
    public YamlOptions duplicate() {
        return new YamlOptions()
            .setAllowUnicode(allowUnicode)
            .setExpandedStyle(expandedStyle)
            .setExplicitStart(explicitStart)
            .setIncludeComments(includeComments)
            .setIndentSize(indentSize)
            .setMultiDocument(multiDocument)
            .setStrict(strict);
    }
}