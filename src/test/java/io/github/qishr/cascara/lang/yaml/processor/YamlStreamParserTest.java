package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.yaml.ast.*;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YamlStreamParserTest {

    private final YamlOptions options = new YamlOptions().setMultiDocument(true);
    private final YamlAstParser parser = new YamlAstParser()
            .setOptions(options)
            .setReporter(new StandardReporter().setLevel(Level.TRACE));

    @Test
    void testMultiDocumentStreamWithDirectives() {
        String yaml =
            "%YAML 1.2\n" +
            "--- \n" +
            "doc: 1\n" +
            "...\n" +
            "%TAG !yaml! tag:yaml.org,2002:\n" +
            "--- \n" +
            "doc: 2";

        // Since we changed parser.parse() to return a YamlStreamNode
        YamlStreamNode stream = (YamlStreamNode) parser.parse(yaml);

        assertNotNull(stream);
        List<YamlDocumentNode> docs = stream.getDocuments();
        assertEquals(2, docs.size());

        // Validate Document 1
        YamlDocumentNode doc1 = docs.get(0);
        assertEquals(1, doc1.getDirectives().size());
        assertEquals("%YAML 1.2", doc1.getDirectives().get(0).getContent());
        assertTrue(doc1.getBody() instanceof YamlMapNode);

        // Validate Document 2
        YamlDocumentNode doc2 = docs.get(1);
        assertEquals(1, doc2.getDirectives().size());
        assertEquals("%TAG !yaml! tag:yaml.org,2002:", doc2.getDirectives().get(0).getContent());
        assertTrue(doc2.getBody() instanceof YamlMapNode);
    }

    @Test
    void testFileHeaderAndFooterComments() {
        String yaml =
            "# File Header Comment\n" +
            "--- \n" +
            "payload: true\n" +
            "# File Footer Comment";

        YamlStreamNode stream = (YamlStreamNode) parser.parse(yaml);

        assertNotNull(stream);
        assertEquals(1, stream.getDocuments().size());

        // Comments belonging to the stream level (outside documents)
        List<YamlCommentNode> streamComments = stream.getComments();
        assertEquals(2, streamComments.size());
        assertEquals(" File Header Comment", streamComments.get(0).asString());
        assertEquals(" File Footer Comment", streamComments.get(1).asString());
    }

    @Test
    void testEmptyExplicitDocument() {
        String yaml = "--- ...";
        YamlStreamNode stream = (YamlStreamNode) parser.parse(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlNode body = stream.getDocuments().get(0).getBody();

        // An empty document body defaults to an empty scalar
        assertTrue(body instanceof YamlScalarNode);
        String strVal = ((YamlScalarNode) body).asString();
        assertEquals("", strVal);
    }
}