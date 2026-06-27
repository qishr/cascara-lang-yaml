package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.PullParser;
import io.github.qishr.cascara.common.lang.streaming.Event;

import java.io.InputStream;

public class YamlPullParser extends AbstractYamlProcessor<YamlPullParser> implements PullParser {
    private final YamlStreamEngine engine;
    private final InputStream input;

    public YamlPullParser(InputStream input) {
        this.input = input;
        this.engine = new YamlStreamEngine(input);
    }

    @Override protected YamlPullParser self() { return this; }

    @Override
    public boolean hasNext() throws ParserException {
        return engine.hashNextEvent();
    }

    @Override
    public Event nextEvent() throws ParserException {
        return engine.nextEvent();
    }

    @Override
    public void close() throws Exception {
        if (input != null) {
            input.close();
        }
    }
}