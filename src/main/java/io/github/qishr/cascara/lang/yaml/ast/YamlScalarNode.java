package io.github.qishr.cascara.lang.yaml.ast;

import java.util.List;
import java.util.Objects;

import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.lang.yaml.YamlOptions;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;

/// Represents a leaf node in the YAML AST containing a single scalar value.
public class YamlScalarNode extends YamlNode implements ScalarAstNode<YamlNode> {
    // private static final YamlPrimitiveDelegate YAML_PRIMITIVE_DELEGATE = new YamlPrimitiveDelegate();

    private final String lexeme;
    private final String content;
    // private Primitive primitive;



    // TODO: Set this in constructors
    private PrimitiveType schemaType;



    private QuoteStyle quoteStyle = QuoteStyle.UNDETERMINED;
    private ScalarStyle scalarStyle;
    private ChompingStyle chompingStyle;
    // private final ScalarAstDelegate delegate;
    private YamlOptions options;

    // dialect-aware native value cache
    private Object jvmValue;
    private boolean nativeValueCached;

    private String stringValue;
    private boolean stringValueCached = false;

    /// Constructor for use in parsers.
    /// Used when reading raw text from a file stream.
    /// Takes a String and triggers full lexical dialect type inference.
    public YamlScalarNode(int line, int column, PrimitiveType schemaType, String raw, String unescapedContent, QuoteStyle quoteStyle, YamlOptions options) {
        super(line, column);
        this.lexeme = raw;
        // fromString treats the input as text content to be parsed
        // this.primitive = Primitive.fromString(unescapedContent, quoteStyle)
        //     .setDelegate(YAML_PRIMITIVE_DELEGATE);
        this.options = options;
        this.content = unescapedContent;
        this.quoteStyle = quoteStyle;
        this.schemaType = schemaType;
        this.stringValueCached = false;
        this.nativeValueCached = false;
    }

