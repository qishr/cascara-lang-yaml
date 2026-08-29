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
import io.github.qishr.cascara.lang.yaml.util.ChompingStyle;
import io.github.qishr.cascara.lang.yaml.util.NodeStyle;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;
import io.github.qishr.cascara.lang.yaml.util.YamlVisitor;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;

/// Represents a leaf node in the YAML AST containing a single scalar value.
public class YamlScalar extends YamlNode implements ScalarAstNode<YamlNode> {
    private PrimitiveType primitiveType;

    // dialect-aware native value cache
    private Object jvmValue;
    private boolean isJvmValueCached;

    private String stringValue;
    private boolean isStringValueCached = false;

    private String originalContent;
    private ScalarStyle scalarStyle = ScalarStyle.UNDETERMINED;
    private ChompingStyle chompingStyle;

    /// Constructor for use in parsers.
    /// Used when reading raw text from a file stream.
    /// Takes a token and triggers full lexical dialect type
    /// inference from the token's content.
    public YamlScalar(
        YamlToken token,
        PrimitiveType primitiveType,
        YamlOptions options
    ) {
        super(token, options);
        this.originalContent = token.getContent();
        this.primitiveType = primitiveType;
        this.scalarStyle = token.getScalarStyle();

        nodeStyle = (scalarStyle == ScalarStyle.LITERAL || scalarStyle == ScalarStyle.FOLDED)
            ? NodeStyle.BLOCK
            : NodeStyle.FLOW;
    }

