package io.github.qishr.cascara.lang.yaml.processor;

import java.util.HashSet;
import java.util.Set;

import io.github.qishr.cascara.common.lang.LanguageOptions;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.common.lang.ast.QuoteStyle;
import io.github.qishr.cascara.common.lang.processor.Emitter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.lang.yaml.YamlDocument;
import io.github.qishr.cascara.lang.yaml.YamlOptions;
import io.github.qishr.cascara.lang.yaml.ast.CollectionStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlAliasNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlCommentNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;

/// Responsible for converting a [YamlDocument] AST back into a valid YAML string.
///
/// This emitter is high-fidelity: it prioritizes preserving the original [CollectionStyle]
/// and [QuoteStyle] of nodes while ensuring that comments are placed correctly relative
/// to their owner nodes.
///
/// ### Indentation Logic
/// The emitter maintains a virtual column through the `indent` parameters passed
/// during recursive calls. It handles special cases like "compact" sequences where
/// the mapping starts on the same line as the sequence dash (`- key: value`).
public class YamlEmitter implements Emitter {
    private final StringBuilder sb = new StringBuilder();
    private final Set<String> writtenAnchors = new HashSet<>();
    private static final String NL = System.lineSeparator();
    private YamlOptions options = new YamlOptions();
    private Reporter reporter;

    public YamlEmitter() {
        // reporter = new StandardReporter().setLevel(Level.TRACE);
    }

    @Override
    public YamlEmitter setReporter(Reporter reporter) {
        this.reporter = reporter;
        return this;
    }

    @Override
    public YamlEmitter setOptions(LanguageOptions<?> options) {
        if (options instanceof YamlOptions yamlOptions) {
            this.options = yamlOptions;
        }
        return this;
    }

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

    /// Primary entry point for emitting a full document.
    ///
    /// @param doc The [YamlDocument] containing the AST and header comments.
    /// @return A formatted YAML string.
    public String emit(YamlDocument doc) {
        if (doc == null) return "";
        sb.setLength(0);
        emitBlockComments(doc, 0);
        if (doc.getRoot() != null) {
            emitNode(doc.getRoot(), 0, false, false);
        }
        debugOutput(sb.toString());
        return sb.toString();
    }

    public String emit(YamlNode root) {
        if (root instanceof YamlDocument doc) return emit(doc);
        writtenAnchors.clear();
        sb.setLength(0);
        emitNode(root, 0, false, false);
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
        // 1. ANCHOR CHECK
        String anchor = node.getAnchor();
        if (anchor != null && !anchor.isEmpty() && !(node instanceof YamlAliasNode)) {
            sb.append("&").append(anchor);
            if (node instanceof YamlScalarNode) sb.append(" ");
        }

        // 2. ALIAS CHECK
        if (node instanceof YamlAliasNode alias) {
            sb.append("*").append(alias.getAlias());
            return;
        }

        if (!isFlow) emitBlockComments(node, indent);

        if (node instanceof YamlScalarNode scalar) {
            // If we are a sequence item on the same line as the dash,
            // the dash and space ARE the indent for the first line.
            int scalarIndent = isSequenceItem ? 0 : indent;
            emitScalarInternal(scalar, scalarIndent, isFlow);
        } else if (node instanceof YamlMapNode map) {
            if (map.getStyle() == CollectionStyle.FLOW) {
                emitFlowMap(map);
                if (!isFlow) sb.append(NL);
            } else {
                emitMap(map, indent, isSequenceItem);
            }
        } else if (node instanceof YamlSequenceNode seq) {
            if (seq.getStyle() == CollectionStyle.FLOW) {
                emitFlowSequence(seq);
                if (!isFlow) sb.append(NL);
            } else {
                emitSequence(seq, indent, isSequenceItem);
            }
        }
    }

