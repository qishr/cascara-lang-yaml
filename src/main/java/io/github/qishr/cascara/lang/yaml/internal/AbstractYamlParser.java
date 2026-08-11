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


package io.github.qishr.cascara.lang.yaml.internal;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.LangDiagnosticCode;
import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.processor.Processor;
import io.github.qishr.cascara.common.lang.streaming.StreamingEventType;
import io.github.qishr.cascara.common.lang.token.TokenCategory;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;
import io.github.qishr.cascara.lang.yaml.ast.NodeStyle;
import io.github.qishr.cascara.lang.yaml.ast.ScalarStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchor;
import io.github.qishr.cascara.lang.yaml.ast.YamlComment;
import io.github.qishr.cascara.lang.yaml.ast.YamlDirective;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
import io.github.qishr.cascara.lang.yaml.exception.YamlDiagnosticCode;
import io.github.qishr.cascara.lang.yaml.exception.YamlParserException;
import io.github.qishr.cascara.lang.yaml.processor.AbstractYamlProcessor;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.streaming.YamlStreamingEvent;
import io.github.qishr.cascara.lang.yaml.token.YamlErrorToken;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

public abstract class AbstractYamlParser<P extends Processor> extends AbstractYamlProcessor<P> {
    private static final int CIRCULAR_BUFFER_SIZE = 256;

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_YELLOW = "\u001B[33m";

    private YamlTokenizer tokenizer;

    protected TokenBuffer tokenBuffer;

    private int depth = 0;

    private int depthLimit;
    protected boolean isMultiDocumentParsing = false;

    /// Buffer to hold comments until a data node is created to claim them.
    private final List<YamlComment> pendingComments = new ArrayList<>();
    private final Map<String, YamlNode> anchorRegistry = new HashMap<>();

    private boolean queueEvents = false;
    private boolean pushEvents = false;

    private Thread parserThread;
    private BlockingDeque<YamlStreamingEvent> events;
    private AtomicBoolean streamEnded = new AtomicBoolean();

    protected AbstractYamlParser() {
    }

    //
    //
    //

    protected void pushEvents(InputStream input) {
        debug("pushEvents");
        preParseStateInit();
        pushEvents = true;
        tokenBuffer.open(input);
        pushEvent(tokenBuffer.peek(), StreamingEventType.START_STREAM, null);

        // TODO: DOC, etc

        parseInternal();

        pushEvent(tokenBuffer.peek(), StreamingEventType.END_STREAM, null);
    }

    protected void queueEvents(InputStream input) {
        debug("queueEvents");
        preParseStateInit();
        queueEvents = true;

        streamEnded.set(false);

        // TODO: Make this capacity higher
        events = new LinkedBlockingDeque<>(2);

        // TODO: Remove this once OnDemandTokenBuffer is working
        tokenBuffer = new PreloadedTokenBuffer();
        //------------------------------------------------------

        tokenBuffer.open(input);
        debug("BEGIN");
        parserThread = new Thread(() -> {
            queueEvent(tokenBuffer.peek(), StreamingEventType.START_STREAM, null);
            parseInternal();
            streamEnded.set(true);
            queueEvent(tokenBuffer.peek(), StreamingEventType.END_STREAM, null);
            debug("END");
        });
        parserThread.start();
    }

    protected boolean hasNextEvent() {
        return !streamEnded.get() || !events.isEmpty();
    }

    protected YamlStreamingEvent nextEvent() {
        debug("nextEvent...");
        if (!hasNextEvent()) {
            debug("nextEvent - stream ended");
        }
        YamlStreamingEvent event;
		try {
			event = events.takeFirst();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
            return null;
		}
        debug("nextEvent: " + event.getType());
        return event;
    }

    //
    //
    //


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

    public List<YamlToken> getTokens() {
        if (tokenBuffer instanceof PreloadedTokenBuffer preloaded) {
            return preloaded.getTokens();
        }
        return List.of();
    }

    //
    // Parsing Methods
    //

    /// Helper to execute internal parsing logic and unpack based on options.
    protected YamlNode parseAndUnpack() {
        YamlStream stream = parseInternal();

        // If the developer wants the full multi-document structure, hand over the stream node
        if (isMultiDocumentParsing) {
            return stream;
        }

        // Otherwise, stay backward-compatible and return the naked first document body
        return stream.getDocuments().isEmpty()
            ? new YamlMap()
            : stream.getDocuments().get(0).getBody();
    }

    protected YamlStream parseInternal() {
        if (this.tokenBuffer.isEmpty()) {
            return new YamlStream();
        }

        consume(YamlTokenType.STREAM_START, LangDiagnosticCode.EXPECTED_STREAM_START);

        YamlStream streamNode = new YamlStream(tokenBuffer.peek());

        // Multi-Document Processing Loop
        while (!tokenBuffer.isAtEnd() && !check(YamlTokenType.STREAM_END) && !check(YamlTokenType.EOF)) {
            int previousPosition = tokenBuffer.offset();

            trace("PI-while");

            if (check(YamlTokenType.NEWLINE) ||
                (check(YamlTokenType.INDENT) && !checkNext(YamlTokenType.DIRECTIVE)) ||
                (check(YamlTokenType.DEDENT) && !checkNext(YamlTokenType.DIRECTIVE))) {
                tokenBuffer.advance();
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

            if (tokenBuffer.isAtEnd() || check(YamlTokenType.STREAM_END) || check(YamlTokenType.EOF)) {
                break;
            }

            // Guard: Malformed indentations that break out of blocks cannot start documents
            if (options.isStrict()
                && (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR) || check(YamlTokenType.VALUE_INDICATOR))) {
                YamlToken badToken = tokenBuffer.peek();
                error(badToken, YamlDiagnosticCode.UNEXPECTED_TOKEN, badToken.getType());
            }

            // YamlToken t = tokenBuffer.peek();
            if (check(YamlTokenType.DOCUMENT_END)) {
                tokenBuffer.advance();
            } else {
                streamNode.addDocument(parseDocument());
                if (!check(YamlTokenType.DIRECTIVE) &&
                    !check(YamlTokenType.DOCUMENT_START)) {
                    break;
                }
            }

            if (tokenBuffer.offset() == previousPosition) {
                break;
            }
        }

        // Attach any loose trailing comments collected during document parsing
        streamNode.getComments().addAll(pendingComments);
        pendingComments.clear();

        // Safely consume trailing layout artifacts and capture any trailing file footer comments
        while (!tokenBuffer.isAtEnd() && !check(YamlTokenType.STREAM_END) && !check(YamlTokenType.EOF)) {
            if (check(YamlTokenType.NEWLINE) || check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                tokenBuffer.advance();
            } else if (check(YamlTokenType.COMMENT)) {
                streamNode.getComments().add(parseComment());
            } else {
                break;
            }
        }

        if (check(YamlTokenType.ERROR)) {
            error(tokenBuffer.peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN);
        } else if (check(YamlTokenType.STREAM_END)) {
            tokenBuffer.advance();
        } else if (check(YamlTokenType.EOF)) {
            tokenBuffer.advance();
        }

        return streamNode;
    }