    /// Constructor for use in parsers.
    /// Used when content is not identical to the token's content
    /// Takes a token and a string and triggers full lexical
    /// dialect type inference from the string.
    public YamlScalar(
        YamlToken token,
        String content,
        PrimitiveType primitiveType,
        ScalarStyle scalarStyle,
        YamlOptions options
    ) {
        super(token, options);
        this.originalContent = content;
        this.primitiveType = primitiveType;
        this.scalarStyle = scalarStyle;

        nodeStyle = (scalarStyle == ScalarStyle.LITERAL || scalarStyle == ScalarStyle.FOLDED)
            ? NodeStyle.BLOCK
            : NodeStyle.FLOW;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public YamlScalar(
        Object jvmValue,
        ScalarStyle scalarStyle,
        YamlOptions options
    ) {
        super(options);
        this.primitiveType = PrimitiveType.of(jvmValue);
        this.scalarStyle = scalarStyle;
        this.jvmValue = jvmValue;
        this.isJvmValueCached = true;

        nodeStyle = (scalarStyle == ScalarStyle.LITERAL || scalarStyle == ScalarStyle.FOLDED)
            ? NodeStyle.BLOCK
            : NodeStyle.FLOW;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public YamlScalar(Object jvmValue, ScalarStyle scalarStyle) {
        this(jvmValue, scalarStyle, YamlOptions.DEFAULT);
        nodeStyle = (scalarStyle == ScalarStyle.LITERAL || scalarStyle == ScalarStyle.FOLDED)
            ? NodeStyle.BLOCK
            : NodeStyle.FLOW;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public YamlScalar(Object jvmValue) {
        this(jvmValue, ScalarStyle.UNDETERMINED);
    }

    /// The default constructor
    public YamlScalar() {
        this(null);
    }

    public YamlOptions getOptions() {
        return options;
    }

    @Override
    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    // Derive quotedness from quote style
    public boolean isQuoted() {
        return getQuoteStyle() != QuoteStyle.PLAIN;
    }

    public ScalarStyle getScalarStyle() {
        if (scalarStyle == ScalarStyle.UNDETERMINED) {
            scalarStyle = inferQuoteStyle(getPrimitive());
        }
        return scalarStyle;
    }

    public YamlScalar setScalarStyle(ScalarStyle scalarStyle) {
        this.scalarStyle = scalarStyle;
        nodeStyle =  (scalarStyle == ScalarStyle.LITERAL || scalarStyle == ScalarStyle.FOLDED)
            ? NodeStyle.BLOCK
            : NodeStyle.FLOW;
        return this;
    }

    /// Gets the quoting style used for this scalar.
    @Override
    public QuoteStyle getQuoteStyle() {
        return switch (getScalarStyle()) {
            case DOUBLE_QUOTED -> QuoteStyle.DOUBLE;
            case SINGLE_QUOTED -> QuoteStyle.SINGLE;
            default -> QuoteStyle.PLAIN;
        };
    }

    /// Sets the quoting style.
    @Override
    public YamlScalar setQuoteStyle(QuoteStyle quoteStyle) {
        setScalarStyle(switch (quoteStyle) {
            case DOUBLE -> ScalarStyle.DOUBLE_QUOTED;
            case SINGLE -> ScalarStyle.SINGLE_QUOTED;
            case PLAIN -> ScalarStyle.PLAIN;
            case UNDETERMINED -> ScalarStyle.UNDETERMINED;
        });
        return this;
    }

    public ChompingStyle getChompingStyle() {
        return chompingStyle;
    }

    public YamlScalar setChompingStyle(ChompingStyle style) {
        this.chompingStyle = style;
        return this;
    }

    /// Returns the original raw (unescaped) string as seen in the source file.
    public String getLexeme() {
        return primitiveType == PrimitiveType.NULL
            ? ""
            : token == null ? null : token.getLexeme();
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
        jvmValue = parse(originalContent, scalarStyle);
        isJvmValueCached = true;
        return jvmValue;
    }

    @Override
    public String asString() {
        if (!isStringValueCached) {
            if (primitiveType != PrimitiveType.NULL) {
                if (token == null) {
                    if (primitiveType == PrimitiveType.NUMBER) {
                        Number number = (Number) jvmValue;
                        if (number.intValue() == number.doubleValue()) {
                            stringValue = String.valueOf(number.intValue());
                        } else {
                            stringValue = String.valueOf(number.doubleValue());
                        }
                    } else {
                        stringValue = (jvmValue == null) ? null : String.valueOf(jvmValue);
                    }
                } else {
                    stringValue = unescape(originalContent, scalarStyle);
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

    /// Compares this scalar with another for equality.
    ///
    /// https://yaml.org/spec/1.2.2/#3213-node-comparison
    ///
    /// Two nodes must have the same tag and content to be equal. Since each tag applies to exactly one kind, this implies that the two nodes must have the same kind to be equal.
    ///
    /// Two scalars are equal only when their tags and canonical forms are equal character-by-character. Equality of collections is defined recursively.
    /// {@inheritDoc}
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YamlScalar that)) return false;

        return Objects.equals(this.asString(), that.asString()) &&
               Objects.equals(this.getTag(), that.getTag());
    }

    /// {@inheritDoc}
    @Override
    public int hashCode() {
        return Objects.hash(asString(), getTag());
    }

    /// {@inheritDoc}
    @Override
    public String toString() {
        if (getStartLine() > 0) {
            return this.getClass().getSimpleName() + " [" + asString() + "] at " + getStartLine() + ":" + getStartColumn();
        } else {
            return this.getClass().getSimpleName();
        }
    }

    //
    //
    //

    public static ScalarStyle inferQuoteStyle(Object value) {
        if (value == null) {
            return ScalarStyle.PLAIN;
        }

        if (value instanceof CharSequence cs) {
            String str = cs.toString();

            // 1. Empty strings must be quoted
            if (str.isEmpty()) {
                return ScalarStyle.DOUBLE_QUOTED;
            }

            // 2. If it matches a YAML boolean, null, or numeric literal, it MUST be quoted
            //    to force it to remain a string.
            String lowered = str.trim().toLowerCase();
            if (lowered.equals("true") || lowered.equals("yes") || lowered.equals("on") ||
                lowered.equals("false") || lowered.equals("no") || lowered.equals("off") ||
                lowered.equals("null") || lowered.equals("~")) {
                return ScalarStyle.DOUBLE_QUOTED;
            }

            // Check if it's a numeric literal that needs quoting
            try {
                Double.parseDouble(str);
                return ScalarStyle.DOUBLE_QUOTED;
            } catch (NumberFormatException ignored) {}

            // 3. Check for leading/trailing whitespace or special structural YAML indicators
            char first = str.charAt(0);
            if (Character.isWhitespace(first) || Character.isWhitespace(str.charAt(str.length() - 1))) {
                return ScalarStyle.DOUBLE_QUOTED;
            }

            // YAML indicators that break plain scalars if present anywhere or at the start
            if (first == '-' || first == '?' || first == ':' || first == ',' ||
                first == '[' || first == ']' || first == '{' || first == '}' ||
                first == '#' || first == '&' || first == '*' || first == '!' ||
                first == '|' || first == '>' || first == '\'' || first == '"' ||
                first == '%' || first == '@' || first == '_') {
                return ScalarStyle.DOUBLE_QUOTED;
            }

            // Containment checks for inline structural indicators
            if (str.contains(": ") || str.contains(" #")) {
                return ScalarStyle.DOUBLE_QUOTED;
            }

            return ScalarStyle.PLAIN;
        }

        if (value instanceof Character) {
            char ch = (Character) value;
            // Quote if it's a structural control character or whitespace
            if (Character.isWhitespace(ch) || ch == ':' || ch == ',' || ch == '-' || ch == '#' || ch == '\'' || ch == '"') {
                return ScalarStyle.DOUBLE_QUOTED;
            }
            return ScalarStyle.PLAIN;
        }

        return ScalarStyle.PLAIN;
    }

    public PrimitiveType inferType(String raw, ScalarStyle quoteStyle) {
        // raw should never be null here, right? Is this needed?
        if (raw == null) {
            return PrimitiveType.NULL;
        }

        // Plain scalars
        if (quoteStyle == ScalarStyle.PLAIN) {

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
        if (isHexRaw(s)) return PrimitiveType.INTEGER;
        if (isOctalRaw(s)) return PrimitiveType.INTEGER;

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

    // Parsing
    public Object parse(String raw, ScalarStyle quoteStyle) {

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

    // Unescaping
    public String unescape(String text, ScalarStyle style) {
        if (style == ScalarStyle.DOUBLE_QUOTED) {
            return unescapeDoubleQuotes(text);
        } else if (style == ScalarStyle.SINGLE_QUOTED) {
            return unescapeSingleQuotes(text);
        }
        return text;
    }

    /// Simple unescaper for double-quoted YAML strings
    private String unescapeDoubleQuotes(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int len = s.length();

        while (i < len) {
            char c = s.charAt(i);

            if (c != '\\' || i + 1 >= len) {
                out.append(c);
                i++;
                continue;
            }

            char esc = s.charAt(i + 1);

            // --- Standard escapes ---
            switch (esc) {
                case 'n':  out.append('\n'); i += 2; continue;
                case 't':  out.append('\t'); i += 2; continue;
                // case '\t':  out.append('\t'); i += 2; continue;
                case 'r':  out.append('\r'); i += 2; continue;
                case 'b':  out.append('\b'); i += 2; continue;
                // case 'f':  out.append('\f'); i += 2; continue;
                case '\\': out.append('\\'); i += 2; continue;
                case '"':  out.append('"');  i += 2; continue;
                // case '\'': out.append('\''); i += 2; continue;
            }

            // --- \\uXXXX ---
            if (esc == 'u' && i + 5 < len) {
                int code = 0;
                boolean ok = true;
                for (int j = i + 2; j < i + 6; j++) {
                    int d = Character.digit(s.charAt(j), 16);
                    if (d < 0) { ok = false; break; }
                    code = (code << 4) | d;
                }
                if (ok) {
                    out.append((char) code);
                    i += 6;
                    continue;
                }
            }

            // --- \xXX ---
            if (esc == 'x' && i + 3 < len) {
                int d1 = Character.digit(s.charAt(i + 2), 16);
                int d2 = Character.digit(s.charAt(i + 3), 16);
                if (d1 >= 0 && d2 >= 0) {
                    out.append((char) ((d1 << 4) | d2));
                    i += 4;
                    continue;
                }
            }

            // In YAML, if the backslash didn't escape anything, we discard it.
            i++;
        }
        return out.toString();
    }


    private String unescapeSingleQuotes(String input) {
        if (input == null) return null;
        return input.replace("''", "'");
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

    // Number parsing (fast, correct)
    private double parseNumber(String raw) {
        if (isHexRaw(raw)) {
            String digits = raw.substring(2);
            return Integer.parseUnsignedInt(digits, 16);
        }

        if (isOctalRaw(raw)) {
            String digits = raw.substring(2);
            return Integer.parseUnsignedInt(digits, 8);
        }

        if (raw.equals("Infinity")) return Double.POSITIVE_INFINITY;
        if (raw.equals("-Infinity")) return Double.NEGATIVE_INFINITY;
        if (raw.equals("NaN")) return Double.NaN;

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