    /// Handles scalar formatting including Literal (|) and quoted styles.
    private void emitScalarInternal(YamlScalarNode scalar, int indent, boolean isFlow) {
        if (scalar == null) return; // TODO: literal null
        String val = scalar.getString();

        // 1. Implicit Null
        if (val == null) {
            if (!isFlow) sb.append(" ".repeat(indent));
            // We do NOT handle comments or NL here anymore; let the caller decide
            return;
        }

        // 2. Block Literal (|)
        QuoteStyle style = scalar.getQuoteStyle();
        if (style == QuoteStyle.LITERAL && !isFlow) {
            sb.append("|").append(NL);
            int blockIndent = indent + options.getIndentSize();
            String indentation = " ".repeat(blockIndent);
            String[] lines = val.split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].isEmpty()) sb.append(indentation);
                sb.append(lines[i]);
                if (i < lines.length - 1) sb.append(NL);
            }
            return; // Caller handles the final NL
        }

        // 3. Plain/Quoted
        if (!isFlow) sb.append(" ".repeat(indent));
        String content;
        if (style == QuoteStyle.DOUBLE) {
            content = "\"" + indentSubsequentLines(escapeDoubleQuotes(val), indent) + "\"";
        } else if (style == QuoteStyle.SINGLE) {
            content = "'" + indentSubsequentLines(val.replace("'", "''"), indent) + "'";
        } else {
            content = isSafePlain(val) ? indentSubsequentLines(val, indent)
                                       : "\"" + indentSubsequentLines(escapeDoubleQuotes(val), indent) + "\"";
        }
        sb.append(content);

        // IF NOT IS FLOW, this is a standalone root scalar or similar
        if (!isFlow) {
            handleInlineComments(scalar);
            sb.append(NL);
        }
    }

    /// Iterates over map entries, managing key-value pairs and block/flow transitions.
    private void emitMap(YamlMapNode map, int indent, boolean isSequenceItem) {
        if (map == null) return;
        var entries = map.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);

            if (i > 0 || !isSequenceItem) {
                sb.append(" ".repeat(indent));
            }

            YamlNode key = entry.getKey();

            for (CommentAstNode c : key.getComments()) {
                if (c instanceof YamlCommentNode ycn && ycn.getStartLine() < key.getStartLine()) {
                    // If it's not the first entry, we already added indentation.
                    // If it is, we need to add it now for the comment.
                    sb.append("# ").append(ycn.getString()).append(NL).append(" ".repeat(indent));
                }
            }

            emitNode(key, 0, false, true); // Clean text emission
            sb.append(":");

            YamlNode value = entry.getValue();
            boolean isBlock = (value instanceof YamlMapNode m && m.getStyle() == CollectionStyle.BLOCK) ||
                              (value instanceof YamlSequenceNode s && s.getStyle() == CollectionStyle.BLOCK);

            if (isBlock) {
                handleInlineComments(key); // Comment for the key line
                sb.append(NL);
                emitNode(value, indent + options.getIndentSize(), false, false);
            } else {
                if (!isImplicitNull(value)) sb.append(" ");

                emitNode(value, 0, false, true); // Clean text
                handleInlineComments(value);    // Value's inline comment
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
    private void emitSequence(YamlSequenceNode seq, int indent, boolean isSequenceItem) {
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
            else if (item instanceof YamlMapNode m && m.getStyle() == CollectionStyle.BLOCK) {
                sb.append(" ");
                emitMap(m, indent + 2, true);
            }
            else if (item instanceof YamlSequenceNode s && s.getStyle() == CollectionStyle.BLOCK) {
                sb.append(NL);
                emitNode(item, indent + options.getIndentSize(), false, false);
            }
            else {
                // COMPACT / FLOW / SCALAR
                if (!isImplicitNull(item)) {
                    sb.append(" ");
                }

                // 1. Force isFlow=true so emitScalarInternal ONLY prints the text
                emitNode(item, 0, true, true);

                // 2. Manually handle the comment for the item here
                handleInlineComments(item);

                // 3. ALWAYS append the newline here.
                // This ensures the line is closed regardless of what the item was.
                sb.append(NL);
            }
        }
    }

    private boolean isImplicitNull(YamlNode node) {
        return node instanceof YamlScalarNode s && s.getString() == null;
    }

    private void handleExpandedItem(YamlNode item, int indent) {
        boolean itemIsFlow = (item instanceof YamlMapNode m && m.getStyle() == CollectionStyle.FLOW) ||
                             (item instanceof YamlSequenceNode s && s.getStyle() == CollectionStyle.FLOW);

        if (itemIsFlow) {
            sb.append(" ".repeat(indent + options.getIndentSize()));
            emitNode(item, 0, false, true);
            sb.append(NL);
        } else {
            emitNode(item, indent + options.getIndentSize(), false, false);
        }
    }

    private void emitFlowMap(YamlMapNode map) {
        if (map == null) return;
        sb.append("{");
        var entries = map.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.getKey() instanceof YamlScalarNode s) sb.append(s.getString());
            sb.append(": ");
            emitNode(entry.getValue(), 0, false, true);
            if (i < entries.size() - 1) sb.append(", ");
        }
        sb.append("}");
    }

    private void emitFlowSequence(YamlSequenceNode seq) {
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
            if (comment instanceof YamlCommentNode ycn && ycn.getStartColumn() <= 1) {
                sb.append(" ".repeat(indent)).append("# ").append(ycn.getString()).append(NL);
            }
        }
    }

    /// Emits comments identified as "inline" (appended to the end of a data line).
    private void handleInlineComments(YamlNode node) {
        if (node == null) return;
        for (CommentAstNode comment : node.getComments()) {
            if (comment instanceof YamlCommentNode ycn && ycn.getStartColumn() > 1) {
                sb.append(" # ").append(ycn.getString());
                break;
            }
        }
    }

    private String indentSubsequentLines(String text, int amount) {
        if (text == null || text.isEmpty()) return "";
        String[] lines = text.split("\\R");
        if (lines.length <= 1) return text;
        String indentation = " ".repeat(amount);
        StringBuilder result = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            result.append(NL).append(indentation).append(lines[i]);
        }
        return result.toString();
    }

    private String escapeDoubleQuotes(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private boolean isSafePlain(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.contains("\n") || s.contains("\r")) return false;
        if (s.matches("^(true|false|null|True|False|NULL)$")) return true;
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