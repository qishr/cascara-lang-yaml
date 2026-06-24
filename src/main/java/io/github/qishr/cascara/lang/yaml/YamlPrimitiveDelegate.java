package io.github.qishr.cascara.lang.yaml;

import io.github.qishr.cascara.common.lang.QuoteStyle;
import io.github.qishr.cascara.common.lang.type.PrimitiveDelegate;

public class YamlPrimitiveDelegate implements PrimitiveDelegate {
    @Override
    public Object coerceLiteralValue(String text) {
        String lowered = text.trim().toLowerCase();
        if (lowered.equals("true") || lowered.equals("yes") || lowered.equals("on")) return Boolean.TRUE;
        if (lowered.equals("false") || lowered.equals("no") || lowered.equals("off")) return Boolean.FALSE;
        if (lowered.equals("null") || lowered.equals("~")) return null;
        return null;
    }

    // @Override
    // public QuoteStyle inferQuoteStyle(Object value) {
    //     QuoteStyle style = QuoteStyle.PLAIN;
    //     if (value instanceof CharSequence || value instanceof Character) {
    //         style = QuoteStyle.DOUBLE;
    //     }
    //     return style;
    // }

    @Override
    public QuoteStyle inferQuoteStyle(Object value) {
        if (value == null) {
            return QuoteStyle.PLAIN;
        }

        if (value instanceof CharSequence cs) {
            String str = cs.toString();

            // 1. Empty strings must be quoted
            if (str.isEmpty()) {
                return QuoteStyle.DOUBLE;
            }

            // 2. If it matches a YAML boolean, null, or numeric literal, it MUST be quoted
            //    to force it to remain a string.
            String lowered = str.trim().toLowerCase();
            if (lowered.equals("true") || lowered.equals("yes") || lowered.equals("on") ||
                lowered.equals("false") || lowered.equals("no") || lowered.equals("off") ||
                lowered.equals("null") || lowered.equals("~")) {
                return QuoteStyle.DOUBLE;
            }

            // Check if it's a numeric literal that needs quoting
            try {
                Double.parseDouble(str);
                return QuoteStyle.DOUBLE;
            } catch (NumberFormatException ignored) {}

            // 3. Check for leading/trailing whitespace or special structural YAML indicators
            char first = str.charAt(0);
            if (Character.isWhitespace(first) || Character.isWhitespace(str.charAt(str.length() - 1))) {
                return QuoteStyle.DOUBLE;
            }

            // YAML indicators that break plain scalars if present anywhere or at the start
            if (first == '-' || first == '?' || first == ':' || first == ',' ||
                first == '[' || first == ']' || first == '{' || first == '}' ||
                first == '#' || first == '&' || first == '*' || first == '!' ||
                first == '|' || first == '>' || first == '\'' || first == '"' ||
                first == '%' || first == '@' || first == '_') {
                return QuoteStyle.DOUBLE;
            }

            // Containment checks for inline structural indicators
            if (str.contains(": ") || str.contains(" #")) {
                return QuoteStyle.DOUBLE;
            }

            return QuoteStyle.PLAIN;
        }

        if (value instanceof Character) {
            char ch = (Character) value;
            // Quote if it's a structural control character or whitespace
            if (Character.isWhitespace(ch) || ch == ':' || ch == ',' || ch == '-' || ch == '#' || ch == '\'' || ch == '"') {
                return QuoteStyle.DOUBLE;
            }
            return QuoteStyle.PLAIN;
        }

        return QuoteStyle.PLAIN;
    }

    /// Overrides the hook from AbstractPrimitive to handle YAML-specific string formatting
    @Override
    public String unescapeQuotedString(String text, QuoteStyle style) {
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
