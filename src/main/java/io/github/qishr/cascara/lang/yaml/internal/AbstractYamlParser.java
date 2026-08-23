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
import java.util.function.BiConsumer;

import io.github.qishr.cascara.common.diagnostic.AbstractLocalizableException;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.diagnostic.code.LangDiagnosticCode;
import io.github.qishr.cascara.common.annotation.Nullable;
import io.github.qishr.cascara.common.lang.processor.Processor;
import io.github.qishr.cascara.common.lang.token.TokenCategory;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;
import io.github.qishr.cascara.common.util.Pair;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.common.util.TermUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchor;
import io.github.qishr.cascara.lang.yaml.ast.YamlCollection;
import io.github.qishr.cascara.lang.yaml.ast.YamlComment;
import io.github.qishr.cascara.lang.yaml.ast.YamlDirective;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNodeProperty;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
import io.github.qishr.cascara.lang.yaml.ast.YamlTag;
import io.github.qishr.cascara.lang.yaml.ast.YamlTagDirective;
import io.github.qishr.cascara.lang.yaml.diagnostic.YamlDiagnosticCode;
import io.github.qishr.cascara.lang.yaml.diagnostic.YamlParserException;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.streaming.YamlStreamingEvent;
import io.github.qishr.cascara.lang.yaml.streaming.YamlStreamingEventType;
import io.github.qishr.cascara.lang.yaml.token.YamlErrorToken;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;
import io.github.qishr.cascara.lang.yaml.util.CommentStyle;
import io.github.qishr.cascara.lang.yaml.util.NodeStyle;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;
import io.github.qishr.cascara.lang.yaml.util.DirectiveType;

public abstract class AbstractYamlParser<P extends Processor> extends AbstractYamlProcessor<P> {
    private static final int CIRCULAR_BUFFER_SIZE = 256;

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_YELLOW = "\u001B[33m";

    private YamlTokenizer tokenizer;
    protected TokenBuffer tokenBuffer;

    // Options
    protected boolean isMultiDocumentParsing = false;
    private boolean continueAfterError = true;

    // State
    private int depth;
    private int depthLimit;
    private int flowDepth;
    private int blockCollectionDepth;
    private int implicitKeyDepth;
    private boolean onMarkerLine;
    protected boolean fileEndsWithNewLine = false;
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

