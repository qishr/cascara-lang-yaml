package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.PushParser;
import io.github.qishr.cascara.common.lang.streaming.Event;
import io.github.qishr.cascara.common.lang.streaming.StreamHandler;

import java.io.InputStream;

public class YamlPushParser extends AbstractYamlProcessor<YamlPushParser> implements PushParser {

    public YamlPushParser() {}

    @Override protected YamlPushParser self() { return this; }

    @Override
    public void parse(InputStream input, StreamHandler handler) throws ParserException {
        YamlStreamEngine executionEngine = new YamlStreamEngine(input, getReporter(), getOptions().isIncludeComments());

        while (executionEngine.hashNextEvent()) {
            Event event = executionEngine.nextEvent();
            if (event != null) {
                handler.onEvent(event);
            }
        }
    }
}