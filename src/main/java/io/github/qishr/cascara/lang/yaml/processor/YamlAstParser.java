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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.LangDiagnosticCode;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.common.lang.annotation.Experimental;
import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.AstParser;
import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;
import io.github.qishr.cascara.lang.yaml.ast.CollectionStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlAliasNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchorNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlCommentNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlDirectiveNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocumentNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlStreamNode;
import io.github.qishr.cascara.lang.yaml.exception.YamlDiagnosticCode;
import io.github.qishr.cascara.lang.yaml.exception.YamlParserException;
import io.github.qishr.cascara.lang.yaml.token.YamlErrorToken;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

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

public class YamlAstParser extends AbstractYamlProcessor<YamlAstParser> implements AstParser<YamlNode, YamlToken> {

    private Tokenizer<YamlToken> tokenizer;

    /// The list of all tokens received from the tokenizer.
    ///
    /// Initial capacity: 256.
    private final List<YamlToken> tokenBuffer = new ArrayList<>(256);

    private int current = 0;
    private int depth = 0;

    /// Buffer to hold comments until a data node is created to claim them.
    private final List<YamlCommentNode> pendingComments = new ArrayList<>();
    private final Map<String, YamlNode> anchorRegistry = new HashMap<>();

    private int lastNewlineOrComment;

    private int DEPTH_LIMIT;

    /// Default constructor for SPI.
    public YamlAstParser() {
        applyOptions();
    }

    @Override protected YamlAstParser self() { return this; }

    public YamlAstParser setOptions(YamlOptions options) {
        super.setOptions(options);
        applyOptions();
        return this;
    }

    private void applyOptions() {
        this.DEPTH_LIMIT = options.getDepthLimit();
    }

    /// Entry point for parsing a full YAML source string.
    @Override
    public YamlNode parse(String text) {
        ensureTokenBufferFilled(text);
        return parseAndUnpack();
    }

    public YamlNode parse(byte[] data) {
        ensureTokenBufferFilled(new String(data));
        return parseAndUnpack();
    }

    @Override
    public YamlNode parse(Reader reader) {
        ensureTokenBufferFilled(reader);
        return parseAndUnpack();
    }

    /// Entry point for parsing an InputStream.
    @Override
    public YamlNode parse(InputStream is) {
        ensureTokenBufferFilled(is);
        return parseAndUnpack();
    }

    /// Type-safe method specifically for multi-document scenarios.
    @Experimental
    public YamlStreamNode parseMulti(String text) {
        YamlOptions originalOptions = this.options;
        try {
            this.options = originalOptions.duplicate().setMultiDocument(true);
            return (YamlStreamNode) parse(text);
        } finally {
            this.options = originalOptions; // Safely restore original state
        }
    }

    @Experimental
    public YamlStreamNode parseMulti(byte[] data) {
        YamlOptions originalOptions = this.options;
        try {
            this.options = originalOptions.duplicate().setMultiDocument(true);
            return (YamlStreamNode) parse(new String(data));
        } finally {
            this.options = originalOptions; // Safely restore original state
        }
    }

    /// Type-safe method specifically for multi-document scenarios.
    @Experimental
    public YamlStreamNode parseMulti(InputStream is) {
        YamlOptions originalOptions = this.options;
        try {
            this.options = originalOptions.duplicate().setMultiDocument(true);
            return (YamlStreamNode) parse(is);
        } finally {
            this.options = originalOptions; // Safely restore original state
        }
    }

    /// Primary parsing core driven directly by the Tokenizer interface structure.
    @Override
    public YamlNode parse(Tokenizer<YamlToken> tokenizer) {
        this.tokenizer = tokenizer;
        ensureTokenBufferFilled();
        return parseAndUnpack();
    }

    /// Entry point for parsing a list of tokens.
    @Override
    public YamlNode parse(List<YamlToken> tokens) {
        this.tokenizer = null;
        this.tokenBuffer.clear();
        if (tokens != null) {
            this.tokenBuffer.addAll(tokens);
        }
        this.current = 0;
        this.anchorRegistry.clear();
        this.pendingComments.clear();

        return parseAndUnpack();
    }

    public List<YamlToken> getTokens() {
        return tokenBuffer;
    }

    //
    // Private Methods
    //

    /// Helper to execute internal parsing logic and unpack based on options.
    private YamlNode parseAndUnpack() {
        YamlStreamNode stream = parseInternal();

        // If the developer wants the full multi-document structure, hand over the stream node
        if (options.isMultiDocument()) {
            return stream;
        }

        // Otherwise, stay backward-compatible and return the naked first document body
        return stream.getDocuments().isEmpty()
            ? new YamlMapNode()
            : stream.getDocuments().get(0).getBody();
    }

    /// Helper to centralize tokenizer execution
    private void ensureTokenBufferFilled(String text) {
        YamlTokenizer tz = new YamlTokenizer();
        tz.setOptions(options);
        tz.setReporter(reporter);
        tz.open(text);
        fillBuffer(tz);
    }

    /// Helper to centralize tokenizer execution
    private void ensureTokenBufferFilled(Reader reader) {
        YamlTokenizer tz = new YamlTokenizer();
        tz.setOptions(options);
        tz.setReporter(reporter);
        tz.open(reader);
        fillBuffer(tz);
    }

    private void ensureTokenBufferFilled(InputStream is) {
        YamlTokenizer tz = new YamlTokenizer();
        tz.setOptions(options);
        tz.setReporter(reporter);
        tz.open(is);
        fillBuffer(tz);
    }

