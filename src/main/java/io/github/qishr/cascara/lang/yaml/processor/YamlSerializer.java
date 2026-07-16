package io.github.qishr.cascara.lang.yaml.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.common.lang.processor.AbstractSerializer;
import io.github.qishr.cascara.common.lang.processor.AstParser;
import io.github.qishr.cascara.common.lang.type.TypeReference;
import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

/// Standard implementation for YAML serialization.
public class YamlSerializer extends AbstractSerializer<YamlSerializer,YamlNode,YamlScalarNode,YamlSequenceNode,YamlMapNode,YamlMapEntryNode,YamlNode> {

    private YamlAstParser parser;
    private YamlOptions options = new YamlOptions();
    private Reporter reporter = new NoOpReporter();

    public YamlSerializer() {
        super(AbstractYamlProcessor.YAML_CONTENT_TYPE_STRING, new YamlNodeFactory(), new YamlOptions());
    }

    @Override
    public YamlSerializer self() {
        return this;
    }

    /// {@inheritDoc}
    @Override
    public ContentType getContentType() {
        return AbstractYamlProcessor.YAML_CONTENT_TYPE;
    }

    /// {@inheritDoc}
    @Override
    public YamlSerializer setReporter(Reporter reporter) {
        this.reporter = reporter;
        return this;
    }

    /// {@inheritDoc}
    @Override
    public YamlSerializer setOptions(LanguageOptions<?> options) {
        this.options = (YamlOptions) options;
        super.setOptions(options);
        // super.setDelegate(new YamlScalarDelegate((YamlOptions)options));
        return this;
    }

    //
    // Serializer Implementation
    //

    /// {@inheritDoc}
    @Override
    public YamlSerializer setParser(AstParser<YamlNode,?> parser) {
        if (!(parser instanceof YamlAstParser YamlAstParser)) {
            throw new SerializerException(GenericDiagnosticCode.ERROR, "Parser must be a YamlAstParser");
        }
        this.parser = YamlAstParser;
        return this;
    }

    /// {@inheritDoc}
    @Override
    public String toText(Object jvmInstance) {
        // Step 1: Object -> AST
        YamlNode ast = toAst(jvmInstance);
        // Step 2: AST -> String
        return new YamlEmitter().setOptions(options).emit(ast);
    }

    /// {@inheritDoc}
    @Override
    public void toWriter(Object jvmInstance, Writer writer) throws IOException {
        YamlNode ast = toAst(jvmInstance);
        String text = new YamlEmitter().setOptions(options).emit(ast);
        writer.write(text);
    }

    /// {@inheritDoc}
    @Override
    public YamlNode toAst(Object jvmInstance) {
        return serialize(jvmInstance);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromText(String text, Class<C> jvmType) {
        // Step 1: String -> AST
        YamlNode ast = getParser().parse(text);
        // Step 2: AST -> Object
        return fromAst(ast, jvmType);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromText(String text, TypeReference<C> typeRef) {
        // Step 1: String -> AST
        YamlNode ast = getParser().parse(text);
        // Step 2: AST -> Object
        return fromAst(ast, typeRef);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromReader(Reader reader, Class<C> jvmType) {
        YamlNode ast = getParser().parse(reader);
        return fromAst(ast, jvmType);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromReader(Reader reader, TypeReference<C> typeRef) {
        YamlNode ast = getParser().parse(reader);
        return fromAst(ast, typeRef);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromStream(InputStream is, Class<C> jvmType) {
        // Step 1: String -> AST
        YamlNode ast = getParser().parse(is);
        // Step 2: AST -> Object
        return fromAst(ast, jvmType);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromStream(InputStream is, TypeReference<C> typeRef) {
        // Step 1: String -> AST
        YamlNode ast = getParser().parse(is);
        // Step 2: AST -> Object
        return fromAst(ast, typeRef);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromAst(YamlNode astNode, Class<C> jvmType) {
        return (C) deserialize(astNode, jvmType);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromAst(YamlNode astNode, TypeReference<C> typeRef) {
        return (C) deserialize(astNode, typeRef);
    }

    private YamlAstParser getParser() {
        if (parser == null) {
            parser = new YamlAstParser();
            parser.setReporter(reporter);
        }
        return parser;
    }

    /// {@inheritDoc}
    @Override
    protected YamlNode serializeKey(Object key) {
        return serialize(key);
    }
}