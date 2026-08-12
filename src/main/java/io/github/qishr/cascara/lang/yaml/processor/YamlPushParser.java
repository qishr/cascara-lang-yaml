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


package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.PushParser;
import io.github.qishr.cascara.common.lang.streaming.StreamingEventType;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.internal.AbstractYamlParser;
import io.github.qishr.cascara.lang.yaml.streaming.YamlStreamingEvent;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.common.lang.streaming.StreamHandler;

import java.io.InputStream;

public class YamlPushParser extends AbstractYamlParser<YamlPushParser> implements PushParser {

    // private YamlStreamEngine engine = new YamlStreamEngine();
    StreamHandler handler;

    public YamlPushParser() {}

    @Override protected YamlPushParser self() { return this; }

    // public YamlTokenizer getTokenizer() {
    //     return engine.getTokenizer();
    // }

    @Override
    public void parse(InputStream input, StreamHandler handler) throws ParserException {
        // engine.setOptions(options);
        // engine.setReporter(reporter);
        // engine.setStream(input);

        this.handler = handler;
        pushEvents(input);

        // while (engine.hasNextEvent()) {
        //     StreamingEvent event = engine.nextEvent();
        //     if (event != null) {
        //         handler.onEvent(event);
        //     }
        // }
    }

    //
    //
    //

    protected void pushEvents(InputStream input) {
        preParseStateInit();
        tokenBuffer.open(input);
        createEvent(tokenBuffer.peek(), StreamingEventType.START_STREAM, null);

        // TODO: DOC, etc

        parseInternal();

        createEvent(tokenBuffer.peek(), StreamingEventType.END_STREAM, null);
    }

    @Override
    protected void createEvent(YamlToken token, StreamingEventType type, String content) {
        createEvent(token.getStartLine(), token.getStartColumn(), type, content);
    }

    @Override
    protected void createEvent(YamlNode node, StreamingEventType type, String content) {
        createEvent(node.getStartLine(), node.getStartColumn(), type, content);
    }

    private void createEvent(int line, int column, StreamingEventType type, String content) {

        YamlStreamingEvent event = new YamlStreamingEvent(
            line, column,
            type,
            content
        );

        handler.onEvent(event);

    }
}