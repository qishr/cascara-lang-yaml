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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.common.lang.processor.Emitter;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchor;
import io.github.qishr.cascara.lang.yaml.ast.YamlComment;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
import io.github.qishr.cascara.lang.yaml.internal.AbstractYamlProcessor;
import io.github.qishr.cascara.lang.yaml.util.NodeStyle;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;

/// Responsible for converting a [YamlNode] AST back into a valid YAML string.
///
/// This emitter is high-fidelity: it prioritizes preserving the original [NodeStyle]
/// and [QuoteStyle] of nodes while ensuring that comments are placed correctly relative
/// to their owner nodes.
///
/// ### Indentation Logic
/// The emitter maintains a virtual column through the `indent` parameters passed
/// during recursive calls. It handles special cases like "compact" sequences where
/// the mapping starts on the same line as the sequence dash (`- key: value`).
public class YamlEmitter extends AbstractYamlProcessor<YamlEmitter> implements Emitter {
    private final StringBuilder sb = new StringBuilder();
    private final Set<String> writtenAnchors = new HashSet<>();
    private static final String NL = System.lineSeparator();

    public YamlEmitter() {
    }

    @Override protected YamlEmitter self() { return this; }

    // TODO:
    // emitTagIfPresent
    // Directives
    // test26DV bug

    @Override public void emitScalar(String value) { appendText(value); }
    @Override public void emitMapStart() {}
    @Override public void emitMapEnd() {}
    @Override public void emitSequenceStart() {}
    @Override public void emitSequenceEnd() {}
    @Override public void emitPropertySeparator() { appendText(": "); }
    @Override public void emitItemSeparator() {}
    @Override public void emitNewLine() { appendText(NL); }
    @Override public void indent() {}
    @Override public void dedent() {}
    @Override public String getOutput() { return sb.toString(); }

    /// Primary entry point for emitting a full document or a multi-document stream.
    ///
    /// @param root AST root (can be a YamlStreamNode, YamlMap, YamlSequenceNode, or YamlScalarNode).
    /// @return A formatted YAML string.
    public String emit(YamlNode root) {
        writtenAnchors.clear();
        sb.setLength(0);

        if (root instanceof YamlStream stream) {
            var documents = stream.getDocuments();
            for (int i = 0; i < documents.size(); i++) {
                YamlDocument doc = documents.get(i);
                emitDocument(doc, i, documents.size());


            }
        } else {
            // Fallback for direct single-node emission
            emitNode(root, 0, false, false);
        }

        debugOutput(sb.toString());
        return sb.toString();
    }

    private void emitDocument(YamlDocument doc, int docNum, int numDocs) {
        // Write explicit document markers if there are multiple documents,
        // or if the document explicitly contains directives.
        if (options.isExplicitStart() || numDocs > 1 || !doc.getDirectives().isEmpty()) {
            appendText("---");
            appendText(NL);
        }

        // TODO: Directives

        // Process the body of this specific document
        emitNode(doc.getBody(), 0, false, false);

        // Append a newline between documents if we aren't at the very end
        if (docNum < numDocs - 1 && sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            appendText(NL);
        }
    }

