package io.github.qishr.cascara.lang.yaml.processor;

import java.io.IOException;
import java.io.Writer;

import io.github.qishr.cascara.common.annotation.Experimental;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.util.TermUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
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
    private static final String SPACE = " ";
    private static final String NEWLINE = "\n";
    private static final String SINGLE_QUOTE = "'";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String VALUE_INDICATOR = ":";
    private static final String ITEM_INDICATOR = "-";

    private StringBuilder string;
    private Writer writer;

    private int indentSpaces;
    private int indentSize;
    private int indicatorIndentSize;
    private boolean atStartOfLine;

    public YamlAstEmitter() {

    }

    @Override
    public YamlAstEmitter self() { return this; }

    public String toString(YamlNode node) {
        string = new StringBuilder();
        initialize();
        emitNode(node);
        return string.toString();
    }

    public void toWriter(YamlNode node, Writer writer) {
        this.writer = writer;
        initialize();
        emitNode(node);
    }

    private void emitNode(YamlNode node) {
        switch (node) {
            case YamlStream stream -> emitStream(stream);
            case YamlDocument document -> emitDocument(document);
            case YamlMap map -> emitMap(map);
            case YamlSequence sequence -> emitSequence(sequence);
            case YamlScalar scalar -> emitScalar(scalar);
            default -> error(node, YamlDiagnosticCode.UNEXPECTED_NODE_TYPE,
                             node.getClass().getSimpleName());
        }
    }

    private void emitStream(YamlStream stream) {
        // TODO: Implement this
        error(stream, GenericDiagnosticCode.UNIMPLEMENTED_METHOD, "YamlAstEmitter", "emitStream");
    }

    private void emitDocument(YamlDocument document) {
        // TODO: Implement this
        error(document, GenericDiagnosticCode.UNIMPLEMENTED_METHOD, "YamlAstEmitter", "emitDocument");
    }

    private void emitMap(YamlMap map) {
        if (map.getNodeStyle() == NodeStyle.FLOW) {
            emitFlowMap(map);
            return;
        }
        boolean firstItem = true;
        int prevIndentSpaces = indentSpaces;
        for (YamlMapEntry entry : map.getEntries()) {
            if (!firstItem) {
                emit(NEWLINE);
            }
            YamlNode key = entry.getKey();
            int keyIndent = indentOf(key, prevIndentSpaces);

            indentSpaces = keyIndent;

            emitNode(key);
            emit(VALUE_INDICATOR);

            YamlNode value = entry.getValue();
            indentSpaces = value.getStartColumn() > 0
                ? value.getStartColumn() - 1 // Columns start at 1
                : keyIndent + indentSize;

            if (value.getStartLine() > key.getStartLine()) {
                emit(NEWLINE);
            } else {
                emit(SPACE);
            }

            emitNode(value);

            indentSpaces = prevIndentSpaces;
            firstItem = false;
        }
    }

    private void emitSequence(YamlSequence sequence) {
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
                emit(NEWLINE);
            }

            indentSpaces = indicatorIndent;
            emit(ITEM_INDICATOR);
            emit(SPACE);

            indentSpaces = indicatorIndent + 2; // Indicator plus space
            emitNode(item);

            firstItem = false;
        }
        indentSpaces = prevIndentSpaces;
    }

    private void emitFlowMap(YamlMap map) {
        // TODO: Implement this
        error(map, GenericDiagnosticCode.UNIMPLEMENTED_METHOD, "YamlAstEmitter", "emitFlowMap");
    }

    private void emitFlowSequence(YamlSequence sequence) {
        // TODO: Implement this
        error(sequence, GenericDiagnosticCode.UNIMPLEMENTED_METHOD, "YamlAstEmitter", "emitFlowSequence");
    }

    private void emitScalar(YamlScalar scalar) {
        String text = null;
        String lexeme = scalar.getLexeme();

        if (lexeme == null) {
            if (scalar.getScalarStyle() == ScalarStyle.SINGLE_QUOTED) {
                text = SINGLE_QUOTE + escapeSinglueQuoted(scalar.asString()) + SINGLE_QUOTE;
            } else if (scalar.getScalarStyle() == ScalarStyle.DOUBLE_QUOTED) {
                text = DOUBLE_QUOTE + escapeDoubleQuoted(scalar.asString()) + DOUBLE_QUOTE;
            } else if (scalar.getScalarStyle() == ScalarStyle.LITERAL) {
                // TODO: Implement this
            } else if (scalar.getScalarStyle() == ScalarStyle.FOLDED) {
                // TODO: Implement this
            } else {
                // Plain
                text = scalar.asString();
            }
        } else {
            text = formatLexeme(scalar);
        }

        emit(text);
    }

    //
    //
    //

    private int indentOf(YamlNode node, int defaultValue) {
        return node.getStartColumn() > 0
            ? node.getStartColumn() - 1 // Columns start at 1
            : defaultValue;
    }

    private String formatLexeme(YamlScalar scalar) {
        String lexeme = scalar.getLexeme();
        // TODO: Transform indentation if any ancestor node has been modified
        return lexeme;
    }

    private String escapeSinglueQuoted(String text) {
        // TODO: Implement this
        return text;
    }

    private String escapeDoubleQuoted(String text) {
        // TODO: Implement this
        return text;
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

    private void initialize() {
        // Configuration
        indentSize = options.getIndentSize();
        indicatorIndentSize = 0;

        // State
        atStartOfLine = true;
        indentSpaces = 0;
    }

    //
    // Diagnostics
    //

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
