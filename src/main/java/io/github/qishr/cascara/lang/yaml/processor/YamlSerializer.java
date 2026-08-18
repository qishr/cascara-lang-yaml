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
import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.common.lang.processor.AbstractSerializer;
import io.github.qishr.cascara.common.lang.processor.AstParser;
import io.github.qishr.cascara.common.lang.type.TypeReference;
import io.github.qishr.cascara.common.util.ContentType;
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
    private Reporter reporter = new NoOpReporter();

    // Options
    private int indentSize;
    private int indicatorIndentSize;
    private boolean outputResolvedAliases;
    private boolean outputComments;
    private boolean outputExpandedStyle;

    // State
    private int indentSpaces;
    private boolean atStartOfLine;
    private boolean preceededByWhitespace;
    private YamlNode previousNode;
    private YamlNode rootNode;
    private boolean isSingleScalar;

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
        emitNode(rootNode, false);
        emitInlineComments(previousNode);
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
        emitNode(rootNode, false);
        emitInlineComments(previousNode);
    }

    /// {@inheritDoc}
    @Override
    public YamlNode toAst(Object jvmInstance) {
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
        return (C) deserialize(astNode, jvmType);
    }

    /// {@inheritDoc}
    @Override
    public <C> C fromAst(YamlNode astNode, TypeReference<C> typeRef) {
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

    private void emitNode(YamlNode node, boolean isKeyOfBlockMap) {
        emitNodeProperties(node);

        if (!preceededByWhitespace && previousNode != null && !isImplicitNull(node)) {
            if (newLineBefore(node)) {
                emitNewLine();
            } else {
                emitSpace();
            }
        }

        emitBlockComments(node);
        switch (node) {
            case YamlStream stream -> emitStream(stream);
            case YamlDocument document -> emitDocument(document, 1, 1);
            case YamlMap map -> emitMap(map, isKeyOfBlockMap);
            case YamlSequence sequence -> emitSequence(sequence, isKeyOfBlockMap);
            case YamlScalar scalar -> emitScalar(scalar, isKeyOfBlockMap);
            case YamlAlias alias -> emitAlias(alias, isKeyOfBlockMap);
            default -> error(node, YamlDiagnosticCode.UNEXPECTED_NODE_TYPE,
                             node.getClass().getSimpleName());
        }
        if (!(node instanceof YamlScalar)) {
            isSingleScalar = false;
        }
    }

    private void emitStream(YamlStream stream) {
        var documents = stream.getDocuments();
        for (int i = 0; i < documents.size(); i++) {
            YamlDocument doc = documents.get(i);
            emitDocument(doc, i, documents.size());
        }
    }

    private void emitDocument(YamlDocument doc, int docNum, int numDocs) {
        // Write explicit document markers if there are multiple documents,
        // or if the document explicitly contains directives.
        if (options.isExplicitStart() || numDocs > 1 || !doc.getDirectives().isEmpty()) {
            emit(DOCUMENT_START_MARKER);
            emitNewLine();;
        }

        // TODO: Directives

        // Process the body of this specific document
        emitNode(doc.getBody(), false);

        // Append a newline between documents if we aren't at the very end
        // if (docNum < numDocs - 1 && sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
        //     appendText(NL);
        // }

        // if (docNum < numDocs - 1 && !isSingleScalar) {
        //     emitNewLine();
        // }
    }

    private void emitMap(YamlMap map, boolean isKeyOfBlockMap) {
        if (map == null) return;
        if (map.getNodeStyle() == NodeStyle.FLOW) {
            emitFlowMap(map);
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

            // Determine if this key requires an explicit complex layout block ('?')
            boolean hasComplexKey = (key instanceof YamlMap m && m.getNodeStyle() == NodeStyle.BLOCK) ||
                                    (key instanceof YamlSequence s && s.getNodeStyle() == NodeStyle.BLOCK);

            int keyIndent = indentOf(key, prevIndentSpaces);

            if ("tiles".equals(key.asString())) {
                debug("Debug");
            }

            if (hasComplexKey) {
                indentSpaces = keyIndent - 2; // Less indentation for the indicator
                emit(KEY_INDICATOR);
                emitSpace();
                // indentSpaces = keyIndent + 2;
            }

            indentSpaces = keyIndent;
            emitNode(key, true);

            if (hasComplexKey) {
                // emitNewLine();
                indentSpaces = keyIndent - 2; // Less indentation for the indicator
            }

            emit(VALUE_INDICATOR);

            YamlNode value = entry.getValue();

            if (newLineBefore(value)) {
                emitInlineComments(key);
            }

            int expectedValueIndent = keyIndent + indentSize;
            // int expectedValueIndent = hasComplexKey
            //     ? keyIndent + 2
            //     : keyIndent + indentSize;

            indentSpaces = indentOf(value, expectedValueIndent);

            emitNode(value, false);

            firstItem = false;
        }
        indentSpaces = prevIndentSpaces;
    }

    private void emitSequence(YamlSequence sequence, boolean isKeyOfBlockMap) {
        if (sequence == null) return;
        if (sequence.getNodeStyle() == NodeStyle.FLOW) {
            emitFlowSequence(sequence);
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
            emitNode(item, false);

            firstItem = false;
        }
        indentSpaces = prevIndentSpaces;
    }

    private void emitFlowMap(YamlMap map) {
        emit("{");
        var entries = map.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.getKey() instanceof YamlScalar s) {
                emitScalar(s, false);
            }
            emit(VALUE_INDICATOR);
            emitSpace();
            emitNode(entry.getValue(), false);
            if (i < entries.size() - 1) emit(", ");
        }
        emit("}");
    }

    private void emitFlowSequence(YamlSequence seq) {
        emit("[");
        var items = seq.getElements();
        for (int i = 0; i < items.size(); i++) {
            emitNode(items.get(i), false);
            if (i < items.size() - 1) {
                emitComma();
                emitSpace();
            }
        }
        emit("]");
    }

    private void emitScalar(YamlScalar scalar, boolean isKeyOfBlockMap) {
        String text = null;
        String lexeme = scalar.getLexeme();

        if (lexeme == null) {
            if (isImplicitNull(scalar)) {
                // TODO: or "null", depending on options
                text = "";
            } else if (scalar.getScalarStyle() == ScalarStyle.SINGLE_QUOTED) {
                text = singleQuote(scalar.asString());
            } else if (scalar.getScalarStyle() == ScalarStyle.DOUBLE_QUOTED) {
                text = doubleQuote(scalar.asString());
            } else if (scalar.getScalarStyle() == ScalarStyle.LITERAL) {
                // TODO: Implement this
            } else if (scalar.getScalarStyle() == ScalarStyle.FOLDED) {
                // TODO: Implement this
            } else {
                // Plain
                String string = scalar.asString();
                if (isSafePlain(string)) {
                    text = string;
                } else {
                    text = doubleQuote(string);
                }
            }
        } else {
            text = formatLexeme(scalar);
        }

        emit(text);
        if (!isKeyOfBlockMap) {
            emitInlineComments(scalar);
        }
        previousNode = scalar;
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

    private void emitAlias(YamlAlias alias, boolean isBlockKey) {
        if (outputResolvedAliases) {
            YamlNode target = alias.getResolvedNode();
            emitNode(target, isBlockKey);
        } else {
            emit("*");
            emit(alias.getName());
            if (!isBlockKey) {
                emitInlineComments(alias);
            }
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
                if (previousNode != null && property.getStartLine() > previousNode.getStartLine()) {
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
            if (comment.getNodeStyle() == NodeStyle.BLOCK) {
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
            if (comment.getNodeStyle() == NodeStyle.FLOW) {
                if (!preceededByWhitespace) {
                    emitSpace();
                }
                emit(COMMENT_INDICATOR);
                emit(comment.asString());
                break;
            }
        }
    }

    //
    // Low-level emitting methods
    //

    private void emitNewLine() {
        emit(NEWLINE);
        preceededByWhitespace = true;
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
    }

    private void append(String text) {
        if (string != null) {
            string.append(text);
            if (reporter.reportsTrace()) {
                reporter.trace("StringBuidler:\n" + DebugUtils.debugStringBuilder(string, -1));
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

    // TODO: This was supposed to be eused
    private int startLineOf(YamlNode node) {
        if (node.getProperties().isEmpty()) {
            return node.getStartLine();
        } else {
            return node.getProperties().getFirst().getStartLine();
        }
    }

    private boolean newLineBefore(YamlNode node) {
        if (!preceededByWhitespace && previousNode != null) {
            if (node.getStartLine() > previousNode.getStartLine()) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private int indentOf(YamlNode node, int defaultValue) {
        return node.getStartColumn() > 0
            ? node.getStartColumn() - 1 // Columns start at 1
            : defaultValue;
    }

    private boolean isImplicitNull(YamlNode node) {
        return node instanceof YamlScalar s && s.asString() == null;
    }

    private String formatLexeme(YamlScalar scalar) {
        String lexeme = scalar.getLexeme();
        // TODO: Transform indentation if any ancestor node has been modified
        return lexeme;
    }

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

    private boolean isSafePlain(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.contains("\n") || s.contains("\r")) return false;
        if (s.matches("^(true|false|null|True|False|NULL)$")) return true; // TOOO: Values are missing from here
        char first = s.charAt(0);
        if ("-?:,[]{}#&*!|>'\"%@` ".indexOf(first) != -1) return false;
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

    private void setupEmitter() {
        // Configuration
        indentSize = options.getIndentSize();
        indicatorIndentSize = 0; // TODO: Add to options
        outputResolvedAliases = options.outputResolvedAliases();
        outputComments = options.outputComments();
        outputExpandedStyle = options.outputExpandedStyle();

        // State
        atStartOfLine = true;
        indentSpaces = 0;
        previousNode = null;
        preceededByWhitespace = true;
        isSingleScalar = true;
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

    protected void report(Level level, String message, Object... details) {
        String indentation = "  ".repeat(Math.max(0, indentSpaces));
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