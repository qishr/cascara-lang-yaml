package io.github.qishr.cascara.lang.yaml;

import io.github.qishr.cascara.common.lang.type.ScalarAstDelegate;
import io.github.qishr.cascara.common.lang.type.SchemaType;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;

public class YamlScalarDelegate implements ScalarAstDelegate {
    private YamlOptions options;

    public YamlScalarDelegate(YamlOptions options) {
        this.options = options;
    }

    // ------------------------------------------------------------
    // Type inference
    // ------------------------------------------------------------
    @Override
    public SchemaType inferType(String raw, QuoteStyle quoteStyle, boolean isKey) {

        // raw should never be null here, right? Is this needed?
        if (raw == null) {
            return SchemaType.NULL;
        }


        // Quoted strings
        if (quoteStyle == QuoteStyle.DOUBLE) {
            return SchemaType.STRING;
        }
        if (quoteStyle == QuoteStyle.SINGLE) {
            return SchemaType.STRING;
        }

        // Plain scalars
        if (quoteStyle == QuoteStyle.PLAIN) {

            // Boolean
            if ("true".equals(raw) ||
                "false".equals(raw) ||
                "yes".equals(raw) ||
                "no".equals(raw) ||
                "on".equals(raw) ||
                "off".equals(raw)
            ) {
                return SchemaType.BOOLEAN;
            }

            // Null
            if ("null".equals(raw) || raw.isBlank()) {
                return SchemaType.NULL;
            }

            if (isSpecialNumber(raw)) {
                return SchemaType.NUMBER;
            }
            if (isHexRaw(raw)) {
                return SchemaType.NUMBER;
            }
            if (isOctalRaw(raw)) {
                return SchemaType.NUMBER;
            }
            if (isDecimalNumber(raw)) {
                return SchemaType.NUMBER;
            }

            return SchemaType.STRING;
        }

        return SchemaType.STRING;
    }

    // ------------------------------------------------------------
    // Quote style inference
    // ------------------------------------------------------------
    // @Override
    // public QuoteStyle inferQuoteStyle(Object scalar, boolean isKey) {

    //     // TODO: Look at data to see if it needs quoted?

    //     // // Keys: strict JSON always double-quoted
    //     // if (isKey && !options.allowUnquotedKeys()) {
    //     //     return QuoteStyle.DOUBLE;
    //     // }

    //     // // JSON5-like: single quotes allowed
    //     // if (scalar instanceof String && options.allowSingleQuotedStrings()) {
    //     //     return QuoteStyle.SINGLE;
    //     // }

    //     return QuoteStyle.DOUBLE;
    // }

