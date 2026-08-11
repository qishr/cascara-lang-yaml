package io.github.qishr.cascara.lang.yaml.internal;

import java.io.InputStream;
import java.io.Reader;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

public interface TokenBuffer {

    void setReporter(Reporter reporter);

    void setTokenizer(YamlTokenizer tokenizer);

    YamlTokenizer getTokenizer();

    // The highest number this is called with is 4. It's usually called with 1.
    /// Ensures that the lookahead buffer has retrieved tokens up to the requested
    /// lookahead index offset.
    void ensureBuffered(int ahead);

    boolean isEmpty();

    // This is only used by the parser to check it hasn't got stuck on a token
    int offset();

    boolean isAtEnd(int ahead);

    boolean isAtEnd();

    YamlToken peekAhead(int ahead);

    YamlToken peek();

    int size();

    YamlToken previous();

    YamlToken advance();

    boolean isTrailingStreamComment();

    // Get next 4 tokens as a string.
    String upcomingTokens();



    void open(String text);
    void open(byte[] data);
    void open(Reader reader);
    void open(InputStream is);
}