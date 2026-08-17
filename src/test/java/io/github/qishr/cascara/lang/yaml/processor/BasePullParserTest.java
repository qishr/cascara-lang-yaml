package io.github.qishr.cascara.lang.yaml.processor;

import java.io.InputStream;

import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

public class BasePullParserTest extends BaseTest {

    protected YamlOptions parserOptions = new YamlOptions()
        .setPreloadTokenBuffer(true);

    protected YamlPullParser newParser(InputStream input) {
        YamlPullParser parser = new YamlPullParser(input);
        parser.setOptions(parserOptions);
        parser.setReporter(parserReporter);
        parser.getTokenizer().setReporter(tokenizerReporter);
        return parser;
    }
}
