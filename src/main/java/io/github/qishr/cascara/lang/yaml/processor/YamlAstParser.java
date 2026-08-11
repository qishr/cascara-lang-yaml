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

import java.io.InputStream;
import java.io.Reader;
import java.util.List;

import io.github.qishr.cascara.common.lang.annotation.Experimental;
import io.github.qishr.cascara.common.lang.processor.AstParser;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
import io.github.qishr.cascara.lang.yaml.internal.AbstractYamlParser;
import io.github.qishr.cascara.lang.yaml.internal.PreloadedTokenBuffer;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

/// A recursive descent parser that transforms a stream of [YamlToken]s into a [YamlNode] AST.
///
/// This parser is designed for **high-fidelity AST construction**, meaning it preserves
/// comments, indentation styles, and quote styles for round-tripping.
///
/// ### Core Responsibilities
/// * **Structural Validation**: Enforces strict column alignment for map keys and sequence items.
/// * **Trivia Management**: Buffers comments using [pendingComments] and attaches them to
///   the next appropriate data node (Scalar, Map, or Sequence).
/// * **Indentation Lifecycle**: Manages block boundaries by consuming `INDENT` and `DEDENT`
///   tokens through the [parseValue] dispatcher.
public class YamlAstParser extends AbstractYamlParser<YamlAstParser> implements AstParser<YamlNode, YamlToken, YamlTokenizer> {

    public YamlAstParser() {
    }

    @Override protected YamlAstParser self() { return this; }

    //
    // Single document parsing
    //

    /// Entry point for parsing a full YAML source string.
    @Override
    public YamlNode parse(String text) {
        preParseStateInit();
        tokenBuffer.open(text);
        return parseAndUnpack();
    }

    public YamlNode parse(byte[] data) {
        preParseStateInit();
        tokenBuffer.open(data);
        return parseAndUnpack();
    }

    @Override
    public YamlNode parse(Reader reader) {
        preParseStateInit();
        tokenBuffer.open(reader);
        return parseAndUnpack();
    }

    /// Entry point for parsing an InputStream.
    @Override
    public YamlNode parse(InputStream is) {
        preParseStateInit();
        tokenBuffer.open(is);
        return parseAndUnpack();
    }

    //
    // Multi-document parsing
    //

    /// Type-safe method specifically for multi-document scenarios.
    @Experimental
    public YamlStream parseMulti(String text) {
        preParseStateInit();
        isMultiDocumentParsing = true;
        tokenBuffer.open(text);
        return (YamlStream)parseAndUnpack();
    }

    @Experimental
    public YamlStream parseMulti(byte[] data) {
        preParseStateInit();
        isMultiDocumentParsing = true;
        tokenBuffer.open(data);
        return (YamlStream)parseAndUnpack();
    }

    /// Method specifically for multi-document scenarios.
    @Experimental
    public YamlStream parseMulti(InputStream is) {
        preParseStateInit();
        isMultiDocumentParsing = true;
        tokenBuffer.open(is);
        return (YamlStream)parseAndUnpack();
    }

    //
    // Tokenizer parsing
    //

    /// Primary parsing core driven directly by the Tokenizer interface structure.
    @Override
    public YamlNode parse(YamlTokenizer tokenizer) {
        preParseStateInit();
        tokenBuffer.setTokenizer(tokenizer);
        return parseAndUnpack();
    }

    /// Entry point for parsing a list of tokens.
    @Override
    public YamlNode parse(List<YamlToken> tokens) {
        preParseStateInit();
        PreloadedTokenBuffer preloaded = new PreloadedTokenBuffer();
        preloaded.preload(tokens);
        return parseAndUnpack();
    }
}