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

import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.processor.Emitter;
import io.github.qishr.cascara.lang.yaml.ast.NodeStyle;
import io.github.qishr.cascara.lang.yaml.ast.ScalarStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchor;
import io.github.qishr.cascara.lang.yaml.ast.YamlComment;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;

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

    // TODO: emitTagIfPresent
    @Override public void emitScalar(String value) { sb.append(value); }
    @Override public void emitMapStart() {}
    @Override public void emitMapEnd() {}
    @Override public void emitSequenceStart() {}
    @Override public void emitSequenceEnd() {}
    @Override public void emitPropertySeparator() { sb.append(": "); }
    @Override public void emitItemSeparator() {}
    @Override public void emitNewLine() { sb.append(NL); }
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

                // Write explicit document markers if there are multiple documents,
                // or if the document explicitly contains directives.
                // if (documents.size() > 1 || !doc.getDirectives().isEmpty()) {
                //     sb.append("---").append(NL);
                // }
                if (options.isExplicitStart() || documents.size() > 1 || !doc.getDirectives().isEmpty()) {
                    sb.append("---").append(NL);
                }


                // Process the body of this specific document
                emitNode(doc.getBody(), 0, false, false);

                // Append a newline between documents if we aren't at the very end
                if (i < documents.size() - 1 && sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append(NL);
                }
            }
        } else {
            // Fallback for direct single-node emission
            emitNode(root, 0, false, false);
        }

        debugOutput(sb.toString());
        return sb.toString();
    }

    /// Recursive dispatcher for AST nodes.
    ///
    /// @param node The current node to emit.
    /// @param indent The current base indentation level.
    /// @param isSequenceItem True if this node is the direct value of a sequence dash.
    /// @param isFlow True if we are inside a flow context (prevents forced newlines).
    private void emitNode(YamlNode node, int indent, boolean isSequenceItem, boolean isFlow) {
        if (node == null) return;

        // 1. Extract the actual target data node if wrapped in an Anchor decorator
        YamlNode targetNode = node;
        while (targetNode instanceof YamlAnchor wrapper) {
            targetNode = wrapper.getInnerNode();
        }

        // 2. ALIAS CHECK
        if (targetNode instanceof YamlAlias alias) {
            sb.append("*").append(alias.getAlias());
            return;
        }

        // 3. ANCHOR CHECK (Using targetNode to fetch anchor metadata safely)
        // String anchor = targetNode.getAnchor();
        // if (anchor != null && !anchor.isEmpty()) {
        //     sb.append("&").append(anchor);
        //     if (targetNode instanceof YamlScalarNode) sb.append(" ");
        // }
        if (!options.stripAnchors()) {
            String anchor = targetNode.getAnchor();
            if (anchor != null && !anchor.isEmpty()) {
                sb.append("&").append(anchor);
                if (targetNode instanceof YamlScalar) sb.append(" ");
            }
        }

        // if (!isFlow) emitBlockComments(targetNode, indent);
        if (!options.stripComments() && !isFlow) {
            emitBlockComments(targetNode, indent);
        }

        // 4. STRUCTURAL EVALUATION (Checking targetNode instead of node)
        if (targetNode instanceof YamlScalar scalar) {
            // If we are a sequence item on the same line as the dash,
            // the dash and space ARE the indent for the first line.
            int scalarIndent = isSequenceItem ? 0 : indent;
            emitScalarInternal(scalar, scalarIndent, isFlow);
        } else if (targetNode instanceof YamlMap map) {
            if (map.getStyle() == NodeStyle.FLOW) {
                emitFlowMap(map);
                if (!isFlow) sb.append(NL);
            } else {
                emitMap(map, indent, isSequenceItem);
            }
        } else if (targetNode instanceof YamlSequence seq) {
            if (seq.getStyle() == NodeStyle.FLOW) {
                emitFlowSequence(seq);
                if (!isFlow) sb.append(NL);
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
            if (!isFlow) sb.append(" ".repeat(indent));
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
                        sb.append("\n");
                        sb.append(" ".repeat(scalar.getStartColumn() - 1));
                    } else {
                    }
                    sb.append(lexemeLines[i]);
                }
                if (isBlock) {
                    sb.append(NL);
                }
            } else {
                if (!isFlow) sb.append(" ".repeat(indent));
                sb.append(lexeme);
            }

            if (!isFlow) {
                // handleInlineComments(scalar);
                if (!options.stripComments()) {
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
            sb.append(style == ScalarStyle.LITERAL ? "|" : ">").append(NL);

            int blockIndent = indent + options.getIndentSize();
            String indentation = " ".repeat(blockIndent);

            String[] lines = stringValue.split("\\R", -1);
            int limit = lines.length;

            // Safe end-of-string newline clipping
            if (limit > 0 && lines[limit - 1].isEmpty()) {
                limit--;
            }

            for (int i = 0; i < limit; i++) {
                sb.append(indentation).append(lines[i]).append(NL);
            }
            return;
        }

        if (!isFlow) sb.append(" ".repeat(indent));

        String content = formatAndIndentMultiline(stringValue, style, indent);
        sb.append(content);

        // IF NOT IS FLOW, this is a standalone root scalar or similar
        if (!isFlow) {
            if (!options.stripComments()) {
                handleInlineComments(scalar);
            }
            sb.append(NL);
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
            entries.sort(Comparator.comparing(e -> e.getKey().asString()));
        }
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);

            if (i > 0 || !isSequenceItem) {
                sb.append(" ".repeat(indent));
            }

            YamlNode key = entry.getKey();

            for (CommentAstNode c : key.getComments()) {
                if (c instanceof YamlComment ycn && ycn.getStartLine() < key.getStartLine()) {
                    sb.append("#").append(ycn.asString()).append(NL).append(" ".repeat(indent));
                }
            }

            // Determine if this key requires an explicit complex layout block ('?')
            boolean isComplexKey = (key instanceof YamlMap m && m.getStyle() == NodeStyle.BLOCK) ||
                                  (key instanceof YamlSequence s && s.getStyle() == NodeStyle.BLOCK);

            if (isComplexKey) {
                sb.append("?").append(NL);
                // Emit the nested block key with deeper indentation
                emitNode(key, indent + options.getIndentSize(), false, false);
                // Align the property indicator back to the current map entry indentation level
                sb.append(" ".repeat(indent)).append(":");
            } else {
                // Keep standard flat emission for plain scalar labels
                emitNode(key, 0, false, true);
                sb.append(":");
            }

            YamlNode value = entry.getValue();
            boolean isBlock = (value instanceof YamlMap m && m.getStyle() == NodeStyle.BLOCK) ||
                              (value instanceof YamlSequence s && s.getStyle() == NodeStyle.BLOCK);

            if (isBlock) {
                if (!isComplexKey) {
                    // handleInlineComments(key); // Comment for the key line
                    if (!options.stripComments()) {
                        handleInlineComments(key);
                    }
                }
                sb.append(NL);
                emitNode(value, indent + options.getIndentSize(), false, false);
            }
            // Handle multiline string block scalars cleanly (only for plain/block targeted text)
            else if (value instanceof YamlScalar scalar
                    && scalar.getQuoteStyle() != QuoteStyle.DOUBLE
                    && scalar.getQuoteStyle() != QuoteStyle.SINGLE
                    && scalar.asString() != null
                    && (scalar.asString().contains("\n") || scalar.asString().contains("\r"))) {
                if (!isComplexKey) {
                    // handleInlineComments(key);
                    if (!options.stripComments()) {
                        handleInlineComments(key);
                    }
                }

                sb.append(" ");
                emitScalarInternal(scalar, indent + options.getIndentSize(), false);
            }
            else {
                if (!isImplicitNull(value)) sb.append(" ");
                emitNode(value, 0, false, true); // Clean text
                // handleInlineComments(value);    // Value's inline comment
                if (!options.stripComments()) {
                    handleInlineComments(value);
                }
                sb.append(NL);
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
                sb.append(" ".repeat(indent));
            }
            sb.append("-");

            // 2. Now decide how to handle the VALUE after that dash
            if (options.isExpandedStyle()) {
                if (isImplicitNull(item)) {
                    // It's a null value in expanded style.
                    // We just need the newline to finish this item's line.
                    sb.append(NL);
                } else {
                    sb.append(NL);
                    // Handle flow vs block indentation
                    handleExpandedItem(item, indent);
                }
            }
            else if (item instanceof YamlMap m && m.getStyle() == NodeStyle.BLOCK) {
                sb.append(" ");
                emitMap(m, indent + 2, true);
            }
            else if (item instanceof YamlSequence s && s.getStyle() == NodeStyle.BLOCK) {
                sb.append(NL);
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
                    sb.append(NL);
                    emitScalarInternal(scalar, indent + options.getIndentSize(), false);
                }
                else {
                    // True compact inline scalars / flow collections
                    if (!isImplicitNull(item)) {
                        sb.append(" ");
                    }

                    // Force isFlow=true only for single lines / flow structures
                    emitNode(item, 0, true, true);
                    // handleInlineComments(item);
                    if (!options.stripComments()) {
                        handleInlineComments(item);
                    }
                    sb.append(NL);
                }
            }
        }
    }

    private boolean isImplicitNull(YamlNode node) {
        return node instanceof YamlScalar s && s.asString() == null;
    }

    private void handleExpandedItem(YamlNode item, int indent) {
        // boolean itemIsFlow = (item instanceof YamlMap m && m.getStyle() == NodeStyle.FLOW) ||
        //                      (item instanceof YamlSequenceNode s && s.getStyle() == NodeStyle.FLOW);

        boolean itemIsFlow = item.getNodeStyle() == NodeStyle.FLOW;

        if (itemIsFlow) {
            sb.append(" ".repeat(indent + options.getIndentSize()));
            emitNode(item, 0, false, true);
            sb.append(NL);
        } else {
            emitNode(item, indent + options.getIndentSize(), false, false);
            // sb.append(NL);
        }
    }

    private void emitFlowMap(YamlMap map) {
        if (map == null) return;
        sb.append("{");
        var entries = map.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.getKey() instanceof YamlScalar s) sb.append(s.asString());
            sb.append(": ");
            emitNode(entry.getValue(), 0, false, true);
            if (i < entries.size() - 1) sb.append(", ");
        }
        sb.append("}");
    }

    private void emitFlowSequence(YamlSequence seq) {
        if (seq == null) return;
        sb.append("[");
        var items = seq.getElements();
        for (int i = 0; i < items.size(); i++) {
            emitNode(items.get(i), 0, false, true);
            if (i < items.size() - 1) sb.append(", ");
        }
        sb.append("]");
    }

    /// Emits comments that were identified by the parser as being on their own line.
    private void emitBlockComments(YamlNode node, int indent) {
        if (node == null) return;
        for (CommentAstNode comment : node.getComments()) {
            if (comment instanceof YamlComment ycn && ycn.getStartColumn() <= 1) {
                sb.append(" ".repeat(indent)).append("#").append(ycn.asString()).append(NL);
            }
        }
    }

    /// Emits comments identified as "inline" (appended to the end of a data line).
    private void handleInlineComments(YamlNode node) {
        if (node == null) return;
        for (CommentAstNode comment : node.getComments()) {
            if (comment instanceof YamlComment ycn && ycn.getStartColumn() > 1) {
                sb.append(" #").append(ycn.asString());
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

    private void debugOutput(String output) {
        if (reporter == null) return;
        reporter.trace("--- EMITTER DEBUG START ---");
        reporter.trace(output.replace(" ", "·").replace("\n", "↵\n"));
        reporter.trace("--- EMITTER DEBUG END ---");
    }
}