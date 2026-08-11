package io.github.qishr.cascara.lang.yaml.internal;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.exception.YamlParserException;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.token.YamlErrorToken;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

public class TokenBuffer {
    private static final int MAX_DEBUG_STRING_LENGTH = 20;

    private final List<YamlToken> tokenBuffer = new ArrayList<>(256);
    private int lastNewlineOrComment;
    private YamlTokenizer tokenizer;
    private Reporter reporter = new NoOpReporter();

    private int current = 0;
    // private int depth = 0;

    public TokenBuffer() {

    }

    public void setReporter(Reporter reporter) {
        this.reporter = reporter == null ? new NoOpReporter() : reporter;
    }

    public void setTokenizer(YamlTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public YamlTokenizer getTokenizer() {
        return tokenizer;
    }

    //
    //
    //

    public void preload(YamlTokenizer tokenizer) {
        this.tokenizer = tokenizer;
        YamlToken next;
        int idx = 0;
        while ((next = tokenizer.nextToken()) != null) {
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

    public void preload(List<YamlToken> tokens) {
        tokenBuffer.addAll(tokens);
    }

    public void add(YamlToken token) {
        tokenBuffer.add(token);
    }


    //
    //
    //

    // The highest number this is called with is 4. It's usually called with 1.
    /// Ensures that the lookahead buffer has retrieved tokens up to the requested lookahead index offset.
    public void ensureBuffered(int ahead) {
        if (tokenizer == null) return; // Running in fixed List fallback mode

        int targetIndex = current + ahead;
        while (tokenBuffer.size() <= targetIndex) {
            YamlToken next = tokenizer.nextToken();
            if (next == null) break;
            tokenBuffer.add(next);
            if (next.getType() == YamlTokenType.EOF || next.getType() == YamlTokenType.STREAM_END) {
                break;
            }
        }
    }

    public List<YamlToken> getTokens() {
        return tokenBuffer;
    }

    public boolean isEmpty() {
        return tokenBuffer.isEmpty();
    }

    //
    //
    //

    // This is only used by the parser to check it hasn't got stuck on a token
    public int offset() {
        return current;
    }

    public boolean isAtEnd(int ahead) {
        if (current + ahead >= tokenBuffer.size()) return true;
        YamlTokenType type = tokenBuffer.get(current + ahead).getType();
        return type == YamlTokenType.EOF || type == YamlTokenType.STREAM_END;
    }

    public boolean isAtEnd() {
        return isAtEnd(0);
    }


    //

    public YamlToken peekAhead(int ahead) {
        int targetIndex = current + ahead;
        if (targetIndex >= tokenBuffer.size()) {
            return tokenBuffer.isEmpty() ? null : tokenBuffer.get(tokenBuffer.size() - 1);
        }
        return tokenBuffer.get(targetIndex);
    }

    public YamlToken peek() {
        return tokenBuffer.get(current);
    }

    //


    public int size() {
        return tokenBuffer.size();
    }

    public YamlToken previous() {
        return tokenBuffer.get(current - 1);
    }

    public YamlToken advance() {
        trace("advance");
        if (!isAtEnd()) current++;
        return previous();
    }

    //

    public boolean isTrailingStreamComment() {
        return current >= lastNewlineOrComment;
    }

    //
    //
    //

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
        ensureBuffered(0);
        if (current >= tokenBuffer.size()) return;

        // Create indentation based on recursion depth
        // String indent = "  ".repeat(Math.max(0, depth));
        String indent = "";

        char first = message.charAt(0);
        String output;

        if (first == '>' || first == '<') {
            output = first + ANSI_YELLOW + message.substring(1) + ANSI_RESET;
        } else {
            output = message;
        }

        reporter.debug("L%3d C%3d I%3d %s%s: %s",
            tokenBuffer.get(current).getStartLine(),
            tokenBuffer.get(current).getStartColumn(),
            current,
            indent,
            output,
            upcomingTokens());
    }

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_WHITE = "\u001B[37m";

    // Get next 4 tokens as a string.
    public String upcomingTokens() {
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
                String lexeme = StringUtils.debugString(token.getContent());
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
