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

import io.github.qishr.cascara.common.lang.processor.PullParser;
import io.github.qishr.cascara.common.lang.streaming.StreamingEvent;
import io.github.qishr.cascara.common.lang.streaming.StreamingEventType;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.internal.AbstractYamlParser;
import io.github.qishr.cascara.lang.yaml.streaming.YamlStreamingEvent;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

import java.io.InputStream;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class YamlPullParser extends AbstractYamlParser<YamlPullParser> implements PullParser {
    private final InputStream input;
    private Thread parserThread;
    private BlockingDeque<YamlStreamingEvent> events;
    private AtomicBoolean streamEnded = new AtomicBoolean();

    /// Default constructor for SPI.
    public YamlPullParser() {
        input = null;
    }

    public YamlPullParser(InputStream input) {
        this.input = input;
        queueEvents(input);
    }

    @Override protected YamlPullParser self() { return this; }

    @Override
    public boolean hasNext() {
        return !streamEnded.get() || !events.isEmpty();
    }

    @Override
    public StreamingEvent next() {
        debug("nextEvent...");
        if (!hasNext()) {
            debug("nextEvent - stream ended");
        }
        YamlStreamingEvent event;
		try {
			event = events.takeFirst();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
            return null;
		}
        debug("nextEvent: " + event.getType());
        return event;
    }

    @Override
    public void close() throws Exception {
        if (input != null) {
            input.close();
        }
    }

    //
    //
    //

    private void queueEvents(InputStream input) {
        debug("queueEvents");
        preParseStateInit();
        // queueEvents = true;

        streamEnded.set(false);

        // TODO: Make this capacity higher. Benchmark various values.
        events = new LinkedBlockingDeque<>(2);

        tokenBuffer.open(input);
        parserThread = new Thread(() -> {
            parseInternal();
            streamEnded.set(true);
        });
        parserThread.start();
    }

    @Override
    protected void createEvent(YamlToken token, StreamingEventType type, String content) {
        queueEvent(token.getStartLine(), token.getStartColumn(), type, content);
    }

    @Override
    protected void createEvent(YamlNode node, StreamingEventType type, String content) {
        queueEvent(node.getStartLine(), node.getStartColumn(), type, content);
    }

    private void queueEvent(int line, int column, StreamingEventType type, String content) {
        YamlStreamingEvent event = new YamlStreamingEvent(
            line, column,
            type,
            content
        );

        try {
            events.putLast(event);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}