    /// Recursive dispatcher for AST nodes.
    ///
    /// @param node The current node to emit.
    /// @param indent The current base indentation level.
    /// @param isSequenceItem True if this node is the direct value of a sequence dash.
    /// @param isFlow True if we are inside a flow context (prevents forced newlines).
    private void emitNode(YamlNode node, int indent, boolean isSequenceItem, boolean isFlow) {
        if (node == null) return;

        if (node instanceof YamlDocument document) {
            emitDocument(document, 0, 1);
            return;
        }

        // 1. Extract the actual target data node if wrapped in an Anchor decorator
        YamlNode targetNode = node;
        // while (targetNode instanceof YamlAnchor wrapper) {
        //     targetNode = wrapper.getInnerNode();
        // }

        // 2. ALIAS CHECK
        if (targetNode instanceof YamlAlias alias) {
            appendText("*");
            appendText(alias.getName());
            return;
        }

        // 3. ANCHOR CHECK (Using targetNode to fetch anchor metadata safely)
        if (!options.stripAnchors()) {
            String anchor = targetNode.getAnchor();
            if (anchor != null && !anchor.isEmpty()) {
                appendText("&");
                appendText(anchor);
                if (targetNode instanceof YamlScalar) {
                    appendText(" ");
                }
                if (targetNode instanceof YamlMap) {
                    appendText("\n");
                }
                if (targetNode instanceof YamlSequence) {
                    appendText("\n");
                }
            }
        }

        if (options.outputComments() && !isFlow) {
            emitBlockComments(targetNode, indent);
        }

        // 4. STRUCTURAL EVALUATION (Checking targetNode instead of node)
        if (targetNode instanceof YamlScalar scalar) {
            // If we are a sequence item on the same line as the dash,
            // the dash and space ARE the indent for the first line.
            int scalarIndent = isSequenceItem ? 0 : indent;
            emitScalarInternal(scalar, scalarIndent, isFlow);
        } else if (targetNode instanceof YamlMap map) {
            if (map.getNodeStyle() == NodeStyle.FLOW && !options.forceBlockCollections()) {
                emitFlowMap(map);
                if (!isFlow) appendText(NL);
            } else {
                emitMap(map, indent, isSequenceItem);
            }
        } else if (targetNode instanceof YamlSequence seq) {
            if (seq.getNodeStyle() == NodeStyle.FLOW && !options.forceBlockCollections()) {
                emitFlowSequence(seq);
                if (!isFlow) appendText(NL);
            } else {
                emitSequence(seq, indent, isSequenceItem);
            }
        }
    }

    /// Handles scalar formatting including Literal (|), Folded (>), and quoted styles.
    private void emitScalarInternal(YamlScalar scalar, int indent, boolean isFlow) {
        if (scalar == null) return;

        String stringValue = scalar.asString();

        // 1. Implicit Null
        if (stringValue == null) {
            if (!isFlow) appendText(" ".repeat(indent));
            if (options.forceExplicitNull()) {
                if (!isFlow || options.forceBlockCollections()) appendText(" ");
                appendText("null");
            }
            return;
        }

        String lexeme = scalar.getToken() == null ? null : scalar.getToken().getLexeme();

        boolean isBlock = scalar.getScalarStyle() == ScalarStyle.LITERAL ||
                scalar.getScalarStyle() == ScalarStyle.FOLDED;

        boolean isMultiLine = isBlock || (stringValue.contains("\n"));

        ScalarStyle style;
        if (options.normalizeScalarFormatting()) {
            style = ScalarStyle.DOUBLE_QUOTED;
        }
        else if (lexeme != null) {
            if (isMultiLine) {
                String[] lexemeLines = lexeme.split("\n");
                for (int i = 0; i < lexemeLines.length; i++) {
                    if (i > 0) {
                        appendText("\n");
                        // appendText(" ".repeat(scalar.getStartColumn() - 1));
                    }
                    appendText(lexemeLines[i]);
                }
                if (isBlock) {
                    appendText(NL);
                }
            } else {
                if (!isFlow) appendText(" ".repeat(indent));
                appendText(lexeme);
            }

            if (!isFlow) {
                // TODO: handleInlineComments(scalar) ?
                if (options.outputComments()) {
                    handleInlineComments(scalar);
                }
            }
            return;
        }
        else {
            style = scalar.getScalarStyle();
        }

        // AUTO-PROMOTION: If the style is PLAIN but the text contains newlines,
        // force it to LITERAL_BLOCK so it serializes into a valid block scalar.
        if (!isFlow && style == ScalarStyle.PLAIN && (stringValue.contains("\n") || stringValue.contains("\r"))) {
            style = ScalarStyle.LITERAL;
        }

        // 2. Block Literal (|) and Folded (>)
        if ((style == ScalarStyle.LITERAL || style == ScalarStyle.FOLDED) && !isFlow) {
            appendText(style == ScalarStyle.LITERAL ? "|" : ">");
            appendText(NL);

            int blockIndent = indent + options.getIndentSize();
            String indentation = " ".repeat(blockIndent);

            String[] lines = stringValue.split("\\R", -1);
            int limit = lines.length;

            // Safe end-of-string newline clipping
            if (limit > 0 && lines[limit - 1].isEmpty()) {
                limit--;
            }

            for (int i = 0; i < limit; i++) {
                appendText(indentation);
                appendText(lines[i]);
                appendText(NL);
            }
            return;
        }

        if (!isFlow) appendText(" ".repeat(indent));

        String content = formatAndIndentMultiline(stringValue, style, indent);
        appendText(content);

        // IF NOT IS FLOW, this is a standalone root scalar or similar
        if (!isFlow) {
            if (options.outputComments()) {
                handleInlineComments(scalar);
            }
            appendText(NL);
        }
    }

