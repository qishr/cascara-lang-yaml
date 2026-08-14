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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.LangDiagnosticCode;
import io.github.qishr.cascara.common.annotation.Nullable;
import io.github.qishr.cascara.common.lang.processor.Processor;
import io.github.qishr.cascara.common.lang.streaming.StreamingEventType;
import io.github.qishr.cascara.common.lang.token.TokenCategory;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;
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
import io.github.qishr.cascara.lang.yaml.ast.YamlTagDirective;
import io.github.qishr.cascara.lang.yaml.exception.YamlDiagnosticCode;
import io.github.qishr.cascara.lang.yaml.exception.YamlParserException;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.streaming.YamlStreamingEvent;
import io.github.qishr.cascara.lang.yaml.token.YamlErrorToken;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;
import io.github.qishr.cascara.lang.yaml.util.NodeStyle;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;
import io.github.qishr.cascara.lang.yaml.util.YamlDirectiveType;

public abstract class AbstractYamlParser<P extends Processor> extends AbstractYamlProcessor<P> {
    private static final int CIRCULAR_BUFFER_SIZE = 256;

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_YELLOW = "\u001B[33m";

    private YamlTokenizer tokenizer;
    protected TokenBuffer tokenBuffer;

    private int depth = 0;
    private int depthLimit;

    protected boolean isMultiDocumentParsing = false;

    private boolean continueAfterError = true;
    protected AtomicBoolean errorEncountered = new AtomicBoolean();

    /// Buffer to hold comments until a data node is created to claim them.
    private final List<YamlComment> pendingComments = new ArrayList<>();
    private final Map<String, YamlNode> anchorRegistry = new HashMap<>();

    private YamlDocument document;

    protected AbstractYamlParser() {
    }

    protected void handleEvent(YamlStreamingEvent event) {}

    public P setContinueAfterError(boolean b) {
        continueAfterError = b;
        return self();
    }

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

