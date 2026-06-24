package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.LanguageOptions;
import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.common.lang.processor.AbstractSerializer;
import io.github.qishr.cascara.common.lang.processor.Parser;
import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.lang.yaml.YamlOptions;
import io.github.qishr.cascara.lang.yaml.YamlPrimitiveDelegate;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;

/// Standard implementation for YAML serialization.
public class YamlSerializer extends AbstractSerializer<YamlSerializer,YamlNode,YamlScalarNode,YamlSequenceNode,YamlMapNode,YamlMapEntryNode> {

    private YamlParser parser;
    private YamlOptions options = new YamlOptions();
    private Reporter reporter = new NoOpReporter();

    public YamlSerializer() {
        super(AbstractYamlProcessor.YAML_CONTENT_TYPE_STRING, new YamlFactory(), new YamlPrimitiveDelegate());
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
        return this;
    }

    //
    // Serializer Implementation
    //

    @Override
    public YamlSerializer setParser(Parser<YamlNode,?> parser) {
        if (!(parser instanceof YamlParser yamlParser)) {
            throw new SerializerException(GenericDiagnosticCode.ERROR, "Parser must be a YamlParser");
        }
        this.parser = yamlParser;
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
    public <C> C fromText(String text, Class<C> jvmType) {
        // Step 1: String -> AST
        YamlNode ast = getParser().parse(text);
        // Step 2: AST -> Object
        return fromAst(ast, jvmType);
    }

    @Override
    public YamlNode toAst(Object jvmInstance) {
        return serialize(jvmInstance);
    }

    @Override
    public <C> C fromAst(YamlNode astNode, Class<C> jvmType) {
        return (C) deserialize(astNode, jvmType);
    }

    private YamlParser getParser() {
        if (parser == null) {
            parser = new YamlParser();
            parser.setReporter(reporter);
        }
        return parser;
    }
}