    private String formatAndIndentMultiline(String text, ScalarStyle style, int amount) {
        if (text == null) return "";

        // Match the original plain scalar fallback rule:
        // If it's plain but unsafe (like containing a newline), force it to DOUBLE quotes.
        if (style == ScalarStyle.PLAIN && !isSafePlain(text)) {
            style = ScalarStyle.DOUBLE_QUOTED;
        }

        if (text.isEmpty()) {
            if (style == ScalarStyle.DOUBLE_QUOTED) return "\"\"";
            if (style == ScalarStyle.SINGLE_QUOTED) return "''";
            return "";
        }

        // Split preserving trailing empty lines
        String[] lines = text.split("\\R", -1);
        String indentation = " ".repeat(amount);
        StringBuilder result = new StringBuilder();

        if (style == ScalarStyle.DOUBLE_QUOTED) {
            result.append("\"");
        } else if (style == ScalarStyle.SINGLE_QUOTED) {
            result.append("'");
        }

        for (int i = 0; i < lines.length; i++) {
            String processedLine;
            if (style == ScalarStyle.DOUBLE_QUOTED) {
                processedLine = escapeDoubleQuotesInline(lines[i]);
            } else if (style == ScalarStyle.SINGLE_QUOTED) {
                processedLine = lines[i].replace("'", "''");
            } else {
                processedLine = lines[i];
            }

            result.append(processedLine);

            // Only append line breaks and indentation if this isn't the absolute last element
            if (i < lines.length - 1) {
                result.append(NL).append(indentation);
            }
        }

        if (style == ScalarStyle.DOUBLE_QUOTED) {
            result.append("\"");
        } else if (style == ScalarStyle.SINGLE_QUOTED) {
            result.append("'");
        }

        return result.toString();
    }

