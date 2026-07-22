// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


package io.github.qishr.cascara.lang.yaml.ast;

import java.util.List;
import java.util.Objects;

import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;

/// Represents a leaf node in the YAML AST containing a single scalar value.
public class YamlScalarNode extends YamlNode implements ScalarAstNode<YamlNode> {
    // private final String lexeme;
    // private final String content;

    private PrimitiveType primitiveType;
    private QuoteStyle quoteStyle = QuoteStyle.UNDETERMINED;

    // dialect-aware native value cache
    private Object jvmValue;
    private boolean isJvmValueCached;

    private String stringValue;
    private boolean isStringValueCached = false;

    private String originalContent;
    private ScalarStyle scalarStyle;
    private ChompingStyle chompingStyle;

    private YamlOptions options;

    /// Constructor for use in parsers.
    /// Used when reading raw text from a file stream.
    /// Takes a token and triggers full lexical dialect type
    ///  inference from the token's content.
    public YamlScalarNode(
        YamlToken token,
        PrimitiveType primitiveType,
        YamlOptions options
    ) {
        super(token);
        this.originalContent = token.getContent();
        this.primitiveType = primitiveType;
        this.quoteStyle = token.getQuoteStyle();
        this.options = (options == null) ? YamlOptions.DEFAULT : options;
    }

