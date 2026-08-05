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
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNodeFactory;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

/// Standard implementation for YAML serialization.
public class YamlSerializer extends AbstractSerializer<YamlSerializer,YamlNode,YamlScalar,YamlSequence,YamlMap,YamlMapEntry,YamlNode> {

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
    public YamlSerializer setParser(AstParser<YamlNode,?,?> parser) {
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