package io.github.qishr.cascara.lang.yaml.processor;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchor;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;

public class YamlAliasResolver {

    /**
     * Traverses the document to collect all anchors, then replaces
     * all YamlAlias nodes with their dereferenced target nodes.
     */
    public static YamlNode resolve(YamlNode root) {
        Map<String, YamlNode> anchorMap = new HashMap<>();
        collectAnchors(root, anchorMap);
        return resolveNode(root, anchorMap);
    }

    /**
     * Resolves aliases across all documents in a stream, maintaining a single
     * anchor mapping table across document boundaries if needed.
     */
    public static YamlStream resolve(YamlStream stream) {
        if (stream == null) return null;

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

    private static void collectAnchors(YamlNode node, Map<String, YamlNode> anchorMap) {
        if (node == null) return;

        if (node instanceof YamlAnchor anchor) {
            if (anchor.getName() != null) {
                // Register target node associated with this anchor name
                anchorMap.put(anchor.getName(), anchor.getInnerNode());
            }
            collectAnchors(anchor.getInnerNode(), anchorMap);
            return;
        }

        if (node instanceof YamlMap map) {
            for (YamlMapEntry entry : map.getEntries()) {
                collectAnchors(entry.getKey(), anchorMap);
                collectAnchors(entry.getValue(), anchorMap);
            }
        } else if (node instanceof YamlSequence seq) {
            for (YamlNode child : seq.getChildren()) {
                collectAnchors(child, anchorMap);
            }
        }
    }

    private static YamlNode resolveNode(YamlNode node, Map<String, YamlNode> anchorMap) {
        if (node == null) return null;

        // If the node itself is an alias, resolve it from the map or internal target
        if (node instanceof YamlAlias alias) {
            YamlNode target = alias.getResolvedNode();
            if (target == null && alias.getName() != null) {
                target = anchorMap.get(alias.getName());
            }
            // Recurse on the target in case an alias points to another alias/anchor wrapper
            return resolveNode(target, anchorMap);
        }

        if (node instanceof YamlAnchor anchor) {
            // Preserve or unwrap anchor wrapper depending on AST model
            YamlNode resolvedInner = resolveNode(anchor.getInnerNode(), anchorMap);
            return new YamlAnchor(anchor.getToken(), anchor.getName(), resolvedInner);
        }

        if (node instanceof YamlMap map) {
            YamlMap resolvedMap = new YamlMap(map.getToken(), map.getOptions());
            for (YamlMapEntry entry : map.getEntries()) {
                YamlNode resolvedKey = resolveNode(entry.getKey(), anchorMap);
                YamlNode resolvedValue = resolveNode(entry.getValue(), anchorMap);
                resolvedMap.put(new YamlMapEntry(resolvedKey, resolvedValue));
            }
            return resolvedMap;
        }

        if (node instanceof YamlSequence seq) {
            YamlSequence resolvedSeq = new YamlSequence(seq.getToken());
            for (YamlNode child : seq.getChildren()) {
                resolvedSeq.add(resolveNode(child, anchorMap));
            }
            return resolvedSeq;
        }

        return node;
    }
}