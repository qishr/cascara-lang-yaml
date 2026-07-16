package io.github.qishr.cascara.lang.yaml.processor;

import java.io.InputStream;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.common.lang.processor.AbstractSerializer;
import io.github.qishr.cascara.common.lang.processor.AstParser;
import io.github.qishr.cascara.common.lang.type.TypeReference;
import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.lang.yaml.YamlOptions;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;

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

    @Override
    public YamlSerializer setParser(AstParser<YamlNode,?> parser) {
        if (!(parser instanceof YamlAstParser YamlAstParser)) {
            throw new SerializerException(GenericDiagnosticCode.ERROR, "Parser must be a YamlAstParser");
        }
        this.parser = YamlAstParser;
        return this;
    }

    @Override
    public String toText(Object jvmInstance) {
        // Step 1: Object -> AST
        YamlNode ast = toAst(jvmInstance);
        // Step 2: AST -> String
        return new YamlEmitter().setOptions(options).emit(ast);
    }

    @Override
    public YamlNode toAst(Object jvmInstance) {
        return serialize(jvmInstance);
    }

    @Override
    public <C> C fromText(String text, Class<C> jvmType) {
        // Step 1: String -> AST
        YamlNode ast = getParser().parse(text);
        // Step 2: AST -> Object
        return fromAst(ast, jvmType);
    }

    @Override
    public <C> C fromText(String text, TypeReference<C> typeRef) {
        // Step 1: String -> AST
        YamlNode ast = getParser().parse(text);
        // Step 2: AST -> Object
        return fromAst(ast, typeRef);
    }

    @Override
    public <C> C fromStream(InputStream is, Class<C> jvmType) {
        // Step 1: String -> AST
        YamlNode ast = getParser().parse(is);
        // Step 2: AST -> Object
        return fromAst(ast, jvmType);
    }

    @Override
    public <C> C fromStream(InputStream is, TypeReference<C> typeRef) {
        // Step 1: String -> AST
        YamlNode ast = getParser().parse(is);
        // Step 2: AST -> Object
        return fromAst(ast, typeRef);
    }

    @Override
    public <C> C fromAst(YamlNode astNode, Class<C> jvmType) {
        return (C) deserialize(astNode, jvmType);
    }

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

    @Override
    protected YamlNode serializeKey(Object key) {
        return serialize(key);
    }
}