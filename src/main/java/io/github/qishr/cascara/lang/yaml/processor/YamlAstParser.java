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

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.LangDiagnosticCode;
import io.github.qishr.cascara.common.lang.annotation.Experimental;
import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.processor.AstParser;
import io.github.qishr.cascara.common.lang.processor.Tokenizer;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.ast.NodeStyle;
import io.github.qishr.cascara.lang.yaml.ast.ScalarStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchor;
import io.github.qishr.cascara.lang.yaml.ast.YamlComment;
import io.github.qishr.cascara.lang.yaml.ast.YamlDirective;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
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

public class YamlAstParser extends AbstractYamlProcessor<YamlAstParser> implements AstParser<YamlNode, YamlToken, YamlTokenizer> {

    private static final int MAX_DEBUG_STRING_LENGTH = 20;

    private YamlTokenizer tokenizer;

    /// The list of all tokens received from the tokenizer.
    ///
    /// Initial capacity: 256.
    private final List<YamlToken> tokenBuffer = new ArrayList<>(256);

    private int current = 0;
    private int depth = 0;

    /// Buffer to hold comments until a data node is created to claim them.
    private final List<YamlComment> pendingComments = new ArrayList<>();
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
    public YamlStream parseMulti(String text) {
        YamlOptions originalOptions = this.options;
        try {
            this.options = originalOptions.duplicate().setMultiDocument(true);
            return (YamlStream) parse(text);
        } finally {
            this.options = originalOptions; // Safely restore original state
        }
    }

    @Experimental
    public YamlStream parseMulti(byte[] data) {
        YamlOptions originalOptions = this.options;
        try {
            this.options = originalOptions.duplicate().setMultiDocument(true);
            return (YamlStream) parse(new String(data));
        } finally {
            this.options = originalOptions; // Safely restore original state
        }
    }

    /// Type-safe method specifically for multi-document scenarios.
    @Experimental
    public YamlStream parseMulti(InputStream is) {
        YamlOptions originalOptions = this.options;
        try {
            this.options = originalOptions.duplicate().setMultiDocument(true);
            return (YamlStream) parse(is);
        } finally {
            this.options = originalOptions; // Safely restore original state
        }
    }

