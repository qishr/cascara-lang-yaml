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

import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.yaml.ast.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AstParserStreamTests extends AstParserTestBase {

    @BeforeEach
    protected void setup() {
        super.setup();
        parser.getOptions().setMultiDocument(true);
    }

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
        YamlStream stream = (YamlStream) parser.parse(yaml);

        assertNotNull(stream);
        List<YamlDocument> docs = stream.getDocuments();
        assertEquals(2, docs.size());

        // Validate Document 1
        YamlDocument doc1 = docs.get(0);
        assertEquals(1, doc1.getDirectives().size());
        assertEquals("%YAML 1.2", doc1.getDirectives().get(0).getContent());
        assertTrue(doc1.getBody() instanceof YamlMap);

        // Validate Document 2
        YamlDocument doc2 = docs.get(1);
        assertEquals(1, doc2.getDirectives().size());
        assertEquals("%TAG !yaml! tag:yaml.org,2002:", doc2.getDirectives().get(0).getContent());
        assertTrue(doc2.getBody() instanceof YamlMap);
    }

    @Test
    void testFileHeaderAndFooterComments() {
        String yaml =
            "# File Header Comment\n" +
            "--- \n" +
            "payload: true\n" +
            "# File Footer Comment";

        if (DEBUG) {
            parser.getTokenizer().setReporter(
                new StandardReporter()
                    .setLevel(Level.DEBUG)
                    .setAnsiColoringEnabled(true)
            );
            TestUtils.dumpTokens(parser.getTokenizer().tokenize(yaml));
        }

        YamlStream stream = (YamlStream) parser.parse(yaml);

        assertNotNull(stream);
        assertEquals(1, stream.getDocuments().size());

        // Comments belonging to the stream level (outside documents)
        List<YamlComment> streamComments = stream.getComments();
        assertEquals(2, streamComments.size());
        assertEquals(" File Header Comment", streamComments.get(0).asString());
        assertEquals(" File Footer Comment", streamComments.get(1).asString());
    }

    @Test
    void testEmptyExplicitDocument() {
        String yaml = "--- ...";
        YamlStream stream = (YamlStream) parser.parse(yaml);

        assertEquals(1, stream.getDocuments().size());
        YamlNode body = stream.getDocuments().get(0).getBody();

        // An empty document body defaults to an empty scalar
        assertTrue(body instanceof YamlScalar);
        String strVal = ((YamlScalar) body).asString();
        assertEquals(null, strVal);
    }
}