package io.github.qishr.cascara.lang.yaml.util;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchor;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
import io.github.qishr.cascara.lang.yaml.diagnostic.YamlDiagnosticCode;

public class YamlAliasResolver {

    private Reporter reporter = new NoOpReporter();
    private int depth;

    public YamlAliasResolver() {

    }

    public YamlAliasResolver setReporter(Reporter reporter) {
        this.reporter = reporter;
        return this;
    }

    /**
     * Traverses the document to collect all anchors, then replaces
     * all YamlAlias nodes with their dereferenced target nodes.
     */
    public YamlNode resolve(YamlNode root) {
        if (root == null) return null;
        YamlNode resolved = resolve(root, 0);
        if (resolved == null) {
            reporter.error(YamlDiagnosticCode.DEPTH_LIMIT);
        }
        return resolved;
    }

    private YamlNode resolve(YamlNode root, int depth) {
        Map<String, YamlNode> anchorMap = new HashMap<>();
        collectAnchors(root, anchorMap);
        return resolveNode(root, anchorMap, depth);
    }

    /**
     * Resolves aliases across all documents in a stream, maintaining a single
     * anchor mapping table across document boundaries if needed.
     */
    public YamlStream resolve(YamlStream stream) {
        if (stream == null) return null;
        YamlStream resolved = resolve(stream, 0);
        if (resolved == null) {
            reporter.error(YamlDiagnosticCode.DEPTH_LIMIT);
        }
        return resolved;
    }

    private YamlStream resolve(YamlStream stream, int depth) {

        List<YamlDocument> resolvedDocs = new ArrayList<>();

        for (YamlDocument doc : stream.getDocuments()) {
            YamlNode resolvedBody = resolve(doc.getBody());

            // Reconstruct the document with the resolved body
            // (Adjust constructor/setters to match your YamlDocument API)
            YamlDocument resolvedDoc = new YamlDocument(
                doc.getToken(),
                resolvedBody,
                doc.getDirectives()
            );
            resolvedDocs.add(resolvedDoc);
        }

        return new YamlStream(stream.getToken(), resolvedDocs);
    }

    private void collectAnchors(YamlNode node, Map<String, YamlNode> anchorMap) {
        if (node == null) return;

        // // 1. Check if the node is wrapped in a explicit YamlAnchor node
        // if (node instanceof YamlAnchor anchor) {
        //     if (anchor.getName() != null) {
        //         anchorMap.put(anchor.getName(), anchor.getInnerNode());
        //         debug("Collected wrapper anchor: %s", anchor.getName());
        //     }
        //     collectAnchors(anchor.getInnerNode(), anchorMap);
        //     return;
        // }

        // 2. Check if the node (e.g. YamlScalar) carries an anchor property directly
        if (node.getAnchor() != null && !node.getAnchor().isEmpty()) {
            anchorMap.put(node.getAnchor(), node);
            debug("Collected property anchor: %s on node %s", node.getAnchor(), node);
        }

        // 3. Recurse through collections
        if (node instanceof YamlMap map) {
            debug("Recuring into map");
            for (YamlMapEntry entry : map.getEntries()) {
                debug("key = " + entry.getKey() + "  anchor = " + entry.getKey().getAnchor());
                collectAnchors(entry.getKey(), anchorMap);
                debug("val = " + entry.getValue() + "  anchor = " + entry.getValue().getAnchor());
                collectAnchors(entry.getValue(), anchorMap);
            }
        } else if (node instanceof YamlSequence seq) {
            debug("Recuring into seq");
            for (YamlNode child : seq.getChildren()) {
                collectAnchors(child, anchorMap);
            }
        }
    }

    private YamlNode resolveNode(YamlNode node, Map<String, YamlNode> anchorMap, int depth) {
        if (node == null) return null;

        // TODO: Read from options
        if (depth > 20) {
            return null;
        }

        // Handle Aliases
        if (node instanceof YamlAlias alias) {
            YamlNode target = alias.getResolvedNode();
            if (target == null && alias.getName() != null) {
                target = anchorMap.get(alias.getName());
            }

            if (target != null) {
                debug("Resolved alias '%s' to target node: %s", alias.getName(), target);
                // Recurse in case an alias points to another alias or anchored container
                return resolveNode(target, anchorMap, depth + 1);
            } else {
                debug("Warning: Unresolved alias '%s'", alias.getName());
            }
        }

        // // Handle YamlAnchor wrappers (unwrap them during resolution if desired)
        // if (node instanceof YamlAnchor anchor) {
        //     return resolveNode(anchor.getInnerNode(), anchorMap, depth + 1);
        // }

        // Recursively resolve maps (keys AND values)
        if (node instanceof YamlMap map) {
            YamlMap resolvedMap = new YamlMap(map.getToken(), map.getOptions());
            for (YamlMapEntry entry : map.getEntries()) {
                YamlNode resolvedKey = resolveNode(entry.getKey(), anchorMap, depth + 1);
                YamlNode resolvedValue = resolveNode(entry.getValue(), anchorMap, depth + 1);
                resolvedMap.put(new YamlMapEntry(resolvedKey, resolvedValue));
            }
            return resolvedMap;
        }

        // Recursively resolve sequences
        if (node instanceof YamlSequence seq) {
            YamlSequence resolvedSeq = new YamlSequence(seq.getToken());
            for (YamlNode child : seq.getChildren()) {
                resolvedSeq.add(resolveNode(child, anchorMap, depth + 1));
            }
            return resolvedSeq;
        }

        // YamlScalar (or any other primitive leaf) returns as-is
        return node;
    }

    private void debug(String message, Object... details) {
        reporter.debug(message, details);
    }
}