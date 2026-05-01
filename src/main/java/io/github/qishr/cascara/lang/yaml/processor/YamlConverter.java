package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.common.lang.processor.AstConverter;
import io.github.qishr.cascara.lang.yaml.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;

public class YamlConverter implements AstConverter<YamlNode> {
    @Override
    public ContentType getContentType() {
        return YamlParser.contentType;
    }

    public String toText(AstNode ast) {
        YamlNode yamlNode = fromAst(ast);
        YamlEmitter emitter = new YamlEmitter();
        return emitter.emit(yamlNode);
    }

    public YamlNode fromAst(AstNode ast) {
        if (ast instanceof StructuredDocument astDoc) {
            YamlDocument yamlDoc = new YamlDocument(fromAst(astDoc.getRoot()));
            return yamlDoc;
        } else if (ast instanceof MapAstNode astMap) {
            YamlMapNode yamlMap = new YamlMapNode();
            for (Object entry : astMap.getEntries()) {
                if (entry instanceof MapEntryAstNode astMapEntry) {
                    AstNode astKey = astMapEntry.getKey();
                    AstNode astValue = astMapEntry.getValue();
                    if (astKey instanceof ScalarAstNode astScalarKey) {
                        YamlScalarNode yamlKey = new YamlScalarNode();
                        yamlKey.setValue(astScalarKey.getString());
                        YamlNode yamlValue = fromAst(astValue);
                        yamlMap.put(yamlKey, yamlValue);
                    }
                }
            }
            return yamlMap;
        } else if (ast instanceof SequenceAstNode astSeq) {
            YamlSequenceNode yamlSeq = new YamlSequenceNode();
            for (Object element : astSeq.getElements()) {
                if (element instanceof AstNode astElement) {
                    yamlSeq.add(fromAst(astElement));
                }
            }
            return yamlSeq;
        } else if (ast instanceof ScalarAstNode astScalar) {
            YamlScalarNode yamlScalar = new YamlScalarNode();
            yamlScalar.setPrimitiveValue(astScalar.getPrimitiveValue());
            // yamlScalar.setValue(astScalar.getString());
            return yamlScalar;
        } else {
            System.err.println("Unknown AST node");
            return null;
        }
    }
}