    /// Primary parsing core driven directly by the Tokenizer interface structure.
    @Override
    public YamlNode parse(YamlTokenizer tokenizer) {
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

    @Override
    public List<YamlToken> getTokens() {
        return tokenBuffer;
    }

    @Override
    public YamlTokenizer getTokenizer() {
        if (tokenizer == null) {
            tokenizer = new YamlTokenizer();
            // We don't set the tokenizer's reporter.
            // If the caller wants to set it, they should use:
            //   parser.getTokenizer().setReporter()
            tokenizer.setOptions(options);
        }
        return tokenizer;
    }

    //
    // Private Methods
    //

    private void applyOptions() {
        this.DEPTH_LIMIT = options.getDepthLimit();
    }

    /// Helper to execute internal parsing logic and unpack based on options.
    private YamlNode parseAndUnpack() {
        YamlStream stream = parseInternal();

        // If the developer wants the full multi-document structure, hand over the stream node
        if (options.isMultiDocument()) {
            return stream;
        }

        // Otherwise, stay backward-compatible and return the naked first document body
        return stream.getDocuments().isEmpty()
            ? new YamlMap()
            : stream.getDocuments().get(0).getBody();
    }

    /// Helper to centralize tokenizer execution
    private void ensureTokenBufferFilled(String text) {
        YamlTokenizer tz = getTokenizer();
        tz.open(text);
        fillBuffer(tz);
    }

    /// Helper to centralize tokenizer execution
    private void ensureTokenBufferFilled(Reader reader) {
        YamlTokenizer tz = getTokenizer();
        tz.open(reader);
        fillBuffer(tz);
    }

    private void ensureTokenBufferFilled(InputStream is) {
        YamlTokenizer tz = getTokenizer();
        tz.open(is);
        fillBuffer(tz);
    }

    private void ensureTokenBufferFilled() {
        fillBuffer(getTokenizer());
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

    private YamlStream parseInternal() {
        if (this.tokenBuffer.isEmpty()) {
            return new YamlStream();
        }

        consume(YamlTokenType.STREAM_START, LangDiagnosticCode.EXPECTED_STREAM_START);

        YamlStream streamNode = new YamlStream(peek());

        // Multi-Document Processing Loop
        while (!isAtEnd() && !check(YamlTokenType.STREAM_END) && !check(YamlTokenType.EOF)) {
            int previousPosition = current;

            debug("PI-while");

            if (check(YamlTokenType.NEWLINE) ||
                (check(YamlTokenType.INDENT) && !checkNext(YamlTokenType.DIRECTIVE)) ||
                (check(YamlTokenType.DEDENT) && !checkNext(YamlTokenType.DIRECTIVE))) {
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

            // This is the correct way to do it:
            streamNode.addDocument(parseDocument());
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
    private YamlDocument parseDocument() {
        debug(">parseDocument");
        depth++;
        try {
            YamlToken startToken = peek();
            YamlDocument document = new YamlDocument(startToken);

            if (lookAheadToExplicitMarker()) {
                while (!isAtEnd() && check(YamlTokenType.NEWLINE)) {
                    advance();
                }
            }

            while (check(YamlTokenType.DIRECTIVE)) {
                YamlToken dirToken = advance();
                if (isKnownDirective(dirToken)) {
                    document.addDirective(new YamlDirective(dirToken, dirToken.getContent()));
                } else {
                    warn(
                        dirToken,
                        YamlDiagnosticCode.UNKNOWN_DIRECTIVE,
                        dirToken.getContent()
                    );
                }
                skipTrivia();
            }

            debug("PD-1");
            // After directives, before deciding body, strip layout so skipTrivia can see comments
            while (check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                advance();
            }
            skipTrivia(); // this will now see COMMENT(#d) and buffer it
            debug("PD-2");

            // Clean layout indentation wrapping the explicit document boundaries
            if (check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                ensureBuffered(1);
                if (current + 1 < tokenBuffer.size() && tokenBuffer.get(current + 1).getType() == YamlTokenType.DOCUMENT_START) {
                    advance();
                }
            }

            debug("PD-3");
            if (match(YamlTokenType.DOCUMENT_START)) {
                skipTrivia();
            }
            debug("PD-4");

            if (check(YamlTokenType.DOCUMENT_END) || check(YamlTokenType.DOCUMENT_START) || isAtEnd()) {
                document.setBody(
                    new YamlScalar("", ScalarStyle.PLAIN, options)
                );
            } else {
                YamlNode body = parseValue(0);
                document.setBody(body);
                document.setTag(body.getTag());   // if any
            }

            if (match(YamlTokenType.DOCUMENT_END)) {
                skipTrivia();
            }
            return document;
        } finally {
            depth--;
            debug("<parseDocument");
        }
    }

    /// The primary dispatcher for all YAML values.
    ///
    /// This method is responsible for:
    /// 1. Handling anchors (`&`) and aliases (`*`).
    /// 2. Managing block indentation tokens (`INDENT`/`DEDENT`).
    /// 3. Determining the structural type (Map, Sequence, or Scalar) via lookahead.
    private YamlNode parseValue(int parentIndent) {
        debug(">parseValue");
        depth++;
        if (depth > DEPTH_LIMIT) {
            error(peek(), YamlDiagnosticCode.DEPTH_LIMIT);
        }
        try {
            if (check(YamlTokenType.ERROR)) {
                error(peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, peek().getType());
            }

            if (!check(YamlTokenType.SCALAR) ||
                    peek().getScalarStyle() != ScalarStyle.FOLDED) {
                skipTrivia();
            }

            debug("PV-after-skipTrivia");

            YamlToken startToken = peek();
            YamlNode result = null;
            String pendingAnchor = null;

            if (check(YamlTokenType.ANCHOR)) {
                debug("PV-in-if-anchor1");

                if (lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {
                    // Let parseMap handle the anchor

                    result = parseMap();
                    attachComments(result);
                    debug("PV-after-parseMap-1");

                    // Consume the DEDENT that closes the map
                    consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT_BLOCK_COMMENT);

                    return result;
                } else {
                    // Consume the anchor and set it as pending

                    YamlToken anchorTok = advance();
                    debug("PV-in-if-anchor2");

                    String raw = anchorTok.getContent();
                    pendingAnchor = raw.startsWith("&") ? raw.substring(1) : raw;
                    int ahead = 0;
                    YamlToken candidate = null;

                    skipTrivia();

                    candidate = peek(ahead);
                    while (candidate.getType() == YamlTokenType.NEWLINE ||
                           candidate.getType() == YamlTokenType.COMMENT) {
                        ahead++;
                        candidate = peek(ahead);
                        System.out.println(candidate.getType());
                    }

                    if (candidate.getStartColumn() > parentIndent) {
                        skipTrivia();
                    } else {
                        result = new YamlScalar(
                            peek(),
                            PrimitiveType.NULL,
                            options
                        );
                        YamlAnchor anchorNode = new YamlAnchor(
                            result.getStartLine(),
                            result.getStartColumn(),
                            pendingAnchor,
                            result
                        );
                        anchorRegistry.put(pendingAnchor, anchorNode);
                        return attachComments(anchorNode);
                    }
                }
            }
            debug("PV-after-if-anchor");

            if (check(YamlTokenType.NEWLINE)) {
                advance();
                skipTrivia();
            }

            // 2. Structural dispatch

            // TODO: Should this come after the INDENT below?
            // 1. Collect tags (node-level, including !!str on the document body)
            String pendingTag = null;
            while (check(YamlTokenType.TAG)) {
                debug("PV-TAG start");
                YamlToken tagTok = advance();
                pendingTag = tagTok.getContent();
                skipTrivia();
                debug("PV-TAG end");
            }

            boolean pendingDedent = false;
            if (check(YamlTokenType.INDENT)) {
                advance();
                skipTrivia();
                pendingDedent = true;
                debug("Setting pending dedent");
            }

            if (check(YamlTokenType.KEY_INDICATOR)) {
                result = parseMap();
            }

            else if (lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {
                result = parseMap();
            }

            else if (check(YamlTokenType.ANCHOR)) {
                result = parseValue(peek().getStartColumn());

            } else if (check(YamlTokenType.ALIAS)) {
                debug("PV-in-if-alias1");
                YamlToken tok = advance();
                debug("PV-in-if-alias2");

                String name = tok.getContent().substring(1);
                YamlAlias alias = new YamlAlias(
                    tok,
                    name
                );

                if (anchorRegistry.containsKey(name)) {
                    alias.setResolvedNode(anchorRegistry.get(name));
                }

                result = alias;
            }

            // Flow map
            else if (check(YamlTokenType.MAP_START)) {
                result = parseFlowMap();
            }

            // Flow sequence
            else if (check(YamlTokenType.SEQUENCE_START)) {
                result = parseFlowSequence();
            }

            // Block sequence
            else if (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                result = parseSequence();
            }

            // else if (check(YamlTokenType.SCALAR)) {
            else if (check(YamlTokenType.SCALAR) && startToken.getStartColumn() != parentIndent) {
                if (lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR)) {
                    result = parseMap();
                } else {
                    result = parseScalar();
                    if (check(YamlTokenType.NEWLINE)) {
                        advance();
                    }
                }
            }

            else {
                result = new YamlScalar(
                    peek(),
                    PrimitiveType.NULL,
                    options
                );
            }

            if (pendingDedent) {
                if (check(YamlTokenType.DEDENT)) {
                    debug("Consuming pending dedent");
                    advance();
                }
            }

            // 3. Attach tags
            if (pendingTag != null && result != null) {
                debug("PV-setTag");
                result.setTag(pendingTag);
            }

            // 3. Apply anchor to the produced node
            if (pendingAnchor != null && result != null) {

                result.setAnchor(pendingAnchor);

                YamlAnchor anchorNode = new YamlAnchor(
                    result.getStartLine(),
                    result.getStartColumn(),
                    pendingAnchor,
                    result
                );

                anchorRegistry.put(pendingAnchor, result);

                result = anchorNode;
            }

            return attachComments(result);

        } finally {
            depth--;
            debug("<parseValue");
        }
    }

    /// Parses a block-level mapping and enforces strict key indentation.
    /// This method captures the column of the first key encountered and ensures
    /// all subsequent sibling keys in this map align perfectly.
    private YamlNode parseMap() {
        debug(">parseMap");
        depth++;
        try {
            YamlToken startToken = peek();
            YamlMap map = new YamlMap(startToken, options);
            map.setStyle(NodeStyle.BLOCK);

            Set<Object> seenKeys = new HashSet<>();
            int mapColumn = -1;
            boolean previousKeyWasComplex = false;

            while (!isAtEnd()) {
                skipTrivia();

                if (check(YamlTokenType.INDENT)) {

                    // Only allow nested keys if previous key was complex
                    if (previousKeyWasComplex &&
                        (lookAheadIgnoringComments(YamlTokenType.SCALAR) ||
                         lookAheadIgnoringComments(YamlTokenType.ALIAS) ||
                         lookAheadIgnoringComments(YamlTokenType.ANCHOR) ||
                         lookAheadIgnoringComments(YamlTokenType.KEY_INDICATOR))) {

                        advance();      // consume INDENT
                        skipTrivia();   // move to nested key
                        // fall through to markerToken
                    }

                    else if (lookAheadIgnoringComments(YamlTokenType.NEWLINE)) {
                        // existing empty-line logic
                        advance();
                        skipTrivia();
                        if (check(YamlTokenType.DEDENT)) {
                            advance();
                        }
                        continue;
                    }

                    else {
                        // NEW: invalid indentation
                        error(peek(), YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    }
                }

                if (check(YamlTokenType.DEDENT)) {
                    // Only break if this dedent closes the current map
                    if (!isIndentedDeeperThan(mapColumn)) {
                        if (mapColumn == -1) {
                            System.out.println("Debug: mapColumn is -1");
                        } else {
                            break;
                        }
                    }
                    // Otherwise consume the dedent and continue
                    advance();
                    skipTrivia();
                    continue;
                }

                // Peek at the token that will tell us if an entry is actually here
                debug("at markerToken");
                YamlToken markerToken = peek();

                boolean isExplicitKey = check(YamlTokenType.KEY_INDICATOR);
                boolean isScalarKeyStart =
                    check(YamlTokenType.SCALAR) ||
                    check(YamlTokenType.ALIAS) ||
                    check(YamlTokenType.ANCHOR);

                // A map entry must start with '?', SCALAR, ALIAS, or ANCHOR
                if (!isExplicitKey && !isScalarKeyStart) {

                    if (peek().getStartColumn() > map.getStartColumn()) {
                        error(markerToken, YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    }

                    debug("at break");
                    break;
                }

                int markerColumn = markerToken.getStartColumn();

                if (mapColumn == -1) {
                    mapColumn = markerColumn;
                } else {
                    // boolean isAliasKey = check(YamlTokenType.ALIAS);
                    // boolean isAnchorKey = check(YamlTokenType.ANCHOR);

                    if (markerColumn == mapColumn) {
                        // sibling -> OK
                    }
                    // else if (markerColumn > mapColumn && (isAliasKey || isAnchorKey)) {
                    else if (markerColumn > mapColumn) {

                        // nested alias/anchor -> OK
                    }
                    else {
                        // invalid indentation
                        error(markerToken, YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    }
                }

                // 1. Harvest any block comments sitting directly above this key before it parses
                List<YamlComment> leadingBlockComments = null;
                while (check(YamlTokenType.COMMENT)) {
                    if (leadingBlockComments == null) {
                        leadingBlockComments = new ArrayList<>();
                    }
                    leadingBlockComments.add((YamlComment) parseComment());
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
                        key = parseValue(markerColumn);
                    } else {
                        key = parseKeyNode(markerColumn);
                    }

                } else {
                    // Standard implicit key
                    key = parseKeyNode(markerColumn);
                }

                // previousKeyWasComplex = !(key instanceof YamlScalar);
                if (isExplicitKey) {
                    // Any explicit key (`?`) can legally have nested indentation for its complex key body
                    previousKeyWasComplex = true;
                } else {
                    previousKeyWasComplex = !(key instanceof YamlScalar);
                }


                // 2. Prepend the harvested block comments so they don't get lost
                if (key != null && leadingBlockComments != null && !leadingBlockComments.isEmpty()) {
                    key.addComments(0, leadingBlockComments);
                }

                attachComments(key);

                if (options.isStrict()) {
                    // For scalars, track the underlying unescaped string value.
                    // YamlScalarNode.asString() uses Primitive.asString() - Primitive is immutable and aggressively caches things.
                    // For complex structural nodes, track the node identity/structural equivalence.
                    // TODO: Why not track the lexeme? Surely that's more efficient.
                    Object keyTrackingToken = (key instanceof YamlScalar scalarKey) ? scalarKey.asString() : key;

                    if (!seenKeys.add(keyTrackingToken)) {
                        String duplicateKeyRepresentation = (key instanceof YamlScalar scalarKey)
                            ? scalarKey.asString()
                            : "[Complex Key at Line " + key.getStartLine() + "]";

                        error(previous(), YamlDiagnosticCode.DUPLICATE_KEY, duplicateKeyRepresentation);
                    }
                }

                int keyColumn = key.getStartColumn();

                skipTrivia();

                consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_MAP_KEY);
                parseInlineComment(key);

                debug("before parseValue");
                YamlNode value;
                // if (check(YamlTokenType.NEWLINE) && !isIndentedDeeperThan(keyColumn)) {
                //     value = new YamlScalar(
                //         peek(), PrimitiveType.NULL, options
                //     );
                // }
                // if (check(YamlTokenType.NEWLINE) && !nextNodeIndentedDeeperThan(keyColumn)) {
                //     value = new YamlScalar(peek(), PrimitiveType.NULL, options);
                // }
                if (check(YamlTokenType.NEWLINE) && !hasIndentedValueAfterNewline()) {
                    value = new YamlScalar(peek(), PrimitiveType.NULL, options);
                }
                else {
                    while (check(YamlTokenType.NEWLINE)) {
                        advance();
                        skipTrivia();
                    }
                    boolean isValueIndented = false;
                    if (check(YamlTokenType.INDENT)) {
                        isValueIndented = true;
                        advance();
                    }

                    value = parseValue(mapColumn);

                    while (check(YamlTokenType.NEWLINE)) {
                        advance();
                        skipTrivia();
                    }
                    if (isValueIndented) {
                        consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT);
                    }
                }
                debug("after parseValue");

                map.put(new YamlMapEntry(key, value));

                skipTrivia();
            }
            return map;
        } finally {
            depth--;
            debug("<parseMap");
        }
    }


    private YamlNode parseKeyNode(int parentIndent) {
        YamlToken tok = peek();
        debug(">parseKeyNode");
        depth++;
        try {

            if (tok.getType() == YamlTokenType.MAP_START ||
                tok.getType() == YamlTokenType.SEQUENCE_START ||
                tok.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR ||
                tok.getType() == YamlTokenType.INDENT) {

                return parseValue(parentIndent);
            }

            switch (tok.getType()) {

                case SCALAR:
                    return parseScalar();

                case ALIAS: {
                    advance(); // consume alias token

                    String raw = tok.getContent();
                    String name = raw.startsWith("*") ? raw.substring(1) : raw;

                    YamlAlias alias = new YamlAlias(tok, name);

                    // Mirror parseValue alias resolution
                    if (anchorRegistry.containsKey(name)) {
                        alias.setResolvedNode(anchorRegistry.get(name));
                    }

                    return alias;
                }

                case ANCHOR: {
                    // TODO: This seems pointless now since parseValue handles anchors.
                    advance(); // consume &anchor
                    String raw = tok.getContent();
                    String name = raw.startsWith("&") ? raw.substring(1) : raw;

                    // Parse the scalar key that follows
                    YamlNode key = parseValue(parentIndent);

                    // Register the anchor for later alias resolution
                    anchorRegistry.put(name, key);

                    // Wrap in YamlAnchorNode (same as parseValue)
                    YamlAnchor anchorNode = new YamlAnchor(
                        key.getStartLine(),
                        key.getStartColumn(),
                        name,
                        key
                    );

                    return anchorNode;
                }

                case KEY_INDICATOR:
                    debug("parseKeyNode: KEY_INDICATOR");
                    advance(); // consume '?'
                    skipTrivia();
                    return parseValue(parentIndent);

                default:
                    debug("parseKeyNode: UNEXPECTED " + tok.getType());
                    error(tok, GenericDiagnosticCode.ERROR, "Unexpected token in key position: " + tok.getType());
                    return new YamlScalar(tok, PrimitiveType.ANY, options);
            }
        } finally {
            depth--;
            debug("<parseKeyNode");
        }
    }

    /// Parses a block sequence (list) indicated by leading dashes.
    private YamlSequence parseSequence() {
        debug(">parseSequence");
        depth++;
        try {
            YamlToken startToken = peek();
            int indicatorColumn = startToken.getStartColumn();

            YamlSequence sequence = new YamlSequence(startToken);
            sequence.setStyle(NodeStyle.BLOCK);
            attachComments(sequence);

            while (!isAtEnd()) {
                skipTrivia();
                debug("PS-while");

                // STOP: Hand control back to the dispatcher
                // If we see a DEDENT but the next token is still a '-', this is misaligned indentation
                if (check(YamlTokenType.DEDENT)) {
                    debug("PS-while-dedent");
                    // Regardless of the DEDENT being valid or not, we exit because it's
                    // not part of this sequence and is up to the caller to deal with.
                    break;
                }

                if (!check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                    debug("PS-while-not-seq-indicator");
                    break;
                }

                int currentColumn = peek().getStartColumn();
                if (currentColumn != indicatorColumn) {
                    error(peek(), YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                }

                advance(); // Consume the '-'

                // parseValue handles the content, including potential nested blocks
                YamlNode item = parseValue(indicatorColumn);
                sequence.add(item);

                skipTrivia();
            }

            return sequence;
        } finally {
            depth--;
            debug("<parseSequence");
        }
    }

    /// Parses a flow sequence like [item1, item2].
    private YamlSequence parseFlowSequence() {
        debug(">parseFlowSequence");
        depth++;
        try {
            YamlToken startToken = consume(YamlTokenType.SEQUENCE_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACKET);
            YamlSequence sequence = new YamlSequence(startToken);
            sequence.setStyle(NodeStyle.FLOW);

            while (!check(YamlTokenType.SEQUENCE_END) && !isAtEnd()) {
                skipTrivia();
                sequence.add(parseValue(startToken.getStartColumn()));

                skipTrivia();

                if (!match(YamlTokenType.COMMA)) break;
            }

            consume(YamlTokenType.SEQUENCE_END, YamlDiagnosticCode.EXPECTED_CLOSE_BRACKET);
            return attachComments(sequence);
        } finally {
            depth--;
            debug("<parseFlowSequence");
        }
    }

    /// https://yaml.org/spec/1.2.2/#742-flow-mappings
    ///
    /// Parses a flow map like {key: value, key2: value2}.
    /// Parses a flow-style mapping (e.g., { key: value, key2: value2 }).
    /// This method handles the explicit MAP_START and MAP_END tokens.
    private YamlNode parseFlowMap() {
        debug(">parseFlowMap");
        depth++;
        try {
            YamlToken startToken = consume(YamlTokenType.MAP_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACE_FLOW_MAP);
            YamlMap map = new YamlMap(startToken, options);
            map.setStyle(NodeStyle.FLOW);

            // Clear any whitespace/newlines before checking for an empty map exit
            skipTrivia();

            // Handle empty flow map {}
            if (match(YamlTokenType.MAP_END)) {
                return map;
            }

            while (!isAtEnd()) {
                skipTrivia();

                // 1. Parse Key
                YamlScalar key = parseScalar();

                // 2. Consume Value Indicator
                consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP);

                // 3. Parse Value
                YamlNode value = parseValue(key.getStartColumn());

                // 4. Store Entry
                map.put(new YamlMapEntry(key, value));

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
            debug("<parseFlowMap");
        }
    }

    // TODO: Tidy this up
    private YamlScalar parseScalar() {
        debug(">parseScalar");
        depth++;
        try {
            YamlToken token = consume(YamlTokenType.SCALAR, YamlDiagnosticCode.EXPECTED_SCALAR);

            ScalarStyle style = token.getScalarStyle();

            if (style == ScalarStyle.LITERAL) {
                debug("LITERAL_BLOCK");
                YamlScalar scalar = new YamlScalar(
                    token,
                    token.getContent(),
                    PrimitiveType.STRING,
                    ScalarStyle.LITERAL,
                    options
                );

                if (check(YamlTokenType.COMMENT) && peek().getStartLine() == token.getStartLine()) {
                    scalar.addComment(parseComment());
                }
                parseInlineComment(scalar);
                return scalar;
            }

            if (style == ScalarStyle.FOLDED) {
                debug("FOLDED");

                YamlToken contentToken = null;
                String content;

                if (check(YamlTokenType.SCALAR)) {
                    contentToken = consume(YamlTokenType.SCALAR, YamlDiagnosticCode.EXPECTED_SCALAR);
                    content = contentToken.getContent();
                } else {
                    content = token.getContent();
                }

                // If the tokenizer already produced multi-line content,
                // DO NOT re-fold it. Just return it as-is.
                YamlScalar scalar = new YamlScalar(
                    contentToken != null ? contentToken : token,
                    content,
                    PrimitiveType.STRING,
                    ScalarStyle.FOLDED,
                    options
                );

                // if (check(YamlTokenType.COMMENT) && peek().getStartLine() == token.getStartLine()) {
                //     scalar.addComment(parseComment());
                // }
                parseInlineComment(scalar);
                return scalar;
            }

            if (style == ScalarStyle.PLAIN) {
                debug("PLAIN");
                // just return the scalar as-is
                YamlScalar scalar = new YamlScalar(
                    token,
                    PrimitiveType.ANY,
                    options
                );

                // if (check(YamlTokenType.COMMENT) && peek().getStartLine() == token.getStartLine()) {
                //     scalar.addComment(parseComment());
                // }
                parseInlineComment(scalar);
                return scalar;
            }

            debug("DEFAULT");

            YamlScalar scalar = new YamlScalar(
                token,
                PrimitiveType.ANY,
                options
            );

            if (check(YamlTokenType.COMMENT) && peek().getStartLine() == token.getStartLine()) {
                scalar.addComment(parseComment());
            }

            parseInlineComment(scalar);
            return scalar;
        } finally {
            depth--;
            debug("<parseScalar");
        }
    }

    private YamlComment parseComment() {
        YamlToken token = advance();
        String text = token.getContent() != null ? token.getContent().toString() : "";

        // Strip the raw hash marker, but preserve the exact space fidelity for the AST
        if (text.startsWith("#")) {
            text = text.substring(1);
        }

        return new YamlComment(
            token,
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

    private boolean hasIndentedValueAfterNewline() {
        int i = 1;
        boolean sawIndent = false;

        while (true) {
            int targetIndex = current + i;
            if (targetIndex >= tokenBuffer.size()) return false;

            YamlToken t = tokenBuffer.get(targetIndex);
            YamlTokenType tt = t.getType();

            if (tt == YamlTokenType.NEWLINE || tt == YamlTokenType.COMMENT) {
                i++;
                continue;
            }

            if (tt == YamlTokenType.INDENT) {
                sawIndent = true;
                i++;
                continue;
            }

            // First non‑trivia, non‑INDENT token
            return sawIndent;
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

    private boolean nextNodeIndentedDeeperThan(int parentColumn) {
        int i = 1;
        while (true) {
            int targetIndex = current + i;
            if (targetIndex >= tokenBuffer.size()) return false;

            YamlToken t = tokenBuffer.get(targetIndex);
            YamlTokenType tt = t.getType();

            if (tt == YamlTokenType.NEWLINE || tt == YamlTokenType.COMMENT) {
                i++;
                continue;
            }

            // Any structural node starting at a greater column is "deeper"
            return t.getStartColumn() > parentColumn;
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

    private boolean checkNext(YamlTokenType type) {
        YamlToken next = peek(1);
        return next != null && next.getType() == type;
    }

    private YamlToken advance() {
        debug("advance");
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
        for (YamlComment comment : pendingComments) {
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

        reporter.errorAt(token, code, details);
        if (!reporter.collectsProblems()) {
            throw new YamlParserException(token, code, details);
        }
    }

    /// Log the current method name and upcoming tokens
    private void debug(String methodName) {
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.DEBUG)) return;

        // Ensure at least the current token is loaded to grab safe coordinates
        ensureBuffered(0);
        if (current >= tokenBuffer.size()) return;

        // Create indentation based on recursion depth
        String indent = "  ".repeat(Math.max(0, depth));

        char first = methodName.charAt(0);
        String prefix;
        String message;

        if (first == '>' || first == '<') {
            message = first + ANSI_YELLOW + methodName.substring(1) + ANSI_RESET;
        } else {
            message = methodName;
        }

        reporter.debug("L%3d C%3d I%3d %s%s: %s",
                tokenBuffer.get(current).getStartLine(),
                tokenBuffer.get(current).getStartColumn(),
                current,
                indent,
                message,
                upcomingTokens());
    }

    private static final String ANSI_RESET = "\u001B[0m";

    private static final String ANSI_RED = "\u001B[31m";

    private static final String ANSI_GREEN = "\u001B[32m";

    private static final String ANSI_BLUE = "\u001B[34m";

    private static final String ANSI_YELLOW = "\u001B[33m";

    private static final String ANSI_WHITE = "\u001B[37m";

    // Get next 4 tokens as a string.
    private String upcomingTokens() {
        StringBuilder sb = new StringBuilder();

        // Force up to 4 lookahead tokens into the buffer safely
        ensureBuffered(4);

        int distance = Math.min(tokenBuffer.size() - current, 4);
        for (int i = 0; i < distance; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            YamlToken token = tokenBuffer.get(current + i);
            sb.append(ANSI_BLUE);
            sb.append(token.getType());
            sb.append(ANSI_RESET);
            if (token.getType() == YamlTokenType.SCALAR ||
                token.getType() == YamlTokenType.ANCHOR ||
                token.getType() == YamlTokenType.ALIAS ||
                token.getType() == YamlTokenType.TAG ||
                token.getType() == YamlTokenType.DIRECTIVE
            ){
                String lexeme = StringUtils.debugString(token.getLexeme());
                sb.append("(");
                sb.append(ANSI_WHITE);
                if (lexeme.length() <= MAX_DEBUG_STRING_LENGTH) {
                    sb.append(lexeme);
                } else {
                    sb.append(lexeme.substring(0, MAX_DEBUG_STRING_LENGTH - 1));
                    sb.append(StringUtils.ELLIPSIS);
                }
                sb.append(ANSI_RESET);
                sb.append(")");
            }
        }
        if (tokenBuffer.size() - current > distance) {
            sb.append(", ");
            sb.append(StringUtils.ELLIPSIS);
        }
        return sb.toString();
    }
}