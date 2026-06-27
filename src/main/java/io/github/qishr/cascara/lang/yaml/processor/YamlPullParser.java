package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.PullParser;
import io.github.qishr.cascara.common.lang.streaming.Event;
import io.github.qishr.cascara.lang.yaml.YamlOptions;

import java.io.InputStream;

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
    public boolean hasNext() throws ParserException {
        ensureEngine();
        return engine.hashNextEvent();
    }

    @Override
    public Event nextEvent() throws ParserException {
        ensureEngine();
        return engine.nextEvent();
    }

    // @Override
    // public boolean hasNext() throws ParserException {
    //     return engine.hashNextEvent();
    // }

    // @Override
    // public Event nextEvent() throws ParserException {
    //     return engine.nextEvent();
    // }

    @Override
    public void close() throws Exception {
        if (input != null) {
            input.close();
        }
    }
}