    private void ensureTokenBufferFilled() {
        if (this.tokenizer == null) return;
        fillBuffer(this.tokenizer);
    }

    private void fillBuffer(Tokenizer<YamlToken> tz) {
        this.tokenBuffer.clear();
        this.current = 0;
        this.anchorRegistry.clear();
        this.pendingComments.clear();

        YamlToken next;
        int idx = 0;
        while ((next = tz.nextToken()) != null) {
            tokenBuffer.add(next);
            if (next.getType() == YamlTokenType.NEWLINE || next.getType() == YamlTokenType.COMMENT) {
                lastNewlineOrComment = idx;
            }
            if (next.getType() == YamlTokenType.EOF || next.getType() == YamlTokenType.STREAM_END) {
                break;
            }
            idx++;
        }
    }

    private YamlStreamNode parseInternal() {
        if (this.tokenBuffer.isEmpty()) {
            return new YamlStreamNode();
        }

        consume(YamlTokenType.STREAM_START, LangDiagnosticCode.EXPECTED_STREAM_START);

        YamlStreamNode streamNode = new YamlStreamNode(peek().getStartLine(), peek().getStartColumn());

        // Multi-Document Processing Loop
        while (!isAtEnd() && !check(YamlTokenType.STREAM_END) && !check(YamlTokenType.EOF)) {
            int previousPosition = current;

            // if (check(YamlTokenType.NEWLINE) || check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
            //     advance();
            //     continue;
            // }
            trace("PI-while");

            if ((check(YamlTokenType.NEWLINE) && !lookAheadFor(YamlTokenType.DIRECTIVE)) ||
                (check(YamlTokenType.INDENT) && !lookAheadFor(YamlTokenType.DIRECTIVE)) ||
                (check(YamlTokenType.DEDENT) && !lookAheadFor(YamlTokenType.DIRECTIVE))) {

                advance();
                continue;
            }

            if (check(YamlTokenType.COMMENT)) {
                // If there are no documents yet, AND there is no upcoming explicit
                // document boundary marker or directive, let the first document body claim it.
                if (streamNode.getDocuments().isEmpty() && !lookAheadToExplicitMarker()) {
                    pendingComments.add(parseComment());
                } else {
                    streamNode.getComments().add(parseComment());
                }
                continue;
            }

            if (isAtEnd() || check(YamlTokenType.STREAM_END) || check(YamlTokenType.EOF)) {
                break;
            }

            // Guard: Malformed indentations that break out of blocks cannot start documents
            if (options.isStrict()
                && (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR) || check(YamlTokenType.VALUE_INDICATOR))) {
                YamlToken badToken = peek();
                error(badToken, YamlDiagnosticCode.UNEXPECTED_TOKEN, badToken.getType());
            }



            // // This was wrong because a DIRECTIVE can come between DOCUMENT_START and DOCUMENT_END
            // // Only start a document when appropriate
            // if (streamNode.getDocuments().isEmpty()) {
            //     // First document: implicit or explicit
            //     YamlDocumentNode docNode = parseDocument();
            //     streamNode.addDocument(docNode);
            // } else if (check(YamlTokenType.DOCUMENT_START)) {
            //     // Subsequent documents: must start with ---
            //     YamlDocumentNode docNode = parseDocument();
            //     streamNode.addDocument(docNode);
            // } else {
            //     // No explicit document start → we're done
            //     break;
            // }

            // This is the correct way to do it:
            YamlToken before = peek();
            streamNode.addDocument(parseDocument());
            YamlToken after = peek();
            if (!check(YamlTokenType.DIRECTIVE) &&
                !check(YamlTokenType.DOCUMENT_START)) {
                break;
            }




            if (current == previousPosition) {
                break;
            }
        }

        // Attach any loose trailing comments collected during document parsing
        streamNode.getComments().addAll(pendingComments);
        pendingComments.clear();

        // Safely consume trailing layout artifacts and capture any trailing file footer comments
        while (!isAtEnd() && !check(YamlTokenType.STREAM_END) && !check(YamlTokenType.EOF)) {
            if (check(YamlTokenType.NEWLINE) || check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                advance();
            } else if (check(YamlTokenType.COMMENT)) {
                streamNode.getComments().add(parseComment());
            } else {
                break;
            }
        }

        if (check(YamlTokenType.ERROR)) {
            error(peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN);
        } else if (check(YamlTokenType.STREAM_END)) {
            advance();
        } else if (check(YamlTokenType.EOF)) {
            advance();
        }

