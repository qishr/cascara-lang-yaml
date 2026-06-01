package io.github.qishr.cascara.lang.yaml;

import io.github.qishr.cascara.common.lang.AbstractPrimitive;
import io.github.qishr.cascara.common.lang.QuoteStyle;

public class YamlPrimitive extends AbstractPrimitive {

    public YamlPrimitive(Object primitive, QuoteStyle quoteStyle) {
        super(primitive, quoteStyle);
    }

    @Override
    protected Object coerceLiteralValue(String text) {
        String lowered = text.trim().toLowerCase();
        if (lowered.equals("true") || lowered.equals("yes") || lowered.equals("on")) return Boolean.TRUE;
        if (lowered.equals("false") || lowered.equals("no") || lowered.equals("off")) return Boolean.FALSE;
        if (lowered.equals("null") || lowered.equals("~")) return null;

        return null;
    }

    /// Overrides the hook from AbstractPrimitive to handle YAML-specific string formatting
    @Override
    protected String unescapeQuotedString(String text, QuoteStyle style) {
        if (style == QuoteStyle.DOUBLE) {
            return unescapeDoubleQuotes(text);
        } else if (style == QuoteStyle.SINGLE) {
            return unescapeSingleQuotes(text);
        }
        return text;
    }

    /// Simple unescaper for double-quoted YAML strings
    private String unescapeDoubleQuotes(String input) {
        if (input == null) return null;
        return input.replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r");
    }

    /// YAML single quotes unescape by replacing doubled single quotes with one.
    private String unescapeSingleQuotes(String input) {
        return input == null ? null : input.replace("''", "'");
    }
}
