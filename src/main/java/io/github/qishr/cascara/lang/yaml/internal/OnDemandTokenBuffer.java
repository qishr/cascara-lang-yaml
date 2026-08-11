package io.github.qishr.cascara.lang.yaml.internal;

import java.io.InputStream;
import java.io.Reader;

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


public class OnDemandTokenBuffer implements TokenBuffer {

    private static final int MAX_DEBUG_STRING_LENGTH = 20;

    private final int capacity;
    private final YamlToken[] ring;
    private int start = 0;     // logical index 0
    private int count = 0;     // number of valid tokens

    private YamlTokenizer tokenizer = new YamlTokenizer();
    private Reporter reporter = new NoOpReporter();

    public OnDemandTokenBuffer(int capacity) {
        this.capacity = Math.max(8, capacity);
        this.ring = new YamlToken[this.capacity];
    }

    @Override
    public void setReporter(Reporter reporter) {
        this.reporter = reporter == null ? new NoOpReporter() : reporter;
    }

    @Override
    public void setTokenizer(YamlTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public YamlTokenizer getTokenizer() {
        return tokenizer;
    }

    @Override
    public void open(String text) {
        tokenizer.open(text);
        start = 0;
        count = 0;
    }

    @Override
    public void open(byte[] data) {
        tokenizer.open(new String(data));
        start = 0;
        count = 0;
    }

    @Override
    public void open(Reader reader) {
        tokenizer.open(reader);
        start = 0;
        count = 0;
    }

    @Override
    public void open(InputStream is) {
        tokenizer.open(is);
        start = 0;
        count = 0;
    }

    //
    // Core circular buffer logic
    //

    private int physicalIndex(int logicalIndex) {
        return (start + logicalIndex) % capacity;
    }

    @Override
    public void ensureBuffered(int ahead) {
        int needed = ahead + 1;
        while (count < needed) {
            YamlToken next = tokenizer.nextToken();
            if (next == null) break;

            int insertIndex = physicalIndex(count);
            ring[insertIndex] = next;

            if (count < capacity) {
                count++;
            } else {
                start = (start + 1) % capacity;
            }

            if (next.getType() == YamlTokenType.EOF ||
                next.getType() == YamlTokenType.STREAM_END) {
                break;
            }
        }
    }

    @Override
    public boolean isEmpty() {
        ensureBuffered(0);
        return count == 0;
    }

    @Override
    public int offset() {
        return 0;
    }

    @Override
    public boolean isAtEnd(int ahead) {
        ensureBuffered(ahead);
        if (ahead >= count) return true;
        YamlToken t = ring[physicalIndex(ahead)];
        return t.getType() == YamlTokenType.EOF ||
               t.getType() == YamlTokenType.STREAM_END;
    }

    @Override
    public boolean isAtEnd() {
        return isAtEnd(0);
    }

    @Override
    public YamlToken peekAhead(int ahead) {
        ensureBuffered(ahead);
        if (ahead >= count) {
            return count == 0 ? null : ring[physicalIndex(count - 1)];
        }
        return ring[physicalIndex(ahead)];
    }

    @Override
    public YamlToken peek() {
        ensureBuffered(0);
        return ring[physicalIndex(0)];
    }

    @Override
    public int size() {
        return count;
    }

    @Override
    public YamlToken previous() {
        // previous token is at logical index -1
        if (count == capacity) {
            // full ring: previous is the element just before start
            return ring[(start + capacity - 1) % capacity];
        }
        if (start == 0) return null;
        return ring[start - 1];
    }

    @Override
    public YamlToken advance() {
        trace("> advance");

        ensureBuffered(1);

        if (isAtEnd()) {
            return peek(); // stay on EOF
        }

        // Save the current token BEFORE advancing
        YamlToken consumed = ring[start];

        // Advance logical window
        start = (start + 1) % capacity;
        count--;

        return consumed;
    }

    @Override
    public boolean isTrailingStreamComment() {
        return false;
    }

    //
    // Diagnostics
    //

    private void warn(YamlToken token, DiagnosticCode code, Object... details) {
        reporter.warnAt(token, code, details);
    }

    private void error(YamlToken token, DiagnosticCode code, Object... details) {
        if (token instanceof YamlErrorToken err) {
            code = err.getCode();
            details = err.getDetails();
        }
        reporter.errorAt(token, code, details);
        if (!reporter.collectsProblems()) {
            throw new YamlParserException(token, code, details);
        }
    }

    private void trace(String message, Object... details) {
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.TRACE)) return;
        report(message, details);
    }

    private void debug(String message, Object... details) {
        if (reporter == null ||
            reporter.isSilent() ||
            !reporter.getLevel().includes(Level.DEBUG)) return;
        report(message, details);
    }

    private void report(String message, Object... details) {
        ensureBuffered(0);
        if (count == 0) return;

        YamlToken tok = ring[physicalIndex(0)];

        reporter.debug(
            "L%3d C%3d I%3d %s: %s",
            tok.getStartLine(),
            tok.getStartColumn(),
            0,
            message,
            upcomingTokens()
        );
    }

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLUE  = "\u001B[34m";
    private static final String ANSI_YELLOW= "\u001B[33m";
    private static final String ANSI_WHITE = "\u001B[37m";

    @Override
    public String upcomingTokens() {
        ensureBuffered(4);
        StringBuilder sb = new StringBuilder();

        int limit = Math.min(count, 4);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(", ");

            YamlToken t = ring[physicalIndex(i)];
            sb.append(ANSI_BLUE).append(t.getType()).append(ANSI_RESET);

            if (t.getType() == YamlTokenType.SCALAR ||
                t.getType() == YamlTokenType.ANCHOR ||
                t.getType() == YamlTokenType.ALIAS ||
                t.getType() == YamlTokenType.TAG ||
                t.getType() == YamlTokenType.DIRECTIVE) {

                String lex = StringUtils.debugString(t.getContent());
                sb.append("(").append(ANSI_WHITE);

                if (lex.length() <= MAX_DEBUG_STRING_LENGTH) {
                    sb.append(lex);
                } else {
                    sb.append(lex.substring(0, MAX_DEBUG_STRING_LENGTH - 1))
                      .append(StringUtils.ELLIPSIS);
                }

                sb.append(ANSI_RESET).append(")");
            }
        }

        if (count > limit) sb.append(", …");
        return sb.toString();
    }
}