    /// Constructor for use in parsers.
    /// Used when content is not identical to the token's content
    /// Takes a token and a string and triggers full lexical
    /// dialect type inference from the string.
    public YamlScalarNode(
        YamlToken token,
        String content,
        PrimitiveType primitiveType,
        QuoteStyle quoteStyle,
        YamlOptions options
    ) {
        super(token);
        this.originalContent = content;
        this.primitiveType = primitiveType;
        this.quoteStyle = quoteStyle;
        this.options = (options == null) ? YamlOptions.DEFAULT : options;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public YamlScalarNode(
        Object jvmValue,
        QuoteStyle quoteStyle,
        YamlOptions options
    ) {
        super();
        this.primitiveType = PrimitiveType.of(jvmValue);
        this.quoteStyle = quoteStyle;
        this.options = (options == null) ? YamlOptions.DEFAULT : options;
        this.jvmValue = jvmValue;
        this.isJvmValueCached = true;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public YamlScalarNode(Object jvmValue, QuoteStyle quoteStyle) {
        this(jvmValue, quoteStyle, YamlOptions.DEFAULT);
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public YamlScalarNode(Object jvmValue) {
        this(jvmValue, QuoteStyle.UNDETERMINED);
    }

    /// The default constructor
    public YamlScalarNode() {
        this(null);
    }

    public YamlOptions getOptions() {
        return options;
    }

    // TODO: Add to interface
    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    // Derive quotedness from quote style
    public boolean isQuoted() {
        return getQuoteStyle() != QuoteStyle.PLAIN;
    }

    /// Gets the quoting style used for this scalar.
    @Override
    public QuoteStyle getQuoteStyle() {
        if (quoteStyle == QuoteStyle.UNDETERMINED) {
            quoteStyle = inferQuoteStyle(getPrimitive(), false);
        }
        return quoteStyle;
    }

    /// Sets the quoting style and clears the raw cache.
    @Override
    public YamlScalarNode setQuoteStyle(QuoteStyle quoteStyle) {
        this.quoteStyle = quoteStyle;
        return this;
    }

    public ScalarStyle getScalarStyle() {
        return scalarStyle;
    }

    public YamlScalarNode setScalarStyle(ScalarStyle style) {
        this.scalarStyle = style;
        return this;
    }

    public ChompingStyle getChompingStyle() {
        return chompingStyle;
    }

    public YamlScalarNode setChompingStyle(ChompingStyle style) {
        this.chompingStyle = style;
        return this;
    }

    /// Returns the original raw (unescaped) string as seen in the source file.
    public String getLexeme() {
        return token == null ? null : token.getLexeme();
    }

    @Override
    public String getContent() {
        return token == null ? asString() : originalContent;
    }

    /// Returns the dialect-aware JVM value (cached).
    @Override
    public Object getPrimitive() {
        if (isJvmValueCached) {
            return jvmValue;
        }
        jvmValue = parse(originalContent, quoteStyle);
        isJvmValueCached = true;
        return jvmValue;
    }

    @Override
    public String asString() {
        if (!isStringValueCached) {
            if (primitiveType != PrimitiveType.NULL) {
                if (token == null) {
                    stringValue = (jvmValue == null) ? null : String.valueOf(jvmValue);
                } else {
                    stringValue = unescape(originalContent, quoteStyle);
                }
            }
            isStringValueCached = true;
        }
        return stringValue;
    }

    /// {@inheritDoc}
    @Override
    public int asInteger() {
        return asInteger(0);
    }

    /// {@inheritDoc}
    @Override
    public int asInteger(int defaultValue) {
        Object v = getPrimitive();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /// {@inheritDoc}
    @Override
    public double asDouble() {
        return asDouble(0.0);
    }

    /// {@inheritDoc}
    @Override
    public double asDouble(double defaultValue) {
        Object v = getPrimitive();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /// {@inheritDoc}
    @Override
    public boolean asBoolean() {
        return asBoolean(false);
    }

    /// {@inheritDoc}
    @Override
    public boolean asBoolean(boolean defaultValue) {
        Object v = getPrimitive();
        if (v instanceof Boolean b) return b;
        if (v == null) return defaultValue;
        String s = String.valueOf(v);
        if (s.equals("true") || s.equals("yes") || s.equals("on")) return true;
        if (s.equals("false") || s.equals("no") || s.equals("off")) return false;
        return defaultValue;
    }

    /// {@inheritDoc}
    /// Scalars are leaf nodes and have no children.
    @Override
    public List<YamlNode> getChildren() {
        return List.of();
    }

    // TODO: Rename this to `visit` ?
    @Override
    public void accept(YamlVisitor visitor) {
        visitor.visit(this);
    }

    //
    //
    //

    // TODO: Take anchor into consideration here.
    /// Compares this scalar with another for equality.
    ///
    /// Two scalars are considered equal if they share the same anchor
    /// and logical string value. Source coordinates and quoting styles
    /// are ignored.
    /// {@inheritDoc}
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YamlScalarNode that)) return false;

        // TODO: Take anchor into account
        return Objects.equals(this.asString(), that.asString());
    }

    /// {@inheritDoc}
    @Override
    public int hashCode() {
        return Objects.hash(getLexeme(), getContent(), quoteStyle);
    }

    // /// {@inheritDoc}
    // @Override
    // public String toString() {
    //     return asString();
    //     // return lexeme != null ? lexeme : String.valueOf(getPrimitive());
    // }

    //
    //
    //

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





    public PrimitiveType inferType(String raw, QuoteStyle quoteStyle) {

        // raw should never be null here, right? Is this needed?
        if (raw == null) {
            return PrimitiveType.NULL;
        }


        // Quoted strings
        if (quoteStyle == QuoteStyle.DOUBLE) {
            return PrimitiveType.STRING;
        }
        if (quoteStyle == QuoteStyle.SINGLE) {
            return PrimitiveType.STRING;
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
                return PrimitiveType.BOOLEAN;
            }

            // Null
            if ("null".equals(raw) || raw.isBlank()) {
                return PrimitiveType.NULL;
            }

            PrimitiveType numberType = inferNumberType(raw);
            if (numberType != null) {
                return numberType;
            }

            return PrimitiveType.STRING;
        }

        return PrimitiveType.STRING;
    }

    private PrimitiveType inferNumberType(String s) {
        int len = s.length();
        if (len == 0) return null;

        int i = 0;
        char c = s.charAt(0);

        // optional sign
        if (c == '-' || c == '+') {
            if (len == 1) return null;
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
                if (hasDot) return null;
                hasDot = true;
                continue;
            }

            if (c == 'e' || c == 'E') {
                if (isScientific(s, i)) {
                    return PrimitiveType.NUMBER;
                }
            }

            return null;
        }

        return hasDigit ? (hasDot ? PrimitiveType.NUMBER : PrimitiveType.INTEGER) : null;
    }

    // ------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------
    public Object parse(String raw, QuoteStyle quoteStyle) {

        if (primitiveType == null || primitiveType == PrimitiveType.ANY) {
            primitiveType = inferType(raw, quoteStyle);
        }

        switch (primitiveType) {
            case BOOLEAN:
                return "true".equals(raw);

            case NULL:
                return null;

            case INTEGER:
                return parseInteger(raw);

            case NUMBER:
                return parseNumber(raw);

            case STRING:
            default:
                return unescape(raw, quoteStyle);
        }
    }

    // ------------------------------------------------------------
    // Unescaping
    // ------------------------------------------------------------

    public String unescape(String text, QuoteStyle style) {
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

    private String unescapeSingleQuotes(String input) {
        if (input == null) return null;
        return input.replace("''", "'");
                    // .replace("\\\\", "\\")
                    // .replace("\\n", "\n")
                    // .replace("\\r", "\r");
    }

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

    private long parseInteger(String raw) {
        if (isHexRaw(raw)) {
            String digits = raw.substring(2);
            return Integer.parseUnsignedInt(digits, 16);
        }

        if (isOctalRaw(raw)) {
            String digits = raw.substring(2);
            return Integer.parseUnsignedInt(digits, 8);
        }

        return Long.parseLong(raw);
    }

}