package io.github.qishr.cascara.lang.yaml.processor;

import java.io.InputStream;

import io.github.qishr.cascara.lang.yaml.util.YamlConfig;

public class StreamingPullParserTestBase extends TestBase {

    protected YamlConfig parserOptions = new YamlConfig()
        .setPreloadTokenBuffer(true);

    protected YamlPullParser newParser(InputStream input) {
        YamlPullParser parser = new YamlPullParser(input);
        parser.setConfig(parserOptions);
        parser.setReporter(parserReporter);
        parser.getTokenizer().setReporter(tokenizerReporter);
        return parser;
    }
}
