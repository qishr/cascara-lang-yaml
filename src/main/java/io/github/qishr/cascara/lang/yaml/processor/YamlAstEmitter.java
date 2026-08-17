package io.github.qishr.cascara.lang.yaml.processor;

import java.io.IOException;
import java.io.Writer;

import io.github.qishr.cascara.common.annotation.Experimental;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.common.util.TermUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlComment;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
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

// EventEmitter
// AstEmitter to become part of Serializer

@Experimental
public class YamlAstEmitter extends AbstractYamlProcessor<YamlAstEmitter> {
    private static final String NEWLINE = "\n";
    private static final String SPACE = " ";
    private static final String COMMA = ",";
    private static final String SINGLE_QUOTE = "'";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String VALUE_INDICATOR = ":";
    private static final String ITEM_INDICATOR = "-";
    private static final String COMMENT_INDICATOR = "#";

    private StringBuilder string;
    private Writer writer;

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

    public YamlAstEmitter() {

    }

    @Override
    public YamlAstEmitter self() { return this; }

    public String toString(YamlNode node) {
        string = new StringBuilder();
        setup();
        emitNode(node, false);
        emitInlineComments(previousNode);
        return string.toString();
    }

    public void toWriter(YamlNode node, Writer writer) {
        this.writer = writer;
        setup();
        emitNode(node, false);
        emitInlineComments(previousNode);
    }

    //
    // Node emitting methods
    //

    private void emitNode(YamlNode node, boolean isBlockKey) {

        // TODO: This isn't right. emitNewLine around 132 has already emitte a new line
        // if (!isBlockKey) {
        //     emitInlineComments(previousNode);
        // }

        emitNodeProperties(node);
        if (!preceededByWhitespace && previousNode != null) {
            if (newLineBefore(node)) {
                emitNewLine();
            } else {
                emitSpace();
            }
            // if (node.getStartLine() > previousNode.getStartLine()) {
            //     emitNewLine();
            // } else {
            //     emitSpace();
            // }
        }
        emitBlockComments(node);
        switch (node) {
            case YamlStream stream -> emitStream(stream);
            case YamlDocument document -> emitDocument(document);
            case YamlMap map -> emitMap(map);
            case YamlSequence sequence -> emitSequence(sequence);
            case YamlScalar scalar -> emitScalar(scalar, isBlockKey);
            case YamlAlias alias -> emitAlias(alias, isBlockKey);
            default -> error(node, YamlDiagnosticCode.UNEXPECTED_NODE_TYPE,
                             node.getClass().getSimpleName());
        }
    }

    private void emitStream(YamlStream stream) {
        // TODO: Implement this
        error(stream, GenericDiagnosticCode.UNSUPPORTED_OPERATION,  "emitStream");
    }

    private void emitDocument(YamlDocument document) {
        // TODO: Implement this
        error(document, GenericDiagnosticCode.UNSUPPORTED_OPERATION, "emitDocument");
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

    private void emitMap(YamlMap map) {
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
                emitNewLine(); // 132 HERE
            }
            YamlNode key = entry.getKey();
            int keyIndent = indentOf(key, prevIndentSpaces);

            indentSpaces = keyIndent;

            if ("debug".equals(key.asString())) {
                debug("Debug");
                // TODO: Make comments use NodeStyle so they can be block or flow
            }

            emitNode(key, true);
            emit(VALUE_INDICATOR); // TODO: inline comment on key needs to be after colon

            YamlNode value = entry.getValue();
            if (newLineBefore(value)) {
                emitInlineComments(key);
            }

            indentSpaces = indentOf(value, keyIndent + indentSize);

            emitNode(value, false);

            firstItem = false;
        }
        indentSpaces = prevIndentSpaces;
    }

    private void emitSequence(YamlSequence sequence) {
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

    private void emitScalar(YamlScalar scalar, boolean isBlockKey) {
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
        if (!isBlockKey) {
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


    //
    // Setup and Diagnostics
    //

    private void setup() {
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

    @Override
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