    public YamlScalarNode(Object jvmValue, QuoteStyle quoteStyle, YamlOptions options) {
        super(0, 0);
        this.quoteStyle = quoteStyle;
        this.options = options;


        // TODO:
        // Problem:
        // Serializer set QuoteStyle.PLAIN for a String
        // getPrimitive returns a Double.
        // Serializer does not know what the quote style should be.
        // Serializer does know it's a String
        // Should YamlScalarNode itself determine it's a string from its jvmValue? YES

        // quoteStyle should be treated as a *preference*, and YamlScalarNode should override it if it doesn't suit the type/value.

        this.schemaType = PrimitiveType.of(jvmValue);
        this.lexeme = null;
        this.content = null;

        this.jvmValue = jvmValue;
        this.nativeValueCached = true;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public YamlScalarNode(Object jvmValue, QuoteStyle quoteStyle) {
        this(jvmValue, quoteStyle, YamlOptions.DEFAULT);
        // super( 0, 0);
        // this.lexeme = null;
        // this.content = null;
        // // Pass the object directly into the primitive wrapper
        // // this.primitive = Primitive.of(primitiveValue)
        // //     .setQuoteStyle(quoteStyle)
        // //     .setDelegate(YAML_PRIMITIVE_DELEGATE);
        // this.options = YamlOptions.DEFAULT;
        // this.quoteStyle = quoteStyle;


        // // TODO:
        // this.schemaType = PrimitiveType.of(jvmValue);


        // this.nativeValue = jvmValue;
        // this.nativeValueCached = true;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public YamlScalarNode(Object primitiveValue) {
        this(primitiveValue, QuoteStyle.UNDETERMINED);

        // super(0, 0);
        // this.raw = null;
        // this.content = null; // Cleared cache marks it as dirty for the emitter


        // // // this.primitive = Primitive.of(primitiveValue)
        // // //     .setDelegate(YAML_PRIMITIVE_DELEGATE);
        // // this.quoteStyle = primitive.getQuoteStyle();

        // // TODO: Set quote style here, or later?


        // this.schemaType = PrimitiveType.of(primitiveValue);


        // this.delegate = null;
        // this.nativeValue = primitiveValue;
        // this.nativeValueCached = true;
    }

    /// The default constructor
    public YamlScalarNode() {
        this(null);
        // super( 0, 0);
        // this.raw = null;
        // this.content = null;
        // this.quoteStyle = QuoteStyle.UNDETERMINED;


        // // TODO:
        // this.schemaType = PrimitiveType.ANY;


        // // this.primitive = Primitive.of(null)
        // //     .setDelegate(YAML_PRIMITIVE_DELEGATE);
        // this.delegate = null;
    }

    public PrimitiveType getPrimitiveType() {
        if (schemaType == null || schemaType == PrimitiveType.ANY) {
            schemaType = inferType(lexeme, quoteStyle);
        }
        return schemaType;
    }

    // Updated getter to derive from style
    public boolean isQuoted() {
        return getQuoteStyle() != QuoteStyle.PLAIN;
    }

    /// Gets the quoting style used for this scalar.
    public QuoteStyle getQuoteStyle() {
        if (quoteStyle == QuoteStyle.UNDETERMINED) {
            // TODO: We should get rid of delegate
            quoteStyle = inferQuoteStyle(getPrimitive(), false);
        }
        return quoteStyle;
    }

    /// Sets the quoting style and clears the raw cache.
    public YamlScalarNode setQuoteStyle(QuoteStyle quoteStyle) {
        this.quoteStyle = quoteStyle;
        // this.primitive.setQuoteStyle(quoteStyle);
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

    //
    //
    //

    /// {@inheritDoc}
    /// Scalars are leaf nodes and have no children.
    @Override
    public List<YamlNode> getChildren() {
        return List.of();
    }

    /// Returns the original raw (unescaped) string as seen in the source file.
    public String getLexeme() {
        return lexeme;
        // return (raw != null) ? raw : primitive.asString();
    }

    @Override
    public String getContent() {
        return content;
    }

    /// Returns the dialect-aware JVM value (cached).
    @Override
    public Object getPrimitive() {
        if (nativeValueCached) {
            return jvmValue;
        }

        // if (delegate == null) {
        //     nativeValue = content;
        //     nativeValueCached = true;
        //     return nativeValue;
        // }

        // if (schemaType == PrimitiveType.STRING) {
        //     jvmValue = content; // do not interpret
        //     nativeValueCached = true;
        //     return jvmValue;
        // }


        // TODO: Surely if we're looking for `content`, using `raw` is incorrect?

        // Use content, which is already de-quoted and unescaped by the tokenizer
        // String source = (content != null) ? content : raw;

        jvmValue = parse(content, quoteStyle);
        nativeValueCached = true;
        return jvmValue;
    }

    @Override
    public String asString() {
        if (stringValueCached) return stringValue;

        if (!stringValueCached) {
            // STRING: return logical value (unescaped)
            if (getPrimitiveType() == PrimitiveType.STRING) {
                Object v = getPrimitive(); // unescaped logical value
                stringValue = (v == null) ? null : String.valueOf(v);
            }
            // NUMBER / BOOLEAN / NULL / IDENTIFIER
            else if (content != null) {
                stringValue = content; // lexeme
            }
            // Programmatic node fallback
            else if (lexeme != null) {
                if (lexeme.isEmpty()) {
                    // null
                } else {
                    stringValue = lexeme;
                }
            }
            else {
                Object v = jvmValue;
                stringValue = (v == null) ? null : String.valueOf(v);
            }

            stringValueCached = true;
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

    //
    //
    //

    // TODO: Take anchor into consideration here.
    /// Compares this scalar with another for equality.
    ///
    /// Two scalars are considered equal if they share the same anchor
    /// and logical string value. Source coordinates and quoting styles
    /// are ignored.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YamlScalarNode that)) return false;

        // // Wrong:
        // return Objects.equals(raw, that.raw)
        //     && Objects.equals(content, that.content)
        //     && quoteStyle == that.quoteStyle;

        // TODO: Take anchor into account
        // Correct:
        return Objects.equals(this.asString(), that.asString());
    }

    /// {@inheritDoc}
    @Override
    public int hashCode() {
        return Objects.hash(lexeme, content, quoteStyle);
    }

    /// {@inheritDoc}
    @Override
    public String toString() {
        // return raw != null ? raw : asString();
        return lexeme != null ? lexeme : String.valueOf(getPrimitive());
    }

    // TODO: Rename this to `visit` ?
    @Override
    public void accept(YamlVisitor visitor) {
        visitor.visit(this);
    }




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

            // if (isSpecialNumber(raw)) {
            //     return PrimitiveType.NUMBER;
            // }
            // if (isHexRaw(raw)) {
            //     return PrimitiveType.NUMBER;
            // }
            // if (isOctalRaw(raw)) {
            //     return PrimitiveType.NUMBER;
            // }
            // if (isDecimalNumber(raw)) {
            //     return PrimitiveType.NUMBER;
            // }

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



    // ------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------
    public Object parse(String raw, QuoteStyle quoteStyle) {

        if (schemaType == null || schemaType == PrimitiveType.ANY) {
            schemaType = inferType(raw, quoteStyle);
        }
        // PrimitiveType type = inferType(raw, quoteStyle, isKey);

        switch (schemaType) {
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

    /// YAML single quotes unescape by replacing doubled single quotes with one.
    private String unescapeSingleQuotes(String input) {
        return input == null ? null : input.replace("''", "'");
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