    /// Parses a single document context, safely handling layout tokens near explicit boundaries
    private YamlDocument parseDocument() {
        debug(">parseDocument");
        depth++;
        try {
            YamlToken startToken = tokenBuffer.peek();
            YamlDocument document = new YamlDocument(startToken);

            if (lookAheadToExplicitMarker()) {
                while (!tokenBuffer.isAtEnd() && check(YamlTokenType.NEWLINE)) {
                    tokenBuffer.advance();
                }
            }

            while (check(YamlTokenType.DIRECTIVE)) {
                YamlToken dirToken = tokenBuffer.advance();
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

            trace("PD-1");
            // After directives, before deciding body, strip layout so skipTrivia can see comments
            while (check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                tokenBuffer.advance();
            }
            skipTrivia(); // this will now see COMMENT(#d) and buffer it
            trace("PD-2");

            // Clean layout indentation wrapping the explicit document boundaries
            if (check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                tokenBuffer.ensureBuffered(1);

                if (!tokenBuffer.isAtEnd(1) && tokenBuffer.peekAhead(1).getType() == YamlTokenType.DOCUMENT_START) {
                    tokenBuffer.advance();
                }

            }

            trace("PD-3");
            if (match(YamlTokenType.DOCUMENT_START)) {
                skipTrivia();
            }
            trace("PD-4");

            if (check(YamlTokenType.DOCUMENT_END) || check(YamlTokenType.DOCUMENT_START) || tokenBuffer.isAtEnd()) {
                document.setBody(createNullScalar());
            } else {
                YamlNode body = parseValue(0, false);
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
    private YamlNode parseValue(int parentIndent, boolean isComplexKey) {
        debug(">parseValue");
        depth++;
        if (depth > depthLimit) {
            error(tokenBuffer.peek(), YamlDiagnosticCode.DEPTH_LIMIT);
        }
        try {
            trace("isComplexKey="+isComplexKey);
            if (check(YamlTokenType.ERROR)) {
                error(tokenBuffer.peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, tokenBuffer.peek().getType());
            }

            if (!check(YamlTokenType.SCALAR) ||
                    tokenBuffer.peek().getScalarStyle() != ScalarStyle.FOLDED) {
                skipTrivia();
            }

            trace("PV-after-skipTrivia");

            YamlNode result = null;
            String pendingAnchor = null;

            YamlToken pendingAnchorToken = null;
            boolean hasIndentedAnchor = false;
            if (checkIndented(YamlTokenType.ANCHOR)) {
                trace("PM-hasIndentedAnchor1");
                tokenBuffer.advance(); // consume INDENT
                hasIndentedAnchor = true;
            }

            if (check(YamlTokenType.ANCHOR)) {
                trace("PV-in-if-anchor1");

                // lookAheadIgnoringComments rather than peek as it may be on the next line
                YamlToken colon = lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR);
                if (colon != null) {
                    // Let parseMap handle the anchor
                    result = parseMap(isComplexKey);
                    attachComments(result);
                    trace("PV-after-parseMap-1");


                    // TODO: This must be moved to happen directly after anchor is parsed
                    if (hasIndentedAnchor && check(YamlTokenType.DEDENT)) {
                        tokenBuffer.advance();
                        hasIndentedAnchor = false;
                    }


                    return result;
                } else {
                    // Scalar map keys are handled in parseKeyNode vua parseMap
                    if (tokenBuffer.peekAhead(1).getType() == YamlTokenType.SCALAR &&
                        tokenBuffer.peekAhead(2).getType() == YamlTokenType.VALUE_INDICATOR
                    ){
                        trace("PV-after-parseMap-2");
                        result = parseMap(isComplexKey);
                        attachComments(result);


                        // TODO: This must be moved to happen directly after anchor is parsed
                        if (hasIndentedAnchor && check(YamlTokenType.DEDENT)) {
                            tokenBuffer.advance();
                            hasIndentedAnchor = false;
                        }


                        return result;
                    }

                    // Consume the anchor and set it as pending
                    pendingAnchorToken = tokenBuffer.advance();
                    trace("PV-in-if-anchor3");

                    if (hasIndentedAnchor) {
                        // skipTrivia();
                        if (check(YamlTokenType.NEWLINE)) {
                            tokenBuffer.advance();
                        }
                        consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT);
                        hasIndentedAnchor = false;
                    }

                    String raw = pendingAnchorToken.getContent();
                    pendingAnchor = raw.startsWith("&") ? raw.substring(1) : raw;
                    int ahead = 0;
                    YamlToken candidate = null;

                    skipTrivia();

                    candidate = tokenBuffer.peekAhead(ahead);
                    while (candidate.getType() == YamlTokenType.NEWLINE ||
                           candidate.getType() == YamlTokenType.COMMENT) {
                        ahead++;
                        candidate = tokenBuffer.peekAhead(ahead);
                    }

                    // Before we decide the anchor is followed by an implicit null,
                    // we check the nect token's indent. If it's more indented or
                    // it starts a flow structure, we continue to parse it.

                    if (candidate.getStartColumn() > parentIndent ||
                        candidate.getType() == YamlTokenType.MAP_START ||
                        candidate.getType() == YamlTokenType.SEQUENCE_START ||
                        candidate.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR
                    ) {
                        skipTrivia();
                    } else {
                        result = new YamlScalar(
                            tokenBuffer.peek(),
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

                    if (hasIndentedAnchor && check(YamlTokenType.DEDENT)) {
                        trace("PV-hasIndentedAnchor-dedent");
                        tokenBuffer.advance();
                        if (check(YamlTokenType.NEWLINE)) {
                            tokenBuffer.advance();
                            skipTrivia();
                        }
                        hasIndentedAnchor = false;
                    }
                }
            }
            trace("PV-after-if-anchor");

            if (check(YamlTokenType.NEWLINE)) {
                tokenBuffer.advance();
                skipTrivia();
            }

            boolean hasIndentedTag = false;
            if (checkIndented(YamlTokenType.TAG)) {
                trace("PV-indented-tag start");
                hasIndentedTag = true;
                tokenBuffer.advance();
            }

            boolean expectAnchorDedent = false;
            if (check(YamlTokenType.INDENT)) {
                tokenBuffer.advance();
                trace("Setting expectAnchorDedent");
                skipTrivia();
                expectAnchorDedent = true;
            }

            // Collect tags (node-level, including !!str on the document body)
            String pendingTag = null;
            // TODO:
            // https://yaml.org/spec/1.2.2/#682-tag-directives
            // It is an error to specify more than one “TAG” directive for the same handle
            // in the same document, even if both occurrences give the same prefix.
            while (check(YamlTokenType.TAG)) {
                trace("PV-TAG start");

                // Scalar map keys are handled in parseKeyNode via parseMap
                if (tokenBuffer.peekAhead(1).getType() == YamlTokenType.ANCHOR &&
                    tokenBuffer.peekAhead(2).getType() == YamlTokenType.SCALAR &&
                    tokenBuffer.peekAhead(3).getType() == YamlTokenType.VALUE_INDICATOR
                ) {
                    trace("PV-TAG anchor-scalar-valueIndicator");
                    result = parseMap(isComplexKey);
                    attachComments(result);
                    return result;
                }

                // Scalar map keys are handled in parseKeyNode via parseMap
                if (tokenBuffer.peekAhead(1).getType() == YamlTokenType.SCALAR &&
                    tokenBuffer.peekAhead(2).getType() == YamlTokenType.VALUE_INDICATOR
                ) {
                    trace("PV-TAG scalar-valueIndicator");
                    result = parseMap(isComplexKey);
                    attachComments(result);
                    return result;
                }

                if (tokenBuffer.peekAhead(1).getType() == YamlTokenType.VALUE_INDICATOR) {
                    trace("PV-TAG valueIndicator");
                    result = parseMap(isComplexKey);
                    attachComments(result);
                    // Check: Did parseMap result in the anchor being applied?
                    return result;
                }

                YamlToken tagTok = tokenBuffer.advance();
                pendingTag = tagTok.getContent();

                skipTrivia();
                trace("PV-TAG end");
            }

            if (hasIndentedTag) {
                trace("PV-indented-tag end");
                while (check(YamlTokenType.NEWLINE)) {
                    tokenBuffer.advance();
                }
                consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT);
            }

            boolean expectTagDedent = false;
            if (check(YamlTokenType.INDENT)) {
                tokenBuffer.advance();
                trace("Setting expectTagDedent");
                skipTrivia();
                expectTagDedent = true;
            }

            if (check(YamlTokenType.KEY_INDICATOR)) {
                trace("PV-key-indicator");
                // result = parseMap(isComplexKey);
                result = parseMap(false);
            }
            else if (check(YamlTokenType.ANCHOR)) {
                result = parseValue(tokenBuffer.peek().getStartColumn(), isComplexKey);
            }
            else if (check(YamlTokenType.ALIAS)) {
                trace("PV-in-if-alias1");
                if (tokenBuffer.peekAhead(1).getType() == YamlTokenType.VALUE_INDICATOR) {
                    result = parseMap(isComplexKey);
                } else {
                    YamlToken tok = tokenBuffer.advance();
                    trace("PV-in-if-alias2");
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
            }
            // Flow map
            else if (check(YamlTokenType.MAP_START)) {
                if (lookAheadFlowMapIsFollowedByColon()) {
                    trace("PV-flow-map-as-key");
                    return parseMap(isComplexKey);
                } else {
                    result = parseFlowMap();
                }
            }
            // Flow sequence
            else if (check(YamlTokenType.SEQUENCE_START)) {
                if (lookAheadFlowSequenceIsFollowedByColon()) {
                    return parseMap(isComplexKey);
                } else {
                    result = parseFlowSequence();
                }
            }
            // Block sequence
            else if (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                result = parseSequence();
            }
            else if (check(YamlTokenType.VALUE_INDICATOR)) {
                // Map entry with null key
                result = parseMap(isComplexKey);
            }
            else if (check(YamlTokenType.SCALAR)) {
                if (tokenBuffer.peekAhead(1).getType() == YamlTokenType.VALUE_INDICATOR) {
                    result = parseMap(isComplexKey);
                } else {
                    result = parseScalar();
                    trace("PV-after-parseScalar");
                    if (check(YamlTokenType.NEWLINE)) {
                        tokenBuffer.advance();
                    }
                }
            }
            else {
                result = new YamlScalar(
                    tokenBuffer.peek(),
                    PrimitiveType.NULL,
                    options
                );
            }

            // Check: Should pending comments be attached to the result before this skipTrivia?
            skipTrivia();

            if (expectTagDedent) {
                if (check(YamlTokenType.DEDENT)) {
                    trace("Consuming expectTagDedent");
                    tokenBuffer.advance();
                } else {
                    trace("expectTagDedent not found");
                    // We should really report this error, but it breaks
                    // the valid_12_content_type_records test
                    // error(tokenBuffer.peek(), YamlDiagnosticCode.EXPECTED_DEDENT);
                }
            }

            // 3. Attach tags
            if (pendingTag != null && result != null) {
                trace("PV-setTag");
                if (result instanceof YamlScalar scalar) {
                    if (scalar.getPrimitiveType() == PrimitiveType.NULL) {
                        scalar = new YamlScalar("", ScalarStyle.PLAIN, options);
                        result = scalar;
                    }
                }
                result.setTag(pendingTag);
            }

            if (expectAnchorDedent) {
                if (check(YamlTokenType.DEDENT)) {
                    trace("Consuming expectAnchorDedent");
                    tokenBuffer.advance();
                } else {
                    trace("expectAnchorDedent not found");
                    // We should really report this error, but it breaks
                    // the valid_12_content_type_records test
                    // error(tokenBuffer.peek(), YamlDiagnosticCode.EXPECTED_DEDENT);
                }
            }

            if (hasIndentedAnchor && check(YamlTokenType.DEDENT)) {
                tokenBuffer.advance();
                hasIndentedAnchor = false;
            }


            if (pendingAnchorToken != null) {
                String raw = pendingAnchorToken.getContent();
                String anchorName = raw.startsWith("&") ? raw.substring(1) : raw;
                result.setAnchor(anchorName);
                YamlAnchor anchorNode = new YamlAnchor(
                    result.getStartLine(),
                    result.getStartColumn(),
                    anchorName,
                    result
                );
                anchorRegistry.put(anchorName, result);
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
    private YamlNode parseMap(boolean isComplexKey) {
        debug(">parseMap");
        depth++;
        try {
            trace("isComplexKey="+isComplexKey);
            YamlToken startToken = tokenBuffer.peek();
            YamlMap map = new YamlMap(startToken, options);
            map.setStyle(NodeStyle.BLOCK);

            Set<Object> seenKeys = new HashSet<>();
            int mapColumn = -1;
            boolean expectComplexKeyDedent = false;

            while (!tokenBuffer.isAtEnd()) {
                trace("PM-loop");
                skipTrivia();

                if (check(YamlTokenType.DEDENT)) {
                    trace("DEDENT break");
                    break;
                }

                if (check(YamlTokenType.INDENT)) {
                    trace("PM-complex-key-indent");
                    if (isComplexKey) {
                        trace("isComplexKey, so accepting the indent");
                        tokenBuffer.advance();
                        skipTrivia();
                        expectComplexKeyDedent = true;
                    } else {
                        error(tokenBuffer.peek(), YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    }
                }

                // Peek at the token that will tell us if an entry is actually here
                trace("at markerToken");
                YamlToken markerToken = tokenBuffer.peek();

                boolean hasExplicitKey = check(YamlTokenType.KEY_INDICATOR);
                boolean isScalarKeyStart =
                    check(YamlTokenType.SCALAR) ||
                    check(YamlTokenType.ALIAS) ||
                    check(YamlTokenType.ANCHOR) ||
                    check(YamlTokenType.TAG) ||
                    check(YamlTokenType.VALUE_INDICATOR) ||
                    check(YamlTokenType.MAP_START) ||
                    check(YamlTokenType.SEQUENCE_START);

                // TAGs, but wrapped in INDENT/DEDENT and optional newlines.
                // TODO: Possibly other tokens wrapped in the same way.
                if (checkIndented(YamlTokenType.TAG)) {
                    isScalarKeyStart = true;
                }

                if (!hasExplicitKey && !isScalarKeyStart) {
                    if (tokenBuffer.peek().getStartColumn() > map.getStartColumn()) {
                        error(markerToken, YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    }
                    trace("Not-a-key break");
                    break;
                }

                int markerColumn = markerToken.getStartColumn();

                if (mapColumn == -1) {
                    mapColumn = markerColumn;
                } else {
                    if (markerColumn < mapColumn && !isComplexKey) {
                        error(markerToken, YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    }
                }

                YamlNode key;

                if (hasExplicitKey) {
                    tokenBuffer.advance(); // Consume '?'
                    skipTrivia();
                    // Explicit keys are always parsed via parseValue in case they are complex
                    key = parseValue(markerColumn, true);
                } else {
                    if (check(YamlTokenType.MAP_START) ||
                        check(YamlTokenType.SEQUENCE_START)) {
                        trace("map debug");
                    }
                    // Standard implicit key
                    key = parseKeyNode(markerColumn);
                }

                attachComments(key);

                if (options.isStrict()) {
                    // For scalars, track the underlying unescaped string value.
                    // For complex structural nodes, track the node identity/structural equivalence.
                    Object keyTrackingToken = (key instanceof YamlScalar scalarKey) ? scalarKey.asString() : key;
                    if (!seenKeys.add(keyTrackingToken)) {
                        String duplicateKeyRepresentation = (key instanceof YamlScalar scalarKey)
                            ? scalarKey.asString()
                            : "[Complex Key at Line " + key.getStartLine() + "]";

                        error(tokenBuffer.previous(), YamlDiagnosticCode.DUPLICATE_KEY, duplicateKeyRepresentation);
                    }
                }

                skipTrivia();

                YamlNode value;

                if (check(YamlTokenType.VALUE_INDICATOR)) {
                    consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_MAP_KEY);
                    parseInlineComment(key);

                    trace("before parseValue");
                    if (check(YamlTokenType.NEWLINE) && !hasIndentedValueAfterNewline()) {
                        value = createNullScalar();
                    }
                    else {
                        skipTrivia();
                        boolean isValueIndented = false;
                        boolean hasIndentedAnchor = false;

                        if (checkIndented(YamlTokenType.TAG)) {
                            // hasIndentedTag = true;
                        }
                        else if (checkIndented(YamlTokenType.ANCHOR)) {
                            // Let parseValue handle it
                        }
                        else if (check(YamlTokenType.INDENT)) {
                            trace("PM-value-indent");
                            isValueIndented = true;
                            tokenBuffer.advance();
                        }

                        value = parseValue(mapColumn, false);

                        skipTrivia();
                        if (isValueIndented) {
                            trace("PM-value-dedent");
                            consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT);
                        }

                        if (hasIndentedAnchor) {
                            trace("PM-hasIndentedAnchor-dedent");
                            consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT);
                        }
                    }
                    trace("after parseValue");

                } else {
                    // If there is no value indicator and the key was explicit, the value is null
                    value = createNullScalar();
                    if (!hasExplicitKey) {
                        error(tokenBuffer.peek(), YamlDiagnosticCode.EXPECTED_COLON_MAP_KEY);
                    }
                }

                map.put(new YamlMapEntry(key, value));
                skipTrivia();

                // If a map value is followed by these tokens, the map is finished.
                if (check(YamlTokenType.COMMA) ||
                    check(YamlTokenType.SEQUENCE_END) ||
                    check(YamlTokenType.MAP_END)
                ){
                    trace("PM-structural-end");
                    break;
                }
            }

            if (expectComplexKeyDedent) {
                trace("PM-complex-key-dedent");
                consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT);
            }

            return map;
        } finally {
            depth--;
            debug("<parseMap");
        }
    }


    private YamlNode parseKeyNode(int parentIndent) {
        YamlToken tok = tokenBuffer.peek();
        debug(">parseKeyNode");
        depth++;
        try {

            if (check(YamlTokenType.NEWLINE)) {
                tokenBuffer.advance();
            }

            if (check(YamlTokenType.VALUE_INDICATOR)) {
                // Empty key
                return new YamlScalar(tokenBuffer.peek(), "", PrimitiveType.STRING, ScalarStyle.PLAIN, options);
            }

            if (tok.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR ||
                tok.getType() == YamlTokenType.INDENT) {

                return parseValue(parentIndent, false);
            }

            switch (tok.getType()) {
                case MAP_START: {
                    return parseFlowMap();
                }

                case SEQUENCE_START: {
                    return parseFlowSequence();
                }

                case SCALAR:
                    YamlScalar scalar = parseScalar();
                    // skipTrivia();
                    skipEOL();
                    return scalar;

                case ALIAS: {
                    tokenBuffer.advance(); // consume alias token

                    String raw = tok.getContent();
                    String name = raw.startsWith("*") ? raw.substring(1) : raw;

                    YamlAlias alias = new YamlAlias(tok, name);

                    // Mirror parseValue alias resolution
                    if (anchorRegistry.containsKey(name)) {
                        alias.setResolvedNode(anchorRegistry.get(name));
                    }

                    return alias;
                }

                case TAG: {
                    YamlScalar key;
                    YamlToken tagTok = tokenBuffer.advance();
                    if (check(YamlTokenType.NEWLINE)) {
                        skipTrivia();
                    }
                    if (check(YamlTokenType.ANCHOR)) {
                        YamlToken anchorTok = tokenBuffer.advance();
                        String raw = anchorTok.getContent();
                        String name = raw.startsWith("&") ? raw.substring(1) : raw;
                        skipTrivia();
                        if (check(YamlTokenType.SCALAR)) {
                            key = parseScalar();
                            key.setTag(tagTok.getContent());
                            key.setAnchor(anchorTok.getContent());
                        } else {
                            // If anchor is not followed by scalar, treat as null key with tag+anchor
                            key = createNullScalar();
                            key.setTag(tagTok.getContent());
                            key.setAnchor(anchorTok.getContent());
                        }
                        anchorRegistry.put(name, key);
                        return key;
                    }
                    if (check(YamlTokenType.VALUE_INDICATOR)) {
                        String tag = tagTok.getContent();
                        key = new YamlScalar("", ScalarStyle.PLAIN, options);
                        key.setTag(tag);
                    }
                    else if (check(YamlTokenType.SCALAR)) {
                        String tag = tagTok.getContent();
                        key = parseScalar();


                        if (key instanceof YamlScalar scalarKey) {
                            if (scalarKey.getPrimitiveType() == PrimitiveType.NULL) {
                                key = new YamlScalar("", ScalarStyle.PLAIN, options);
                            }
                        }


                        key.setTag(tag);
                    } else {
                        key = createNullScalar();
                        error(tokenBuffer.peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, tokenBuffer.peek().getType());
                    }
                    return key;
                }

                case ANCHOR: {
                    // Anchors on scalars that start a map are not handled in parseValue.
                    tokenBuffer.advance(); // consume &anchor
                    if (check(YamlTokenType.NEWLINE)) {
                        skipTrivia();
                    }

                    YamlNode key;
                    String raw = tok.getContent();
                    String name = raw.startsWith("&") ? raw.substring(1) : raw;

                    if (check(YamlTokenType.MAP_START)) {
                        key = parseFlowMap();
                    }
                    else if (check(YamlTokenType.SEQUENCE_START)) {
                        key = parseFlowSequence();
                    }
                    else if (check(YamlTokenType.SCALAR)) {
                        // Parse the scalar key that follows
                        // NOTE: at the moment parseKeyNode is only called for simple keys.
                        // If we call it for complex keys, this parseValue call should
                        // specify if it's a complex key.
                        key = parseScalar();
                    }
                    else {
                        key = createNullScalar();
                        if (tokenBuffer.peek().getType().getCategory() != TokenCategory.PUNCTUATION) {
                            error(tokenBuffer.peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, tokenBuffer.peek().getType());
                        }
                    }
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
                    trace("parseKeyNode: KEY_INDICATOR");
                    tokenBuffer.advance(); // consume '?'
                    skipTrivia();
                    // NOTE: at the moment parseKeyNode is only called for simple keys.
                    // If we call it for complex keys, this parseValue call should
                    // specify if it's a complex key.
                    return parseValue(parentIndent, false);

                default:
                    trace("parseKeyNode: UNEXPECTED " + tok.getType());
                    error(tok, GenericDiagnosticCode.ERROR, "Unexpected token in key position: " + tok.getType());
                    return new YamlScalar(tok, PrimitiveType.ANY, options);
            }
        } finally {
            depth--;
            debug("<parseKeyNode");
        }
    }

    /// Parses a block sequence (list) indicated by leading dashes.
    private YamlNode parseSequence() {
        debug(">parseSequence");
        depth++;
        try {




            boolean hasIndentedAnchor = false;
            if (checkIndented(YamlTokenType.ANCHOR)) {
                debug("PA-hasIndentedAnchor1");
                hasIndentedAnchor = true;
                tokenBuffer.advance();
            }

            YamlToken pendingAnchor = null;
            if (check(YamlTokenType.ANCHOR)) {
                pendingAnchor = tokenBuffer.advance();
                if (check(YamlTokenType.NEWLINE)) {
                    skipTrivia();
                }
                debug("PA-hasIndentedAnchor2");
                if (hasIndentedAnchor && check(YamlTokenType.DEDENT)) {
                    tokenBuffer.advance();
                    hasIndentedAnchor = false;
                }
            }




            YamlToken startToken = tokenBuffer.peek();
            int indicatorColumn = startToken.getStartColumn();

            YamlSequence sequence = new YamlSequence(startToken);
            sequence.setStyle(NodeStyle.BLOCK);
            attachComments(sequence);

            while (!tokenBuffer.isAtEnd()) {
                skipTrivia();
                trace("PS-while");

                // STOP: Hand control back to the dispatcher
                // If we see a DEDENT but the next token is still a '-', this is misaligned indentation
                if (check(YamlTokenType.DEDENT)) {
                    trace("PS-while-dedent");
                    // Regardless of the DEDENT being valid or not, we exit because it's
                    // not part of this sequence and is up to the caller to deal with.
                    break;
                }

                if (check(YamlTokenType.INDENT)) {
                    error(tokenBuffer.peek(), YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                }

                if (!check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                    trace("PS-while-not-seq-indicator");
                    break;
                }

                int currentColumn = tokenBuffer.peek().getStartColumn();
                if (currentColumn != indicatorColumn) {
                    error(tokenBuffer.peek(), YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                }

                YamlToken nextTokenIsIndicator = lookAheadIgnoringComments(YamlTokenType.SEQUENCE_ENTRY_INDICATOR);

                tokenBuffer.advance(); // Consume the '-'

                if (nextTokenIsIndicator != null && nextTokenIsIndicator.getStartColumn() == indicatorColumn) {
                    sequence.add(createNullScalar());
                } else {
                    // parseValue handles the content, including potential nested blocks
                    YamlNode item = parseValue(indicatorColumn, false);
                    sequence.add(item);
                }

                skipTrivia();
            }




            if (pendingAnchor != null) {
                String raw = pendingAnchor.getContent();
                String name = raw.startsWith("&") ? raw.substring(1) : raw;

                anchorRegistry.put(name, sequence);

                // Wrap in YamlAnchorNode (same as parseValue)
                YamlAnchor anchorNode = new YamlAnchor(
                    sequence.getStartLine(),
                    sequence.getStartColumn(),
                    name,
                    sequence
                );
                return anchorNode;
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

            while (!check(YamlTokenType.SEQUENCE_END) && !tokenBuffer.isAtEnd()) {
                skipTrivia();

                sequence.add(parseValue(startToken.getStartColumn(), false));
                skipTrivia();

                if (!match(YamlTokenType.COMMA)) break;
                skipTrivia();
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

            while (!tokenBuffer.isAtEnd()) {
                skipTrivia();

                // 1. Parse Key
                // YamlScalar key = parseScalar();
                YamlNode key = parseKeyNode(startToken.getStartColumn());
                skipTrivia();

                YamlNode value;
                if (check(YamlTokenType.VALUE_INDICATOR)) {
                    // 2. Consume Value Indicator
                    consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP);

                    // 3. Parse Value
                    value = parseValue(key.getStartColumn(), false);
                } else {
                    value = createNullScalar();
                }

                // 4. Store Entry
                map.put(new YamlMapEntry(key, value));

                skipTrivia();

                // 5. Check for continuation or end
                if (match(YamlTokenType.COMMA)) {
                    // Allow trailing commas by checking for end after comma
                    if (null != lookAheadIgnoringComments(YamlTokenType.MAP_END)) {
                        skipTrivia();
                    }
                    if (match(YamlTokenType.MAP_END)) {
                        break;
                    }
                    continue;
                } else if (match(YamlTokenType.MAP_END)) {
                    break;
                } else if (null != lookAheadIgnoringComments(YamlTokenType.MAP_END)) {
                    skipTrivia();
                    break;
                } else {
                    error(tokenBuffer.peek(), YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP);
                }
            }
            return map;
        } finally {
            depth--;
            debug("<parseFlowMap");
        }
    }

    private YamlScalar parseScalar() {
        debug(">parseScalar");
        depth++;
        try {
            YamlToken token = consume(YamlTokenType.SCALAR, YamlDiagnosticCode.EXPECTED_SCALAR);

            ScalarStyle style = token.getScalarStyle();
            YamlScalar scalar;

            if (style == ScalarStyle.LITERAL) {
                trace("LITERAL_BLOCK");
                scalar = new YamlScalar(
                    token,
                    token.getContent(),
                    PrimitiveType.STRING,
                    ScalarStyle.LITERAL,
                    options
                );

                if (check(YamlTokenType.COMMENT) && tokenBuffer.peek().getStartLine() == token.getStartLine()) {
                    scalar.addComment(parseComment());
                }
                parseInlineComment(scalar);
                // return scalar;
            }

            else  if (style == ScalarStyle.FOLDED) {
                trace("FOLDED");

                String content;

                content = token.getContent();

                // If the tokenizer already produced multi-line content,
                // DO NOT re-fold it. Just return it as-is.
                scalar = new YamlScalar(
                    token, //contentToken != null ? contentToken : token,
                    content,
                    PrimitiveType.STRING,
                    ScalarStyle.FOLDED,
                    options
                );
                parseInlineComment(scalar);
                // return scalar;
            }

            else if (style == ScalarStyle.PLAIN) {
                trace("PLAIN");
                // just return the scalar as-is
                scalar = new YamlScalar(
                    token,
                    PrimitiveType.ANY,
                    options
                );
                parseInlineComment(scalar);
                // return scalar;
            }
            else {
                trace("DEFAULT");
                scalar = new YamlScalar(
                    token,
                    PrimitiveType.ANY,
                    options
                );
                if (check(YamlTokenType.COMMENT) && tokenBuffer.peek().getStartLine() == token.getStartLine()) {
                    scalar.addComment(parseComment());
                }
                parseInlineComment(scalar);
            }


            debug("PS-before-queueing-event");
            if (queueEvents) {
                debug("PS-begin-queueing-event");
                queueEvent(scalar, StreamingEventType.VALUE_SCALAR, scalar.getContent());
                // try {
                //     YamlStreamingEvent event = new YamlStreamingEvent(scalar.getStartLine(), scalar.getStartColumn(), StreamingEventType.VALUE_SCALAR, scalar.getContent());
				// 	events.putLast(event);
                //     debug("PS-end-queueing-event");
				// } catch (InterruptedException e) {
                //     debug("PS-error-queueing-event");
				// 	// TODO Auto-generated catch block
				// 	e.printStackTrace();
				// }
            }
            if (pushEvents) {
                pushEvent(scalar, StreamingEventType.VALUE_SCALAR, scalar.getContent());
            }


            return scalar;
        } finally {
            depth--;
            debug("<parseScalar");
        }
    }

    private YamlComment parseComment() {
        YamlToken token = tokenBuffer.advance();
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

    /// Collects comments and skips newlines, storing comments in the buffer.
    private void skipTrivia() {
        trace("skipTrivia");
        int extraIndent = 0;

        // If this gets set, we must continue until indentation is back at the level it specified.
        int skipUntilIndentLevel = -1;

        while (!tokenBuffer.isAtEnd()) {
            YamlToken token = tokenBuffer.peek();
            YamlTokenType type = token.getType();
            if (type == YamlTokenType.EOF || type == YamlTokenType.STREAM_END) {
                break;
            }
            if (type == YamlTokenType.NEWLINE) {
                tokenBuffer.advance();
                continue;
            }
            if (type == YamlTokenType.COMMENT) {
                // TODO: This is rubbish. Document level comments don't have to be at column 1.
                // If it's a root-level comment at the end of the file, leave it for the stream
                if (tokenBuffer.peek().getStartColumn() == 1 && tokenBuffer.isTrailingStreamComment()) {
                    break;
                }
                pendingComments.add(parseComment());
                continue;
            }

            // Comments can be indented
            if (type == YamlTokenType.INDENT) {
                // Find the matching DEDENT.
                // If there is nothing but comments and whitespace in between,
                // consume up to and including the DEDENT.
                if (skipUntilIndentLevel > -1) {
                    // debug("can skip indented region");
                    extraIndent++;
                    tokenBuffer.advance();
                    continue;
                }
                else if (skipUntilIndentLevel == -1 && lookAheadToMatchingIndentTriviaOnly()) {
                    skipUntilIndentLevel = extraIndent;
                    extraIndent++;
                    tokenBuffer.advance();
                    continue;
                }
                break;
            }
            if (skipUntilIndentLevel > -1 && type == YamlTokenType.DEDENT) {
                extraIndent--;
                if (extraIndent == 0) {
                    // debug("finished indented region");
                    skipUntilIndentLevel = -1;
                }
                tokenBuffer.advance();
                continue;
            }
            break;
        }
    }

    private YamlScalar createNullScalar() {
        return new YamlScalar(tokenBuffer.peek(), PrimitiveType.NULL, options);
    }

    private void skipEOL() {
        YamlToken token = tokenBuffer.peek();
        YamlTokenType type = token.getType();
        if (type == YamlTokenType.NEWLINE) {
            tokenBuffer.advance();
        }
    }

    private void parseInlineComment(YamlNode node) {
        // If the very next token is a comment on the same line, it belongs to THIS node
        if (check(YamlTokenType.COMMENT) && tokenBuffer.peek().getStartLine() == node.getStartLine()) {
            node.addComment(parseComment());
        }
    }

    /// Clears the [pendingComments] buffer by attaching them to the given node.
    private <T extends YamlNode> T attachComments(T node) {
        for (YamlComment comment : pendingComments) {
            node.addComment(comment);
        }
        pendingComments.clear();
        return node;
    }

    //
    // Push and Pull helpers
    //

    private void pushEvent(YamlToken token, StreamingEventType type, String content) {
        pushEvent(token.getStartLine(), token.getStartColumn(), type, content);
    }

    private void pushEvent(YamlNode node, StreamingEventType type, String content) {
        pushEvent(node.getStartLine(), node.getStartColumn(), type, content);
    }

    private void pushEvent(int line, int column, StreamingEventType type, String content) {


        //
        // TODO
        //


    }

    private void queueEvent(YamlToken token, StreamingEventType type, String content) {
        queueEvent(token.getStartLine(), token.getStartColumn(), type, content);
    }

    private void queueEvent(YamlNode node, StreamingEventType type, String content) {
        queueEvent(node.getStartLine(), node.getStartColumn(), type, content);
    }

    private void queueEvent(int line, int column, StreamingEventType type, String content) {
        YamlStreamingEvent event = new YamlStreamingEvent(
            line, column,
            type,
            content
        );

        debug("PS-begin-queueing-event");
        try {
            events.putLast(event);
            debug("PS-end-queueing-event");
        } catch (InterruptedException e) {
            debug("PS-error-queueing-event");
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    //
    // Navigation & Streaming Lookahead Helpers
    //

    @Nullable
    private YamlToken consume(YamlTokenType type, DiagnosticCode msgCode) {
        if (check(type)) return tokenBuffer.advance();
        YamlToken token = tokenBuffer.peek();
        error(tokenBuffer.peek(), msgCode, token.getType());
        return null;
    }

    private boolean match(YamlTokenType... types) {
        for (YamlTokenType type : types) {
            if (check(type)) {
                tokenBuffer.advance();
                return true;
            }
        }
        return false;
    }

    /// Checks the type of the token returned by `tokenBuffer.peek()`
    private boolean check(YamlTokenType type) {
        if (tokenBuffer.isAtEnd()) return false;
        return tokenBuffer.peek().getType() == type;
    }

    private boolean checkNext(YamlTokenType type) {
        YamlToken next = tokenBuffer.peekAhead(1);
        return next != null && next.getType() == type;
    }

    private boolean isKnownDirective(YamlToken token) {
        String content = token.getContent();
        return content.startsWith("%YAML") || content.startsWith("%TAG");
    }

    private boolean checkIndented(YamlTokenType type) {
        trace("checkIndented");
        if (tokenBuffer.isAtEnd()) return false;
        if (check(YamlTokenType.INDENT)) {
            if (tokenBuffer.peekAhead(1).getType() == type) {
                int ahead = 2;
                while (tokenBuffer.peekAhead(ahead).getType() == YamlTokenType.NEWLINE) {
                    ahead++;
                }
                if (tokenBuffer.peekAhead(ahead).getType() == YamlTokenType.DEDENT) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasIndentedValueAfterNewline() {
        int i = 1;
        boolean sawIndent = false;

        while (true) {
            if (tokenBuffer.isAtEnd(i)) return false;

            YamlToken t = tokenBuffer.peekAhead(i);
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
            // First non‑trivia token
            if (tt == YamlTokenType.SEQUENCE_ENTRY_INDICATOR) {
                return true; // sequence value
            }

            return sawIndent; // fallback for indented block values
        }
    }

    private boolean lookAheadFlowMapIsFollowedByColon() {
        int i = 0;

        // Must start with '{'
        if (!tokenBuffer.peekAhead(i).getType().equals(YamlTokenType.MAP_START)) {
            return false;
        }

        i++; // move past '{'
        int depth = 1;

        // Scan until the matching '}'
        while (!tokenBuffer.isAtEnd(i) && depth > 0) {
            YamlToken t = tokenBuffer.peekAhead(i);

            switch (t.getType()) {
                case MAP_START:
                    depth++;
                    break;

                case MAP_END:
                    depth--;
                    break;

                case NEWLINE:
                case COMMENT:
                    // ignore trivia
                    break;

                default:
                    // normal token, just skip
                    break;
            }

            i++;
        }

        if (depth != 0) {
            // malformed flow map; let normal parsing handle the error
            return false;
        }

        // Now skip trivia after the closing '}'
        while (i < tokenBuffer.size()) {
            YamlToken t = tokenBuffer.peekAhead(i);
            if (t.getType() == YamlTokenType.NEWLINE ||
                t.getType() == YamlTokenType.COMMENT) {
                i++;
                continue;
            }
            break;
        }

        // The next non-trivia token must be ':'
        return i < tokenBuffer.size() &&
               tokenBuffer.peekAhead(i).getType() == YamlTokenType.VALUE_INDICATOR;
    }

    private boolean lookAheadToMatchingIndentTriviaOnly() {
        int indentLevel = 0;
        int ahead = 0;
        while (!tokenBuffer.isAtEnd(ahead)) {
            YamlToken token = tokenBuffer.peekAhead(ahead);
            YamlTokenType type = token.getType();
            if (type == YamlTokenType.EOF || type == YamlTokenType.STREAM_END) {
                break;
            }
            if (type == YamlTokenType.NEWLINE || type == YamlTokenType.COMMENT) {
                ahead++;
                continue;
            }
            if (type == YamlTokenType.INDENT) {
                indentLevel++;
                ahead++;
                continue;
            }
            if (type == YamlTokenType.DEDENT) {
                indentLevel--;
                if (indentLevel == 0) {
                    return true;
                }
                ahead++;
                continue;
            }
            return false;
        }
        return false;
    }

    /// Safely looks ahead to check for an explicit document boundary or directive.
    /// Uses a clean index bound to prevent infinite spin conditions.
    private boolean lookAheadToExplicitMarker() {
        int ahead = 0;

        while (!tokenBuffer.isAtEnd(ahead)) {
            YamlTokenType type = tokenBuffer.peekAhead(ahead).getType();
            if (type == YamlTokenType.DOCUMENT_START || type == YamlTokenType.DIRECTIVE) {
                return true;
            }
            if (type == YamlTokenType.INDENT || type == YamlTokenType.DEDENT ||
                type == YamlTokenType.NEWLINE || type == YamlTokenType.COMMENT) {
                ahead++;
            } else {
                break; // Exit immediately on any structural data token
            }
        }
        return false;
    }

    /// Searches forward from `current` until it reaches a token matching `targetType`.
    /// If it finds `targetType` it returns the token.
    /// If it finds NEWLINE or COMMENT it continues.
    /// If if finds anything else it returns null.
    @Nullable
    private YamlToken lookAheadIgnoringComments(YamlTokenType targetType) {
        int ahead = 1;
        while (!tokenBuffer.isAtEnd(ahead)) {
            YamlToken token = tokenBuffer.peekAhead(ahead);
            YamlTokenType type = token.getType();
            if (type == targetType) return token;
            if (type == YamlTokenType.NEWLINE || type == YamlTokenType.COMMENT) {
                ahead++;
                continue;
            }
            break;
        }
        return null;
    }

    private boolean lookAheadFlowSequenceIsFollowedByColon() {
        int i = 0;

        // Must start with '['
        if (!tokenBuffer.peekAhead(i).getType().equals(YamlTokenType.SEQUENCE_START)) {
            return false;
        }

        i++; // move past '['
        int depth = 1;

        // Scan until the matching ']'
        while (i < tokenBuffer.size() && depth > 0) {
            YamlToken t = tokenBuffer.peekAhead(i);

            switch (t.getType()) {
                case SEQUENCE_START:
                    depth++;
                    break;

                case SEQUENCE_END:
                    depth--;
                    break;

                case NEWLINE:
                case COMMENT:
                    // ignore trivia
                    break;

                default:
                    // normal token, just skip
                    break;
            }

            i++;
        }

        if (depth != 0) {
            // malformed flow sequence; let normal parsing handle the error
            return false;
        }

        // Now skip trivia after the closing ']'
        while (i < tokenBuffer.size()) {
            YamlToken t = tokenBuffer.peekAhead(i);
            if (t.getType() == YamlTokenType.NEWLINE ||
                t.getType() == YamlTokenType.COMMENT) {
                i++;
                continue;
            }
            break;
        }

        // The next non-trivia token must be ':'
        return i < tokenBuffer.size() &&
               tokenBuffer.peekAhead(i).getType() == YamlTokenType.VALUE_INDICATOR;
    }

    protected void preParseStateInit() {
        tokenBuffer = new OnDemandTokenBuffer(CIRCULAR_BUFFER_SIZE);
        tokenBuffer.setTokenizer(getTokenizer());
        anchorRegistry.clear();
        pendingComments.clear();
        depthLimit = options.getDepthLimit();
        isMultiDocumentParsing = options.isMultiDocument();
        // syncObject = Thread.currentThread();
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
    private void trace(String message, Object... details) {
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.TRACE)) return;
        report(message, details);
    }

    /// Log the current method name and upcoming tokens
    private void debug(String message, Object... details) {
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.DEBUG)) return;
        report(message, details);
    }

    private void report(String message, Object... details) {
        // Ensure at least the current token is loaded to grab safe coordinates
        tokenBuffer.ensureBuffered(0);
        if (tokenBuffer.isAtEnd()) return;

        // Create indentation based on recursion depth
        String indent = "  ".repeat(Math.max(0, depth));

        char first = message.charAt(0);
        String output;

        if (first == '>' || first == '<') {
            output = first + ANSI_YELLOW + message.substring(1) + ANSI_RESET;
        } else {
            output = message;
        }

        reporter.debug("L%3d C%3d I%3d %s%s: %s",
            tokenBuffer.peek().getStartLine(),
            tokenBuffer.peek().getStartColumn(),
            tokenBuffer.offset(),
            indent,
            output,
            tokenBuffer.upcomingTokens());
    }
}
