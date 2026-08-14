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
import io.github.qishr.cascara.lang.yaml.internal.AbstractYamlParser;
import io.github.qishr.cascara.lang.yaml.streaming.YamlStreamingEvent;

import java.io.InputStream;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class YamlPullParser extends AbstractYamlParser<YamlPullParser> implements PullParser {
    private final InputStream input;
    private Thread parserThread;
    private BlockingDeque<YamlStreamingEvent> events;
    private AtomicBoolean streamEnded = new AtomicBoolean();
    YamlStreamingEvent nextEvent;

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
        if (nextEvent != null) {
            return true;
        }

        if (events.isEmpty() && (errorEncountered.get() || streamEnded.get())) {
            return false;
        }

        if (!events.isEmpty()) {
            nextEvent = events.poll();
            if (nextEvent.getType() == StreamingEventType.ERROR) {
                errorEncountered.set(true);
                nextEvent = null;
                return false;
            }
            trace("NEXT EVENT: " + nextEvent.getType() + " [a="+nextEvent.getAnchor()+"]");
            return true;
        }

		try {

            trace("hasNext: Waiting for event");
			nextEvent = events.takeFirst();
            if (nextEvent == null) {
                trace("EVENT: null");
            } else {
                trace("EVENT: " + nextEvent.getType() + " [a="+nextEvent.getAnchor()+"]");
            }
            // trace("hasNext: Got event");

		} catch (InterruptedException e) {
            trace("hasNext: Got interrupt");
            nextEvent = null;
		}

        if (nextEvent != null && nextEvent.getType() == StreamingEventType.ERROR) {
            nextEvent = null;
        }
        return nextEvent != null;
    }

    @Override
    public StreamingEvent next() {
        if (!hasNext() || nextEvent == null) {
            // We should probably throw an Exception (no more events)
            return null;
        }
        StreamingEvent event = nextEvent;
        nextEvent = null;
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
        trace("queueEvents");
        preParseStateInit();

        setContinueAfterError(false);

        streamEnded.set(false);

        // TODO: Make this capacity higher. Benchmark various values.
        events = new LinkedBlockingDeque<>(2);

        tokenBuffer.open(input);
        parserThread = new Thread(() -> {
            try {
                parseInternal();
            } catch (Exception e) {
                errorEncountered.set(true);
                trace("parseInternal failed: " + e.getMessage());
                e.printStackTrace();
            }
            trace("parserThread: ended");
            streamEnded.set(true);
        });
        parserThread.setName("yaml-parser");
        parserThread.start();
    }

    @Override
    protected void handleEvent(YamlStreamingEvent event) {
        try {
            events.putLast(event);
        } catch (InterruptedException e) {
            debug("events.putLast failed: " + e.getMessage());
        }
    }
}