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
import io.github.qishr.cascara.common.lang.streaming.StreamingEventType;
import io.github.qishr.cascara.common.lang.token.TokenCategory;
import io.github.qishr.cascara.common.lang.type.PrimitiveType;
import io.github.qishr.cascara.common.util.Pair;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.common.util.TermUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchor;
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
import io.github.qishr.cascara.lang.yaml.token.YamlErrorToken;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;
import io.github.qishr.cascara.lang.yaml.util.CommentStyle;
import io.github.qishr.cascara.lang.yaml.util.NodeStyle;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;
import io.github.qishr.cascara.lang.yaml.util.YamlDirectiveType;

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
    private int implicitKeyDepth;
    private boolean onMarkerLine;
    protected boolean fileEndsWithNewLine = false;
    protected AtomicBoolean errorEncountered = new AtomicBoolean();

    /// Buffer to hold comments until a data node is created to claim them.
    private final List<YamlComment> pendingComments = new ArrayList<>();
    private final Map<String, YamlNode> anchorRegistry = new HashMap<>();

    private YamlDocument document;

    // With this true, BU8La and test57H4 fail.
    // With this false, only FH7J fails.
    boolean test_FH7J = true;

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
                    // debug("Debug");
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
                createEvent(tokenBuffer.peek(), StreamingEventType.START_DOCUMENT, "---");
                tokenBuffer.advance();
                onMarkerLine = true;
                parseTrivia();
            } else {



                createEvent(tokenBuffer.peek(), StreamingEventType.START_DOCUMENT, "");



            }

            if (check(YamlTokenType.DOCUMENT_END) || check(YamlTokenType.DOCUMENT_START) || tokenBuffer.isAtEnd()) {



                document.setBody(createNullScalar(false, null));



            } else {
                YamlNode body = parseValue(0, false, false, false, null);
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
                        parseTrivia();
                    } else {
                        error(nextToken, YamlDiagnosticCode.UNEXPECTED_TOKEN, nextToken.getType());
                    }
                } else {
                    createEvent(tokenBuffer.peek(), StreamingEventType.END_DOCUMENT, "");



                    // // TODO
                    // if (moveParseTrivia) {
                        parseTrivia();
                    // }



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

    private YamlNode parseKey(int parentIndent, boolean isFlowStyle, NodeProperties pendingProperties) {
        debug(">parseKeyNode");
        depth++;
        // implicitKeyDepth++;
        try {

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
                key = parseValue(parentIndent, isFlowStyle, false, false, nodeProperties);
            } else
            if (token.getType() == YamlTokenType.SEQUENCE_ENTRY_INDICATOR ||
                token.getType() == YamlTokenType.INDENT) {
                key = parseValue(parentIndent, false, false, false, nodeProperties);
            } else if (tokenType == YamlTokenType.VALUE_INDICATOR) {
                // Empty key
                key = createEmptyScalar(true, nodeProperties);
            } else if (tokenType == YamlTokenType. MAP_START) {
                key = parseFlowMap(nodeProperties);
            } else if (tokenType == YamlTokenType. SEQUENCE_START) {
                key = parseFlowSequence(nodeProperties);
            } else if (tokenType == YamlTokenType. SCALAR) {
                YamlScalar scalar = parseScalar(true, nodeProperties);
                // String testName = DebugUtils.getTestName();
                // if (testName != null) {
                //     warn(token, GenericDiagnosticCode.WARN, testName);
                // }
                if (flowDepth == 0 && scalar.getLexeme().contains("\n")) {
                    error(scalar.getToken(), YamlDiagnosticCode.IMPLICIT_KEY_SINGLE_LINE);
                }
                key = scalar;
            } else if (tokenType == YamlTokenType. ALIAS) {
                key = parseAlias(pendingProperties);
            } else {
                // TODO: A *tag* here should create an empty scalar, not a null
                if (nodeProperties.anchor != null) {
                    key = createNullScalar(true, nodeProperties);
                    if (tokenBuffer.peek().getType().getCategory() != TokenCategory.PUNCTUATION) {
                        error(tokenBuffer.peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, tokenBuffer.peek().getType());
                    }
                } else {
                    error(token, GenericDiagnosticCode.ERROR, "Unexpected token in key position: " + token.getType());
                    key = new YamlScalar(token, PrimitiveType.ANY, options);
                }
            }
            return key;
        } finally {
            depth--;
            // implicitKeyDepth--;
            debug("<parseKeyNode");
        }
    }

    /// The primary dispatcher for all YAML values.
    ///
    /// This method is responsible for:
    /// 1. Handling anchors (`&`) and aliases (`*`).
    /// 2. Managing block indentation tokens (`INDENT`/`DEDENT`).
    /// 3. Determining the structural type (Map, Sequence, or Scalar) via lookahead.
    private YamlNode parseValue(int parentStartColumn, boolean isFlowStyle, boolean isComplexKey, boolean isSequenceItem, NodeProperties pendingProperties) {
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
            debugProperties("p", pendingProperties);

            if (check(YamlTokenType.ERROR)) {
                error(tokenBuffer.peek(), YamlDiagnosticCode.UNEXPECTED_TOKEN, tokenBuffer.peek().getType());
            }





            int savedIKD0 = implicitKeyDepth;
            implicitKeyDepth = 0;
            parseTrivia();
            implicitKeyDepth = savedIKD0;

            // // // TODO
            // // if (moveParseTrivia2) {
            //     parseBlockComments();
            // // } else {
            // //     if (!check(YamlTokenType.SCALAR) ||
            // //         tokenBuffer.peek().getScalarStyle() != ScalarStyle.FOLDED) {
            // //         parseTrivia();
            // //     }
            // // }






            // Node properties preceeding an initial map key can belong either
            // to the map itself or to the initial key. parseNodeProperties
            // consumes them all and decides what they belong to.




            Pair<NodeProperties,NodeProperties> properties = parseNodePropeties(!isFlowStyle, true, pendingProperties);
            // Pair<NodeProperties,NodeProperties> properties = parseNodePropeties(true, true, pendingProperties);

            NodeProperties collectionProperties = properties.getL();
            NodeProperties nodeProperties = properties.getR();
            YamlNode result = null;





            if (!test_FH7J) {
                int savedIKD1 = implicitKeyDepth;
                implicitKeyDepth = 0;
                parseTrivia();
                implicitKeyDepth = savedIKD1;
            } else {

                // if (check(YamlTokenType.NEWLINE) && checkNext(YamlTokenType.INDENT)) {
                //     tokenBuffer.advance(); // Consume the newline, but leave the indent
                // }

                // if (check(YamlTokenType.NEWLINE)) {
                //     tokenBuffer.advance(); // Consume the newline, but leave the indent
                // }



                // TODO: This breaks it
                // if (check(YamlTokenType.NEWLINE)) {






                // TODO: For test57H4, the tag must belong to the sequence!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                // We must not enter this block

                // Both test57H4 and test_FH7J have a sequence indicator next.
                // Both indicators are in column 1
                // parentColumn is also 1 for both.

                // TODO: We need to know if we're already in the sequence.

                //                   Use isSequenceItem

                // Test: testTagInSequence tests all of this in one go.

                YamlToken sei = lookAheadIgnoringComments(YamlTokenType.SEQUENCE_ENTRY_INDICATOR);
                if (check(YamlTokenType.NEWLINE) && sei != null && isSequenceItem) {
                    int seiCol = sei.getStartColumn();
                    trace("Debug test_FH7J in");
                    if (nodeProperties != null && (nodeProperties.anchor != null || nodeProperties.tag != null)) {
                        trace("Debug test_FH7J has properties");
                        if (nodeProperties.tag != null) {
                            trace("Debug test_FH7J has tag");
                            result = createEmptyScalar(isComplexKey, nodeProperties);
                            nodeProperties.attachTo(result);
                        }
                    }
                    trace("Debug test_FH7J out");
                }




                int savedIKD1 = implicitKeyDepth;
                implicitKeyDepth = 0;
                parseTrivia();
                implicitKeyDepth = savedIKD1;
            }








            debugProperties("c", collectionProperties);
            debugProperties("n", nodeProperties);

            int expectedDedents = 0;
            while (check(YamlTokenType.INDENT)) {
                tokenBuffer.advance();
                trace("indent. re-parsing properties.");

                // If there are node properties (anchor or tag) at this indentation
                // level, merge them with the current properties
                properties = parseNodePropeties(!isFlowStyle, true, collectionProperties, nodeProperties);




                // if (test_FH7J) {
                //     if (check(YamlTokenType.NEWLINE) && checkNext(YamlTokenType.INDENT)) {
                //         tokenBuffer.advance(); // Consume the newline, but leave the indent
                //     }
                // }





                collectionProperties = properties.getL();
                nodeProperties = properties.getR();
                trace("merged properties");
                debugProperties("c", collectionProperties);
                debugProperties("n", nodeProperties);
                expectedDedents++;
            }





            // boolean expectInnerIndent = false;
            if (test_FH7J) {

                // if (check(YamlTokenType.NEWLINE) && checkNext(YamlTokenType.INDENT)) {
                //     tokenBuffer.advance(); // Consume the newline, but leave the indent
                // }
                if (check(YamlTokenType.NEWLINE)) {
                    tokenBuffer.advance(); // Consume the newline, but leave the indent
                }

                if (check(YamlTokenType.INDENT)) {
                    tokenBuffer.advance();
                    // expectInnerIndent = true;
                    expectedDedents++;
                }

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
                result = createNullScalar(isComplexKey, nodeProperties);

            }


            // Map
            else {
                if (true || !isFlowStyle) {
                    if (!isComplexKey &&check(YamlTokenType.MAP_START) && lookAheadFlowMapIsFollowedByColon()) {
                        trace("PV-flowMap-map");
                        return parseMap(true, isComplexKey, collectionProperties, nodeProperties);
                    }
                    else if (!isComplexKey && check(YamlTokenType.SEQUENCE_START) && lookAheadFlowSequenceIsFollowedByColon()) {
                        trace("PV-seq-map");
                        return parseMap(isFlowStyle, isComplexKey, collectionProperties, nodeProperties);
                    }
                    else if (check(YamlTokenType.ALIAS) && tokenBuffer.peekAhead(1).getType() == YamlTokenType.VALUE_INDICATOR) {
                        trace("PV-alias-map");
                        result = parseMap(false, isComplexKey, collectionProperties, nodeProperties);
                    }
                    else if (check(YamlTokenType.SCALAR) && tokenBuffer.peekAhead(1).getType() == YamlTokenType.VALUE_INDICATOR) {
                        trace("PV-scalar-map");
                        result = parseMap(isFlowStyle, isComplexKey, collectionProperties, nodeProperties);
                    }
                    else if (check(YamlTokenType.VALUE_INDICATOR)) {
                        // Map entry with null key
                        trace("PV-valueIndicator-map");
                        result = parseMap(false, isComplexKey, collectionProperties, nodeProperties);
                    }
                    else if (check(YamlTokenType.KEY_INDICATOR)) {
                        trace("PV-keyIndicator-map");
                        result = parseMap(false, false, collectionProperties, nodeProperties);
                    }
                }
            }

            if (result == null) {

                // Other node types

                // Move collection properties into node properties.
                // Beyond this point they can't belong to a collection.
                moveProperties(collectionProperties, nodeProperties, null);

                // TODO: In test_FH7J, parseNodeProperties has consumed the NEWLINE after !!str
                // We kind of need the newline to be left so we can tell if this value is an implicit null
                // or an empty scalar.

                if (check(YamlTokenType.ALIAS)) {
                    trace("PV-in-if-alias1");
                    result = parseAlias(nodeProperties);
                }
                // Flow map
                else if (check(YamlTokenType.MAP_START)) {
                    trace("PV-flow-map passing pendingAnchor " + nodeProperties.anchor);
                    result = parseFlowMap(nodeProperties);
                }
                // Flow sequence
                else if (check(YamlTokenType.SEQUENCE_START)) {
                    trace("PV-flow-seq passing pendingAnchor " + nodeProperties.anchor);
                    result = parseFlowSequence(nodeProperties);
                }
                // Block sequence
                else if (check(YamlTokenType.SEQUENCE_ENTRY_INDICATOR)) {
                    trace("PV-block-seq passing pendingAnchor " + nodeProperties.anchor);
                    result = parseSequence(nodeProperties);
                }
                else if (check(YamlTokenType.SCALAR)) {
                    result = parseScalar(isComplexKey, nodeProperties);
                    trace("PV-after-parseScalar");
                    skipNewline();
                }
                else {
                    result = nodeProperties.isEmpty()
                        ? createNullScalar(isComplexKey, nodeProperties)
                        : createEmptyScalar(isComplexKey, nodeProperties);
                }
            }




            // // TODO
            // if (moveParseTrivia) {
                int savedIKD = implicitKeyDepth;
                implicitKeyDepth = 0;
                parseTrivia();
                implicitKeyDepth = savedIKD;
            // } else {
            //     parseTrivia();
            // }


            // if (expectInnerIndent) {

            // }


            while (expectedDedents > 0) {
                if (check(YamlTokenType.DEDENT)) {
                    trace("Consuming expectedValueDedent");
                    tokenBuffer.advance();
                } else {

                    // TODO
                    // We should really report this error, but we currently arrive here to do a bug.
                    // This only occurs during testBU8La

                    trace(TermUtils.ANSI_MAGENTA + "expectedValueDedent not found" + TermUtils.ANSI_RESET);
                    // error(tokenBuffer.peek(), YamlDiagnosticCode.EXPECTED_DEDENT);
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
    private YamlNode parseMap(boolean isFlowStyle, boolean isComplexKey, NodeProperties collectionProperties, NodeProperties nodeProperties) {
        debug(">parseMap");
        depth++;
        try {
            YamlToken startToken = tokenBuffer.peek();

            if (onMarkerLine && !isFlowStyle) {
                error(startToken, YamlDiagnosticCode.BLOCK_COLLECTION_SAME_LINE_AS_MARKER);
            }

            trace("isFlowStyle="+isFlowStyle);
            trace("isComplexKey="+isComplexKey);

            // If properties were on a previous line, they belong to a collection
            moveProperties(nodeProperties, collectionProperties, startToken);

            debugProperties("c", collectionProperties);
            debugProperties("n", nodeProperties);

            YamlMap map = new YamlMap(startToken, options);
            map.setNodeStyle(isFlowStyle ? NodeStyle.FLOW : NodeStyle.BLOCK);

            // collectionProperties.attachTo(map);
            attachProperties(map, collectionProperties);
            createEvent(map, StreamingEventType.START_OBJECT);

            Set<Object> seenKeys = new HashSet<>();
            int mapColumn = -1;
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
                    tokenBuffer.advance(); // Consume '?'
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
                    mapColumn = markerColumn;
                } else {
                    // TODO: When checking this, we need to use any properties
                    // as the key's start column, if they exist.

                    // if (markerColumn < mapColumn && !isComplexKey) {
                    //     error(markerToken, YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    // }

                    // This is needed for test_EHF6:
                    if (markerColumn != mapColumn && !isComplexKey) {
                        error(markerToken, YamlDiagnosticCode.INCONSISTENT_INDENTATION);
                    }
                }

                YamlNode key;

                // if (hasExplicitKey) {
                if (hasExplicitKey && flowDepth == 0) {
                    parseTrivia();
                    // Explicit keys are always parsed via parseValue in case they are complex


                    // TODO: This should be fixed
                    // TODO: Because we do this, "explicit: entry" in testMixedKeysImplicitExplicitNull
                    // is treated as a new map, when "explicit" should be the key
                    // and "entry" should be the value.


                    key = parseValue(markerColumn, false, true, false, nodeProperties);
                } else {
                    // Standard implicit key

                    if (hasExplicitKey) {
                        key = parseKey(markerColumn, flowDepth > 0, nodeProperties);
                    } else {
                        implicitKeyDepth++;
                        key = parseKey(markerColumn, flowDepth > 0, nodeProperties);
                        implicitKeyDepth--;
                    }
                }




                // // TODO
                // if (moveParseTrivia) {
                    parseTrivia();
                // }




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

                YamlNode value;

                if (check(YamlTokenType.VALUE_INDICATOR)) {
                    consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_MAP_KEY);
                    parseInlineComment(key);

                    trace("before parseValue");
                    if (check(YamlTokenType.NEWLINE) && !hasIndentedValueAfterNewline()) {
                        value = createNullScalar(false, null);
                    }
                    else {
                        parseTrivia();
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




                        // // TODO
                        // if (moveParseTrivia2) {
                            parseTrivia();
                        // }





                        value = parseValue(mapColumn, false, false, false, null);

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

                map.put(new YamlMapEntry(key, value, hasExplicitKey));
                parseTrivia();

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

    /// Parses a block sequence (list) indicated by leading dashes.
    private YamlNode parseSequence(NodeProperties pendingProperties) {
        debug(">parseSequence");
        depth++;
        try {

            if (onMarkerLine) {
                error(tokenBuffer.peek(), YamlDiagnosticCode.BLOCK_COLLECTION_SAME_LINE_AS_MARKER);
            }

            debugProperties("p", pendingProperties);

            boolean hasIndentedAnchor = false;
            if (checkIndented(YamlTokenType.ANCHOR)) {
                debug("PA-hasIndentedAnchor1");
                hasIndentedAnchor = true;
                tokenBuffer.advance();
            }

            YamlToken startToken = tokenBuffer.peek();
            int indicatorColumn = startToken.getStartColumn();

            YamlSequence sequence = new YamlSequence(startToken);
            sequence.setNodeStyle(NodeStyle.BLOCK);
            attachComments(sequence);
            attachProperties(sequence, pendingProperties);
            // if (pendingProperties != null) {
            //     pendingProperties.attachTo(sequence);
            // }
            createEvent(sequence, StreamingEventType.START_ARRAY);
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

                YamlToken nextTokenIsIndicator = lookAheadIgnoringComments(YamlTokenType.SEQUENCE_ENTRY_INDICATOR);

                tokenBuffer.advance(); // Consume the '-'




                // // TODO
                // if (moveParseTrivia2) {
                    parseTrivia();
                // }




                if (nextTokenIsIndicator != null && nextTokenIsIndicator.getStartColumn() == indicatorColumn) {
                    sequence.add(createNullScalar(false, null));
                } else {
                    YamlNode item = parseValue(indicatorColumn, false, false, true, null);
                    sequence.add(item);
                }

                parseTrivia();
            }

            createEvent(tokenBuffer.peek(), StreamingEventType.END_ARRAY);

            return sequence;
        } finally {
            depth--;
            debug("<parseSequence");
        }
    }

    /// Parses a flow sequence like [item1, item2].
    private YamlSequence parseFlowSequence(NodeProperties pendingProperties) {
        debug(">parseFlowSequence");
        depth++;
        flowDepth++;
        try {
            // trace("pendingAnchor="+pendingAnchor);
            YamlToken startToken = consume(YamlTokenType.SEQUENCE_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACKET);
            YamlSequence sequence = new YamlSequence(startToken);
            sequence.setNodeStyle(NodeStyle.FLOW);
            attachComments(sequence);

            attachProperties(sequence, pendingProperties);
            // if (pendingAnchor != null) {
            //     sequence.setAnchor(YamlAnchor.extractAnchorName(pendingAnchor.getToken()));
            // }

            createEvent(sequence, StreamingEventType.START_ARRAY);

            while (!check(YamlTokenType.SEQUENCE_END) && !tokenBuffer.isAtEnd()) {
                parseTrivia();

                sequence.add(parseValue(startToken.getStartColumn(), true, false, true, null));
                parseTrivia();

                if (!match(YamlTokenType.COMMA)) break;
                parseTrivia();
            }

            consume(YamlTokenType.SEQUENCE_END, YamlDiagnosticCode.EXPECTED_CLOSE_BRACKET);
            createEvent(tokenBuffer.peek(), StreamingEventType.END_ARRAY);
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
    private YamlNode parseFlowMap(NodeProperties pendingProperties) {
        debug(">parseFlowMap");
        depth++;
        flowDepth++;
        try {
            debugProperties("p", pendingProperties);
            YamlToken startToken = consume(YamlTokenType.MAP_START, YamlDiagnosticCode.EXPECTED_OPEN_BRACE_FLOW_MAP);
            YamlMap map = new YamlMap(startToken, options);
            map.setNodeStyle(NodeStyle.FLOW);
            attachComments(map);

            attachProperties(map, pendingProperties);
            // if (pendingProperties != null) {
            //     pendingProperties.attachTo(map);
            // }

            createEvent(map, StreamingEventType.START_OBJECT);


            // Clear any whitespace/newlines before checking for an empty map exit
            parseTrivia();

            // Handle empty flow map {}
            if (match(YamlTokenType.MAP_END)) {
                createEvent(tokenBuffer.peek(), StreamingEventType.END_OBJECT);
                return map;
            }

            YamlToken markerToken = tokenBuffer.peek();
            int markerColumn = markerToken.getStartColumn();

            while (!tokenBuffer.isAtEnd()) {
                parseTrivia();

                // 1. Parse Key




                // TODO
                // YamlNode key = parseKey(startToken.getStartColumn(), null);
                // boolean hasExplicitKey = check(YamlTokenType.KEY_INDICATOR);
                YamlNode key;
                // if (hasExplicitKey) {
                //     tokenBuffer.advance(); // Consume '?'
                //     parseTrivia();
                //     // Explicit keys are always parsed via parseValue in case they are complex
                //     key = parseValue(markerColumn, false, true, null);
                // } else {
                    // Standard implicit key
                    key = parseKey(markerColumn, true, null);
                // }





                parseTrivia();

                YamlNode value;
                if (check(YamlTokenType.VALUE_INDICATOR)) {
                    // 2. Consume Value Indicator
                    consume(YamlTokenType.VALUE_INDICATOR, YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP);




                    // // TODO
                    // if (moveParseTrivia2) {
                        // skipNewline();
                        parseTrivia();
                    // }




                    // 3. Parse Value
                    value = parseValue(key.getStartColumn(), true, false, false, null);
                } else {
                    value = createNullScalar(false, null);
                }

                // 4. Store Entry
                map.put(new YamlMapEntry(key, value));

                parseTrivia();

                // 5. Check for continuation or end
                if (match(YamlTokenType.COMMA)) {
                    // Allow trailing commas by checking for end after comma
                    if (null != lookAheadIgnoringComments(YamlTokenType.MAP_END)) {
                        parseTrivia();
                    }
                    if (match(YamlTokenType.MAP_END)) {
                        break;
                    }
                    continue;
                } else if (match(YamlTokenType.MAP_END)) {
                    break;
                } else if (null != lookAheadIgnoringComments(YamlTokenType.MAP_END)) {
                    parseTrivia();
                    break;
                } else {
                    error(tokenBuffer.peek(), YamlDiagnosticCode.EXPECTED_COLON_FLOW_MAP);
                }
            }
            createEvent(tokenBuffer.peek(), StreamingEventType.END_OBJECT);
            return map;
        } finally {
            depth--;
            flowDepth--;
            debug("<parseFlowMap");
        }
    }

    private YamlScalar parseScalar(boolean isKey, NodeProperties pendingProperties) {
        debug(">parseScalar");
        depth++;
        try {
            debugProperties("p", pendingProperties);
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
                attachComments(scalar);

                // if (check(YamlTokenType.COMMENT) && tokenBuffer.peek().getStartLine() == token.getStartLine()) {
                //     scalar.addComment(parseComment(CommentStyle.INLINE));
                // }
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
                // if (check(YamlTokenType.COMMENT) && tokenBuffer.peek().getStartLine() == token.getStartLine()) {
                //     scalar.addComment(parseComment(CommentStyle.INLINE));
                // }
                parseInlineComment(scalar);
            }

            pendingProperties.attachTo(scalar);

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

    private YamlNode parseAlias(NodeProperties pendingProperties) {
        debug(">parseAlias");
        depth++;
        try {
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
            createEvent(alias, StreamingEventType.ALIAS);

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

                // if (token.getContent().contains("Footer")) {
                //     debug("Debug");
                // }

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

        // if (text.contains("Footer")) {
        //     debug("Debug");
        // }

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



                //lookAheadIgnoringIndentsAndComments
                // boolean moreProperties = lookAheadIgnoringComments(YamlTokenType.ANCHOR) != null || lookAheadIgnoringComments(YamlTokenType.TAG) != null;
                boolean moreProperties = check(YamlTokenType.ANCHOR) ||
                                         check(YamlTokenType.TAG) ||
                                         lookAheadIgnoringIndentsAndComments(YamlTokenType.ANCHOR) ||
                                         lookAheadIgnoringIndentsAndComments(YamlTokenType.TAG);
                moreProperties |= (!test_FH7J);
                // if (allowMultipleLines && (check(YamlTokenType.NEWLINE) || check(YamlTokenType.COMMENT))) {
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
                    tag = new YamlTag(tagToken);
                    properties.add(tag);
                    nTags++;
                    continue;
                } else if (check(YamlTokenType.TAG)) {
                    trace("tag");
                    YamlToken tagToken = tokenBuffer.advance();
                    tag = new YamlTag(tagToken);
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
                // if (multiLine && startColumn > 1) {
                    trace("multi-line with startColumn " + startColumn);

                    // Properties are on multiple lines. This is only valid if there
                    // are proprties for both a map and its first key,
                    // TODO: OR
                    // If it's on a document-level scalar and there is at most one of each type.

                    for (YamlNodeProperty property : properties) {
                        // YamlTokenType tokenType = token.getType();
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
                    // trace("single line or document level");
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

    private boolean lookAheadIgnoringIndentsAndComments(YamlTokenType targetType) {
        int ahead = 1;
        while (!tokenBuffer.isAtEnd(ahead)) {
            YamlToken token = tokenBuffer.peekAhead(ahead);
            YamlTokenType type = token.getType();
            if (type == targetType) return true;
            if (type == YamlTokenType.NEWLINE || type == YamlTokenType.INDENT || type == YamlTokenType.COMMENT) {
                ahead++;
                continue;
            }
            break;
        }
        return false;
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
            isKey ? StreamingEventType.FIELD_NAME
                  : StreamingEventType.VALUE_SCALAR
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
            isKey ? StreamingEventType.FIELD_NAME
                  : StreamingEventType.VALUE_SCALAR
        );
        return scalar;
    }

    //
    // Event Helpers
    //

    protected void createEvent(YamlToken token, StreamingEventType type) {
        createEvent(token, type, token.getContent());
    }

    protected void createEvent(YamlToken token, StreamingEventType type, String content) {
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

        event.setNode(node);

        handleEvent(event);
    }

    //
    // Anchor, Tag, and Comment Helpers
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

        if (isReportingTrace()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Attaching tag ");
            sb.append(TermUtils.ANSI_WHITE);
            sb.append(tag.getContent());
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

        node.setTag(tag.getContent());
        node.setResolvedTag(resolveTag(tag.getContent()));
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

    private void moveProperties(NodeProperties nodeProperties, NodeProperties collectionProperties, YamlToken startToken) {
        trace("moveProperties");
        if (nodeProperties != null && nodeProperties.startLine > 0) {
            if (nodeProperties.anchor != null && (startToken == null || nodeProperties.anchor.getStartLine() < startToken.getStartLine())) {
                if (collectionProperties != null && collectionProperties.anchor != null) {
                    error(nodeProperties.anchor.getToken(), YamlDiagnosticCode.UNEXPECTED_TOKEN, nodeProperties.anchor.getToken().getType());
                }
                collectionProperties.anchor = nodeProperties.anchor;
                nodeProperties.anchor = null;
            }
            if (nodeProperties.tag != null && (startToken == null || nodeProperties.tag.getStartLine() < startToken.getStartLine())) {
                if (collectionProperties != null && collectionProperties.tag != null) {
                    error(nodeProperties.tag.getToken(), YamlDiagnosticCode.UNEXPECTED_TOKEN, nodeProperties.tag.getToken().getType());
                }
                collectionProperties.tag = nodeProperties.tag;
                nodeProperties.tag = null;
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
                properties.tag.getContent() +
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
        createEvent(token, StreamingEventType.ERROR, formatMessage(code, details));
        errorEncountered.set(true);

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

    // /// Log the current method name and upcoming tokens
    // protected void debug(String message, Object... details) {
    //     if (!reporter.reportsDebug()) return;
    //     report(Level.DEBUG, message, details);
    // }

    // /// Log the current method name and upcoming tokens
    // protected void trace(String message, Object... details) {
    //     if (!reporter.reportsTrace()) return;
    //     report(Level.TRACE, message, details);
    // }

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

        // YamlToken anchorToken;
        // YamlToken tagToken;

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
