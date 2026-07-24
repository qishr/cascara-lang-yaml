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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.processor.Emitter;
import io.github.qishr.cascara.lang.yaml.ast.CollectionStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlAliasNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchorNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlCommentNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocumentNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlStreamNode;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

/// Responsible for converting a [YamlNode] AST back into a valid YAML string.
///
/// This emitter is high-fidelity: it prioritizes preserving the original [CollectionStyle]
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

    // TODO: emitTagIfPresent ?
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
    /// @param root AST root (can be a YamlStreamNode, YamlMapNode, YamlSequenceNode, or YamlScalarNode).
    /// @return A formatted YAML string.
    public String emit(YamlNode root) {
        writtenAnchors.clear();
        sb.setLength(0);

        if (root instanceof YamlStreamNode stream) {
            var documents = stream.getDocuments();
            for (int i = 0; i < documents.size(); i++) {
                YamlDocumentNode doc = documents.get(i);

                // Write explicit document markers if there are multiple documents,
                // or if the document explicitly contains directives.
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
        while (targetNode instanceof YamlAnchorNode wrapper) {
            targetNode = wrapper.getInnerNode();
        }

        // 2. ALIAS CHECK
        if (targetNode instanceof YamlAliasNode alias) {
            sb.append("*").append(alias.getAlias());
            return;
        }

        // 3. ANCHOR CHECK (Using targetNode to fetch anchor metadata safely)
        if (!options.stripAnchors()) {
            String anchor = targetNode.getAnchor();
            if (anchor != null && !anchor.isEmpty()) {
                sb.append("&").append(anchor);
                if (targetNode instanceof YamlScalarNode) sb.append(" ");
            }
        }

        // if (!isFlow) emitBlockComments(targetNode, indent);
        if (!options.stripComments() && !isFlow) {
            emitBlockComments(targetNode, indent);
        }

        // 4. STRUCTURAL EVALUATION (Checking targetNode instead of node)
        if (targetNode instanceof YamlScalarNode scalar) {
            // If we are a sequence item on the same line as the dash,
            // the dash and space ARE the indent for the first line.
            int scalarIndent = isSequenceItem ? 0 : indent;
            emitScalarInternal(scalar, scalarIndent, isFlow, false);
        } else if (targetNode instanceof YamlMapNode map) {
            if (map.getStyle() == CollectionStyle.FLOW) {
                emitFlowMap(map);
                if (!isFlow) sb.append(NL);
            } else {
                emitMap(map, indent, isSequenceItem);
            }
        } else if (targetNode instanceof YamlSequenceNode seq) {
            if (seq.getStyle() == CollectionStyle.FLOW) {
                emitFlowSequence(seq);
                if (!isFlow) sb.append(NL);
            } else {
                emitSequence(seq, indent, isSequenceItem);
            }
        }
    }

    /// Handles scalar formatting including Literal (|), Folded (>), and quoted styles.
    private void emitScalarInternal(YamlScalarNode scalar, int indent, boolean isFlow, boolean isBlockStyle) {
        if (scalar == null) return; // TODO: literal null

        // USE LEXEME ONLY FOR QUOTED SCALARS WITH NON-EMPTY LEXEME
        if (scalar.getToken() != null &&
            scalar.getToken().getLexeme() != null &&
            !scalar.getToken().getLexeme().isEmpty() &&
            (scalar.getQuoteStyle() == QuoteStyle.DOUBLE ||
             scalar.getQuoteStyle() == QuoteStyle.SINGLE)) {

            if (!isFlow) sb.append(" ".repeat(indent));
            sb.append(scalar.getToken().getLexeme());

            if (!isFlow && !options.stripComments()) {
                handleInlineComments(scalar);
            }
            return;
        }

        String val = scalar.asString();

        // 1. Implicit Null
        if (val == null) {
            if (!isFlow) sb.append(" ".repeat(indent));
            return;
        }

        QuoteStyle style;
        if (options.normalizeScalarFormatting()) {
            style = QuoteStyle.DOUBLE;
        } else {
            style = scalar.getQuoteStyle();
        }

        // AUTO-PROMOTION: If the style is PLAIN but the text contains newlines,
        // force it to LITERAL_BLOCK so it serializes into a valid block scalar.
        if (!isFlow && style == QuoteStyle.PLAIN && (val.contains("\n") || val.contains("\r"))) {
            style = QuoteStyle.LITERAL_BLOCK;
        }

        // 2. Block Literal (|) and Folded (>)
        if ((style == QuoteStyle.LITERAL_BLOCK || style == QuoteStyle.FOLDED) && !isFlow) {
            sb.append(style == QuoteStyle.LITERAL_BLOCK ? "|" : ">").append(NL);

            int blockIndent = indent + options.getIndentSize();
            String indentation = " ".repeat(blockIndent);

            String[] lines = val.split("\\R", -1);
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

        String content = formatAndIndentMultiline(val, style, indent);
        sb.append(content);

        // IF NOT IS FLOW, this is a standalone root scalar or similar
        if (!isFlow) {
            // handleInlineComments(scalar);
            if (!options.stripComments()) {
                handleInlineComments(scalar);
            }
            sb.append(NL);
        }
    }

    private String formatAndIndentMultiline(String text, QuoteStyle style, int amount) {
        if (text == null) return "";

        // Match the original plain scalar fallback rule:
        // If it's plain but unsafe (like containing a newline), force it to DOUBLE quotes.
        if (style == QuoteStyle.PLAIN && !isSafePlain(text)) {
            style = QuoteStyle.DOUBLE;
        }

        if (text.isEmpty()) {
            if (style == QuoteStyle.DOUBLE) return "\"\"";
            if (style == QuoteStyle.SINGLE) return "''";
            return "";
        }

        // Split preserving trailing empty lines
        String[] lines = text.split("\\R", -1);
        String indentation = " ".repeat(amount);
        StringBuilder result = new StringBuilder();

        if (style == QuoteStyle.DOUBLE) {
            result.append("\"");
        } else if (style == QuoteStyle.SINGLE) {
            result.append("'");
        }

        for (int i = 0; i < lines.length; i++) {
            String processedLine;
            if (style == QuoteStyle.DOUBLE) {
                processedLine = escapeDoubleQuotesInline(lines[i]);
            } else if (style == QuoteStyle.SINGLE) {
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

        if (style == QuoteStyle.DOUBLE) {
            result.append("\"");
        } else if (style == QuoteStyle.SINGLE) {
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
    private void emitMap(YamlMapNode map, int indent, boolean isSequenceItem) {
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
                if (c instanceof YamlCommentNode ycn && ycn.getStartLine() < key.getStartLine()) {
                    sb.append("#").append(ycn.asString()).append(NL).append(" ".repeat(indent));
                }
            }

            // Determine if this key requires an explicit complex layout block ('?')
            boolean isComplexKey = (key instanceof YamlMapNode m && m.getStyle() == CollectionStyle.BLOCK) ||
                                  (key instanceof YamlSequenceNode s && s.getStyle() == CollectionStyle.BLOCK);

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
            boolean isBlock = (value instanceof YamlMapNode m && m.getStyle() == CollectionStyle.BLOCK) ||
                              (value instanceof YamlSequenceNode s && s.getStyle() == CollectionStyle.BLOCK);

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
            else if (value instanceof YamlScalarNode scalar
                && (scalar.getQuoteStyle() == QuoteStyle.LITERAL_BLOCK ||
                    scalar.getQuoteStyle() == QuoteStyle.FOLDED)) {

                sb.append(" ");
                // pass the current map indent; block body indent is computed inside
                emitScalarInternal(scalar, indent, false, true);
            }
            else if (value instanceof YamlScalarNode scalar
                && scalar.getQuoteStyle() == QuoteStyle.PLAIN
                && scalar.asString() != null
                && (scalar.asString().contains("\n") || scalar.asString().contains("\r"))) {

                sb.append(" ");
                emitScalarInternal(scalar, indent + options.getIndentSize(), false, false);
            }
            else {
                if (!isImplicitNull(value)) sb.append(" ");
                emitNode(value, 0, false, true);   // <‑‑ inline, no indent, flow=true
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
    private void emitSequence(YamlSequenceNode seq, int indent, boolean isSequenceItem) {
        if (seq == null) return;
        var elements = seq.getElements();

        for (int i = 0; i < elements.size(); i++) {
            var item = elements.get(i);

            // dash indentation
            if (!(i == 0 && isSequenceItem)) {
                sb.append(" ".repeat(indent));
            }
            sb.append("-");

            // --- CASE 1: expanded style only applies to block maps/sequences ---
            if (options.isExpandedStyle()
                    && !(item instanceof YamlScalarNode)) {

                sb.append(NL);
                handleExpandedItem(item, indent);
                continue;
            }

            // --- CASE 2: block map under dash (compact) ---
            if (item instanceof YamlMapNode m
                    && m.getStyle() == CollectionStyle.BLOCK) {

                sb.append(" ");
                emitMap(m, indent + 2, true);
                continue;
            }

            // --- CASE 3: block sequence under dash (compact) ---
            if (item instanceof YamlSequenceNode s
                    && s.getStyle() == CollectionStyle.BLOCK) {

                sb.append(NL);
                emitNode(item, indent + options.getIndentSize(), false, false);
                continue;
            }

            // --- CASE 4: multiline scalar (must break) ---
            if (item instanceof YamlScalarNode scalar
                    && scalar.asString() != null
                    && (scalar.asString().contains("\n") || scalar.asString().contains("\r"))) {

                sb.append(NL);
                emitScalarInternal(scalar, indent + options.getIndentSize(), false, false);
                continue;
            }

            // --- CASE 5: SIMPLE SCALAR (compact) ---
            if (!isImplicitNull(item)) {
                sb.append(" ");
                emitNode(item, 0, true, true);   // inline scalar
            }

            if (!options.stripComments()) {
                handleInlineComments(item);
            }

            sb.append(NL);
        }
    }

    private boolean isImplicitNull(YamlNode node) {
        return node instanceof YamlScalarNode s && s.asString() == null;
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
            if (entry.getKey() instanceof YamlScalarNode s) sb.append(s.asString());
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
                sb.append(" ".repeat(indent)).append("#").append(ycn.asString()).append(NL);
            }
        }
    }

    /// Emits comments identified as "inline" (appended to the end of a data line).
    private void handleInlineComments(YamlNode node) {
        if (node == null) return;
        for (CommentAstNode comment : node.getComments()) {
            if (comment instanceof YamlCommentNode ycn && ycn.getStartColumn() > 1) {
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

    //
    // Test
    //

    public static void main(String[] args) {
        // new YamlEmitter().valid_03_multiline_strings();
        // new YamlEmitter().valid_12_content_type_records();
        test_emitter_lexeme();
    }

    private static String VALID_PATH = "src/test/resources/yaml-suite/valid";
    private YamlOptions options = new YamlOptions().setExpandedStyle(true);
    private YamlAstParser parser;
    private Reporter reporter;

    void test_init() {
        options = new YamlOptions().setStrict(true);
        parser = new YamlAstParser()
            .setOptions(options);
    }

    static void test_emitter_lexeme() {
        String yaml = "key:\n  \"one\n\n  two\"";
        YamlAstParser parser = new YamlAstParser().setReporter(new StandardReporter().setLevel(Level.TRACE));
        YamlNode root = parser.parse(yaml);

        YamlEmitter emitter = new YamlEmitter();
        String output = emitter.emit(root);

        if (!yaml.equals(output)) {
            System.out.println("=== INPUT YAML ===");
            System.out.println(Util.debugString(yaml));
            System.out.println("=== EMITTED YAML ===");
            System.out.println(Util.debugString(output));
        }
    }

    // void valid_file_03_multiline_strings() {
    //     validateFile("03-multiline-strings.yaml");
    // }

    void valid_03_multiline_strings() {
        validate("""
            description: |
              This is a multiline
              string with preserved
              newlines.
            """);
    }

    // void valid_04_empty_edge_cases() {
    //     validateFile("04-empty-edge-cases.yaml");
    // }

    // void valid_09_empty_collections() {
    //     validateFile("09-empty-collections.yaml");
    // }

    void valid_12_content_type_records() {
        validateFile("12-content-type-records.yaml");
    }

    private void validateFile(String filename) {
        Path path = Paths.get(VALID_PATH, filename);
        String fileContent = null;
        try {
            fileContent = Files.readString(path);
        } catch (IOException e) {
            System.err.println("Unable to read YAML file " + filename);
            return;
        }
        validate(fileContent);
    }

    private void validate(String fileContent) {
        test_init();

        YamlEmitter emitter = new YamlEmitter().setOptions(options);

        YamlMapNode firstAst = (YamlMapNode) parser.parse(fileContent);
        String firstYaml = emitter.emit(firstAst);


        YamlMapNode secondAst;

        try {
            secondAst = (YamlMapNode) parser.parse(firstYaml);
        } catch (RuntimeException e) {
            System.out.println("Failed to parse emitted YAML");
            System.out.println("\n=== FIRST EMIT ===");
            System.out.println(Util.debugString(firstYaml));
            System.out.println("\n=== FIRST EMIT PLAIN TEXT ===");
            System.out.println(firstYaml);
            traceParser(firstYaml, "Emitted Content");
            return;
        }

        String secondYaml = emitter.emit(secondAst);

        if (!firstAst.equals(secondAst)) {
            System.out.println("validate ast mismatch");
            validateError(fileContent, firstAst, secondAst, firstYaml, secondYaml);
            return;
        }

        if (!firstYaml.equals(secondYaml)) {
            System.out.println("validate content mismatch");
            validateError(fileContent, firstAst, secondAst, firstYaml, secondYaml);
            return;
        }
    }

    private void traceParser(String content, String title) {
        System.out.println("\n=== Parser Trace for " + title + " ===");
        Reporter reporter = new StandardReporter()
            .setLevel(Level.DEBUG)
            .setDisableFlush(false);
        YamlAstParser parser = new YamlAstParser().setOptions(options).setReporter(reporter);
        parser.parse(content);
        Util.dumpTokens(parser.getTokens());
        System.out.flush();
    }

    private void validateError(String fileContent, YamlNode firstAst, YamlNode secondAst, String firstYaml, String secondYaml) {
        System.out.println("\nFile content:");
        System.out.println(Util.debugString(fileContent));

        traceParser(fileContent, "File Content");
        traceParser(firstYaml, "Emitted Content");

        System.out.println("\n=== First AST ===");
        Util.dumpYamlAst(firstAst, "");
        System.out.println("\n=== Second AST ===");
        Util.dumpYamlAst(secondAst, "");

        // System.err.println("\n" + generateDiffMessage(filename, firstYaml, secondYaml) + "\n");

        System.out.println("\n=== FIRST EMIT ===");
        System.out.println(Util.debugString(firstYaml));
        System.out.println("\n=== SECOND EMIT ===");
        System.out.println(Util.debugString(secondYaml));
    }
}