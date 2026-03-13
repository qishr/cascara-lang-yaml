package io.github.qishr.cascara.lang.yaml;

import io.github.qishr.cascara.common.lang.LanguageOptions;

public class YamlOptions extends LanguageOptions<YamlOptions> {
    private boolean allowUnicode = true;
    private boolean explicitStart = false; // Writes '---' if true
    private boolean expandedStyle = false;
    private boolean strict = false;

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

    public boolean isAllowUnicode() { return allowUnicode; }
    public boolean isExplicitStart() { return explicitStart; }
    public boolean isExpandedStyle() { return expandedStyle; }
    public boolean isStrict() { return strict; }
}