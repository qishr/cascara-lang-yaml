package io.github.qishr.cascara.lang.yaml.ast;

import java.util.List;
import java.util.Objects;

import io.github.qishr.cascara.common.lang.QuoteStyle;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.type.Primitive;
import io.github.qishr.cascara.lang.yaml.YamlPrimitiveDelegate;

/// Represents a leaf node in the YAML AST containing a single scalar value.
public class YamlScalarNode extends YamlNode implements ScalarAstNode<YamlNode> {
    private static YamlPrimitiveDelegate YAML_PRIMITIVE_DELEGATE = new YamlPrimitiveDelegate();

    private String raw;
    private Primitive primitive;
    private QuoteStyle quoteStyle;

    /// Constructor for use in parsers.
    /// Used when reading raw text from a file stream.
    /// Takes a String and triggers full lexical dialect type inference.
    public YamlScalarNode(int line, int column, String raw, String unescapedContent, QuoteStyle quoteStyle) {
        super(line, column);
        this.raw = raw;
        // fromString treats the input as text content to be parsed
        this.primitive = Primitive.fromString(unescapedContent, quoteStyle)
            .setDelegate(YAML_PRIMITIVE_DELEGATE);
        this.quoteStyle = quoteStyle;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public YamlScalarNode(Object primitiveValue, QuoteStyle quoteStyle) {
        super( 0, 0);
        this.raw = null; // Cleared cache marks it as dirty for the emitter
        // Pass the object directly into the primitive wrapper
        this.primitive = Primitive.of(primitiveValue)
            .setQuoteStyle(quoteStyle)
            .setDelegate(YAML_PRIMITIVE_DELEGATE);
        this.quoteStyle = quoteStyle;
    }

    /// A programmatic and serializer constructor.
    /// Used when building an AST dynamically in code.
    /// Takes a pre-typed Object and skips text-based type inference.
    public YamlScalarNode(Object primitiveValue) {
        super( 0, 0);
        this.raw = null; // Cleared cache marks it as dirty for the emitter
        this.primitive = Primitive.of(primitiveValue)
            .setDelegate(YAML_PRIMITIVE_DELEGATE);
        this.quoteStyle = primitive.getQuoteStyle();
    }

    /// The default constructor
    public YamlScalarNode() {
        super( 0, 0);
        this.raw = null;
        this.quoteStyle = QuoteStyle.PLAIN;
        // this.primitive = new Primitive(null, QuoteStyle.PLAIN);
        this.primitive = Primitive.of(null)
            .setDelegate(YAML_PRIMITIVE_DELEGATE);
    }

    public static YamlScalarNode fromPrimitive(Primitive primitive) {
        YamlScalarNode node = new YamlScalarNode();
        node.raw = null; // Cleared cache marks it as dirty for the emitter
        node.primitive = primitive;
        node.primitive.setDelegate(YAML_PRIMITIVE_DELEGATE);
        node.quoteStyle = primitive.getQuoteStyle();
        return node;
    }

    /// {@inheritDoc}
    /// Scalars are leaf nodes and have no children.
    @Override
    public List<YamlNode> getChildren() {
        return List.of();
    }

    /// Gets the quoting style used for this scalar.
    public QuoteStyle getQuoteStyle() {
        return quoteStyle;
    }

    // Updated getter to derive from style
    public boolean isQuoted() {
        return quoteStyle != QuoteStyle.PLAIN;
    }

    /// Sets the quoting style and clears the raw cache.
    public void setQuoteStyle(QuoteStyle quoteStyle) {
        this.quoteStyle = quoteStyle;
        // this.primitive.setQuoteStyle(quoteStyle);
    }

    /// Returns the original raw (unescaped) string as seen in the source file.
    public String getRaw() {
        return (raw != null) ? raw : primitive.asString();
    }

    /// {@inheritDoc}
    /// Performs basic type inference to return the most appropriate Java object.
    @Override
    public Object getPrimitive() {
        return primitive != null ? primitive.unwrap() : null;
    }

    @Override
    public void setPrimitive(Object primitive) {
        this.primitive = Primitive.of(primitive)
            .setQuoteStyle(quoteStyle)
            .setDelegate(YAML_PRIMITIVE_DELEGATE);
        this.raw = null; // If the primitive is updated, we no longer have a valid raw value.
    }

    /// {@inheritDoc}
    @Override
    public String asString() {
        return primitive.asString();
    }

    /// {@inheritDoc}
    @Override
    public int asInteger() {
        return asInteger(0);
    }

    /// {@inheritDoc}
    @Override
    public int asInteger(int defaultValue) {
        return primitive.asInteger(defaultValue);
    }

    /// {@inheritDoc}
    @Override
    public double asDouble() {
        return asDouble(0.0);
    }

    /// {@inheritDoc}
    @Override
    public double asDouble(double defaultValue) {
        return primitive.asDouble(defaultValue);
    }

    /// {@inheritDoc}
    @Override
    public boolean asBoolean() {
        return asBoolean(false);
    }

    /// {@inheritDoc}
    @Override
    public boolean asBoolean(boolean defaultValue) {
        return primitive.asBoolean(defaultValue);
    }




    // TODO: Same anchor
    /// Compares this scalar with another for equality.
    ///
    /// Two scalars are considered equal if they share the same anchor
    /// and logical string value. Source coordinates and quoting styles
    /// are ignored.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YamlScalarNode that)) return false;
        return Objects.equals(primitive, that.primitive);
    }

    /// {@inheritDoc}
    @Override
    public int hashCode() {
        return Objects.hash(primitive);
    }

    /// {@inheritDoc}
    @Override
    public String toString() {
        return asString();
    }
}