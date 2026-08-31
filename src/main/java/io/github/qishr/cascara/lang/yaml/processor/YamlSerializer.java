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

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.common.lang.processor.AbstractSerializer;
import io.github.qishr.cascara.common.lang.processor.AstParser;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;
import io.github.qishr.cascara.common.lang.type.TypeReference;
import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.common.util.TermUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlComment;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNodeProperty;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
import io.github.qishr.cascara.lang.yaml.diagnostic.YamlDiagnosticCode;
import io.github.qishr.cascara.lang.yaml.diagnostic.YamlParserException;
import io.github.qishr.cascara.lang.yaml.internal.AbstractYamlProcessor;
import io.github.qishr.cascara.lang.yaml.internal.DebugUtils;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.CommentStyle;
import io.github.qishr.cascara.lang.yaml.util.NodeStyle;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;
import io.github.qishr.cascara.lang.yaml.util.YamlNodeFactory;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

/// Standard implementation for YAML serialization.
public class YamlSerializer extends AbstractSerializer<YamlSerializer,YamlNode,YamlScalar,YamlSequence,YamlMap,YamlMapEntry,YamlNode> {
    private static final String NEWLINE = "\n";
    private static final String SPACE = " ";
    private static final String COMMA = ",";
    private static final String SINGLE_QUOTE = "'";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String KEY_INDICATOR = "?";
    private static final String VALUE_INDICATOR = ":";
    private static final String ITEM_INDICATOR = "-";
    private static final String COMMENT_INDICATOR = "#";
    private static final String DOCUMENT_START_MARKER = "---";
    private static final String DOCUMENT_END_MARKER = "...";

    private StringBuilder string;
    private Writer writer;
    private YamlAstParser parser;
    private YamlOptions options = new YamlOptions();

    // Options
    private int indentSize;
    private int indicatorIndentSize;
    private boolean outputResolvedAliases;
    private boolean outputComments;
    private boolean outputExpandedStyle;
    private boolean forceExplicitNull;
    private boolean debugStringBuilder = true;
    private boolean alwaysEndWithNewLine;
    private boolean retainFormatting;

    // State
    private boolean isSynthetic;
    private int indentSpaces;
    private boolean atStartOfLine;
    private boolean preceededByWhitespace;
    private int originalLineNumber;
    private int previousEmissionLineNumber;
    private YamlNode previousNode;
    private YamlNode rootNode;
    private boolean newLineAlreadyEmitted;

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
    public String toString(Object jvmInstance) {
        if (jvmInstance instanceof YamlNode node) {
            rootNode = node;
        } else {
            rootNode = toAst(jvmInstance);
        }
        string = new StringBuilder();
        setupEmitter();
        emitRootNode(rootNode);
        emitTrailingComments(rootNode);
        emitFileEnding();
        return string.toString();
    }

    /// {@inheritDoc}
    @Override
    public void toWriter(Object jvmInstance, Writer writer) throws IOException {
        if (jvmInstance instanceof YamlNode node) {
            rootNode = node;
        } else {
            rootNode = toAst(jvmInstance);
        }
        this.writer = writer;
        setupEmitter();
        emitRootNode(rootNode);
        emitTrailingComments(rootNode);
        emitFileEnding();
    }