    private String escapeDoubleQuotesInline(String value) {
        if (value == null) return "";
        // Only escape literal backslashes and quotes; literal inline \n and \r checks are omitted
        // here because they are handled structurally by the line splitter.
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /// Iterates over map entries, managing key-value pairs and block/flow transitions.
    private void emitMap(YamlMap map, int indent, boolean isSequenceItem) {
        if (map == null) return;
        var entries = map.getEntries();
        if (options.sortKeys()) {
            entries = new ArrayList<>(entries);
            entries.sort(
                Comparator.comparing(
                    MapEntryAstNode::getKeyString,
                    Comparator.nullsLast(String::compareTo)
                )
            );
        }
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);

            if (i > 0 || !isSequenceItem) {
                appendText(" ".repeat(indent));
            }

            YamlNode key = entry.getKey();

            for (CommentAstNode c : key.getComments()) {
                if (c instanceof YamlComment ycn && ycn.getStartLine() < key.getStartLine()) {
                    appendText("#");
                    appendText(ycn.asString());
                    appendText(NL);
                    appendText(" ".repeat(indent));
                }
            }

            // Determine if this key requires an explicit complex layout block ('?')
            boolean isComplexKey = (key instanceof YamlMap m && m.getNodeStyle() == NodeStyle.BLOCK) ||
                                  (key instanceof YamlSequence s && s.getNodeStyle() == NodeStyle.BLOCK);

            if (isComplexKey) {
                appendText("?");
                appendText(NL);
                // Emit the nested block key with deeper indentation
                emitNode(key, indent + options.getIndentSize(), false, false);
                // Align the property indicator back to the current map entry indentation level
                appendText(" ".repeat(indent));
                appendText(":");
            } else {
                // Keep standard flat emission for plain scalar labels
                emitNode(key, 0, false, true);
                if (key instanceof YamlAlias) {
                    appendText(" ");
                }
                appendText(":");
            }

            YamlNode value = entry.getValue();
            boolean isBlock = (value instanceof YamlMap m && m.getNodeStyle() == NodeStyle.BLOCK) ||
                              (value instanceof YamlSequence s && s.getNodeStyle() == NodeStyle.BLOCK);

            if (isBlock) {
                if (!isComplexKey) {
                    if (options.outputComments()) {
                        handleInlineComments(key);
                    }
                }
                appendText(NL);
                emitNode(value, indent + options.getIndentSize(), false, false);
            }
            // Handle multiline string block scalars cleanly (only for plain/block targeted text)
            else if (value instanceof YamlScalar scalar
                    && scalar.getQuoteStyle() != QuoteStyle.DOUBLE
                    && scalar.getQuoteStyle() != QuoteStyle.SINGLE
                    && scalar.asString() != null
                    && (scalar.asString().contains("\n") || scalar.asString().contains("\r"))) {
                if (!isComplexKey) {
                    if (options.outputComments()) {
                        handleInlineComments(key);
                    }
                }

                appendText(" ");
                emitScalarInternal(scalar, indent + options.getIndentSize(), false);
            }
            else {
                if (!isImplicitNull(value)) appendText(" ");

                // emitNode(value, 0, false, true);
                emitNode(value, indent + options.getIndentSize(), false, true);

                if (options.outputComments()) {
                    handleInlineComments(value);
                }

                YamlNode targetNode = value;
                // if (value instanceof YamlAnchor anchor) {
                //     targetNode = anchor.getInnerNode();
                // }

                if (!(targetNode instanceof YamlMap)) {
                    appendText(NL);
                }
            }
        }
    }

    /// Emits a block sequence with support for "compact" vs "expanded" styles.
    ///
    /// Compact (Default):
    /// ```yaml
    /// - key: value
    /// ```
    ///
    /// Expanded:
    /// ```yaml
    /// -
    ///   key: value
    /// ```
    private void emitSequence(YamlSequence seq, int indent, boolean isSequenceItem) {
        if (seq == null) return;
        var elements = seq.getElements();
        for (int i = 0; i < elements.size(); i++) {
            var item = elements.get(i);

            // 1. ALWAYS write the indentation and the dash for every element
            if (!(i == 0 && isSequenceItem)) {
                appendText(" ".repeat(indent));
            }
            appendText("-");

            // 2. Now decide how to handle the VALUE after that dash
            if (options.outputExpandedStyle()) {
                if (isImplicitNull(item)) {
                    // It's a null value in expanded style.
                    // We just need the newline to finish this item's line.
                    appendText(NL);
                } else {
                    appendText(NL);
                    // Handle flow vs block indentation
                    handleExpandedItem(item, indent);
                }
            }
            else if (item instanceof YamlMap m && m.getNodeStyle() == NodeStyle.BLOCK) {
                appendText(" ");
                emitMap(m, indent + 2, true);
            }
            else if (item instanceof YamlSequence s && s.getNodeStyle() == NodeStyle.BLOCK) {
                appendText(NL);
                emitNode(item, indent + options.getIndentSize(), false, false);
            }
            else {
                // COMPACT / FLOW / SCALAR
                if (item instanceof YamlScalar scalar
                        && scalar.getQuoteStyle() != QuoteStyle.DOUBLE
                        && scalar.getQuoteStyle() != QuoteStyle.SINGLE
                        && scalar.asString() != null
                        && (scalar.asString().contains("\n") || scalar.asString().contains("\r"))) {
                    // It's a multiline string scalar! It CANNOT be inline/flowed after a compact dash.
                    // It must trigger a newline and follow block formatting guidelines.
                    appendText(NL);
                    emitScalarInternal(scalar, indent + options.getIndentSize(), false);
                }
                else {
                    // True compact inline scalars / flow collections
                    if (!isImplicitNull(item)) {
                        appendText(" ");
                    }

                    // Force isFlow=true only for single lines / flow structures
                    emitNode(item, 0, true, true);
                    if (options.outputComments()) {
                        handleInlineComments(item);
                    }
                    appendText(NL);
                }
            }
        }
    }

    private boolean isImplicitNull(YamlNode node) {
        return node instanceof YamlScalar s && s.asString() == null;
    }

    private void handleExpandedItem(YamlNode item, int indent) {
        boolean itemIsFlow = item.getNodeStyle() == NodeStyle.FLOW;

        if (itemIsFlow) {
            appendText(" ".repeat(indent + options.getIndentSize()));
            emitNode(item, 0, false, true);
            appendText(NL);
        } else {
            emitNode(item, indent + options.getIndentSize(), false, false);
        }
    }

    private void emitFlowMap(YamlMap map) {
        if (map == null) return;
        appendText("{");
        var entries = map.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.getKey() instanceof YamlScalar s) appendText(s.asString());
            appendText(": ");
            emitNode(entry.getValue(), 0, false, true);
            if (i < entries.size() - 1) appendText(", ");
        }
        appendText("}");
    }

    private void emitFlowSequence(YamlSequence seq) {
        if (seq == null) return;
        appendText("[");
        var items = seq.getElements();
        for (int i = 0; i < items.size(); i++) {
            emitNode(items.get(i), 0, false, true);
            if (i < items.size() - 1) appendText(", ");
        }
        appendText("]");
    }

    /// Emits comments that were identified by the parser as being on their own line.
    private void emitBlockComments(YamlNode node, int indent) {
        if (node == null) return;
        for (CommentAstNode comment : node.getComments()) {
            if (comment instanceof YamlComment ycn && ycn.getStartColumn() <= 1) {
                appendText(" ".repeat(indent));
                appendText("#");
                appendText(ycn.asString());
                appendText(NL);
            }
        }
    }

    /// Emits comments identified as "inline" (appended to the end of a data line).
    private void handleInlineComments(YamlNode node) {
        if (node == null) return;
        for (CommentAstNode comment : node.getComments()) {
            if (comment instanceof YamlComment ycn && ycn.getStartColumn() > 1) {
                appendText(" #");
                appendText(ycn.asString());
                break;
            }
        }
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

    private void appendText(String text) {
        sb.append(text);

        boolean debug = reporter != null &&
                        !reporter.isSilent() &&
                        reporter.getLevel().includes(Level.DEBUG);
        if (debug) {
            reporter.debug("StringBuidler:\n" + debugStringBuilder(sb, 2));
        }
    }

    private String debugStringBuilder(StringBuilder sb, int lines) {
        StringBuilder output = new StringBuilder();
        int length = sb.length();
        if (length < 2) {
            return StringUtils.debugString(sb.toString());
        }
        int line = 0;
        int lineEnd = length - 1;
        int preceedingNewline = -1;
        while (line < lines && lineEnd > 0) {
            preceedingNewline = sb.lastIndexOf("\n", lineEnd - 1);
            output.insert(0, "\n");
            int end = lineEnd < length ? lineEnd + 1 : lineEnd;
            String b = sb.substring(preceedingNewline + 1, end);
            output.insert(0, StringUtils.debugString(b));
            lineEnd = preceedingNewline;
            line++;
        }
        return output.toString();
    }

    private void debugOutput(String output) {
        if (reporter == null) return;
        reporter.trace("--- EMITTER DEBUG START ---");
        reporter.trace(output.replace(" ", "·").replace("\n", "↵\n"));
        reporter.trace("--- EMITTER DEBUG END ---");
    }
}