    protected YamlStream parseStream() {
        if (this.tokenBuffer.isEmpty()) {
            return new YamlStream();
        }

        consume(YamlTokenType.STREAM_START, LangDiagnosticCode.EXPECTED_STREAM_START);
        createEvent(tokenBuffer.peek(), YamlStreamingEventType.START_STREAM);

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
                    pendingComments.add(parseComment(CommentStyle.LEADING));
                } else {

                    // TODO:
                    // Basically: If there is an explicit document marker:
                    //   - Add this comment directly to the stream
                    //   streamNode.getComments().add(parseComment(CommentStyle.LEADING));
                    // Otherwise: Do as below
                    if (isMultiDocumentParsing) {
                        streamNode.getComments().add(parseComment(CommentStyle.LEADING));
                    } else {
                        pendingComments.add(parseComment(CommentStyle.LEADING));
                    }
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

                if (tokenAfterDocType == YamlTokenType.EOF) {
                    String fileEnding = tokenAfterDoc.getContent();
                    debug("fileEnding: " + StringUtils.debugString(fileEnding));
                    if (fileEnding.equals("\n")) {
                        fileEndsWithNewLine = true;
                    }
                }

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
        for (YamlComment comment : pendingComments) {
            comment.setCommentStyle(CommentStyle.TRAILING);
            streamNode.addComment(comment);
        }
        pendingComments.clear();

        // Safely consume trailing layout artifacts and capture any trailing file footer comments
        while (!tokenBuffer.isAtEnd() && !check(YamlTokenType.STREAM_END) && !check(YamlTokenType.EOF)) {
            if (check(YamlTokenType.NEWLINE) || check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                tokenBuffer.advance();
            } else if (check(YamlTokenType.COMMENT)) {
                streamNode.getComments().add(parseComment(CommentStyle.INLINE));
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
            createEvent(tokenBuffer.peek(), YamlStreamingEventType.END_STREAM);
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
                parseTrivia();
            }
            boolean hasDirectives = !document.getDirectives().isEmpty();

            // if (hasDirectives && (check(YamlTokenType.EOF) || check(YamlTokenType.STREAM_END))) {
            if (hasDirectives && !check(YamlTokenType.DOCUMENT_START)) {
                error(tokenBuffer.peek(), YamlDiagnosticCode.MISSING_DIRECTIVES_END_INDICATOR);
                return document;
            }

            trace("PD-1");
            // After directives, before deciding body, strip layout so skipTrivia can see comments
            while (check(YamlTokenType.INDENT) || check(YamlTokenType.DEDENT)) {
                tokenBuffer.advance();
            }
            parseTrivia(); // this will now see COMMENT(#d) and buffer it
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
                createEvent(tokenBuffer.peek(), YamlStreamingEventType.START_DOCUMENT, "---");
                tokenBuffer.advance();
                onMarkerLine = true;
                parseTrivia();
            } else {
                createEvent(tokenBuffer.peek(), YamlStreamingEventType.START_DOCUMENT, "");
            }

            if (check(YamlTokenType.DOCUMENT_END) || check(YamlTokenType.DOCUMENT_START) || tokenBuffer.isAtEnd()) {
                document.setBody(createNullScalar(false, null));
            } else {
                YamlNode body = parseValue(null, null, false);
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
                        createEvent(tokenBuffer.peek(), YamlStreamingEventType.END_DOCUMENT, "...");
                        tokenBuffer.advance();
                        parseTrivia();
                    } else {
                        error(nextToken, YamlDiagnosticCode.UNEXPECTED_TOKEN, nextToken.getType());
                    }
                } else {
                    createEvent(tokenBuffer.peek(), YamlStreamingEventType.END_DOCUMENT, "");
                    parseTrivia();
                }
            }
            return document;
        } finally {
            depth--;
            debug("<parseDocument");
        }
    }

    private void parseDirective() {
        YamlToken directiveToken = tokenBuffer.peek();
        if (isKnownDirective(directiveToken)) {
            String content = directiveToken.getContent();
            YamlDirective directive = null;
            if (content.startsWith("%YAML ")) {
                if (document.getYamlDirective() != null) {
                    error(directiveToken, YamlDiagnosticCode.DUPICATE_YAML_DIRECTIVE);
                }
                String version = parseYamlDirective(directiveToken);
                content = content.substring(6);
                directive = new YamlDirective(
                    directiveToken,
                    DirectiveType.YAML,
                    version
                );
            }
            if (content.startsWith("%TAG ")) {
                Pair<String,String> tag = parseTagDirective(directiveToken);
                content = content.substring(5);
                directive = new YamlTagDirective(
                    directiveToken,
                    DirectiveType.TAG,
                    content,
                    tag.getL(), // name
                    tag.getR()  // value
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
        tokenBuffer.advance();
    }

    private String parseYamlDirective(YamlToken token) {
        boolean foundNonWhitespace = false;
        boolean foundSpaceAfterNonWhitespace = false;
        String version = "";
        String content = token.getContent();
        for (int i = 5; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '#') {
                break;
            } else if (c == ' ' || c == '\t') {
                if (foundNonWhitespace) foundSpaceAfterNonWhitespace = true;
            } else {
                if (foundSpaceAfterNonWhitespace) {
                    error(token, YamlDiagnosticCode.TOO_MANY_PARTS);
                    break;
                }
                foundNonWhitespace = true;
                version += c;
            }
        }
        if (!version.equals("1.2")) {
            error(token, YamlDiagnosticCode.UNSUPPORTED_VERSION, version);
        }
        return version;
    }

    private Pair<String,String> parseTagDirective(YamlToken token) {
        String content = token.getContent().substring(5);
        // TODO: Make this more robust, like %YAML directive parsing
        int space = content.indexOf(' ');
        String name = content.substring(0, space);
        String value = content.substring(space).trim();
        return new Pair<>(name, value);
    }

    private YamlNode parseKey(YamlCollection parentCollection, NodeProperties pendingProperties) {
        debug(">parseKeyNode");
        depth++;
        try {

            boolean isFlowStyle = flowDepth > 0;
            debugProperties("p", pendingProperties);

            if (check(YamlTokenType.NEWLINE)) {
                tokenBuffer.advance();
            }

            Pair<NodeProperties,NodeProperties> properties = parseNodePropeties(true, false, pendingProperties);
            NodeProperties nodeProperties = properties.getR();

            YamlToken token = tokenBuffer.peek();
            YamlTokenType tokenType = token.getType();
            YamlNode key;

            if (tokenType == YamlTokenType. KEY_INDICATOR) {
                trace("parseKeyNode: KEY_INDICATOR");
                tokenBuffer.advance(); // consume '?'
                key = parseValue(parentCollection, nodeProperties, false);
            } else
            if (token.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR ||
                token.getType() == YamlTokenType.INDENT) {
                key = parseValue(parentCollection, nodeProperties, false);
            } else if (tokenType == YamlTokenType.VALUE_INDICATOR) {
                // Empty key
                key = createEmptyScalar(true, nodeProperties);
            } else if (tokenType == YamlTokenType. MAP_START) {
                key = parseFlowMap(nodeProperties, null, false);
            } else if (tokenType == YamlTokenType. SEQUENCE_START) {
                key = parseFlowSequence(true, nodeProperties);
            } else if (tokenType == YamlTokenType. SCALAR) {
                YamlScalar scalar = parseScalar(parentCollection, nodeProperties, true);
                if (flowDepth == 0 && scalar.getLexeme().contains("\n")) {
                    error(scalar.getToken(), YamlDiagnosticCode.IMPLICIT_KEY_SINGLE_LINE);
                }
                key = scalar;
            } else if (tokenType == YamlTokenType. ALIAS) {
                key = parseAlias(nodeProperties);
            } else {
                // A *tag* here should create an empty scalar, not a null
                if (nodeProperties.anchor != null) {
                    key = createNullScalar(true, nodeProperties);
                    if (tokenBuffer.peek().getType().getCategory() != TokenCategory.PUNCTUATION) {
                        error(tokenBuffer.peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, tokenBuffer.peek().getType());
                    }
                } else {
                    error(token, GenericDiagnosticCode.ERROR, "Unexpected token in key position: " + token.getType());
                    key = createEmptyScalar(true, nodeProperties);
                }
            }
            return key;
        } finally {
            depth--;
            debug("<parseKeyNode");
        }
    }

    /// The primary dispatcher for all YAML values.
    ///
    /// This method is responsible for:
    /// 1. Handling anchors (`&`) and aliases (`*`).
    /// 2. Managing block indentation tokens (`INDENT`/`DEDENT`).
    /// 3. Determining the structural type (Map, Sequence, or Scalar) via lookahead.
    private YamlNode parseValue(
        YamlCollection parentCollection,
        NodeProperties pendingProperties,
        boolean isKey
    ) {
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
                    createEvent(tokenBuffer.peek(), YamlStreamingEventType.ERROR);
                    return null;
                }
                if (errorEncountered.get()) {
                    // Parser error
                    debug("ERROR ENCOUNTERED");
                    createEvent(tokenBuffer.peek(), YamlStreamingEventType.ERROR);
                    return null;
                }
            }

            boolean isFlowStyle = flowDepth > 0;
            int parentStartColumn = parentCollection == null ? 0 : parentCollection.getStartColumn();
            boolean isSequenceItem = (parentCollection instanceof YamlSequence);
            // boolean isFlowSequenceItem = (parentCollection instanceof YamlSequence seq) && seq.getNodeStyle() == NodeStyle.FLOW;

            trace("isFlowStyle="+isFlowStyle);
            trace("isComplexKey="+isKey);
            debugProperties("p", pendingProperties);

            if (check(YamlTokenType.ERROR)) {
                error(tokenBuffer.peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, tokenBuffer.peek().getType());
            }

            parseTrivia();

            // Node properties preceeding an initial map key can belong either
            // to the map itself or to the initial key. parseNodeProperties
            // consumes them all and decides what they belong to.

            Pair<NodeProperties,NodeProperties> properties = parseNodePropeties(!isFlowStyle, true, pendingProperties);

            NodeProperties collectionProperties = properties.getL();
            NodeProperties nodeProperties = properties.getR();
            YamlNode result = null;

            YamlToken sei = lookAheadIgnoringComments(YamlTokenType.SEQUENCE_ENTRY_INDICATOR);
            if (check(YamlTokenType.NEWLINE) && sei != null && isSequenceItem) {
                trace("sequence entry after newline ahead");
                if (nodeProperties != null && (nodeProperties.anchor != null || nodeProperties.tag != null)) {
                    // if (nodeProperties.tag != null) {
                    if (nodeProperties.anchor != null || nodeProperties.tag != null) {
                        result = createEmptyScalar(isKey, nodeProperties);
                        nodeProperties.attachTo(result);
                    }
                }
            }

            parseTrivia();

            debugProperties("c", collectionProperties);
            debugProperties("n", nodeProperties);

            boolean isInCollection = blockCollectionDepth > 0;

            int expectedDedents = 0;
            while (check(YamlTokenType.INDENT)) {
                tokenBuffer.advance();

                // If there are node properties (anchor or tag) at this indentation
                // level, merge them with the current properties
                properties = parseNodePropeties(!isFlowStyle, true, collectionProperties, nodeProperties);
                collectionProperties = properties.getL();
                nodeProperties = properties.getR();
                trace("merged properties");
                debugProperties("c", collectionProperties);
                debugProperties("n", nodeProperties);
                expectedDedents++;
            }

            if (check(YamlTokenType.NEWLINE)) {
                tokenBuffer.advance(); // Consume the newline, but leave the indent
            }

            if (check(YamlTokenType.INDENT)) {
                tokenBuffer.advance();
                expectedDedents++;
            }

            // With this block testSKE5 and test6KGN pass
            YamlToken nextToken = tokenBuffer.peek();
            if (result == null && nextToken.getStartColumn() == parentStartColumn &&
                nodeProperties.tag == null &&
                !nodeProperties.isEmpty() &&
                nextToken.getType() != YamlTokenType.SEQUENCE_ENTRY_INDICATOR)
            {

                // TODO: Does this work for everything?
                trace(TermUtils.ANSI_MAGENTA + "next token is at parent column" + TermUtils.ANSI_RESET);
                result = createNullScalar(isKey, nodeProperties);

            }
            else {
                // Map parsing entry points

                if (!isKey && check(YamlTokenType.MAP_START) && lookAheadFlowMapIsFollowedByColon()) {
                    trace("PV-flowMap-map");
                    // For test_Q9WF, this should not be flow style.
                    return parseMap(parentCollection, collectionProperties, nodeProperties, isKey);
                }
                else if (!isKey && check(YamlTokenType.SEQUENCE_START) && lookAheadFlowSequenceIsFollowedByColon()) {
                    trace("PV-seq-map");
                    return parseMap(parentCollection, collectionProperties, nodeProperties, isKey);
                }


                if (check(YamlTokenType.ALIAS) && tokenBuffer.peekAhead(1).getType() == YamlTokenType.VALUE_INDICATOR) {
                    trace("PV-alias-map");
                    result = parseMap(parentCollection, collectionProperties, nodeProperties, isKey);
                    if (result == null) return null;
                }
                else if (check(YamlTokenType.SCALAR) && tokenBuffer.peekAhead(1).getType() == YamlTokenType.VALUE_INDICATOR) {
                    trace("PV-scalar-map");
                    if (flowDepth > 0) {

                        // TODO: Are we sure parseFlowMap doesn't need to know id it's a key?

                        // result = parseFlowMap(collectionProperties, nodeProperties, isKey);

                        // Implicit flow map does not have {} around it.
                        result = parseFlowMap(collectionProperties, nodeProperties, true);
                    } else {
                        result = parseMap(parentCollection, collectionProperties, nodeProperties, isKey);
                    }
                    if (result == null) return null;
                }
                else if (check(YamlTokenType.VALUE_INDICATOR)) {
                    // Map entry with null key
                    trace("PV-valueIndicator-map");
                    result = parseMap(parentCollection, collectionProperties, nodeProperties, isKey);
                    if (result == null) return null;
                }
                else if (check(YamlTokenType.KEY_INDICATOR)) {
                    trace("PV-keyIndicator-map");
                    result = parseMap(parentCollection, collectionProperties, nodeProperties, false);
                    if (result == null) return null;
                }
            }

            if (result == null && check(YamlTokenType.MAP_START)) {
                trace("PV-flow-map");
                result = parseFlowMap(collectionProperties, nodeProperties, false);
                if (result == null) return null;
            }

            if (result == null) {
                // Other node types

                // Move collection properties into node properties.
                // Beyond this point they can't belong to a collection.
                moveProperties(collectionProperties, nodeProperties, null);

                if (check(YamlTokenType.ALIAS)) {
                    result = parseAlias(nodeProperties);
                    if (result == null) return null;
                }
                // // Flow map
                // else if (check(YamlTokenType.MAP_START)) {
                //     trace("PV-flow-map passing pendingAnchor " + nodeProperties.anchor);
                //     result = parseFlowMap(nodeProperties);
                //     if (result == null) return null;
                // }
                // Flow sequence
                else if (check(YamlTokenType.SEQUENCE_START)) {
                    result = parseFlowSequence(isInCollection, nodeProperties);
                    if (result == null) return null;
                }
                // Block sequence
                else if (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                    if (isFlowStyle) {
                        error(tokenBuffer.peek(), YamlDiagnosticCode.BLOCK_COLLECTION_INSIDE_FLOW);
                    }
                    if (nodeProperties.anchor != null && nodeProperties.anchor.getStartLine() == tokenBuffer.peek().getStartLine()) {
                        error(nodeProperties.anchor.getToken(), YamlDiagnosticCode.MISSING_NEWLINE_BLOCK_SEQ_PROPS);
                    }
                    result = parseSequence(nodeProperties);
                    if (result == null) return null;
                }
                else if (check(YamlTokenType.SCALAR)) {
                    result = parseScalar(parentCollection, nodeProperties, isKey);
                    if (result == null) return null;
                    skipNewline();
                }
                else {
                    result = nodeProperties.isEmpty()
                        ? createNullScalar(isKey, nodeProperties)
                        : createEmptyScalar(isKey, nodeProperties);
                }
            }

            parseTrivia();

            while (expectedDedents > 0) {
                if (check(YamlTokenType.DEDENT)) {
                    trace("Consuming expectedValueDedent");
                    tokenBuffer.advance();
                } else {
                    error(tokenBuffer.peek(), YamlDiagnosticCode.EXPECTED_DEDENT);
                }
                expectedDedents--;
            }

            return result;
        } finally {
            depth--;
            debug("<parseValue");
        }
    }

    /// Parses a block-level mapping and enforces strict key indentation.
    /// This method captures the column of the first key encountered and ensures
    /// all subsequent sibling keys in this map align perfectly.
    private YamlNode parseMap(YamlCollection parentCollection, NodeProperties collectionProperties, NodeProperties nodeProperties, boolean isComplexKey) {
        debug(">parseMap");
        depth++;
        blockCollectionDepth++;
        try {
            YamlToken startToken = tokenBuffer.peek();

            if (onMarkerLine && flowDepth < 1) {
                error(startToken, YamlDiagnosticCode.BLOCK_COLLECTION_SAME_LINE_AS_MARKER);
            }



            // // TODO: This was for  but it breaks test87E4 and others
            // If flowDepth > 0, this map should be a flow map.

            // if (parentCollection != null &&
            //     parentCollection.getNodeStyle() == NodeStyle.FLOW &&
            //     flowDepth > 0
            // ) {
            //     error(startToken, YamlDiagnosticCode.BLOCK_COLLECTION_IN_FLOW_COLLECTION);
            // }



            trace("flowDepth="+flowDepth);
            trace("isComplexKey="+isComplexKey);

            // If properties were on a previous line, they belong to a collection
            moveProperties(nodeProperties, collectionProperties, startToken);

            debugProperties("c", collectionProperties);
            debugProperties("n", nodeProperties);

            YamlMap map = null;

            int mapColumn = -1;
            Set<Object> seenKeys = new HashSet<>();
            boolean expectComplexKeyDedent = false;

            while (!tokenBuffer.isAtEnd()) {
                trace("PM-loop");
                parseTrivia();

                if (check(YamlTokenType.DEDENT)) {
                    trace("DEDENT break");
                    break;
                }

                if (check(YamlTokenType.INDENT)) {
                    trace("PM-complex-key-indent");
                    if (isComplexKey) {
                        trace("isComplexKey, so accepting the indent");
                        tokenBuffer.advance();
                        parseTrivia();
                        expectComplexKeyDedent = true;
                    } else {
                        error(tokenBuffer.peek(), YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    }
                }

                // Peek at the token that will tell us if an entry is actually here
                trace("at markerToken");
                YamlToken markerToken = tokenBuffer.peek();

                boolean hasExplicitKey = false;
                if (check(YamlTokenType.KEY_INDICATOR)) {
                    hasExplicitKey = true;
                }
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

                if (hasExplicitKey) {
                    YamlToken keyIndicator = tokenBuffer.advance(); // Consume '?'
                    debug("keyIndicator="+keyIndicator);
                }

                if (!hasExplicitKey && !isScalarKeyStart) {
                    if (markerToken.getStartColumn() > map.getStartColumn()) {
                        error(markerToken, YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    }
                    trace("Not-a-key break");
                    break;
                }

                int markerColumn = markerToken.getStartColumn();
                if (nodeProperties != null) {
                    if (nodeProperties.anchor != null && nodeProperties.anchor.getStartLine() == markerToken.getStartLine()) {
                        int anchorColumn = nodeProperties.anchor.getStartColumn();
                        if (anchorColumn < markerColumn) {
                            markerColumn = anchorColumn;
                        }
                    }
                    if (nodeProperties.tag != null && nodeProperties.tag.getStartLine() == markerToken.getStartLine()) {
                        int tagColumn = nodeProperties.tag.getStartColumn();
                        if (tagColumn < markerColumn) {
                            markerColumn = tagColumn;
                        }
                    }
                }

                if (mapColumn == -1) {
                    // This is the first mapping ent Set things up.
                    mapColumn = markerColumn;
                    map = new YamlMap(startToken, markerToken.getStartLine(), mapColumn, options);

                    // If flowDepth > 0, this map is inside a flow collection.
                    // As block collections are not aloowed inside flow collections,
                    // this map must be a flow map.
                    map.setNodeStyle(flowDepth > 0 ? NodeStyle.FLOW : NodeStyle.BLOCK);

                    attachProperties(map, collectionProperties);
                    createEvent(map, YamlStreamingEventType.START_MAP);
                } else {
                    if (markerColumn != mapColumn && !isComplexKey) {
                        error(markerToken, YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    }
                }

                YamlNode key;

                if (hasExplicitKey && flowDepth == 0) {
                    parseTrivia();
                    // Explicit keys are always parsed via parseValue in case they are complex
                    key = parseValue(map, nodeProperties, true);
                } else {
                    // Standard implicit key
                    if (hasExplicitKey) {
                        key = parseKey(map, nodeProperties);
                    } else {
                        implicitKeyDepth++;
                        key = parseKey(map, nodeProperties);
                        implicitKeyDepth--;
                    }
                }

                if (key == null) {
                    return null;
                }

                parseTrivia();

                nodeProperties = null;

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

                trace("Before skipTrivia");
                parseTrivia();
                attachComments(key);
                trace("After skipTrivia");

                boolean isIndicatorIndented = false;
                if (check(YamlTokenType.INDENT)) {
                    trace("PM-indicator-indent");
                    isIndicatorIndented = true;
                    tokenBuffer.advance();
                }

                YamlNode value;

                if (check(YamlTokenType.VALUE_INDICATOR)) {
                    consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_MAP_KEY);
                    parseInlineComment(key);

                    trace("before parseValue");
                    if (check(YamlTokenType.NEWLINE) && !hasIndentedValueAfterNewline()) {
                        // TODO: WHen o we arrive here?
                        value = createNullScalar(false, null);
                    }
                    else {
                        parseTrivia();
                        boolean isValueIndented = false;
                        boolean hasIndentedAnchor = false;

                        if (checkIndented(YamlTokenType.TAG)) {
                        }
                        else if (checkIndented(YamlTokenType.ANCHOR)) {
                        }
                        else if (check(YamlTokenType.INDENT)) {
                            trace("PM-value-indent");
                            isValueIndented = true;
                            tokenBuffer.advance();
                        }

                        parseTrivia();

                        value = parseValue(map, null, false);

                        if (value == null) {
                            return null;
                        }

                        parseTrivia();
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
                    value = createNullScalar(false, null);
                    if (!hasExplicitKey) {
                        error(tokenBuffer.peek(), YamlDiagnosticCode.EXPECTED_COLON_MAP_KEY);
                    }
                }

                if (isIndicatorIndented) {
                    trace("PM-indicator-dedent");
                    consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT);
                }

                map.put(new YamlMapEntry(key, value, hasExplicitKey));
                parseTrivia();

                // Break if the next value indicator belongs to the parent mapping entry
                YamlToken nextValueIndicator = lookAheadIgnoringIndentsAndComments(YamlTokenType.VALUE_INDICATOR, 0);
                if (nextValueIndicator != null) {
                    int nextValIndCol = nextValueIndicator.getStartColumn();
                    debug("nextValIndCol="+nextValIndCol);
                    debug("mapColumn="+mapColumn);
                    if (nextValIndCol < mapColumn) {
                        debug("next value indicator belongs to parent mapping");
                        break;
                    }
                }

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

            createEvent(map, YamlStreamingEventType.END_MAP);
            return map;
        } finally {
            depth--;
            blockCollectionDepth--;
            debug("<parseMap");
        }
    }

    /// Parses a block sequence (list) indicated by leading dashes.
    private YamlNode parseSequence(NodeProperties pendingProperties) {
        debug(">parseSequence");
        depth++;
        blockCollectionDepth++;
        try {

            if (onMarkerLine) {
                error(tokenBuffer.peek(), YamlDiagnosticCode.BLOCK_COLLECTION_SAME_LINE_AS_MARKER);
            }

            debugProperties("p", pendingProperties);

            if (checkIndented(YamlTokenType.ANCHOR)) {
                debug("PA-hasIndentedAnchor1");
                tokenBuffer.advance();
            }

            YamlToken startToken = tokenBuffer.peek();
            int indicatorColumn = startToken.getStartColumn();

            YamlSequence sequence = new YamlSequence(startToken);
            sequence.setNodeStyle(NodeStyle.BLOCK);
            attachComments(sequence);
            attachProperties(sequence, pendingProperties);
            createEvent(sequence, YamlStreamingEventType.START_SEQUENCE);
            attachComments(sequence);

            while (!tokenBuffer.isAtEnd()) {
                parseTrivia();
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

                YamlToken thisIndicator = tokenBuffer.peek();
                YamlToken possibleNextIndicator = lookAheadIgnoringIndentsAndComments(YamlTokenType.SEQUENCE_ENTRY_INDICATOR, 1);

                if (this.blockCollectionDepth > 0 && thisIndicator.isFollowedByTab() && possibleNextIndicator != null ) {
                    error(tokenBuffer.peek(), YamlDiagnosticCode.TABS_NOT_ALLOWED_AS_INDENTATION);
                }

                tokenBuffer.advance(); // Consume this indicator
                parseTrivia();

                if (possibleNextIndicator != null && possibleNextIndicator.getStartColumn() == indicatorColumn) {
                    sequence.add(createNullScalar(false, null));
                } else {
                    YamlNode item = parseValue(sequence, null, false);
                    if (item == null) {
                        return null;
                    }
                    sequence.add(item);
                }

                parseTrivia();
            }

            createEvent(tokenBuffer.peek(), YamlStreamingEventType.END_SEQUENCE);

            return sequence;
        } finally {
            depth--;
            blockCollectionDepth--;
            debug("<parseSequence");
        }
    }

    /// Parses a flow sequence like [item1, item2].
    private YamlSequence parseFlowSequence(boolean isInCollection, NodeProperties pendingProperties) {
        debug(">parseFlowSequence");
        depth++;
        flowDepth++;
        try {
            YamlToken startToken = consume(YamlTokenType.SEQUENCE_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACKET);
            YamlSequence sequence = new YamlSequence(startToken);
            sequence.setNodeStyle(NodeStyle.FLOW);
            attachComments(sequence);
            attachProperties(sequence, pendingProperties);

            createEvent(sequence, YamlStreamingEventType.START_SEQUENCE);

            while (!check(YamlTokenType.SEQUENCE_END) && !tokenBuffer.isAtEnd()) {
                parseTrivia();



                // TODO: Using blockCollectionDepth and this isFlowStyle being passed all the way down,
                // we need to raise an error for test_Y79Y_003
                YamlNode item = parseValue(sequence, null, false);
                if (item == null) {
                    return null;
                }

                sequence.add(item);
                parseTrivia();
                if (!match(YamlTokenType.COMMA)) {
                    break;
                }
                parseTrivia();
            }

            consume(YamlTokenType.SEQUENCE_END, YamlDiagnosticCode.EXPECTED_CLOSE_BRACKET);
            createEvent(tokenBuffer.peek(), YamlStreamingEventType.END_SEQUENCE);
            return sequence;
        } finally {
            depth--;
            flowDepth--;
            debug("<parseFlowSequence");
        }
    }

    /// https://yaml.org/spec/1.2.2/#742-flow-mappings
    ///
    /// Parses a flow map like {key: value, key2: value2}.
    /// Parses a flow-style mapping (e.g., { key: value, key2: value2 }).
    /// This method handles the explicit MAP_START and MAP_END tokens.
    private YamlNode parseFlowMap(NodeProperties collectionProperties, NodeProperties nodeProperties, boolean isImplicitFlowMap) {
        debug(">parseFlowMap");
        depth++;
        flowDepth++;
        try {

            debug("isImplicitFlow=" + isImplicitFlowMap);
            debugProperties("c", collectionProperties);
            debugProperties("n", nodeProperties);
            YamlToken startToken = tokenBuffer.peek();

            if (!isImplicitFlowMap) {
                consume(YamlTokenType.MAP_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACE_FLOW_MAP);
            }

            // If properties were on a previous line, they belong to a collection
            moveProperties(nodeProperties, collectionProperties, null);

            YamlMap map = new YamlMap(startToken, options);
            map.setNodeStyle(NodeStyle.FLOW);
            attachComments(map);
            attachProperties(map, collectionProperties);
            createEvent(map, YamlStreamingEventType.START_MAP);

            // Clear any whitespace/newlines before checking for an empty map exit
            parseTrivia();

            // Handle empty flow map {}
            if (match(YamlTokenType.MAP_END)) {
                createEvent(tokenBuffer.peek(), YamlStreamingEventType.END_MAP);
                return map;
            }

            while (!tokenBuffer.isAtEnd()) {
                parseTrivia();

                // 1. Parse Key

                boolean hasExplicitKey = check(YamlTokenType.KEY_INDICATOR);
                YamlNode key;
                if (hasExplicitKey) {
                    tokenBuffer.advance(); // Consume '?'
                    if (lookAheadIgnoringIndentsAndComments(YamlTokenType.MAP_START, 0) != null ||
                        lookAheadIgnoringIndentsAndComments(YamlTokenType.MAP_END, 0) != null ||
                        lookAheadIgnoringIndentsAndComments(YamlTokenType.SEQUENCE_START, 0) != null ||
                        lookAheadIgnoringIndentsAndComments(YamlTokenType.SEQUENCE_END, 0) != null ||
                        lookAheadIgnoringIndentsAndComments(YamlTokenType.COMMA, 0) != null
                    ){
                        key = createNullScalar(true, null);
                    } else {
                        // Complex key
                        parseTrivia();
                        key = parseKey(map, null);
                    }
                } else {
                    // Standard implicit key
                    key = parseKey(map, null);
                }

                parseTrivia();

                YamlNode value;
                if (check(YamlTokenType.VALUE_INDICATOR)) {
                    // 2. Consume Value Indicator
                    consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP);
                    parseTrivia();

                    // 3. Parse Value
                    value = parseValue(map, null, false);
                    if (value == null) {
                        return null;
                    }
                } else {
                    value = createNullScalar(false, null);
                }
                // 4. Store Entry
                map.put(new YamlMapEntry(key, value));

                parseTrivia();

                if (isImplicitFlowMap && check(YamlTokenType.COMMA)) {
                    // The comma belongs to the parent collection
                    break;
                }

                // 5. Check for continuation or end
                if (match(YamlTokenType.COMMA)) {
                    // Allow trailing commas by checking for end after comma
                    if (null != lookAheadIgnoringComments(YamlTokenType.MAP_END)) {
                        parseTrivia();
                    }
                    if (match(YamlTokenType.MAP_END)) {
                        break;
                    }



                    if (null != lookAheadIgnoringComments(YamlTokenType.SEQUENCE_END)) {
                        trace("parseFlowMap; break0 on SEQ_END");
                        parseTrivia();
                        break;
                    }
                    if (isImplicitFlowMap && check(YamlTokenType.SEQUENCE_END)) {
                        trace("parseFlowMap; break1 on SEQ_END");
                        break;
                    }



                    continue;
                } else if (match(YamlTokenType.MAP_END)) {
                    break;



                } else if (isImplicitFlowMap && check(YamlTokenType.SEQUENCE_END)) {
                    trace("parseFlowMap; break2 on SEQ_END");
                    break;



                } else if (null != lookAheadIgnoringComments(YamlTokenType.MAP_END)) {
                    parseTrivia();
                    break;
                } else {
                    error(tokenBuffer.peek(), YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP);
                }
            }
            createEvent(tokenBuffer.peek(), YamlStreamingEventType.END_MAP);
            return map;
        } finally {
            depth--;
            flowDepth--;
            debug("<parseFlowMap");
        }
    }

    private YamlScalar parseScalar(YamlCollection parentCollection, NodeProperties pendingProperties, boolean isKey) {
        debug(">parseScalar");
        depth++;
        try {
            debugProperties("p", pendingProperties);
            YamlToken token = consume(YamlTokenType.SCALAR, YamlDiagnosticCode.EXPECTED_SCALAR);
            ScalarStyle style = token.getScalarStyle();

            // Block scalar values in collections must be indented
            if (style == ScalarStyle.LITERAL || style == ScalarStyle.FOLDED) {
                if (token != null &&
                    !token.getContent().isEmpty() &&
                    token.getBlockIndent() == 0 &&
                    parentCollection != null
                ) {
                    error(token, YamlDiagnosticCode.BLOCK_SCALAR_COLLECTION_INDENT);
                }
            }



            // if (style == ScalarStyle.PLAIN && flowDepth > 0) {
            //     // Plain scalars inside flow collections cannot be multi-line
            //     if (token.getLexeme().contains("\n")) {
            //     }
            // }

            // if (isKey) {
            //     if (token.getLexeme().contains("\n")) {
            //         // Implicit keys need to be on a single line
            //         error(token, YamlDiagnosticCode.IMPLICIT_KEY_SINGLE_LINE);
            //     }
            // }

            //
            // TODO: More errors have crept in
            //
            if (parentCollection instanceof YamlMap &&
                parentCollection.getNodeStyle() == NodeStyle.FLOW &&
                blockCollectionDepth > 0
            )  {
                if(token.getStartLine() > parentCollection.getStartLine()) {
                    if (token.startsOnNewLine() && token.getFirstLineIndent() == 0) {
                        error(token, YamlDiagnosticCode.FLOW_MAP_BLOCK_INDENT);
                    }
                }
            }



            if ((parentCollection instanceof YamlSequence) && parentCollection.getNodeStyle() == NodeStyle.FLOW) {
                // Check for test_Y79Y_3
                // TODO: should this not be:
                // if(token.getStartLine() > parentCollection.getStartLine() && token.getFirstLineIndent() == 0) {
                if (token.startsOnNewLine() && token.getFirstLineIndent() == 0 && blockCollectionDepth > 0) {
                    error(token, YamlDiagnosticCode.FLOW_SEQUENCE_BLOCK_COLLECTION_INDENT);
                }
            }

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
                attachComments(scalar);
                parseInlineComment(scalar);
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
                attachComments(scalar);
                parseInlineComment(scalar);
            }

            else if (style == ScalarStyle.PLAIN) {
                trace("PLAIN");
                // just return the scalar as-is
                scalar = new YamlScalar(
                    token,
                    PrimitiveType.ANY,
                    options
                );
                attachComments(scalar);
                parseInlineComment(scalar);
            }
            else {
                trace("DEFAULT");
                scalar = new YamlScalar(
                    token,
                    PrimitiveType.ANY,
                    options
                );
                attachComments(scalar);
                parseInlineComment(scalar);
            }

            pendingProperties.attachTo(scalar);

            if (isKey) {
                createEvent(scalar, YamlStreamingEventType.KEY);
            } else {
                createEvent(scalar, YamlStreamingEventType.VALUE_SCALAR);
            }
            return scalar;
        } finally {
            depth--;
            debug("<parseScalar");
        }
    }

    private YamlNode parseAlias(NodeProperties pendingProperties) {
        debug(">parseAlias");
        depth++;
        try {
            if (pendingProperties != null) {
                if (pendingProperties.anchor != null) {
                    error(pendingProperties.anchor.getToken(), YamlDiagnosticCode.ALIAS_MUST_NOT_SPECIFY_PROPERTIES);
                }
                if (pendingProperties.tag != null) {
                    error(pendingProperties.tag.getToken(), YamlDiagnosticCode.ALIAS_MUST_NOT_SPECIFY_PROPERTIES);
                }
            }

            YamlToken aliasToken = tokenBuffer.advance();

            String raw = aliasToken.getContent();
            String name = raw.startsWith("*") ? raw.substring(1) : raw;

            YamlAlias alias = new YamlAlias(aliasToken, name);
            attachComments(alias);

            if (pendingProperties != null) {
                pendingProperties.attachTo(alias);
            }

            // Mirror parseValue alias resolution
            if (anchorRegistry.containsKey(name)) {
                alias.setResolvedNode(anchorRegistry.get(name));
            }
            createEvent(alias, YamlStreamingEventType.ALIAS);

            return alias;
        } finally {
            depth--;
            debug("<parseAlias");
        }
    }

    /// Collects comments and skips newlines, storing comments in the buffer.
    private void parseTrivia() {
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
                skipNewline();
                continue;
            }



            // TODO: tidy this up
            if (type == YamlTokenType.COMMENT) {
                // TODO: This is rubbish. Document level comments don't have to be at column 1.
                // If it's a root-level comment at the end of the file, leave it for the stream
                if (tokenBuffer.peek().getStartColumn() == 1 && tokenBuffer.isTrailingStreamComment()) {
                    break;
                }
                pendingComments.add(parseComment(CommentStyle.LEADING)); // TODO: flow or block?
                continue;
            }



            // Comments can be indented
            if (type == YamlTokenType.INDENT) {
                // Find the matching DEDENT.
                // If there is nothing but comments and whitespace in between,
                // consume up to and including the DEDENT.
                if (skipUntilIndentLevel > -1) {
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
                    skipUntilIndentLevel = -1;
                }
                tokenBuffer.advance();
                continue;
            }
            break;
        }
    }

    private void skipNewline() {
        if (check(YamlTokenType.NEWLINE)) {
            if (implicitKeyDepth > 0) {
                error(tokenBuffer.peek(), YamlDiagnosticCode.IMPLICIT_KEY_SINGLE_LINE);
            }
            tokenBuffer.advance();
            onMarkerLine = false;
        }
    }

    private void parseBlockComments() {
        while (!tokenBuffer.isAtEnd()) {
            if (check(YamlTokenType.COMMENT)) {
                YamlComment comment = parseComment(CommentStyle.LEADING);
                pendingComments.add(comment);
                continue;
            } else if (checkIndented(YamlTokenType.COMMENT)) {
                tokenBuffer.advance();
                YamlComment comment = parseComment(CommentStyle.LEADING);
                pendingComments.add(comment);
                tokenBuffer.advance();
                continue;
            } else {
                break;
            }
        }
    }

    private YamlComment parseComment(CommentStyle commentStyle) {
        YamlToken token = tokenBuffer.advance();
        String text = token.getContent() != null ? token.getContent().toString() : "";

        // Strip the raw hash marker, but preserve the exact space fidelity
        if (text.startsWith("#")) {
            text = text.substring(1);
        }

        return new YamlComment(
            token,
            text,
            commentStyle
        );
    }

    private void parseInlineComment(YamlNode node) {
        // If the very next token is a comment on the same line, it belongs to THIS node
        if (check(YamlTokenType.COMMENT) && tokenBuffer.peek().getStartLine() == node.getStartLine()) {
            node.addComment(parseComment(CommentStyle.INLINE));
        }
    }

    private Pair<NodeProperties,NodeProperties> parseNodePropeties(boolean allowMultipleLines, boolean handleIndents, NodeProperties pendingProperties) {
        return parseNodePropeties(allowMultipleLines, handleIndents, pendingProperties, null);
    }

    private Pair<NodeProperties,NodeProperties> parseNodePropeties(boolean allowMultipleLines, boolean handleIndents, NodeProperties pendingCollectionProperties, NodeProperties pendingNodeProperties) {
        debug(">parseNodeProperties");
        depth++;
        try {
            trace("allowMultipleLines=" + allowMultipleLines);
            List<YamlNodeProperty> properties = new ArrayList<>();
            int nAnchors = 0;
            int nTags = 0;
            YamlAnchor anchor = null;
            YamlTag tag = null;

            // Add pending collection properties to the new list
            if (pendingCollectionProperties != null) {
                if (pendingCollectionProperties.anchor != null) {
                    anchor = pendingCollectionProperties.anchor;
                    nAnchors++;
                    properties.add(anchor);
                    trace("merged anchor " + anchor);
                }
                if (pendingCollectionProperties.tag != null) {
                    tag = pendingCollectionProperties.tag;
                    properties.add(tag);
                    nTags++;
                    trace("merged tag " + tag);
                }
            }

            // Add pending node properties to the new list
            if (pendingNodeProperties != null) {
                if (pendingNodeProperties.anchor != null) {
                    anchor = pendingNodeProperties.anchor;
                    properties.add(anchor);
                    nAnchors++;
                    trace("merged anchor " + pendingNodeProperties.anchor);
                }
                if (pendingNodeProperties.tag != null) {
                    tag = pendingNodeProperties.tag;
                    properties.add(tag);
                    nTags++;
                    trace("merged tag " + tag);
                }
            }

            // Build a list of property nodes - tags and anchors
            while (true) {

                boolean moreProperties = check(YamlTokenType.ANCHOR) ||
                                         check(YamlTokenType.TAG) ||
                                         lookAheadIgnoringIndentsAndComments(YamlTokenType.ANCHOR) ||
                                         lookAheadIgnoringIndentsAndComments(YamlTokenType.TAG);

                if (allowMultipleLines && moreProperties && (check(YamlTokenType.NEWLINE) || check(YamlTokenType.COMMENT))) {
                    parseTrivia();
                    continue;
                }

                if (handleIndents && checkIndented(YamlTokenType.ANCHOR)) {
                    trace("indented anchor");
                    YamlToken anchorToken = consumeIndented(YamlTokenType.ANCHOR);
                    anchor = new YamlAnchor(anchorToken);
                    properties.add(anchor);
                    nAnchors++;
                    continue;
                } else if (check(YamlTokenType.ANCHOR)) {
                    trace("anchor");
                    YamlToken anchorToken = tokenBuffer.advance();
                    anchor = new YamlAnchor(anchorToken);
                    properties.add(anchor);
                    nAnchors++;
                    continue;
                }

                if (handleIndents && checkIndented(YamlTokenType.TAG)) {
                    trace("indented tag");
                    YamlToken tagToken = consumeIndented(YamlTokenType.TAG);
                    tag = parseTag(tagToken); //new YamlTag(tagToken);
                    properties.add(tag);
                    nTags++;
                    continue;
                } else if (check(YamlTokenType.TAG)) {
                    trace("tag");
                    YamlToken tagToken = tokenBuffer.advance();
                    tag = parseTag(tagToken); //new YamlTag(tagToken);
                    properties.add(tag);
                    nTags++;
                    continue;
                }
                break;
            }

            // For flow scenarios, determining if they're valid is simple...
            if (!allowMultipleLines) {
                if (nAnchors > 1) {
                    error(tag.getToken(), YamlDiagnosticCode.TOO_MANY_ANCHORS);
                }
                if (nTags > 1) {
                    error(tag.getToken(), YamlDiagnosticCode.TOO_MANY_TAGS);
                }
            }

            // In non-flow scenarios, we might be parsing properties for
            // both a map and its first key together.

            NodeProperties collectionProperties = new NodeProperties(this::attachAnchor, this::attachTag);
            NodeProperties nodeProperties = new NodeProperties(this::attachAnchor, this::attachTag);

            if (!properties.isEmpty()) {
                int firstLine = properties.getFirst().getStartLine();
                int lastLine = properties.getLast().getStartLine();
                int startColumn = properties.getFirst().getStartColumn();
                boolean multiLine = firstLine != lastLine;
                if (multiLine) {
                    trace("multi-line with startColumn " + startColumn);
                    for (YamlNodeProperty property : properties) {
                        if (property.getStartLine() == lastLine) {
                            if (property instanceof YamlAnchor anchorProperty) {
                                if (nodeProperties.anchor != null) {
                                    error(property.getToken(), YamlDiagnosticCode.TOO_MANY_ANCHORS);
                                }
                                nodeProperties.anchor = anchorProperty;
                            }
                            if (property instanceof YamlTag tagProperty) {
                                if (nodeProperties.tag != null) {
                                    error(property.getToken(), YamlDiagnosticCode.TOO_MANY_TAGS);
                                }
                                nodeProperties.tag = tagProperty;
                            }
                            nodeProperties.startLine = property.getStartLine(); // Should all be on one line
                            if (nodeProperties.startColumn < 1) {
                                nodeProperties.startColumn = property.getStartColumn();
                            }
                        } else {
                            if (property instanceof YamlAnchor anchorProperty) {
                                if (collectionProperties.anchor != null) {
                                    error(property.getToken(), YamlDiagnosticCode.TOO_MANY_ANCHORS);
                                }
                                collectionProperties.anchor = anchorProperty;
                            }
                            if (property instanceof YamlTag tagProperty) {
                                if (collectionProperties.tag != null) {
                                    error(property.getToken(), YamlDiagnosticCode.TOO_MANY_TAGS);
                                }
                                collectionProperties.tag = tagProperty;
                            }
                            if (collectionProperties.startLine < 1) {
                                collectionProperties.startLine = property.getStartLine();
                                collectionProperties.startColumn = property.getStartColumn();
                            }
                        }
                    }
                } else {
                    trace("single line");
                    if (nAnchors > 1) {
                        error(tag.getToken(), YamlDiagnosticCode.TOO_MANY_ANCHORS);
                    }
                    if (nTags > 1) {
                        error(tag.getToken(), YamlDiagnosticCode.TOO_MANY_TAGS);
                    }
                    nodeProperties.startLine = properties.getFirst().getStartLine();
                    nodeProperties.startColumn = properties.getFirst().getStartColumn();
                    nodeProperties.anchor = anchor;
                    nodeProperties.tag = tag;
                }
            }
            return new Pair<>(collectionProperties, nodeProperties);
        } finally {
            depth--;
            debug("<parseNodeProperties");
        }
    }


    // §6.8.2 Tag Handles
    private YamlTag parseTag(YamlToken token) {
        String raw = token.getContent();

        boolean e0 = false; // First exclamation mark
        boolean e1 = false; // Second exclamation mark
        String v0 = "";
        String v1 = "";

        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '!') {
                if (!e0) {
                    e0 = true;
                } else if (!e1) {
                    e1 = true;
                } else {
                    error(token, YamlDiagnosticCode.MALFORMED_TAG, raw);
                }
            } else if (e0) {
                if (e1) {
                    v1 += ch;
                } else {
                    v0 += ch;
                }
            }
        }

        if (e1) {
            YamlTag tag = new YamlTag(token, "!" + v0 + "!", v0, v1);
            return tag;
        } else {
            YamlTag tag = new YamlTag(token, "!", "", v0);
            return tag;
        }
    }

    //
    // Navigation and Lookahead Helpers
    //

    @Nullable
    private YamlToken consume(YamlTokenType type, DiagnosticCode msgCode) {
        if (check(type)) return tokenBuffer.advance();
        YamlToken token = tokenBuffer.peek();
        error(tokenBuffer.peek(), msgCode, token.getType());
        return null;
    }

    private YamlToken consumeIndented(YamlTokenType tokenType) {
        trace("consumeIndented " + tokenType);
        consume(YamlTokenType.INDENT, YamlDiagnosticCode.EXPECTED_INDENT);
        parseTrivia();
        YamlToken indentedToken = tokenBuffer.advance();
        parseTrivia();
        consume(YamlTokenType.DEDENT, YamlDiagnosticCode.EXPECTED_DEDENT);
        return indentedToken;
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

    private boolean checkIndented(YamlTokenType type) {
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
        return lookAheadIgnoringComments(targetType, 1);
    }

    private YamlToken lookAheadIgnoringComments(YamlTokenType targetType, int startAhead) {
        int ahead = startAhead;
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

    private boolean lookAheadIgnoringIndentsAndComments(YamlTokenType targetType) {
        return lookAheadIgnoringIndentsAndComments(targetType, 1) != null;
    }

    private YamlToken lookAheadIgnoringIndentsAndComments(YamlTokenType targetType, int startAhead) {
        int ahead = startAhead;
        while (!tokenBuffer.isAtEnd(ahead)) {
            YamlToken token = tokenBuffer.peekAhead(ahead);
            YamlTokenType type = token.getType();
            if (type == targetType) return token;
            if (type == YamlTokenType.NEWLINE || type == YamlTokenType.INDENT || type == YamlTokenType.COMMENT) {
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

    private boolean isKnownDirective(YamlToken token) {
        String content = token.getContent();
        return content.startsWith("%YAML") || content.startsWith("%TAG");
    }

    private YamlScalar createEmptyScalar(boolean isKey, NodeProperties nodeProperties) {
        debug("createEmptyScalar");
        YamlScalar scalar = new YamlScalar(tokenBuffer.peek(), "", PrimitiveType.STRING, ScalarStyle.PLAIN, options);
        if (nodeProperties != null) {
            nodeProperties.attachTo(scalar);
        }
        createEvent(
            scalar,
            isKey ? YamlStreamingEventType.KEY
                  : YamlStreamingEventType.VALUE_SCALAR
        );
        return scalar;
    }

    private YamlScalar createNullScalar(boolean isKey, NodeProperties pendingProperties) {
        debug("createNullScalar");
        YamlScalar scalar = new YamlScalar(tokenBuffer.peek(), PrimitiveType.NULL, options);
        if (pendingProperties != null) {
            pendingProperties.attachTo(scalar);
        }
        createEvent(
            scalar,
            isKey ? YamlStreamingEventType.KEY
                  : YamlStreamingEventType.VALUE_SCALAR
        );
        return scalar;
    }

    //
    // Event Helpers
    //

    protected void createEvent(YamlToken token, YamlStreamingEventType type) {
        createEvent(token, type, token.getContent());
    }

    protected void createEvent(YamlToken token, YamlStreamingEventType type, String content) {
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

    private void createEvent(YamlNode node, YamlStreamingEventType type) {
        YamlNode target = node;
        String anchorName = "";

        if (type == YamlStreamingEventType.ALIAS ||
            type == YamlStreamingEventType.KEY ||
            type == YamlStreamingEventType.VALUE_SCALAR ||
            type == YamlStreamingEventType.START_MAP ||
            type == YamlStreamingEventType.START_SEQUENCE
        ) {
            if (node.getAnchor() instanceof String anchor) {
                anchorName = anchor;
            }
        }

        NodeStyle nodeStyle = flowDepth > 0
            ? NodeStyle.FLOW
            : target.getNodeStyle();

        ScalarStyle scalarStyle = null;
        if (target instanceof YamlScalar scalar) {
            scalarStyle = scalar.getScalarStyle();
        }

        YamlStreamingEvent event = new YamlStreamingEvent(
            target.getStartLine(),
            target.getStartColumn(),
            type,
            nodeStyle,
            scalarStyle,
            node.getTag(), // TODO: Are we sure this should not be target.getTag() ?
            node.getResolvedTag(),
            anchorName,
            target.getToken().getLexeme(),
            target.asString()
        );

        handleEvent(event);
    }

    //
    // Node Properties and Comment Helpers
    //

    private void attachAnchor(YamlNode node, YamlAnchor anchor) {
        if (anchor == null) return;

        String anchorName = YamlAnchor.extractAnchorName(anchor.getToken());

        if (isReportingTrace()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Attaching anchor ");
            sb.append(TermUtils.ANSI_WHITE);
            sb.append(anchorName);
            sb.append(TermUtils.ANSI_RESET);
            if (node instanceof YamlScalar scalar) {
                sb.append(" to YamlScalar ");
                sb.append(TermUtils.ANSI_WHITE);
                sb.append(StringUtils.debugString(10, scalar.getContent()));
                sb.append(TermUtils.ANSI_RESET);
            } else {
                sb.append(" to ");
                sb.append(node.getClass().getSimpleName());
            }
            trace(sb.toString());
        }

        node.setAnchor(anchorName);
        anchorRegistry.put(anchorName, node);
    }

    private void attachTag(YamlNode node, YamlTag tag) {
        if (tag == null) return;

        String tagContent = tag.getRawValue();
        if (isReportingTrace()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Attaching tag ");
            sb.append(TermUtils.ANSI_WHITE);
            sb.append(tagContent);
            sb.append(TermUtils.ANSI_RESET);
            if (node instanceof YamlScalar scalar) {
                sb.append(" to YamlScalar ");
                sb.append(TermUtils.ANSI_WHITE);
                sb.append(StringUtils.debugString(10, scalar.getContent()));
                sb.append(TermUtils.ANSI_RESET);
            } else {
                sb.append(" to ");
                sb.append(node.getClass().getSimpleName());
            }
            trace(sb.toString());
        }

        resolveTag(tag);

        node.setResolvedTag(tag.getResolvedValue());
        node.setTag(tag.getRawValue());
    }


    // According to the YAML specification (§6.8.2 Tag Handles), there are
    // only three categories of tag handles:
    //
    // Primary Handle (!): Starts with !, does not end with !. (e.g., !local, !foo)
    //
    // Secondary Handle (!!): Exactly two exclamation marks. (e.g., !!str, !!int, !!map)
    //
    // Named Handles (!name!): Starts with !, contains a handle name, and
    // ends with a second !. (e.g., !e!tag, !prefix!A)

    private void resolveTag(YamlTag tag) {
        List<YamlDirective> directives = document.getDirectives();
        for (YamlDirective d : directives) {
            if (d instanceof YamlTagDirective tagDirective) {
                String directiveHandle = tagDirective.getName();
                if (directiveHandle.equals(tag.getHandle())) {
                    debug("Debug");
                    String decodedSuffix = decodeTagUri(tag.getSuffix());
                    tag.setResolvedValue(tagDirective.getValue() + decodedSuffix);
                    return;
                }
            }
        }

        String raw = tag.getRawValue();

        if (raw.equals("!")) {
            tag.setResolvedValue("!");
            return;
        }

        if (raw.startsWith("!!")) {
            String name = raw.substring(2);
            tag.setResolvedValue("tag:yaml.org,2002:" + name);
            return;
        }

        if (raw.startsWith("!<") && raw.endsWith(">")) {
            tag.setResolvedValue(raw.substring(2, raw.length() - 1));
            return;
        }

        if (tag.getHandle().endsWith("!") && !tag.getHandleName().isEmpty()) {
            if (tag.getToken() == null) {
                System.out.println("Debug");
            }
            error(tag.getToken(), YamlDiagnosticCode.CANNOT_RESOLVE_TAG, tag.getRawValue());
        }

        tag.setResolvedValue(raw);

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

    ///
    /// @param from
    /// @param to
    /// @param startToken If this is null, any properties that exist will be moved.
    ///                   If this is a token, a property must appear on a line before the token for it to be moved.
    private void moveProperties(NodeProperties from, NodeProperties to, YamlToken startToken) {
        trace("moveProperties");
        if (from != null && from.startLine > 0) {
            if (from.anchor != null && (startToken == null || from.anchor.getStartLine() < startToken.getStartLine())) {
                if (to != null && to.anchor != null) {
                    error(from.anchor.getToken(), YamlDiagnosticCode.UNEXPECTED_TOKEN, from.anchor.getToken().getType());
                }
                to.anchor = from.anchor;
                from.anchor = null;
            }
            if (from.tag != null && (startToken == null || from.tag.getStartLine() < startToken.getStartLine())) {
                if (to != null && to.tag != null) {
                    error(from.tag.getToken(), YamlDiagnosticCode.UNEXPECTED_TOKEN, from.tag.getToken().getType());
                }
                to.tag = from.tag;
                from.tag = null;
            }
        }
    }

    private void attachProperties(YamlNode node, NodeProperties properties) {
        if (properties != null) {
            properties.attachTo(node);
        }
    }

    /// Clears the [pendingComments] buffer by attaching them to the given node.
    private void attachComments(YamlNode node) {
        for (YamlComment comment : pendingComments) {
            node.addComment(comment);
        }
        pendingComments.clear();
    }

    //
    // Setup and Diagnostics
    //

    protected void preParseStateInit() {
        // Options
        depthLimit = options.getDepthLimit();
        isMultiDocumentParsing = options.isMultiDocument();
        if (options.preloadTokenBuffer()) {
            tokenBuffer = new PreloadedTokenBuffer();
        } else {
            tokenBuffer = new OnDemandTokenBuffer(CIRCULAR_BUFFER_SIZE);
        }

        // State
        tokenBuffer.setTokenizer(getTokenizer());
        anchorRegistry.clear();
        pendingComments.clear();
        errorEncountered.set(false);
        fileEndsWithNewLine = false;
        flowDepth = 0;
        depth = 0;
        implicitKeyDepth = 0;
        onMarkerLine = false;
        blockCollectionDepth = 0;
    }

    private void debugProperties(String prefix, NodeProperties properties) {
        if (properties == null) return;
        if (properties.anchor != null) {
            trace(
                prefix + ".anchor=" +
                TermUtils.ANSI_WHITE +
                properties.anchor.getName() +
                TermUtils.ANSI_RESET
            );
        }
        if (properties.tag != null) {
            trace(
                prefix + ".tag=" +
                TermUtils.ANSI_WHITE +
                properties.tag.getRawValue() +
                TermUtils.ANSI_RESET
            );
        }
    }

    protected boolean isReportingTrace() {
        return reporter != null &&
               !reporter.isSilent() &&
               reporter.getLevel().includes(Level.TRACE);
    }

    protected void error(YamlToken token, DiagnosticCode code, Object... details) {
        errorEncountered.set(true);
        createEvent(token, YamlStreamingEventType.ERROR, formatMessage(code, details));

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

    protected void report(Level level, String message, Object... details) {
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

        if (level == Level.TRACE) {
            reporter.trace("L%3d C%3d I%3d %s%s: %s",
                tokenBuffer.peek().getStartLine(),
                tokenBuffer.peek().getStartColumn(),
                tokenBuffer.offset(),
                indent,
                output,
                tokenBuffer.upcomingTokens());
        } else {
            reporter.debug("L%3d C%3d I%3d %s%s: %s",
                tokenBuffer.peek().getStartLine(),
                tokenBuffer.peek().getStartColumn(),
                tokenBuffer.offset(),
                indent,
                output,
                tokenBuffer.upcomingTokens());
        }
    }

    private String formatMessage(DiagnosticCode code, Object... details) {
        return AbstractLocalizableException.getLocalizer().format(code, details);
    }

    private static class NodeProperties {
        int startLine;
        int startColumn;
        YamlAnchor anchor;
        YamlTag tag;
        BiConsumer<YamlNode,YamlAnchor> anchorHandler;
        BiConsumer<YamlNode,YamlTag> tagHandler;

        public NodeProperties(BiConsumer<YamlNode,YamlAnchor> anchorHandler,
                              BiConsumer<YamlNode,YamlTag> tagHandler
        ) {
            this.anchorHandler = anchorHandler;
            this.tagHandler = tagHandler;
        }

        public void attachTo(YamlNode node) {
            anchorHandler.accept(node, anchor);
            tagHandler.accept(node, tag);

            // Add to node's properties in original order
            if (anchor != null && tag != null) {
                if (anchor.getToken().getOffset() < tag.getToken().getOffset()) {
                    node.getProperties().add(anchor);
                    node.getProperties().add(tag);
                } else {
                    node.getProperties().add(tag);
                    node.getProperties().add(anchor);
                }
            } else if (anchor != null) {
                node.getProperties().add(anchor);
            } else if (tag != null) {
                node.getProperties().add(tag);
            }

            // ensure they can't be reused
            anchor = null;
            tag = null;
        }

        public boolean isEmpty() {
            return anchor == null && tag == null;
        }
    }
}
