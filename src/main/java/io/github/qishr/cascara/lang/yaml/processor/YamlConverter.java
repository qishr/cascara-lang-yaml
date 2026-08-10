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

import io.github.qishr.cascara.common.diagnostic.code.LangDiagnosticCode;
import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.common.lang.plain.PlainMapNode;
import io.github.qishr.cascara.common.lang.plain.PlainNode;
import io.github.qishr.cascara.common.lang.plain.PlainScalarNode;
import io.github.qishr.cascara.common.lang.plain.PlainSequenceNode;
import io.github.qishr.cascara.common.lang.processor.AstConverter;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.exception.YamlConverterException;

public class YamlConverter extends AbstractYamlProcessor<YamlConverter> implements AstConverter<YamlNode> {
    @Override protected YamlConverter self() { return this; }

    public String toText(AstNode ast) {
        YamlNode yamlNode = fromAst(ast);
        YamlEmitter emitter = new YamlEmitter();
        return emitter.emit(yamlNode);
    }

    @Nullable
    public YamlNode fromAst(AstNode ast) {
        if (ast == null) return null;

        if (ast instanceof MapAstNode astMap) {
            YamlMap yamlMap = new YamlMap();
            for (Object entry : astMap.getEntries()) {
                // TODO: MapEntryAstNode should probably have a method of obtaining a string key
                if (entry instanceof MapEntryAstNode astMapEntry) {
                    if (astMapEntry.getKey() instanceof AstNode astKey) {
                        AstNode astValue = astMapEntry.getValue();
                        if (astKey instanceof ScalarAstNode astScalarKey) {
                            YamlScalar yamlKey = new YamlScalar(astScalarKey.asString());
                            YamlNode yamlValue = fromAst(astValue);
                            yamlMap.put(yamlKey, yamlValue);
                        }
                    } else if (astMapEntry.getKey() instanceof String stringKey) {
                        AstNode astValue = astMapEntry.getValue();
                        YamlScalar yamlKey = new YamlScalar(stringKey);
                        YamlNode yamlValue = fromAst(astValue);
                        yamlMap.put(yamlKey, yamlValue);
                    }
                }
            }
            return yamlMap;
        } else if (ast instanceof SequenceAstNode astSeq) {
            YamlSequence yamlSeq = new YamlSequence();
            for (Object element : astSeq.getElements()) {
                if (element instanceof AstNode astElement) {
                    yamlSeq.add(fromAst(astElement));
                }
            }
            return yamlSeq;
        } else if (ast instanceof ScalarAstNode astScalar) {
            YamlScalar yamlScalar = new YamlScalar(astScalar.getPrimitive());
            return yamlScalar;
        } else {
            String name = (ast == null) ? "null" : ast.getClass().getSimpleName();
            throw new YamlConverterException(LangDiagnosticCode.UNKNOWN_NODE_TYPE, name);
        }
    }

    @Nullable
    public PlainNode toPlainAst(YamlNode yaml) {
        if (yaml == null) return null;

        if (yaml instanceof YamlMap map) {
            PlainMapNode out = new PlainMapNode();
            for (YamlMapEntry e : map.getEntries()) {
                PlainNode key = toPlainAst(e.getKey());
                PlainNode val = toPlainAst(e.getValue());
                out.put(key, val);
            }
            return out;
        }

        if (yaml instanceof YamlSequence seq) {
            PlainSequenceNode out = new PlainSequenceNode();
            for (YamlNode child : seq.getChildren()) {
                out.add(toPlainAst(child));
            }
            return out;
        }

        if (yaml instanceof YamlScalar scalar) {
            return convertScalar(scalar);   // tag logic goes here
        }

        throw new YamlConverterException(
            LangDiagnosticCode.UNKNOWN_NODE_TYPE,
            yaml.getClass().getSimpleName()
        );
    }

    @Nullable
    private PlainScalarNode convertScalar(YamlScalar scalar) {
        if (scalar == null) return null;

        String tag = scalar.getTag();

        if (tag == null) {
            // Untagged: if double-quoted, treat as string
            if (scalar.getQuoteStyle() == QuoteStyle.DOUBLE) {
                return new PlainScalarNode(normalizeString(scalar));
            }
            return new PlainScalarNode(scalar.getPrimitive());
        }
        Object value = scalar.getPrimitive();
        return new PlainScalarNode(value);
    }

    private String normalizeString(YamlScalar scalar) {
        if (scalar.getPrimitiveType() == PrimitiveType.NULL) {
            return "";
        }
        String s = scalar.asString();
        return s;
    }
}