    protected YamlStream parseInternal() {
        if (this.tokenBuffer.isEmpty()) {
            return new YamlStream();
        }

        consume(YamlTokenType.STREAM_START, LangDiagnosticCode.EXPECTED_STREAM_START);
        createEvent(tokenBuffer.peek(), StreamingEventType.START_STREAM);

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

            if (check(YamlTokenType.DOCUMENT_END)) {
                tokenBuffer.advance();
            } else {
                YamlDocument doc = parseDocument();
                streamNode.addDocument(doc);

                YamlToken tokenAfterDoc = tokenBuffer.peek();
                YamlTokenType tokenAfterDocType = tokenAfterDoc.getType();
                debug("tokenAfterDoc="+tokenAfterDoc);

                // if (!check(YamlTokenType.DIRECTIVE) &&
                //     !check(YamlTokenType.DOCUMENT_START)) {
                //     break;
                // }
                if (tokenBuffer.isAtEnd() || check(YamlTokenType.STREAM_END) || check(YamlTokenType.EOF)) {
                    break;
                }
                if (!doc.hasEndMarker() && !(
                    tokenAfterDocType == YamlTokenType.COMMENT ||
                    tokenAfterDocType == YamlTokenType.DOCUMENT_START ||
                    tokenAfterDocType == YamlTokenType.DEDENT // If the body is a sequence this happens
                )) {
                    error(tokenBuffer.peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, tokenAfterDocType);
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

        if (!errorEncountered.get()) {
            createEvent(tokenBuffer.peek(), StreamingEventType.END_STREAM);
        }

        return streamNode;
    }

    /// Parses a single document context, safely handling layout tokens near explicit boundaries
    private YamlDocument parseDocument() {
        debug(">parseDocument");
        depth++;
        try {
            YamlToken startToken = tokenBuffer.peek();
            document = new YamlDocument(startToken);

            if (lookAheadToExplicitMarker()) {
                while (!tokenBuffer.isAtEnd() && check(YamlTokenType.NEWLINE)) {
                    tokenBuffer.advance();
                }
            }

            while (check(YamlTokenType.DIRECTIVE)) {
                parseDirective();
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

            YamlToken docStartToken = tokenBuffer.peek();
            if (docStartToken.getType() == YamlTokenType.DOCUMENT_START) {
                createEvent(tokenBuffer.peek(), StreamingEventType.START_DOCUMENT, "---");
                tokenBuffer.advance();
                skipTrivia();
            } else {
                createEvent(tokenBuffer.peek(), StreamingEventType.START_DOCUMENT, "");
            }

            if (check(YamlTokenType.DOCUMENT_END) || check(YamlTokenType.DOCUMENT_START) || tokenBuffer.isAtEnd()) {
                document.setBody(createNullScalar(false, null, null));
            } else {
                YamlNode body = parseValue(0, false, false, null);
                if (body != null) {
                    document.setBody(body);
                    document.setTag(body.getTag());   // if any
                }
            }

            YamlToken docEndToken = tokenBuffer.peek();
            if (!errorEncountered.get()) {
                if (docEndToken.getType() == YamlTokenType.DOCUMENT_END) {
                    document.setHasEndMarker(true);
                    YamlToken nextToken = tokenBuffer.peekAhead(1);
                    YamlTokenType nextType = nextToken.getType();
                    // There should be nothing else on the same line as the end marker
                    if (nextType == YamlTokenType.NEWLINE ||
                        nextType == YamlTokenType.COMMENT ||
                        nextType == YamlTokenType.EOF ||
                        nextType == YamlTokenType.STREAM_END) {
                        createEvent(tokenBuffer.peek(), StreamingEventType.END_DOCUMENT, "...");
                        tokenBuffer.advance();
                        skipTrivia();
                    } else {
                        error(nextToken, YamlDiagnosticCode.UNEXPECTED_TOKEN, nextToken.getType());
                    }
                } else {
                    createEvent(tokenBuffer.peek(), StreamingEventType.END_DOCUMENT, "");
                }
            }

            return document;
        } finally {
            depth--;
            debug("<parseDocument");
        }
    }

    private void parseDirective() {
        YamlToken directiveToken = tokenBuffer.advance();
        if (isKnownDirective(directiveToken)) {

            // The entire tag string, e.g.:
            // %TAG !! tag:example.com,2000:app/
            String content = directiveToken.getContent();

            YamlDirective directive = null;
            if (content.startsWith("%YAML ")) {
                content = content.substring(6);
                directive = new YamlDirective(
                    directiveToken,
                    YamlDirectiveType.YAML,
                    directiveToken.getContent()
                );
            }
            if (content.startsWith("%TAG ")) {
                content = content.substring(5);

                int space = content.indexOf(' ');
                String name = content.substring(0, space).trim().substring(1);
                String value = content.substring(space).trim();

                directive = new YamlTagDirective(
                    directiveToken,
                    YamlDirectiveType.TAG,
                    directiveToken.getContent(),
                    name,
                    value
                );
            }

            document.addDirective(directive);
        } else {
            warn(
                directiveToken,
                YamlDiagnosticCode.UNKNOWN_DIRECTIVE,
                directiveToken.getContent()
            );
        }
    }

    /// The primary dispatcher for all YAML values.
    ///
    /// This method is responsible for:
    /// 1. Handling anchors (`&`) and aliases (`*`).
    /// 2. Managing block indentation tokens (`INDENT`/`DEDENT`).
    /// 3. Determining the structural type (Map, Sequence, or Scalar) via lookahead.
    private YamlNode parseValue(int parentIndent, boolean isFlowStyle, boolean isComplexKey, YamlToken previousPendingAnchor) {
        debug(">parseValue");
        depth++;
        if (depth > depthLimit) {
            error(tokenBuffer.peek(), YamlDiagnosticCode.DEPTH_LIMIT);
        }
        try {
            if (!continueAfterError) {
                if (tokenBuffer.peek().getType() == YamlTokenType.ERROR) {
                    // Tokenizer error
                    debug("ERROR TOKEN");
                    errorEncountered.set(true);
                    createEvent(tokenBuffer.peek(), StreamingEventType.ERROR);
                    return null;
                }
                if (errorEncountered.get()) {
                    // Parser error
                    debug("ERROR ENCOUNTERED");
                    createEvent(tokenBuffer.peek(), StreamingEventType.ERROR);
                    return null;
                }
            }

            trace("isFlowStyle="+isFlowStyle);
            trace("isComplexKey="+isComplexKey);
            trace("previousPendingAnchor="+previousPendingAnchor);

            if (check(YamlTokenType.ERROR)) {
                error(tokenBuffer.peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, tokenBuffer.peek().getType());
            }

            if (!check(YamlTokenType.SCALAR) ||
                    tokenBuffer.peek().getScalarStyle() != ScalarStyle.FOLDED) {
                skipTrivia();
            }

            trace("PV-after-skipTrivia");

            NodeContext nodeContext = new NodeContext();
            YamlNode result = null;

            // YamlToken anchorToken = null;
            // boolean newlineAfterAnchor = false;
            // boolean hasIndentedAnchor = false;
            if (checkIndented(YamlTokenType.ANCHOR)) {
                trace("PM-hasIndentedAnchor1");
                tokenBuffer.advance(); // consume INDENT
                nodeContext.hasIndentedAnchor = true;
            }

            if (check(YamlTokenType.ANCHOR)) {
                nodeContext.anchorToken = tokenBuffer.peek();
                String pendingAnchorName = extractAnchorName(nodeContext.anchorToken);
                trace("PV-anchor: " + nodeContext.anchorToken);

                YamlToken possibleNewline = lookAheadIgnoringComments(YamlTokenType.NEWLINE);
                nodeContext.newlineAfterAnchor = possibleNewline != null;

                YamlToken mapAnchor = null;
                YamlToken keyAnchor = null;
                if (previousPendingAnchor != null) {
                    mapAnchor = previousPendingAnchor;
                    keyAnchor = nodeContext.anchorToken;
                } else {
                    if (nodeContext.newlineAfterAnchor) {
                        mapAnchor = nodeContext.anchorToken;
                    } else {
                        keyAnchor = nodeContext.anchorToken;
                    }
                }
                trace("PV-anchor mapAnchor = " + mapAnchor);
                trace("PV-anchor keyAnchor = " + keyAnchor);


                // lookAheadIgnoringComments rather than peek as it may be on the next line
                YamlToken colon = lookAheadIgnoringComments(YamlTokenType.VALUE_INDICATOR);
                if (colon != null) {
                    // Let parseMap handle the anchor
                    trace("PV-anchor passing1 m=" + nodeContext.anchorToken + " k=null");
                    result = parseMap(false, isComplexKey, null, nodeContext.anchorToken, null);
                    attachComments(result);

                    // TODO: This must be moved to happen directly after anchor is parsed
                    if (nodeContext.hasIndentedAnchor && check(YamlTokenType.DEDENT)) {
                        tokenBuffer.advance();
                        nodeContext.hasIndentedAnchor = false;
                    }

                    return result;
                } else {
                    // Scalar map keys are handled in parseKeyNode via parseMap
                    if (tokenBuffer.peekAhead(1).getType() == YamlTokenType.SCALAR &&
                        tokenBuffer.peekAhead(2).getType() == YamlTokenType.VALUE_INDICATOR
                    ){

                        trace("PV-anchor passing2 m=" + mapAnchor + " k=" + keyAnchor);

                        // Old logic: The anchor belongs to the next node, so don't pass it in here
                        // result = parseMap(false, isComplexKey, null, null, null);

                        // New logic: mapAnchor belogns to this map, keyAnchor belongs to its first key
                        result = parseMap(false, isComplexKey, null, mapAnchor, keyAnchor);

                        attachComments(result);


                        // TODO: Should this be moved to happen directly after anchor is parsed ?
                        if (nodeContext.hasIndentedAnchor && check(YamlTokenType.DEDENT)) {
                            tokenBuffer.advance();
                            nodeContext.hasIndentedAnchor = false;
                        }


                        return result;
                    }

                    // Consume the anchor and set it as pending
                    nodeContext.anchorToken = tokenBuffer.advance();
                    trace("PV-anchor-else");

                    if (nodeContext.hasIndentedAnchor) {
                        // skipTrivia();
                        if (check(YamlTokenType.NEWLINE)) {
                            tokenBuffer.advance();
                        }
                        consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT);
                        nodeContext.hasIndentedAnchor = false;
                    }

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


                        // TODO: Is this correct?
                        result = createNullScalar(isComplexKey, null, nodeContext.anchorToken);


                        YamlAnchor anchorNode = new YamlAnchor(
                            result.getStartLine(),
                            result.getStartColumn(),
                            pendingAnchorName,
                            result
                        );
                        trace("anchor applied");
                        anchorRegistry.put(pendingAnchorName, anchorNode);
                        return attachComments(anchorNode);
                    }

                    if (nodeContext.hasIndentedAnchor && check(YamlTokenType.DEDENT)) {
                        trace("PV-hasIndentedAnchor-dedent");
                        tokenBuffer.advance();
                        if (check(YamlTokenType.NEWLINE)) {
                            tokenBuffer.advance();
                            skipTrivia();
                        }
                        nodeContext.hasIndentedAnchor = false;
                    }
                    trace("anchor pending: " + nodeContext.anchorToken);
                }
            }



            YamlToken mapAnchor = null;
            YamlToken keyAnchor = null;
            if (previousPendingAnchor != null) {
                mapAnchor = previousPendingAnchor;
                keyAnchor = nodeContext.anchorToken;
            } else {


                // mapAnchor = pendingAnchor;
                if (nodeContext.newlineAfterAnchor) {
                    keyAnchor = nodeContext.anchorToken;
                } else {
                    mapAnchor = nodeContext.anchorToken;
                }


            }
            trace("mapAnchor = " + mapAnchor);
            trace("keyAnchor = " + keyAnchor);



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
                    trace("PV-TAGanchor-scalar-valueIndicator passing m=" + nodeContext.anchorToken + " k=null");
                    result = parseMap(false, isComplexKey, pendingTag, nodeContext.anchorToken, null);
                    attachComments(result);
                    return result;
                }

                // Scalar map keys are handled in parseKeyNode via parseMap
                if (tokenBuffer.peekAhead(1).getType() == YamlTokenType.SCALAR &&
                    tokenBuffer.peekAhead(2).getType() == YamlTokenType.VALUE_INDICATOR
                ) {
                    trace("PV-TAG-scalar-valueIndicator passing m=" + nodeContext.anchorToken + " k=null");
                    result = parseMap(false, isComplexKey, pendingTag, nodeContext.anchorToken, null);
                    attachComments(result);
                    return result;
                }

                if (tokenBuffer.peekAhead(1).getType() == YamlTokenType.VALUE_INDICATOR) {
                    trace("PV-TAG-valueindicator passing m=" + nodeContext.anchorToken + " k=null");
                    result = parseMap(false, isComplexKey, pendingTag, nodeContext.anchorToken, null);
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
                trace("PV-key-indicator passing m=" + nodeContext.anchorToken + " k=null");
                result = parseMap(false, false, pendingTag, nodeContext.anchorToken, null);
            }
            else if (check(YamlTokenType.ANCHOR)) {



                trace("PV-TAG-anchor passing " + nodeContext.anchorToken);
                result = parseValue(tokenBuffer.peek().getStartColumn(), isFlowStyle, isComplexKey, nodeContext.anchorToken);



            }
            else if (check(YamlTokenType.ALIAS)) {
                trace("PV-in-if-alias1");
                if (tokenBuffer.peekAhead(1).getType() == YamlTokenType.VALUE_INDICATOR) {
                    trace("PV-alias passing m=" + nodeContext.anchorToken + " k=null");
                    result = parseMap(false, isComplexKey, pendingTag, nodeContext.anchorToken, null);
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

                    createEvent(alias, StreamingEventType.ALIAS);

                    result = alias;
                }
            }
            // Flow map
            else if (check(YamlTokenType.MAP_START)) {
                if (lookAheadFlowMapIsFollowedByColon()) {
                    trace("PV-flow-map-as-key passing m=" + nodeContext.anchorToken + " k=null");
                    return parseMap(true, isComplexKey, pendingTag, nodeContext.anchorToken, null);
                } else {
                    trace("PV-flow-map passing pendingAnchor " + nodeContext.anchorToken);
                    result = parseFlowMap(nodeContext.anchorToken);
                }
            }
            // Flow sequence
            else if (check(YamlTokenType.SEQUENCE_START)) {
                if (lookAheadFlowSequenceIsFollowedByColon()) {



                    // TODO: There are probably other places in parsseValue where we should be
                    // indicating to the next parse method if we're inside a flow.
                    // return parseMap(false, isComplexKey, pendingTag, pendingAnchorName);

                    trace("PV-seq-map passing m=" + mapAnchor + " k="+keyAnchor);
                    return parseMap(isFlowStyle, isComplexKey, pendingTag, mapAnchor, keyAnchor);



                } else {
                    trace("PV-flow-seq passing pendingAnchor " + nodeContext.anchorToken);
                    result = parseFlowSequence(nodeContext.anchorToken);
                }
            }
            // Block sequence
            else if (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                trace("PV-block-seq passing pendingAnchor " + nodeContext.anchorToken);
                result = parseSequence(pendingTag, nodeContext.anchorToken);
            }
            else if (check(YamlTokenType.VALUE_INDICATOR)) {
                // Map entry with null key
                trace("PV-value-indicator passing m=null k=null !!!!");
                result = parseMap(false, isComplexKey, pendingTag, null, null);
            }
            else if (check(YamlTokenType.SCALAR)) {
                if (tokenBuffer.peekAhead(1).getType() == YamlTokenType.VALUE_INDICATOR) {


                    trace("PV-scalar passing m=" + nodeContext.anchorToken + " k=null");
                    result = parseMap(isFlowStyle, isComplexKey, pendingTag, nodeContext.anchorToken, null);


                } else {
                    result = parseScalar(isComplexKey, pendingTag, nodeContext.anchorToken);
                    trace("PV-after-parseScalar");
                    if (check(YamlTokenType.NEWLINE)) {
                        tokenBuffer.advance();
                    }
                }
            }
            else {
                result = createNullScalar(isComplexKey, pendingTag, nodeContext.anchorToken);
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
                attachTag(result, pendingTag);
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

            if (nodeContext.hasIndentedAnchor && check(YamlTokenType.DEDENT)) {
                tokenBuffer.advance();
                nodeContext.hasIndentedAnchor = false;
            }


            if (nodeContext.anchorToken != null) {
                trace("PV applying anchor "+nodeContext.anchorToken);
                String raw = nodeContext.anchorToken.getContent();
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
    private YamlNode parseMap(boolean isFlowStyle, boolean isComplexKey, String tag, YamlToken pendingMapAnchor, YamlToken pendingKeyAnchor) {
        debug(">parseMap");
        depth++;
        try {
            trace("isFlowStyle="+isFlowStyle);
            trace("isComplexKey="+isComplexKey);
            trace("pendingMapAnchor="+pendingMapAnchor);
            trace("pendingKeyAnchor="+pendingKeyAnchor);

            YamlToken startToken = tokenBuffer.peek();
            YamlMap map = new YamlMap(startToken, options);
            map.setNodeStyle(isFlowStyle ? NodeStyle.FLOW : NodeStyle.BLOCK);

            if (pendingMapAnchor != null) {
                map.setAnchor(extractAnchorName(pendingMapAnchor));
            }
            attachTag(map, tag);
            createEvent(map, StreamingEventType.START_OBJECT);

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
                    key = parseValue(markerColumn, false, true, pendingKeyAnchor);
                } else {
                    if (check(YamlTokenType.MAP_START) ||
                        check(YamlTokenType.SEQUENCE_START)) {
                        trace("map debug");
                    }
                    // Standard implicit key
                    key = parseKeyNode(markerColumn, pendingKeyAnchor);
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
                        value = createNullScalar(false, null, null);
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

                        value = parseValue(mapColumn, false, false, null);

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
                    value = createNullScalar(false, null, null);
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

            createEvent(map, StreamingEventType.END_OBJECT);
            return map;
        } finally {
            depth--;
            debug("<parseMap");
        }
    }


    private NodeContext parseNodeContext(YamlToken pendingAnchor) {
        debug(">parseNodeContext");
        depth++;
        try {

            NodeContext nodeContext = new NodeContext();

            // TODO: pendingAnchor that was passed in
            if (pendingAnchor != null) {
                nodeContext.anchorToken = pendingAnchor;
            }

            if (check(YamlTokenType.TAG)) {
                parseTag(nodeContext);
            }

            if (check(YamlTokenType.NEWLINE)) {
                skipTrivia();
            }

            if (check(YamlTokenType.ANCHOR)) {
                if (pendingAnchor != null) {
                    debug("ERROR");
                    // TODO: error
                }
                parseAnchor(nodeContext);
            }

            if (check(YamlTokenType.NEWLINE)) {
                skipTrivia();
            }

            if (check(YamlTokenType.TAG)) {
                // TODO: If a tag was alread parsed: error
                parseTag(nodeContext);
            }

            // TODO
            // Register the anchor for later alias resolution
            // anchorRegistry.put(anchorName, key);
            // Wrap in YamlAnchorNode (same as parseValue)
            // YamlAnchor anchorNode = new YamlAnchor(
            //     key.getStartLine(),
            //     key.getStartColumn(),
            //     anchorName,
            //     key
            // );


            return nodeContext;
        } finally {
            depth--;
            debug("<parseNodeContext");
        }

    }

    private void parseAnchor(NodeContext nodeContext) {
        // Anchors on scalars that start a map are not handled in parseValue.

        nodeContext.anchorToken = tokenBuffer.peek();
        tokenBuffer.advance(); // consume &anchor

        // if (check(YamlTokenType.NEWLINE)) {
        //     skipTrivia();
        // }

        // YamlNode key;
        nodeContext.anchorName = extractAnchorName(nodeContext.anchorToken);
    }

    private void parseTag(NodeContext nodeContext) {
        YamlToken tagTok = tokenBuffer.advance();
        nodeContext.tag = tagTok.getContent();
    }


    private YamlNode parseKeyNode(int parentIndent, YamlToken pendingAnchor) {
        debug(">parseKeyNode");
        depth++;
        try {

            trace("pendingAnchor="+pendingAnchor);

            if (check(YamlTokenType.NEWLINE)) {
                tokenBuffer.advance();
            }

            NodeContext nodeContext = parseNodeContext(pendingAnchor);
            YamlToken token = tokenBuffer.peek();
            YamlTokenType tokenType = token.getType();
            YamlNode key;

            if (token.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR ||
                token.getType() == YamlTokenType.INDENT) {
                key = parseValue(parentIndent, false, false, nodeContext.anchorToken);
            } else if (tokenType == YamlTokenType.VALUE_INDICATOR) {
                // Empty key

                // YamlScalar key = new YamlScalar(tokenBuffer.peek(), "", PrimitiveType.STRING, ScalarStyle.PLAIN, options);

                // TODO: Pending anchor?
                key = createEmptyScalar(true, nodeContext.tag);
                // createEvent(key, StreamingEventType.FIELD_NAME);
            } else if (tokenType == YamlTokenType. MAP_START) {
                key = parseFlowMap(nodeContext.anchorToken);
            } else if (tokenType == YamlTokenType. SEQUENCE_START) {
                key = parseFlowSequence(nodeContext.anchorToken);
            } else if (tokenType == YamlTokenType. SCALAR) {
                YamlScalar scalar = parseScalar(true,null,  nodeContext.anchorToken);
                // skipTrivia();
                skipEOL();
                key = scalar;
            } else if (tokenType == YamlTokenType. ALIAS) {
                tokenBuffer.advance(); // consume alias token

                String raw = token.getContent();
                String name = raw.startsWith("*") ? raw.substring(1) : raw;

                YamlAlias alias = new YamlAlias(token, name);

                // Mirror parseValue alias resolution
                if (anchorRegistry.containsKey(name)) {
                    alias.setResolvedNode(anchorRegistry.get(name));
                }
                createEvent(alias, StreamingEventType.ALIAS);

                key = alias;
            } else if (tokenType == YamlTokenType. KEY_INDICATOR) {
                trace("parseKeyNode: KEY_INDICATOR");
                tokenBuffer.advance(); // consume '?'
                skipTrivia();
                // NOTE: at the moment parseKeyNode is only called for simple keys.
                // If we call it for complex keys, this parseValue call should
                // specify if it's a complex key.
                key = parseValue(parentIndent, false, false, nodeContext.anchorToken);
            } else {
                if (nodeContext.anchorToken != null) {
                    // TODO: What happens to pendingAnchor now?
                    key = createNullScalar(true, null, token);

                    if (tokenBuffer.peek().getType().getCategory() != TokenCategory.PUNCTUATION) {
                        error(tokenBuffer.peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, tokenBuffer.peek().getType());
                    }

                    //
                } else {
                    error(token, GenericDiagnosticCode.ERROR, "Unexpected token in key position: " + token.getType());
                    key = new YamlScalar(token, PrimitiveType.ANY, options);
                }
            }

            if (nodeContext.anchorToken != null) {
                anchorRegistry.put(nodeContext.anchorName, key);

                // Wrap in YamlAnchorNode (same as parseValue)
                YamlAnchor anchorNode = new YamlAnchor(
                    key.getStartLine(),
                    key.getStartColumn(),
                    nodeContext.anchorName,
                    key
                );
                key = anchorNode;
            }

            return key;
        } finally {
            depth--;
            debug("<parseKeyNode");
        }
    }

    /// Parses a block sequence (list) indicated by leading dashes.
    private YamlNode parseSequence(String tag, YamlToken pendingAnchor) {
        debug(">parseSequence");
        depth++;
        try {

            trace("anchor="+pendingAnchor);

            boolean hasIndentedAnchor = false;
            if (checkIndented(YamlTokenType.ANCHOR)) {
                debug("PA-hasIndentedAnchor1");
                hasIndentedAnchor = true;
                tokenBuffer.advance();
            }

            String anchorName = null;
            YamlToken anchor = null;
            if (check(YamlTokenType.ANCHOR)) {
                anchor = tokenBuffer.advance();
                String raw = anchor.getContent();
                anchorName = (raw.length() > 1 && raw.charAt(0) == '&' ? raw.substring(1) : raw);

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
            sequence.setNodeStyle(NodeStyle.BLOCK);
            attachTag(sequence, tag);
            if (anchorName != null) {
                sequence.setAnchor(anchorName);
            }
            if (pendingAnchor != null) {
                sequence.setAnchor(extractAnchorName(pendingAnchor));
            }
            createEvent(sequence, StreamingEventType.START_ARRAY);

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
                    sequence.add(createNullScalar(false, null, null));
                } else {
                    // parseValue handles the content, including potential nested blocks


                    // YamlNode item = parseValue(indicatorColumn, true, false, null);
                    YamlNode item = parseValue(indicatorColumn, false, false, null);


                    sequence.add(item);
                }

                skipTrivia();
            }
            YamlNode node = sequence;

            if (anchor != null) {
                String raw = anchor.getContent();
                String name = raw.startsWith("&") ? raw.substring(1) : raw;

                anchorRegistry.put(name, sequence);

                // Wrap in YamlAnchorNode (same as parseValue)
                YamlAnchor anchorNode = new YamlAnchor(
                    sequence.getStartLine(),
                    sequence.getStartColumn(),
                    name,
                    sequence
                );
                // return anchorNode;
                node = anchorNode;
            }


            createEvent(tokenBuffer.peek(), StreamingEventType.END_ARRAY);

            return node;
        } finally {
            depth--;
            debug("<parseSequence");
        }
    }

    /// Parses a flow sequence like [item1, item2].
    private YamlSequence parseFlowSequence(YamlToken pendingAnchor) {
        debug(">parseFlowSequence");
        depth++;
        try {
            trace("pendingAnchor="+pendingAnchor);
            YamlToken startToken = consume(YamlTokenType.SEQUENCE_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACKET);
            YamlSequence sequence = new YamlSequence(startToken);
            sequence.setNodeStyle(NodeStyle.FLOW);

            if (pendingAnchor != null) {
                sequence.setAnchor(extractAnchorName(pendingAnchor));
            }
            createEvent(sequence, StreamingEventType.START_ARRAY);

            while (!check(YamlTokenType.SEQUENCE_END) && !tokenBuffer.isAtEnd()) {
                skipTrivia();

                sequence.add(parseValue(startToken.getStartColumn(), true, false, null));
                skipTrivia();

                if (!match(YamlTokenType.COMMA)) break;
                skipTrivia();
            }

            consume(YamlTokenType.SEQUENCE_END, YamlDiagnosticCode.EXPECTED_CLOSE_BRACKET);
            createEvent(tokenBuffer.peek(), StreamingEventType.END_ARRAY);
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
    private YamlNode parseFlowMap(YamlToken pendingAnchor) {
        debug(">parseFlowMap");
        depth++;
        try {
            trace("pendingAnchor="+pendingAnchor);
            YamlToken startToken = consume(YamlTokenType.MAP_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACE_FLOW_MAP);
            YamlMap map = new YamlMap(startToken, options);
            map.setNodeStyle(NodeStyle.FLOW);

            if (pendingAnchor != null) {
                map.setAnchor(extractAnchorName(pendingAnchor));
            }
            createEvent(map, StreamingEventType.START_OBJECT);


            // Clear any whitespace/newlines before checking for an empty map exit
            skipTrivia();

            // Handle empty flow map {}
            if (match(YamlTokenType.MAP_END)) {
                createEvent(tokenBuffer.peek(), StreamingEventType.END_OBJECT);
                return map;
            }

            while (!tokenBuffer.isAtEnd()) {
                skipTrivia();

                // 1. Parse Key
                // YamlScalar key = parseScalar();


                // TODO: Do we pass an anchor on here?
                YamlNode key = parseKeyNode(startToken.getStartColumn(), null);


                skipTrivia();

                YamlNode value;
                if (check(YamlTokenType.VALUE_INDICATOR)) {
                    // 2. Consume Value Indicator
                    consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP);

                    // 3. Parse Value
                    value = parseValue(key.getStartColumn(), true, false, null);
                } else {
                    value = createNullScalar(false, null, null);
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
            createEvent(tokenBuffer.peek(), StreamingEventType.END_OBJECT);
            return map;
        } finally {
            depth--;
            debug("<parseFlowMap");
        }
    }

    private YamlScalar parseScalar(boolean isKey, String tag, YamlToken pendingAnchor) {
        debug(">parseScalar" + (pendingAnchor == null ? "" : "(a="+pendingAnchor+")"));
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

            if (tag != null) {
                attachTag(scalar, tag);
            }

            if (pendingAnchor != null) {
                scalar.setAnchor(extractAnchorName(pendingAnchor));
            }

            if (isKey) {
                createEvent(scalar, StreamingEventType.FIELD_NAME);
            } else {
                createEvent(scalar, StreamingEventType.VALUE_SCALAR);
            }
            return scalar;
        } finally {
            depth--;
            debug("<parseScalar");
        }
    }

    private YamlScalar createEmptyScalar(boolean isKey, String tag) {
        debug("createEmptyScalar");
        // YamlScalar scalar = new YamlScalar("", ScalarStyle.PLAIN, options);
        YamlScalar scalar = new YamlScalar(tokenBuffer.peek(), "", PrimitiveType.STRING, ScalarStyle.PLAIN, options);
        attachTag(scalar, tag);
        // TODO: Anchor
        createEvent(
            scalar,
            isKey ? StreamingEventType.FIELD_NAME
                  : StreamingEventType.VALUE_SCALAR
        );
        return scalar;
    }

    private YamlScalar createNullScalar(boolean isKey, String tag, YamlToken pendingAnchor) {
        debug("createNullScalar");
        YamlScalar scalar = new YamlScalar(tokenBuffer.peek(), PrimitiveType.NULL, options);
        attachTag(scalar, tag);
        scalar.setAnchor(extractAnchorName(pendingAnchor));
        createEvent(
            scalar,
            isKey ? StreamingEventType.FIELD_NAME
                  : StreamingEventType.VALUE_SCALAR
        );
        return scalar;
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

    @Nullable
    private String extractAnchorName(YamlToken anchorToken) {
        if (anchorToken == null) {
            return null;
        }
        String raw = anchorToken.getContent();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String name = raw.startsWith("&") ? raw.substring(1) : raw;
        return name;
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

    //
    // Event Helpers
    //

    private void createEvent(YamlToken token, StreamingEventType type) {
        createEvent(token, type, token.getContent());
    }

    private void createEvent(YamlToken token, StreamingEventType type, String content) {
        YamlStreamingEvent event = new YamlStreamingEvent(
            token.getStartLine(),
            token.getStartColumn(),
            type,
            null,
            null,
            "",
            "",
            "",
            token.getLexeme(),
            content
        );
        handleEvent(event);
    }

    private void createEvent(YamlNode node, StreamingEventType type) {
        YamlNode target = node;
        String anchorName = "";

        if (type == StreamingEventType.ALIAS ||
            type == StreamingEventType.FIELD_NAME ||
            type == StreamingEventType.VALUE_SCALAR ||
            type == StreamingEventType.START_OBJECT ||
            type == StreamingEventType.START_ARRAY
        ) {
            if (node.getAnchor() instanceof String anchor) {
                anchorName = anchor;
            }
        }

        ScalarStyle scalarStyle = null;
        if (target instanceof YamlScalar scalar) {
            scalarStyle = scalar.getScalarStyle();
        }

        debug("event: " + type + " [a="+anchorName+"]");

        YamlStreamingEvent event = new YamlStreamingEvent(
            target.getStartLine(),
            target.getStartColumn(),
            type,
            target.getNodeStyle(),
            scalarStyle,
            node.getTag(), // TODO: Are we sure this should not be target.getTag() ?
            node.getResolvedTag(),
            anchorName,
            target.getToken().getLexeme(),
            target.asString()
        );

        event.setNode(node);

        handleEvent(event);
    }

    //
    //
    //

    protected void preParseStateInit() {
        if (options.preloadTokenBuffer()) {
            tokenBuffer = new PreloadedTokenBuffer();
        } else {
            tokenBuffer = new OnDemandTokenBuffer(CIRCULAR_BUFFER_SIZE);
        }

        tokenBuffer.setTokenizer(getTokenizer());
        anchorRegistry.clear();
        pendingComments.clear();
        depthLimit = options.getDepthLimit();
        isMultiDocumentParsing = options.isMultiDocument();
    }

    private void attachTag(YamlNode node, String tag) {
        node.setTag(tag);
        node.setResolvedTag(resolveTag(tag));
    }

    private String resolveTag(String raw) {
        if (raw == null) return null;

        if (raw.startsWith("!!")) {
            String name = raw.substring(2);
            return "tag:yaml.org,2002:" + name;
        }

        if (raw.startsWith("!<") && raw.endsWith(">")) {
            return raw.substring(2, raw.length() - 1);
        }

        // Local tag (requires TAG directive)
        if (raw.startsWith("!")) {
            return applyTagDirective(raw.substring(1));
        }

        return raw;
    }

    String applyTagDirective(String suffix) {
        // suffix is everything after the leading "!"
        // e.g. "e!vector" or "foo" or "bar/baz"

        List<YamlDirective> directives = document.getDirectives();

        for (YamlDirective d : directives) {
            if (d instanceof YamlTagDirective tagDirective) {
                String handle = tagDirective.getName();
                if (suffix.startsWith(handle)) {
                    String decodedSuffix = decodeTagUri(suffix.substring(handle.length()));
                    return tagDirective.getValue() + decodedSuffix;
                }
            }
        }

        // No matching directive → local tag with no mapping
        // YAML 1.2 says: treat as a non-specific tag
        return "!" + suffix;
    }

    private String decodeTagUri(String rawSuffix) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rawSuffix.length(); i++) {
            char c = rawSuffix.charAt(i);
            if (c == '%' && i + 2 < rawSuffix.length()) {
                String hex = rawSuffix.substring(i + 1, i + 3);
                try {
                    int codePoint = Integer.parseInt(hex, 16);
                    sb.append((char) codePoint);
                    i += 2; // Skip hex digits
                    continue;
                } catch (NumberFormatException ignored) {
                    // Fall through if not valid hex
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    //
    // Errors and Diagnostics
    //

    protected void error(YamlToken token, DiagnosticCode code, Object... details) {
        errorEncountered.set(true);
        // if (!continueAfterError && streamingThread != null) {
        //     System.out.println("AbstractYamlParser: notifying streamingThread");
        //     streamingThread.notify();
        // }

        if (token instanceof YamlErrorToken error) {
            code = error.getCode();
            details = error.getDetails();
        }

        reporter.errorAt(token, code, details);
        if (!reporter.collectsProblems()) {
            throw new YamlParserException(token, code, details);
        }
    }

    protected void warn(YamlToken token, DiagnosticCode code, Object... details) {
        reporter.warnAt(token, code, details);
    }

    /// Log the current method name and upcoming tokens
    protected void trace(String message, Object... details) {
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.TRACE)) return;
        report(message, details);
    }

    /// Log the current method name and upcoming tokens
    protected void debug(String message, Object... details) {
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

    private static class NodeContext {
        String tag;
        String anchorName;
        YamlToken anchorToken;
        boolean newlineAfterAnchor = false;
        boolean hasIndentedAnchor = false;
    }
}