    /// {@inheritDoc}
    @Override
    public YamlNode toAst(Object jvmInstance) {
        setupSerializer();
        return serialize(jvmInstance);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromString(String text, Class<C> jvmType) {
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
        setupSerializer();
        return (C) deserialize(astNode, jvmType);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromAst(YamlNode astNode, TypeReference<C> typeRef) {
        setupSerializer();
        return (C) deserialize(astNode, typeRef);
    }

    //
    //
    //

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

    //
    // Node emitting methods
    //

    private void emitRootNode(YamlNode node) {
        if (node.getToken() == null || node.getStartLine() < 1) {
            isSynthetic = true;
        }
        emitNode(node, false, false, false);

        boolean isMultiLine = previousEmissionLineNumber > 1;
        // if (alwaysEndWithNewLine && !atStartOfLine) {
        // if ((alwaysEndWithNewLine || isSynthetic) && !atStartOfLine) {
        if ((alwaysEndWithNewLine || isMultiLine) && !atStartOfLine) {
            emitNewLine();
        }
    }

    private void emitNode(YamlNode node, boolean isMapKey, boolean isSequenceItem, boolean isInsideFlow) {
        emitNodeProperties(node);

        // if (!preceededByWhitespace && previousNode != null && !isImplicitNull(node)) {
        //     if (newLineBefore(node, isSequenceItem, isInsideFlow)) {
        //         emitNewLine();
        //     } else {
        //         emitSpace();
        //     }
        // }

        // if (!preceededByWhitespace && previousNode != null && !isImplicitNull(node)) {
            if (newLineBefore(node, isSequenceItem, isInsideFlow)) {
                emitNewLine();
            // } else {
            //     emitSpace();
            }
        // }

        emitBlockComments(node);
        switch (node) {
            case YamlStream stream -> emitStream(stream);
            case YamlDocument document -> emitDocument(document, 1, 1);
            case YamlMap map -> emitMap(map, isMapKey, isSequenceItem, isInsideFlow);
            case YamlSequence sequence -> emitSequence(sequence, isMapKey, isSequenceItem, isInsideFlow);
            case YamlScalar scalar -> emitScalar(scalar, isMapKey, isSequenceItem, isInsideFlow);
            case YamlAlias alias -> emitAlias(alias, isMapKey, isSequenceItem, isInsideFlow);
            default -> error(node, YamlDiagnosticCode.UNEXPECTED_NODE_TYPE,
                             node.getClass().getSimpleName());
        }
    }

    private void emitStream(YamlStream stream) {
        debug(">emitStream");
        depth++;
        try {
            var documents = stream.getDocuments();
            for (int i = 0; i < documents.size(); i++) {
                YamlDocument doc = documents.get(i);
                emitDocument(doc, i, documents.size());
            }
        } finally {
            depth--;
            debug("<emitStream");
        }
    }

    private void emitDocument(YamlDocument doc, int docNum, int numDocs) {
        debug(">emitDocument");
        depth++;
        try {
            // Write explicit document markers if there are multiple documents,
            // or if the document explicitly contains directives.
            if (options.isExplicitStart() ||
                numDocs > 1 ||
                !doc.getDirectives().isEmpty() ||
                doc.hasStartMarker())
            {
                if (!atStartOfLine) {
                    emitNewLine();
                }
                emit(DOCUMENT_START_MARKER);
                if (doc.getBody().isPreceededByNewLine()) {
                    emitNewLine();
                } else {
                    emitSpace();
                }
            }

            // TODO: Directives

            // Process the body of this specific document
            emitNode(doc.getBody(), false, false, false);

            // Append a newline between documents if we aren't at the very end
            // if (docNum < numDocs - 1 && sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            //     appendText(NL);
            // }

            // if (docNum < numDocs - 1 && !isSingleScalar) {
            //     emitNewLine();
            // }
        } finally {
            depth--;
            debug("<emitDocument");
        }
    }

    private void emitMap(YamlMap map, boolean isMapKey, boolean isSequenceItem, boolean isInsideFlow) {
        debug(">emitMap");
        depth++;
        try {
            if (map == null) return;
            if (map.getNodeStyle() == NodeStyle.FLOW) {
                emitFlowMap(map, isMapKey, isSequenceItem, isInsideFlow);
                return;
            }
            boolean firstItem = true;
            int prevIndentSpaces = indentSpaces;
            // We use getEntries() because entrySet() is unordered
            for (YamlMapEntry entry : map.getEntries()) {
                if (!firstItem) {
                    emitNewLine();
                }
                YamlNode key = entry.getKey();
                YamlNode value = entry.getValue();

                // Determine if this key requires an explicit complex layout block ('?')
                boolean hasComplexKey = (key instanceof YamlMap m && m.getNodeStyle() == NodeStyle.BLOCK) ||
                                        (key instanceof YamlSequence s && s.getNodeStyle() == NodeStyle.BLOCK);

                if (retainFormatting) {
                    hasComplexKey |= entry.hasExplicitKey();
                }

                // if ("key".equals(key.asString())) {
                //     debug("Debug");
                // }
                // if ("false".equals(value.asString())) {
                //     debug("Debug");
                // }

                int keyIndent = indentOf(key, prevIndentSpaces);

                indentSpaces = keyIndent;
                if (hasComplexKey) {



                    // if (!isSynthetic(key)) {
                    //     indentSpaces = keyIndent - 2;
                    // }



                    emit(KEY_INDICATOR);
                    emitSpace();
                    indentSpaces = keyIndent + 2;
                    newLineAlreadyEmitted = true;
                }

                // indentSpaces = keyIndent;
                emitNode(key, true, false, false);

                if (hasComplexKey) {
                    emitNewLine();
                    indentSpaces = keyIndent;



                    // if (!(isSynthetic(key))) {
                    //     indentSpaces = keyIndent - 2;
                    // }



                }

                if (previousNode instanceof YamlAlias) {
                    emitSpace();
                }

                emit(VALUE_INDICATOR);

                if (hasComplexKey) {
                    indentSpaces = keyIndent;
                    newLineAlreadyEmitted = true;
                }

                if (newLineBefore(value, false, false)) {
                    emitInlineComments(key);
                }

                int expectedValueIndent = keyIndent + indentSize;
                // int expectedValueIndent = hasComplexKey
                //     ? keyIndent + 2
                //     : keyIndent + indentSize;

                indentSpaces = indentOf(value, expectedValueIndent);

                emitNode(value, false, false, false);

                firstItem = false;
            }
            indentSpaces = prevIndentSpaces;
        } finally {
            depth--;
            debug("<emitMap");
        }
    }

    private void emitSequence(YamlSequence sequence, boolean isMapKey, boolean isSequenceItem, boolean isInsideFlow) {
        debug(">emitSequence");
        depth++;
        try {
            if (sequence == null) return;
            if (sequence.getNodeStyle() == NodeStyle.FLOW) {
                emitFlowSequence(sequence, isMapKey, isSequenceItem, isInsideFlow);
                return;
            }
            boolean firstItem = true;
            int prevIndentSpaces = indentSpaces;

            // TODO: Take ancestor node modification into account
            int indicatorIndent = indentOf(sequence, prevIndentSpaces + indicatorIndentSize);

            for (YamlNode item : sequence) {
                if (!firstItem) {
                    emitNewLine();
                }

                indentSpaces = indicatorIndent;
                emit(ITEM_INDICATOR);
                if (outputExpandedStyle) {
                    emitNewLine();
                } else {
                    emitSpace();
                }

                indentSpaces = indicatorIndent + 2; // Indicator plus space
                newLineAlreadyEmitted = true;
                emitNode(item, false, true, false);

                firstItem = false;
            }
            indentSpaces = prevIndentSpaces;
        } finally {
            depth --;
            debug("<emitSequence");
        }
    }

    private void emitFlowMap(YamlMap map, boolean isMapKey, boolean isSequenceItem, boolean isInsideFlow) {
        debug(">emitFlowMap");
        depth++;
        try {
            if (!preceededByWhitespace) {
                emitSpace();
            }
            emit("{");
            var entries = map.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                if (entry.getKey() instanceof YamlScalar s) {
                    emitScalar(s, false, false, true);
                }
                emit(VALUE_INDICATOR);
                emitSpace();
                emitNode(entry.getValue(), false, false, true);
                if (i < entries.size() - 1) emit(", ");
            }
            emit("}");
            debug("<emitFlowMap");
        } finally {
            depth--;
            debug("<emitFlowSequence>");
        }
    }

    private void emitFlowSequence(YamlSequence seq, boolean isMapKey, boolean isSequenceItem, boolean isInsideFlow) {
        debug(">emitFlowSequence");
        depth++;
        try {
            if (!preceededByWhitespace) {
                emitSpace();
            }
            emit("[");
            var items = seq.getElements();
            for (int i = 0; i < items.size(); i++) {
                emitNode(items.get(i), false, true, true);
                if (i < items.size() - 1) {
                    emitComma();
                    emitSpace();
                }
            }
            emit("]");
        } finally {
            depth--;
            debug("<emitFlowSequence>");
        }
    }

    private void emitScalar(YamlScalar scalar, boolean isMapKey, boolean isSequenceItem, boolean isInsideFlow) {
        debug(">emitScalar");
        depth++;
        try {
            String text = formatScalar(scalar);



            String lexeme = scalar.getLexeme();

            if (scalar.getPrimitiveType() == PrimitiveType.NULL) {
                if (!forceExplicitNull && isImplicitNull(scalar)) {
                    text = "";
                } else {
                    text = "null";
                }
            } else if (lexeme == null) {
                if (scalar.getPrimitiveType() == PrimitiveType.STRING) {

                } else {
                    text = scalar.asString();
                }
            } else {
                text = formatScalar(scalar);
            }

            if (!isSynthetic(scalar)) {
                originalLineNumber = scalar.getStartLine();
            }

            if (!preceededByWhitespace && !text.isBlank()) {
                emitSpace();
            }

            emit(text);
            if (!isMapKey) {
                emitInlineComments(scalar);
            }
            previousNode = scalar;
        } finally {
            depth--;
            debug("<emitScalar>");
        }
    }

    private void emitMultilineScalar(YamlScalar scalar) {
        if (scalar == null) return;
        String text = scalar.asString();
        ScalarStyle style = scalar.getScalarStyle();
        if (text == null) return;

        // Match the original plain scalar fallback rule:
        // If it's plain but unsafe (like containing a newline), force it to DOUBLE quotes.
        if (style == ScalarStyle.PLAIN && !isSafePlain(text)) {
            style = ScalarStyle.DOUBLE_QUOTED;
        }

        if (text.isEmpty()) {
            if (style == ScalarStyle.DOUBLE_QUOTED) {
                emit(DOUBLE_QUOTE);
                emit(DOUBLE_QUOTE);
            }
            if (style == ScalarStyle.SINGLE_QUOTED) {
                emit(SINGLE_QUOTE);
                emit(SINGLE_QUOTE);
            }
            return;
        }

        // Split preserving trailing empty lines
        String[] lines = text.split("\\R", -1);

        if (style == ScalarStyle.DOUBLE_QUOTED) {
            emit(DOUBLE_QUOTE);
        } else if (style == ScalarStyle.SINGLE_QUOTED) {
            emit(SINGLE_QUOTE);
        }

        for (int i = 0; i < lines.length; i++) {
            String processedLine;
            if (style == ScalarStyle.DOUBLE_QUOTED) {
                processedLine = escapeDoubleQuoted(lines[i]);
            } else if (style == ScalarStyle.SINGLE_QUOTED) {
                processedLine = escapeSingleQuoted(lines[i]);
            } else {
                processedLine = lines[i];
            }

            emit(processedLine);

            // Only append line breaks and indentation if this isn't the absolute last element
            if (i < lines.length - 1) {
                emitNewLine();
            }
        }

        if (style == ScalarStyle.DOUBLE_QUOTED) {
            emit(DOUBLE_QUOTE);
        } else if (style == ScalarStyle.SINGLE_QUOTED) {
            emit(SINGLE_QUOTE);
        }
    }

    private void emitAlias(YamlAlias alias, boolean isBlockKey, boolean isSequenceItem, boolean isInsideFlow) {
        debug(">emitAlias");
        depth++;
        try {
            if (outputResolvedAliases) {
                YamlNode target = alias.getResolvedNode();
                emitNode(target, isBlockKey, isSequenceItem, isInsideFlow);
            } else {
                if (!isSynthetic(alias)) {
                    originalLineNumber = alias.getStartLine();
                }

                if (!preceededByWhitespace) {
                    emitSpace();
                }

                emit("*");
                emit(alias.getName());
                if (!isBlockKey) {
                    emitInlineComments(alias);
                }
            }
            previousNode = alias;
        } finally {
            depth--;
            debug("<emitAlias");
        }
    }

    //
    // Properties and Comments emitting methods
    //

    private void emitNodeProperties(YamlNode node) {
        if (!node.getProperties().isEmpty()) {
            if (!preceededByWhitespace) {
                emitSpace();
            }
            for (YamlNodeProperty property : node.getProperties()) {
                // if (previousNode != null && property.getStartLine() > previousNode.getStartLine()) {
                if (newLineBefore(property, false, false)) {
                // if (property.isPreceededByNewLine()) {
                    emitNewLine();
                }
                if (!preceededByWhitespace) {
                    emitSpace();
                }
                if (property.getToken() != null) {
                    emit(property.getToken().getLexeme());
                } else {
                    // TODO: Implement this
                }
                previousNode = property;
            }
        }
    }

    /// Emits comments that were identified by the parser as being on their own line.
    private void emitBlockComments(YamlNode node) {
        if (node == null || !outputComments) return;
        for (YamlComment comment : node.getComments()) {
            if (comment.getCommentStyle() == CommentStyle.LEADING) {
                emit("#");
                emit(comment.asString());
                emitNewLine();
            }
        }
    }

    /// Emits comments identified as "inline" (appended to the end of a data line).
    private void emitInlineComments(YamlNode node) {
        if (node == null || !outputComments) return;
        for (YamlComment comment : node.getComments()) {
            if (comment.getCommentStyle() == CommentStyle.INLINE) {
                if (!preceededByWhitespace) {
                    emitSpace();
                }
                emit(COMMENT_INDICATOR);
                emit(comment.asString());
                break;
            }
        }
    }

    private void emitTrailingComments(YamlNode node) {
        if (node == null || !outputComments) return;
        for (YamlComment comment : node.getComments()) {
            if (comment.getCommentStyle() == CommentStyle.TRAILING) {
                if (!preceededByWhitespace) {
                    emitNewLine();
                }
                emit("#");
                emit(comment.asString());
                // emitNewLine();
            }
        }
    }

    //
    // Low-level emitting methods
    //

    private void emitNewLine() {
        emit(NEWLINE);
        preceededByWhitespace = true;
        newLineAlreadyEmitted = true;
        originalLineNumber++;
    }

    private void emitSpace() {
        emit(SPACE);
        preceededByWhitespace = true;
    }

    private void emitComma() {
        emit(COMMA);
    }

    private void emit(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (atStartOfLine && indentSpaces > 0) {
            append(" ".repeat(indentSpaces));
        }
        append(text);
        atStartOfLine = text.endsWith("\n");
        preceededByWhitespace = atStartOfLine;
        // if (originalLineNumber > -1) {
            previousEmissionLineNumber = originalLineNumber;
        // }
        newLineAlreadyEmitted = false;
        trace("Emitted text for line " + originalLineNumber + ": " + StringUtils.debugString(16, text));
    }

    private void append(String text) {
        if (string != null) {
            string.append(text);
            if (reporter.reportsTrace()) {
                debugStringBuilder();
            }
        } else if (writer != null) {
            try {
				writer.write(text);
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
    }

    //
    // Helpers
    //

    // TODO: This was supposed to be used
    private int startLineOf(YamlNode node) {
        if (node.getProperties().isEmpty()) {
            return node.getStartLine();
        } else {
            return node.getProperties().getFirst().getStartLine();
        }
    }

    private boolean newLineBefore(YamlNode node, boolean isSequenceItem, boolean isInsideFlow) {
        if (newLineAlreadyEmitted) {
            return false;
        }
        if (retainFormatting) {
            if (previousNode != null && node.getStartLine() > previousNode.getStartLine()) {
                return true;
            }
            if (node.isPreceededByNewLine()) {
                return true;
            }
            return false;
        }
        if (!preceededByWhitespace && previousNode != null) {
            if (isScalar(node) || isSequenceItem || isInsideFlow) {
                return false;
            } else {
                return true;
            }

            // if (isSynthetic(node)) {
            //     // Synthetic node
            //     // if (node instanceof YamlScalar s && "2".equals(s.getContent())) {
            //     //     debug("Debug");
            //     // }


            //     //
            //     // TODO: This needs changed to use canonical YAML
            //     //


            //     if (isScalar(node) || isSequenceItem || isInsideFlow) {
            //         return false;
            //     } else {
            //         return true;
            //     }
            // } else {
            //     // Parsed node
            //     if (node.getStartLine() > previousEmissionLineNumber) {
            //         trace("Start line differs for " + debugNode(node) + " (prev line = " + previousEmissionLineNumber + ")");
            //         return true;
            //     } else {
            //         return false;
            //     }
            // }
        } else {
            return false;
        }
    }

    private boolean isSynthetic(YamlNode node) {
        // return false;
        return node.getStartLine() < 1;
    }

    private boolean isScalar(YamlNode node) {
        return (node instanceof YamlScalar) ||
               (node instanceof YamlAlias);
    }

    private int indentOf(YamlNode node, int expectedIndent) {
        // if (isSynthetic(node)) {
            return expectedIndent;
        // } else {
        //     if (node.getProperties().isEmpty()) {
        //         return node.getStartColumn() - 1; // Columns start at 1
        //     } else {
        //         return indentOf(node.getProperties().getFirst(), expectedIndent);
        //     }
        // }
    }

    private boolean isImplicitNull(YamlNode node) {
        return node instanceof YamlScalar s && s.asString() == null;
    }

    private String formatScalar(YamlScalar scalar) {
        if (options.normalizeScalarFormatting() || scalar.getLexeme() == null) {
            return normalizeScalar(scalar);
        } else {
            String lexeme = scalar.getLexeme();
            warn(GenericDiagnosticCode.WARN, "formatLexeme called");
            // TODO: Transform indentation if any ancestor node has been modified
            return lexeme;
        }
    }

    private String normalizeScalar(YamlScalar scalar) {
        return switch(scalar.getPrimitiveType()) {
            case STRING -> formatString(scalar);
            default -> {
                // TODO: Number formatting
                yield scalar.getLexeme();
            }
        };
    }

    private String formatString(YamlScalar scalar) {
        String text;
        String string = scalar.asString();
        ScalarStyle scalarStyle = scalar.getScalarStyle();

        // TODO: We need to o a similar "isSafePlain" check for single quoted, and blocks.
        if (scalarStyle == ScalarStyle.PLAIN) {
            if (!isSafePlain(string)) {
                scalarStyle = ScalarStyle.DOUBLE_QUOTED;
            }
        }

        if (scalarStyle == ScalarStyle.SINGLE_QUOTED) {
            text = singleQuote(string);
        } else if (scalarStyle == ScalarStyle.DOUBLE_QUOTED) {
            text = doubleQuote(string);
        } else if (scalarStyle == ScalarStyle.LITERAL) {
            // TODO: Implement this
            text = "";
        } else if (scalarStyle == ScalarStyle.FOLDED) {
            // TODO: Implement this
            text = "";
        } else {
            // Plain
            text = string;
        }
        return text;
    }

    // private String normalizeString(YamlScalar scalar) {

    //     return scalar.asString();

    //     // String content = scalar.getContent();
    //     // if (isSafePlain(content)) {
    //     //     return content;
    //     // } else {
    //     //     return doubleQuote(content);
    //     // }
    // }

    private String singleQuote(String text) {
        return SINGLE_QUOTE + escapeSingleQuoted(text) + SINGLE_QUOTE;
    }

    private String doubleQuote(String text) {
        return DOUBLE_QUOTE + escapeDoubleQuoted(text) + DOUBLE_QUOTE;
    }

    private String escapeSingleQuoted(String text) {
        return text.replace("'", "''");
    }

    private String escapeDoubleQuoted(String text) {
        if (text == null) return "";
        // Only escape literal backslashes and quotes; literal inline \n and \r checks are omitted
        // here because they are handled structurally by the line splitter.
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // private boolean isSafePlain(String s) {
    //     if (s == null || s.isEmpty()) return false;
    //     if (s.contains("\n") || s.contains("\r")) return false;
    //     if (s.matches("^(true|false|null|True|False|NULL)$")) return true; // TOOO: Values are missing from here
    //     char first = s.charAt(0);
    //     if ("-?:,[]{}#&*!|>'\"%@` ".indexOf(first) != -1) return false;
    //     if (s.contains(": ") || s.contains(" #") || s.endsWith(":")) return false;
    //     return s.chars().allMatch(c -> c <= 127);
    // }

    private boolean isSafePlain(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.contains("\n") || s.contains("\r")) return false;
        if (s.matches("^(true|false|null|True|False|NULL)$")) return true; // TOOO: Values are missing from here
        char first = s.charAt(0);
        char second = s.length() == 1 ? ' ' : s.charAt(1);

        boolean secondIsWhiteSpace = second == ' ' || second == '\t' || second == '\n' || second == '\r';

        if (secondIsWhiteSpace && "-?:,[]{}#&*!|>'\"%@` ".indexOf(first) != -1) {
            return false;
        }
        if (",[]{}#&*!|>'\"%@` ".indexOf(first) != -1) {
            return false;
        }

        // if ("-,[]{}#&*!|>'\"%@` ".indexOf(first) != -1) return false;
        if (s.contains(": ") || s.contains(" #") || s.endsWith(":")) return false;
        return s.chars().allMatch(c -> c <= 127);
    }

    private void emitFileEnding() {
        // if (previousNode != null && !isSingleScalar) {
        //     emitNewLine();
        // }

        if (rootNode.fileEndsWithNewLine()) {
            if (!atStartOfLine) {
                emitNewLine();
            }
        }
    }

    //
    // Setup and Diagnostics
    //

    protected void setupSerializer() {
        // Configuration
        super.setupSerializer();
        depthLimit = options.getDepthLimit();

        // State
        depth = 0;
    }

    private void setupEmitter() {
        // Configuration
        indentSize = options.getIndentSize();
        indicatorIndentSize = 0; // TODO: Add to options
        outputResolvedAliases = options.outputResolvedAliases();
        outputComments = options.outputComments();
        outputExpandedStyle = options.outputExpandedStyle();
        forceExplicitNull = options.forceExplicitNull();
        alwaysEndWithNewLine = options.setAlwaysEndWithNewLine();
        retainFormatting = options.retainFormatting();

        // State
        atStartOfLine = true;
        indentSpaces = 0;
        previousNode = null;
        preceededByWhitespace = true;
        previousEmissionLineNumber = 1;
        originalLineNumber = 1;
        depth = 0;
        isSynthetic = false;
        newLineAlreadyEmitted = true;
    }

    protected void error(YamlNode node, DiagnosticCode code, Object... details) {
        reporter.errorAt(node.getToken(), code, details);
        if (!reporter.collectsProblems()) {
            throw new YamlParserException(node.getToken(), code, details);
        }
    }

    protected void warn(YamlToken token, DiagnosticCode code, Object... details) {
        reporter.warnAt(token, code, details);
    }

    protected String debugNode(YamlNode node) {
        if (node instanceof YamlScalar scalar) {
            if (node.getStartLine() > 0) {
                return node.getClass().getSimpleName() + " (" +
                TermUtils.ANSI_WHITE + scalar.asString() + TermUtils.ANSI_GREEN +
                ") at " + node.getStartLine() + ":" + node.getStartColumn();
            } else {
                return node.getClass().getSimpleName();
            }
        } else {
            return node.toString();
        }
    }

    protected void debugStringBuilder() {
        if (debugStringBuilder) {
            reporter.trace("StringBuilder:");
            try {
                reporter.getWriter(Level.TRACE).write(2, DebugUtils.debugStringBuilder(string, -1));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected void report(Level level, String message, Object... details) {
        String indentation = "  ".repeat(Math.max(0, depth));
        char first = message.charAt(0);
        String output;
        if (first == '>' || first == '<') {
            output = first + TermUtils.ANSI_YELLOW + message.substring(1) + TermUtils.ANSI_RESET;
        } else {
            output = message;
        }
        if (level == Level.TRACE) {
            reporter.trace(indentation + output);
        } else {
            reporter.debug(indentation + output);
        }
    }
}