    @Override
    public QuoteStyle inferQuoteStyle(Object value, boolean isKey) {
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


    // ------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------
    @Override
    public Object parse(String raw, QuoteStyle quoteStyle, boolean isKey) {

        SchemaType type = inferType(raw, quoteStyle, isKey);

        switch (type) {
            case BOOLEAN:
                return "true".equals(raw);

            case NULL:
                return null;

            case NUMBER:
                return parseNumber(raw);

            case STRING:
            default:
                return unescape(raw, quoteStyle, isKey);
        }
    }

    // ------------------------------------------------------------
    // Unescaping
    // ------------------------------------------------------------

    @Override
    public String unescape(String text, QuoteStyle style, boolean isKey) {
        System.out.println("Debug: unescape " + text);

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

    // ------------------------------------------------------------
    // JVM conversions
    // ------------------------------------------------------------
    @Override
    public boolean toBoolean(Object scalar) {
        String s = String.valueOf(scalar);
        if (s.equals("true") || s.equals("yes") || s.equals("on")) return Boolean.TRUE;
        if (s.equals("false") || s.equals("no") || s.equals("off")) return Boolean.FALSE;
        throw new IllegalArgumentException("Not a boolean: " + s);
    }

    @Override
    public int toInteger(Object scalar) {
        return (int) toDouble(scalar);
    }

    @Override
    public long toLong(Object scalar) {
        return (long) toDouble(scalar);
    }

    @Override
    public float toFloat(Object scalar) {
        return (float) toDouble(scalar);
    }

    @Override
    public double toDouble(Object scalar) {
        return Double.parseDouble(String.valueOf(scalar));
    }

    @Override
    public int toIntegerOrDefault(Object scalar, int defaultValue) {
        try {
            return toInteger(scalar);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public long toLongOrDefault(Object scalar, long defaultValue) {
        try {
            return toLong(scalar);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public float toFloatOrDefault(Object scalar, float defaultValue) {
        try {
            return toFloat(scalar);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public double toDoubleOrDefault(Object scalar, double defaultValue) {
        try {
            return toDouble(scalar);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public boolean toBooleanOrDefault(Object scalar, boolean defaultValue) {
        try {
            return toBoolean(scalar);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    //
    //
    //

    // ------------------------------------------------------------
    // Internal helpers (fast, no regex, no startsWith)
    // ------------------------------------------------------------
    private boolean isIdentifier(String raw) {
        int len = raw.length();
        if (len == 0) return false;

        char c = raw.charAt(0);
        if (!((c >= 'A' && c <= 'Z') ||
              (c >= 'a' && c <= 'z') ||
              c == '_')) {
            return false;
        }

        for (int i = 1; i < len; i++) {
            c = raw.charAt(i);
            if (!((c >= 'A' && c <= 'Z') ||
                  (c >= 'a' && c <= 'z') ||
                  (c >= '0' && c <= '9') ||
                  c == '_')) {
                return false;
            }
        }

        return true;
    }

    private boolean isSpecialNumber(String raw) {
        return raw.equals("Infinity") ||
               raw.equals("-Infinity") ||
               raw.equals("NaN");
    }

    private boolean isHexRaw(String raw) {
        return raw.length() > 2 &&
               raw.charAt(0) == '0' &&
               (raw.charAt(1) == 'x' || raw.charAt(1) == 'X');
    }

    private boolean isOctalRaw(String raw) {
        return raw.length() > 2 &&
               raw.charAt(0) == '0' &&
               (raw.charAt(1) == 'o' || raw.charAt(1) == 'O');
    }

    private boolean isDecimalNumber(String s) {
        int len = s.length();
        if (len == 0) return false;

        int i = 0;
        char c = s.charAt(0);

        // optional sign
        if (c == '-' || c == '+') {
            if (len == 1) return false;
            i = 1;
        }

        boolean hasDigit = false;
        boolean hasDot = false;

        for (; i < len; i++) {
            c = s.charAt(i);

            if (c >= '0' && c <= '9') {
                hasDigit = true;
                continue;
            }

            if (c == '.') {
                if (hasDot) return false;
                hasDot = true;
                continue;
            }

            if (c == 'e' || c == 'E') {
                return isScientific(s, i);
            }

            return false;
        }

        return hasDigit;
    }

    private boolean isScientific(String s, int ePos) {
        int len = s.length();
        if (ePos == 0 || ePos == len - 1) return false;

        int i = ePos + 1;
        char c = s.charAt(i);

        // optional sign
        if (c == '+' || c == '-') {
            i++;
            if (i == len) return false;
        }

        boolean hasDigit = false;

        for (; i < len; i++) {
            c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
                continue;
            }
            return false;
        }

        return hasDigit;
    }

    // ------------------------------------------------------------
    // Number parsing (fast, correct)
    // ------------------------------------------------------------
    private double parseNumber(String raw) {

        // // JSON5 hex
        // if (options.allowHexadecimalNumbers() && isHexRaw(raw)) {
        if (isHexRaw(raw)) {
            String digits = raw.substring(2);
            return Integer.parseUnsignedInt(digits, 16);
        }

        // // JSON5 octal
        // if (options.allowHexadecimalNumbers() && isOctalRaw(raw)) {
        if (isOctalRaw(raw)) {
            String digits = raw.substring(2);
            return Integer.parseUnsignedInt(digits, 8);
        }

        // // JSON5 special numbers
        // if (options.allowInfinityAndNaN()) {
            if (raw.equals("Infinity")) return Double.POSITIVE_INFINITY;
            if (raw.equals("-Infinity")) return Double.NEGATIVE_INFINITY;
            if (raw.equals("NaN")) return Double.NaN;
        // }

        // JSON strict / JSON5 decimal
        return Double.parseDouble(raw);
    }

    public YamlOptions getOptions() {
        return options;
    }
}
