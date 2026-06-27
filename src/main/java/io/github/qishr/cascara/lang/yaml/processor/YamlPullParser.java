package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.PullParser;
import io.github.qishr.cascara.common.lang.streaming.Event;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;

import java.io.InputStream;
import java.util.NoSuchElementException;

public class YamlPullParser extends AbstractYamlProcessor<YamlPullParser> implements PullParser {
    private YamlStreamEngine engine;
    private final InputStream input;

    public YamlPullParser(InputStream input) {
        this.input = input;
    }

    @Override protected YamlPullParser self() { return this; }

    private void ensureEngine() {
        if (engine == null) {
            this.engine = new YamlStreamEngine(input, getReporter(), getOptions().isIncludeComments());
        }
    }

    @Override
    public boolean hasNext() {
        try {
            ensureEngine();
            return engine.hashNextEvent();
        } catch (ParserException e) {
            throw new RuntimeException("Error scanning for next streaming event", e);
        }
    }

    @Override
    public Event next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more YAML streaming events available.");
        }
        return engine.nextEvent(); // Throws ParserException, which is a RuntimeException
    }

    @Override
    public void close() throws Exception {
        if (input != null) {
            input.close();
        }
    }
}