        return streamNode;
    }

    /// Parses a single document context, safely handling layout tokens near explicit boundaries
    private YamlDocumentNode parseDocument() {
        trace(">parseDocument");
        depth++;
        try {
            YamlToken startToken = peek();
            YamlDocumentNode document = new YamlDocumentNode(startToken.getStartLine(), startToken.getStartColumn());

            if (lookAheadToExplicitMarker()) {
                while (!isAtEnd() && check(YamlTokenType.NEWLINE)) {
                    advance();
                }
            }

            while (check(YamlTokenType.DIRECTIVE)) {
                YamlToken dirToken = advance();


                if (isKnownDirective(dirToken)) {
                    document.addDirective(new YamlDirectiveNode(dirToken.getStartLine(), dirToken.getStartColumn(), dirToken.getContent()));
                } else {
                    warn(
                        dirToken,
                        YamlDiagnosticCode.UNKNOWN_DIRECTIVE,
                        "Unknown directive ignored: " + dirToken.getContent()
                    );
                }


                skipTrivia();
            }



            trace("PD-1");
            // After directives, before deciding body, strip layout so skipTrivia can see comments
            while (check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                advance();
            }
            skipTrivia(); // this will now see COMMENT(#d) and buffer it

            trace("PD-2");


            // Clean layout indentation wrapping the explicit document boundaries
            if (check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                ensureBuffered(1);
                if (current + 1 < tokenBuffer.size() && tokenBuffer.get(current + 1).getType() == YamlTokenType.DOCUMENT_START) {
                    advance();
                }
            }

            trace("PD-3");

            if (match(YamlTokenType.DOCUMENT_START)) {
                skipTrivia();
            }

            trace("PD-4");

            if (check(YamlTokenType.DOCUMENT_END) || check(YamlTokenType.DOCUMENT_START) || isAtEnd()) {
                document.setBody(new YamlScalarNode(peek().getStartLine(), peek().getStartColumn(), PrimitiveType.ANY, "", "", QuoteStyle.PLAIN, options));
            } else {
                document.setBody(parseValue());
            }

            if (match(YamlTokenType.DOCUMENT_END)) {
                skipTrivia();
            }
            return document;
        } finally {
            depth--;
            trace("<parseDocument");
        }
    }

    /// Safely looks ahead to check for an explicit document boundary or directive.
    /// Uses a clean index bound to prevent infinite spin conditions.
    private boolean lookAheadToExplicitMarker() {
        int index = current;
        int max = tokenBuffer.size();

        while (index < max) {
            YamlTokenType type = tokenBuffer.get(index).getType();
            if (type == YamlTokenType.DOCUMENT_START || type == YamlTokenType.DIRECTIVE) {
                return true;
            }
            if (type == YamlTokenType.INDENT || type == YamlTokenType.DEDENT ||
                type == YamlTokenType.NEWLINE || type == YamlTokenType.COMMENT) {
                index++; // Guarantee progression
            } else {
                break; // Exit immediately on any structural data token
            }
        }
        return false;
    }

    /// The primary dispatcher for all YAML values.
    ///
    /// This method is responsible for:
    /// 1. Handling anchors (`&`) and aliases (`*`).
    /// 2. Managing block indentation tokens (`INDENT`/`DEDENT`).
    /// 3. Determining the structural type (Map, Sequence, or Scalar) via lookahead.
    private YamlNode parseValue() {
        trace(">parseValue");
        depth++;
        if (depth > DEPTH_LIMIT) {
            error(peek(), YamlDiagnosticCode.DEPTH_LIMIT);
        }
        try {
            if (check(YamlTokenType.ERROR)) {
                error(peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, peek().getType());
            }

            skipTrivia();
            trace("PV-after-skipTrivia");

            // This block is not being entered in this test, so it cannot be the problem.
            // Making assumptions like this without verifying they are true leads to
            // incorrect code. DO NOT DO THAT!
            if (check(YamlTokenType.DEDENT) || check(YamlTokenType.EOF)) {
                return new YamlScalarNode(
                    peek().getStartLine(),
                    peek().getStartColumn(),
                    PrimitiveType.NULL,
                    "",
                    null,
                    QuoteStyle.PLAIN,
                    options
                );
            }

            YamlNode result;

            // 1. Capture anchor if present
            String pendingAnchor = null;

            if (check(YamlTokenType.ANCHOR)) {
                // YamlNode anchorNode = parseAnchor();
                trace("PV-in-if-anchor1");

                if (lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {
                    result = parseMap();
                    // TODO: This result ends up being ignored and "Structural dispatch"
                    // ends up in the final `else` block an returns a null scalar.
                    attachComments(result);
                    return result;
                } else {

                    YamlToken anchorTok = advance();
                    trace("PV-in-if-anchor2");

                    String raw = anchorTok.getContent();
                    pendingAnchor = raw.startsWith("&") ? raw.substring(1) : raw;

                    // Newly added:
                    if (check(YamlTokenType.VALUE_INDICATOR)) {
                        YamlNode innerMap = parseMap();
                        // Normalizer will unwrap this later
                        return new YamlAnchorNode(
                            innerMap.getStartLine(),
                            innerMap.getStartColumn(),
                            pendingAnchor,
                            innerMap
                        );
                    }

                    // Fast-path: &anchor scalar
                    else if (check(YamlTokenType.SCALAR)) {
                        trace("PV-in-if-scalar1");
                        YamlNode scalar = parseScalar();
                        trace("PV-in-if-scalar2");

                        anchorRegistry.put(pendingAnchor, scalar);

                        YamlAnchorNode anchorNode = new YamlAnchorNode(
                            scalar.getStartLine(),
                            scalar.getStartColumn(),
                            pendingAnchor,
                            scalar
                        );

                        return attachComments(anchorNode);
                    }
                }

                skipTrivia();
            }

            trace("PV-after-if-anchor");



            List<YamlToken> tags = new ArrayList<>();
            while (check(YamlTokenType.TAG)) {
                tags.add(advance());
                skipTrivia();
            }



            // 2. Structural dispatch

            if (check(YamlTokenType.INDENT)) {
                trace("PV-in-if-indent1");
                advance();
                trace("PV-in-if-indent2");
                skipTrivia();
                trace("PV-in-if-indent3");

                if (check(YamlTokenType.ANCHOR)) {
                    YamlToken anchorTok = advance();
                    String raw = anchorTok.getContent();
                    String pendingAnchor2 = raw.startsWith("&") ? raw.substring(1) : raw;

                    skipTrivia();

                    // If this is a key (SCALAR + ":"), parse a map
                    if ((check(YamlTokenType.SCALAR) || check(YamlTokenType.ALIAS)) &&
                        lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {

                        YamlNode innerMap = parseMap();

                        // Normalizer will unwrap this later
                        return new YamlAnchorNode(
                            innerMap.getStartLine(),
                            innerMap.getStartColumn(),
                            pendingAnchor2,
                            innerMap
                        );
                    }

                    // Otherwise fall through to normal structural parsing
                }

                if (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                    result = parseSequence();
                } else if (check(YamlTokenType.KEY_INDICATOR)) {
                    result = parseMap();
                } else if ((check(YamlTokenType.SCALAR) ||
                            check(YamlTokenType.ALIAS) ||
                            check(YamlTokenType.ANCHOR))
                        && lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {
                    result = parseMap();
                } else {
                    result = parseValue();
                }

                trace("PV-in-if-indent4");
                skipTrivia();
                trace("PV-in-if-indent5");

                if (options.isStrict()) {
                    consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT_BLOCK_COMMENT);
                } else if (check(YamlTokenType.DEDENT)) {
                    advance();
                }
            }

            else if (check(YamlTokenType.KEY_INDICATOR)) {
                result = parseMap();
            }

            else if (check(YamlTokenType.ALIAS)) {
                trace("PV-in-if-alias1");
                YamlToken tok = advance();
                trace("PV-in-if-alias2");

                String name = tok.getContent().substring(1);
                YamlAliasNode alias = new YamlAliasNode(
                    tok.getStartLine(),
                    tok.getStartColumn(),
                    name
                );

                if (anchorRegistry.containsKey(name)) {
                    alias.setResolvedNode(anchorRegistry.get(name));
                }

                result = alias;
            }

            else if (check(YamlTokenType.MAP_START)) {
                result = parseFlowMap();
            }

            else if (check(YamlTokenType.SEQUENCE_START)) {
                result = parseFlowSequence();
            }

            else if (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                result = parseSequence();
            }

            else if (check(YamlTokenType.SCALAR)) {
                if (lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {
                    result = parseMap();
                } else {
                    result = parseScalar();
                }
            }

            else {
                result = new YamlScalarNode(
                    peek().getStartLine(),
                    peek().getStartColumn(),
                    PrimitiveType.NULL,
                    "",
                    null,
                    QuoteStyle.PLAIN,
                    options
                );
            }

            // 3. Apply anchor to the produced node
            if (pendingAnchor != null && result != null) {

                result.setAnchor(pendingAnchor);

                YamlAnchorNode anchorNode = new YamlAnchorNode(
                    result.getStartLine(),
                    result.getStartColumn(),
                    pendingAnchor,
                    result
                );

                anchorRegistry.put(pendingAnchor, result);

                result = anchorNode;
            }

            // 3. Attach tags
            for (YamlToken tagToken : tags) {
                result.setTag(tagToken.getLexeme());
            }

            return attachComments(result);

        } finally {
            depth--;
            trace("<parseValue");
        }
    }





















    /// Parses a block-level mapping and enforces strict key indentation.
    /// This method captures the column of the first key encountered and ensures
    /// all subsequent sibling keys in this map align perfectly.
    private YamlNode parseMap() {
        trace(">parseMap");
        depth++;
        try {
            YamlToken startToken = peek();
            YamlMapNode map = new YamlMapNode(startToken.getStartLine(), startToken.getStartColumn());
            map.setStyle(CollectionStyle.BLOCK);

            Set<Object> seenKeys = new HashSet<>();
            int mapColumn = -1;

            while (!isAtEnd()) {
                skipTrivia(); // TODO: PERFORMANCE: this is slow

                if (check(YamlTokenType.INDENT)) {
                    if (lookAheadIgnoringComments(YamlTokenType.NEWLINE)) { // TODO: PERFORMANCE: this is slow
                        advance(); // Consume INDENT
                        skipTrivia(); // Consume NEWLINE
                        if (check(YamlTokenType.DEDENT)) {
                            advance(); // Consume the DEDENT matching the empty line
                        }
                        continue;
                    }
                }

                // if (check(YamlTokenType.DEDENT)) break;
                if (check(YamlTokenType.DEDENT)) {
                    // Only break if this dedent closes the current map
                    if (!isIndentedDeeperThan(mapColumn)) {
                        break;
                    }
                    // Otherwise consume the dedent and continue
                    advance();
                    skipTrivia();
                    continue;
                }

                // TODO: Block we were supposed to remove but breaks tests if it's removed:
                if (check(YamlTokenType.INDENT)) {
                    advance();
                    skipTrivia();
                }

                // Peek at the token that will tell us if an entry is actually here
                trace("at markerToken");
                YamlToken markerToken = peek();

                boolean isExplicitKey = check(YamlTokenType.KEY_INDICATOR);
                boolean isScalarKeyStart =
                    check(YamlTokenType.SCALAR) ||
                    check(YamlTokenType.ALIAS) ||
                    check(YamlTokenType.ANCHOR);
                    // || (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR) && peek().getStartColumn() == mapColumn);

                // A map entry must start with '?', SCALAR, ALIAS, or ANCHOR
                if (!isExplicitKey && !isScalarKeyStart) {
                    trace("at break");
                    break;
                }

                int col = markerToken.getStartColumn();

                if (mapColumn == -1) {
                    mapColumn = col;
                } else {
                    boolean isAliasKey = check(YamlTokenType.ALIAS);
                    boolean isAnchorKey = check(YamlTokenType.ANCHOR);

                    if (col == mapColumn) {
                        // sibling → OK
                    }
                    else if (col > mapColumn && (isAliasKey || isAnchorKey)) {
                        // nested alias/anchor → OK
                    }
                    else {
                        // invalid indentation
                        error(markerToken, YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    }
                }

                // 1. Harvest any block comments sitting directly above this key before it parses
                List<YamlCommentNode> leadingBlockComments = null;
                while (check(YamlTokenType.COMMENT)) {
                    if (leadingBlockComments == null) {
                        leadingBlockComments = new ArrayList<>();
                    }
                    leadingBlockComments.add((YamlCommentNode) parseComment());
                    if (check(YamlTokenType.NEWLINE)) {
                        advance();
                    }
                }

                YamlNode key;

                if (isExplicitKey) {
                    advance(); // Consume '?'
                    skipTrivia();

                    // Multi-line nested block keys drop down via INDENT or start on a fresh NEWLINE
                    if (check(YamlTokenType.INDENT) || check(YamlTokenType.NEWLINE)) {
                        key = parseValue();
                    } else {
                        // Inline key evaluation: check if a colon belongs to the same line context
                        if (hasInlineValueIndicator()) {
                            key = parseValue(); // Parse inline nested map/sequence
                        } else {
                            key = parseScalar(); // Parse inline simple scalar key
                        }
                    }
                } else {
                    // Standard implicit key
                    // key = parseScalar();
                    key = parseKeyNode();
                }

                // 2. Prepend the harvested block comments so they don't get lost
                if (key != null && leadingBlockComments != null && !leadingBlockComments.isEmpty()) {
                    // key.getComments().addAll(0, leadingBlockComments);
                    key.addComments(0, leadingBlockComments);
                }

                attachComments(key);

                if (options.isStrict()) {
                    // For scalars, track the underlying unescaped string value.
                    // YamlScalarNode.asString() uses Primitive.asString() - Primitive is immutable and aggressively caches things.
                    // For complex structural nodes, track the node identity/structural equivalence.
                    Object keyTrackingToken = (key instanceof YamlScalarNode scalarKey) ? scalarKey.asString() : key;

                    if (!seenKeys.add(keyTrackingToken)) {
                        String duplicateKeyRepresentation = (key instanceof YamlScalarNode scalarKey)
                            ? scalarKey.asString()
                            : "[Complex Key at Line " + key.getStartLine() + "]";

                        error(previous(), YamlDiagnosticCode.DUPLICATE_KEY, duplicateKeyRepresentation);
                    }
                }

                int keyColumn = key.getStartColumn();

                skipTrivia();

                // TODO: Block we were supposed to remove but breaks tests if it's removed:
                // Clear any leftover indentation noise caused by the complex key block structure
                while (check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                    advance();
                    skipTrivia();
                }

                consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_MAP_KEY);
                parseInlineComment(key);


                trace("before parseValue");
                YamlNode value;




                if (check(YamlTokenType.NEWLINE) && !isIndentedDeeperThan(keyColumn)) {
                    value = new YamlScalarNode(peek().getStartLine(), peek().getStartColumn(), PrimitiveType.NULL, "", null, QuoteStyle.PLAIN, options);
                } else {
                    value = parseValue();
                }




                trace("after parseValue");

                map.put(new YamlMapEntryNode(key.getStartLine(), key.getStartColumn(), key, value));

                skipTrivia();
            }
            return map;
        } finally {
            depth--;
            trace("<parseMap");
        }
    }






    private YamlNode parseKeyNode() {
        YamlToken tok = peek();
        trace(">parseKeyNode: " + tok.getType() + " @" + tok.getStartLine() + ":" + tok.getStartColumn());
        depth++;
        try {
            switch (tok.getType()) {

                case SCALAR:
                    trace("parseKeyNode: SCALAR");
                    return parseScalar();

                case ALIAS: {
                    advance(); // consume alias token

                    String raw = tok.getContent();
                    String name = raw.startsWith("*") ? raw.substring(1) : raw;

                    YamlAliasNode alias = new YamlAliasNode(tok.getStartLine(), tok.getStartColumn(), name);

                    // Mirror parseValue alias resolution
                    if (anchorRegistry.containsKey(name)) {
                        alias.setResolvedNode(anchorRegistry.get(name));
                    }

                    return alias;
                }


                case ANCHOR: {
                    advance(); // consume &anchor
                    String raw = tok.getContent();
                    String name = raw.startsWith("&") ? raw.substring(1) : raw;

                    // Consume the ':' that belongs to the anchored key
                    // if (check(YamlTokenType.VALUE_INDICATOR)) {
                    //     advance();
                    // }
                    consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_MAP_KEY);

                    // Parse the scalar key that follows
                    YamlNode key = parseScalar();

                    // ⭐ Register the anchor for later alias resolution
                    anchorRegistry.put(name, key);

                    // Wrap in YamlAnchorNode (same as parseValue)
                    YamlAnchorNode anchorNode = new YamlAnchorNode(
                        key.getStartLine(),
                        key.getStartColumn(),
                        name,
                        key
                    );

                    return anchorNode;
                }

                case KEY_INDICATOR:
                    trace("parseKeyNode: KEY_INDICATOR");
                    advance(); // consume '?'
                    skipTrivia();
                    return parseValue();

                default:
                    trace("parseKeyNode: UNEXPECTED " + tok.getType());
                    error(tok, GenericDiagnosticCode.ERROR, "Unexpected token in key position: " + tok.getType());
                    return new YamlScalarNode(tok.getStartLine(), tok.getStartColumn(),
                        PrimitiveType.ANY, "", "", QuoteStyle.UNDETERMINED, options);
            }
        } finally {
            depth--;
            trace("<parseKeyNode");
        }
    }










    /// Parses a block sequence (list) indicated by leading dashes.
    private YamlSequenceNode parseSequence() {
        trace(">parseSequence");
        depth++;
        try {
            YamlToken start = peek();
            int indicatorColumn = start.getStartColumn();

            YamlSequenceNode sequence = new YamlSequenceNode(start.getStartLine(), start.getStartColumn());
            sequence.setStyle(CollectionStyle.BLOCK);
            attachComments(sequence);

            while (!isAtEnd()) {
                skipTrivia();
                trace("PS-while");

                // STOP: Hand control back to the dispatcher
                // If we see a DEDENT but the next token is still a '-', this is misaligned indentation
                if (check(YamlTokenType.DEDENT)) {
                    trace("PS-while-dedent");
                    if (peek(2).getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR) {
                        trace("PS-while-seq-indicator");
                        YamlToken bad = peek(1);
                        error(bad, YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    }
                    break;
                }


                if (!check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                    trace("PS-while-not-seq-indicator");
                    break;
                }

                int currentColumn = peek().getStartColumn();
                if (currentColumn != indicatorColumn) {
                    error(peek(), YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                }


                advance(); // Consume the '-'

                // parseValue handles the content, including potential nested blocks
                sequence.add(parseValue());

                skipTrivia();
            }

            return sequence;
        } finally {
            depth--;
            trace("<parseSequence");
        }
    }

    /// Parses a flow sequence like [item1, item2].
    private YamlSequenceNode parseFlowSequence() {
        trace(">parseFlowSequence");
        depth++;
        try {
            YamlToken start = consume(YamlTokenType.SEQUENCE_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACKET);
            YamlSequenceNode sequence = new YamlSequenceNode(start.getStartLine(), start.getStartColumn());
            sequence.setStyle(CollectionStyle.FLOW);

            while (!check(YamlTokenType.SEQUENCE_END) && !isAtEnd()) {
                skipTrivia();
                sequence.add(parseValue());

                skipTrivia();

                if (!match(YamlTokenType.COMMA)) break;
            }

            consume(YamlTokenType.SEQUENCE_END, YamlDiagnosticCode.EXPECTED_CLOSE_BRACKET);
            return attachComments(sequence);
        } finally {
            depth--;
            trace("<parseFlowSequence");
        }
    }

    /// Parses a flow map like {key: value, key2: value2}.
    /// Parses a flow-style mapping (e.g., { key: value, key2: value2 }).
    /// This method handles the explicit MAP_START and MAP_END tokens.
    private YamlNode parseFlowMap() {
        trace(">parseFlowMap");
        depth++;
        try {
            YamlToken startToken = consume(YamlTokenType.MAP_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACE_FLOW_MAP);
            YamlMapNode map = new YamlMapNode(startToken.getStartLine(), startToken.getStartColumn());

            // Clear any whitespace/newlines before checking for an empty map exit
            skipTrivia();

            // Handle empty flow map {}
            if (match(YamlTokenType.MAP_END)) {
                return map;
            }

            while (!isAtEnd()) {
                skipTrivia();

                // 1. Parse Key
                YamlScalarNode key = parseScalar();

                // 2. Consume Value Indicator
                consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP);

                // 3. Parse Value
                YamlNode value = parseValue();

                // 4. Store Entry
                map.put(new YamlMapEntryNode(key.getStartLine(), key.getStartColumn(), key, value));

                skipTrivia();

                // 5. Check for continuation or end
                if (match(YamlTokenType.COMMA)) {
                    // Allow trailing commas by checking for end after comma
                    if (check(YamlTokenType.MAP_END)) {
                        advance();
                        break;
                    }
                    continue;
                } else if (match(YamlTokenType.MAP_END)) {
                    break;
                } else {
                    error(peek(), YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP);
                }
            }
            return map;
        } finally {
            depth--;
            trace("<parseFlowMap");
        }
    }

    // private YamlScalarNode parseScalar() {
    //     trace(">parseScalar");
    //     ++depth;
    //     try {
    //         YamlToken token = consume(YamlTokenType.SCALAR, YamlDiagnosticCode.EXPECTED_SCALAR);

    //         String raw = token.getLexeme();
    //         QuoteStyle style = QuoteStyle.PLAIN;

    //         if (raw.startsWith("\"")) {
    //             style = QuoteStyle.DOUBLE;
    //         } else if (raw.startsWith("'")) {
    //             style = QuoteStyle.SINGLE;
    //         } else if (raw.startsWith("|")) {
    //             style = QuoteStyle.LITERAL_BLOCK;
    //         } else if (raw.startsWith(">")) {
    //             style = QuoteStyle.FOLDED;
    //         }

    //         YamlScalarNode scalar = new YamlScalarNode(
    //             token.getStartLine(),
    //             token.getStartColumn(),
    //             PrimitiveType.ANY,// token.getPrimitiveType(),
    //             raw,
    //             token.getContent(),
    //             style,
    //             options
    //         );
    //         scalar.setToken(token);

    //         if (check(YamlTokenType.COMMENT) && peek().getStartLine() == token.getStartLine()) {
    //             scalar.addComment(parseComment());
    //         }

    //         parseInlineComment(scalar);
    //         return scalar;
    //     } finally {
    //         --this.depth;
    //         trace("<parseScalar");
    //     }
    // }

    private YamlScalarNode parseScalar() {
        trace(">parseScalar");
        ++depth;
        try {
            YamlToken token = consume(YamlTokenType.SCALAR, YamlDiagnosticCode.EXPECTED_SCALAR);

            String raw = token.getLexeme();
            String content = token.getContent();
            QuoteStyle style = QuoteStyle.PLAIN;

            if (raw.startsWith("\"")) {
                style = QuoteStyle.DOUBLE;
            } else if (raw.startsWith("'")) {
                style = QuoteStyle.SINGLE;
            } else if (raw.startsWith("|")) {
                style = QuoteStyle.LITERAL_BLOCK;
            } else if (raw.startsWith(">")) {
                style = QuoteStyle.FOLDED;
            }

            // Start with the first chunk
            StringBuilder foldedContent = new StringBuilder(content);
            boolean folded = false;

            // Only fold for plain scalars (35KP case)
            if (style == QuoteStyle.PLAIN) {
                while (true) {

                    if (nextNonTriviaIsMapKey()) {
                        break;
                    }

                    // Consume layout between scalar chunks
                    while (check(YamlTokenType.NEWLINE) || check(YamlTokenType.INDENT)) {
                        advance();
                    }

                    if (check(YamlTokenType.SCALAR)) {
                        // YAML 1.2: newline between non-empty lines → space
                        foldedContent.append(" ");
                        foldedContent.append(peek().getContent());
                        advance();
                        folded = true;
                    } else {
                        break;
                    }
                }
            }

            PrimitiveType primitiveType = folded
                ? PrimitiveType.STRING   // multi-line plain scalar → string
                : PrimitiveType.ANY;     // single token → keep inferred type

            YamlScalarNode scalar = new YamlScalarNode(
                token.getStartLine(),
                token.getStartColumn(),
                primitiveType,
                raw,
                folded ? foldedContent.toString() : content,
                style,
                options
            );
            scalar.setToken(token);

            if (check(YamlTokenType.COMMENT) && peek().getStartLine() == token.getStartLine()) {
                scalar.addComment(parseComment());
            }

            parseInlineComment(scalar);
            return scalar;
        } finally {
            --this.depth;
            trace("<parseScalar");
        }
    }

    private YamlCommentNode parseComment() {
        YamlToken token = advance();
        String text = token.getContent() != null ? token.getContent().toString() : "";

        // Strip the raw hash marker, but preserve the exact space fidelity for the AST
        if (text.startsWith("#")) {
            text = text.substring(1);
        }

        return new YamlCommentNode(
            token.getStartLine(),
            token.getStartColumn(),
            text,
            false
        );
    }

    private void parseInlineComment(YamlNode node) {
        // If the very next token is a comment on the same line, it belongs to THIS node
        if (check(YamlTokenType.COMMENT) && peek().getStartLine() == node.getStartLine()) {
            node.addComment(parseComment());
        }
    }

    /// Collects comments and skips newlines, storing comments in the buffer.
    private void skipTrivia() {
        while (current < tokenBuffer.size()) {
            YamlTokenType type = peek().getType();
            if (type == YamlTokenType.EOF || type == YamlTokenType.STREAM_END) {
                break;
            }
            if (type == YamlTokenType.NEWLINE) {
                advance();
                continue;
            }
            if (type == YamlTokenType.COMMENT) {
                // If it's a root-level comment at the end of the file, leave it for the stream
                if (peek().getStartColumn() == 1 && isTrailingStreamComment(current)) {
                    break;
                }
                pendingComments.add(parseComment());
                continue;
            }
            break;
        }
    }

    //
    // Navigation & Streaming Lookahead Helpers
    //

    /// Ensures that the lookahead buffer has retrieved tokens up to the requested lookahead index offset.
    private void ensureBuffered(int offset) {
        if (tokenizer == null) return; // Running in fixed List fallback mode

        int targetIndex = current + offset;
        while (tokenBuffer.size() <= targetIndex) {
            YamlToken next = tokenizer.nextToken();
            if (next == null) break;
            tokenBuffer.add(next);
            if (next.getType() == YamlTokenType.EOF || next.getType() == YamlTokenType.STREAM_END) {
                break;
            }
        }
    }

    private boolean isIndentedDeeperThan(int parentColumn) {
        int i = 1;
        while (true) {
            int targetIndex = current + i;
            if (targetIndex >= tokenBuffer.size()) return false;

            YamlToken t = tokenBuffer.get(targetIndex);
            if (t.getType() == YamlTokenType.INDENT) {
                return t.getStartColumn() > parentColumn;
            }
            if (t.getType() != YamlTokenType.NEWLINE && t.getType() != YamlTokenType.COMMENT) {
                return false;
            }
            i++;
        }
    }

    // TODO: PERFORMANCE: This is slow

    /// Searches forward from `current` until it reaches a token matching `targetType`.
    /// If it finds `targetType` it returns true.
    /// If it finds NEWLINE or COMMENT it continues.
    /// If if finds anything else it returns false.
    private boolean lookAheadIgnoringComments(YamlTokenType targetType) {
        int lookahead = current + 1;
        while (lookahead < tokenBuffer.size()) {
            YamlTokenType type = tokenBuffer.get(lookahead).getType();
            if (type == targetType) return true;
            if (type == YamlTokenType.NEWLINE || type == YamlTokenType.COMMENT) {
                lookahead++;
                continue;
            }
            break;
        }
        return false;
    }

    private boolean nextNonTriviaIsMapKey() {
        int i = current;

        // Skip NEWLINE/INDENT/DEDENT
        while (i < tokenBuffer.size()) {
            YamlTokenType t = tokenBuffer.get(i).getType();
            if (t == YamlTokenType.NEWLINE || t == YamlTokenType.INDENT || t == YamlTokenType.DEDENT) {
                i++;
                continue;
            }
            break;
        }

        // Now check for SCALAR + KEY_INDICATOR
        if (i + 1 < tokenBuffer.size()
            && tokenBuffer.get(i).getType() == YamlTokenType.SCALAR
            && tokenBuffer.get(i + 1).getType() == YamlTokenType.VALUE_INDICATOR) {
            return true;
        }

        return false;
    }

    private boolean hasInlineValueIndicator() {
        int offset = 0;
        while (true) {
            YamlTokenType type = peek(offset).getType();
            if (type == YamlTokenType.NEWLINE || type == YamlTokenType.EOF || type == YamlTokenType.STREAM_END) {
                return false;
            }
            if (type == YamlTokenType.VALUE_INDICATOR) {
                return true;
            }
            offset++;
        }
    }

    private boolean isAtEnd() {
        if (current >= tokenBuffer.size()) return true;
        YamlTokenType type = tokenBuffer.get(current).getType();
        return type == YamlTokenType.EOF || type == YamlTokenType.STREAM_END;
    }

    /// Checks the type of the token returned by `peek()`
    private boolean check(YamlTokenType type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }

    private boolean lookAheadFor(YamlTokenType type) {
        YamlToken next = peek(1);
        return next != null && next.getType() == type;
    }

    private YamlToken advance() {
        trace("advance");
        if (!isAtEnd()) current++;
        return previous();
    }

    private YamlToken peek() {
        return tokenBuffer.get(current);
    }

    private YamlToken peek(int offset) {
        int targetIndex = current + offset;
        if (targetIndex >= tokenBuffer.size()) {
            return tokenBuffer.isEmpty() ? null : tokenBuffer.get(tokenBuffer.size() - 1);
        }
        return tokenBuffer.get(targetIndex);
    }

    private YamlToken previous() {
        return tokenBuffer.get(current - 1);
    }

    private boolean isTrailingStreamComment(int startPos) {
        return startPos >= lastNewlineOrComment;
    }

    /// Clears the [pendingComments] buffer by attaching them to the given node.
    private <T extends YamlNode> T attachComments(T node) {
        for (YamlCommentNode comment : pendingComments) {
            node.addComment(comment);
        }
        pendingComments.clear();
        return node;
    }

    @Nullable
    private YamlToken consume(YamlTokenType type, DiagnosticCode msgCode) {
        if (check(type)) return advance();
        YamlToken token = peek();
        error(peek(), msgCode, token.getType());
        return null; // TODO: Make this return non-null
    }

    private boolean match(YamlTokenType... types) {
        for (YamlTokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean isKnownDirective(YamlToken token) {
        String content = token.getContent();
        return content.startsWith("%YAML") || content.startsWith("%TAG");
    }

    //
    // Errors and Diagnostics
    //

    private void warn(YamlToken token, DiagnosticCode code, Object... details) {
        reporter.warnAt(token, code, details);
    }

    private void error(YamlToken token, DiagnosticCode code, Object... details) {
        if (token instanceof YamlErrorToken error) {
            code = error.getCode();
            details = error.getDetails();
        }

        System.out.println("ERROR: " + code.getMessage());
        System.err.println("ERROR: " + code.getMessage());
        System.out.flush();
        System.err.flush();

        reporter.errorAt(token, code, details);
        if (!reporter.collectsProblems()) {
            if (code.getCode().equals("YAML-114")) {
                System.out.println("Debug: Throwing YAML-114");
            }
            throw new YamlParserException(token, code, details);
        }
    }

    /// Log the current method name and upcoming tokens
    private void trace(String methodName) {
        if (reporter == null || reporter.isSilent()) return;

        // Ensure at least the current token is loaded to grab safe coordinates
        ensureBuffered(0);
        if (current >= tokenBuffer.size()) return;

        // Create indentation based on recursion depth
        String indent = "  ".repeat(Math.max(0, depth));

        reporter.trace("L%3d C%3d I%3d %s%s: %s",
                tokenBuffer.get(current).getStartLine(),
                tokenBuffer.get(current).getStartColumn(),
                current,
                indent,
                methodName,
                upcomingTokens());
    }

    // Get next 4 tokens as a string.
    private String upcomingTokens() {
        StringBuilder sb = new StringBuilder();

        // Force up to 4 lookahead tokens into the buffer safely
        ensureBuffered(4);

        int distance = Math.min(tokenBuffer.size() - current, 4);
        for (int i = 0; i < distance; i++) {
            YamlToken token = tokenBuffer.get(current + i);
            sb.append(token.getType());
            sb.append("(");
            sb.append(token.getLexeme().replace("\n", "\\n").replace("\r", "\\r"));
            sb.append(") ");
        }
        return sb.toString();
    }
}