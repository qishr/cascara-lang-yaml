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

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.common.lang.processor.PullParser;
import io.github.qishr.cascara.common.lang.streaming.StreamingEvent;
import io.github.qishr.cascara.lang.yaml.internal.YamlStreamEngine;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

import java.io.InputStream;
import java.util.NoSuchElementException;

public class YamlPullParser extends AbstractYamlProcessor<YamlPullParser> implements PullParser {
    private YamlStreamEngine engine = new YamlStreamEngine();
    private final InputStream input;

    /// Default constructor for SPI.
    public YamlPullParser() {
        input = null;
    }

    public YamlPullParser(InputStream input) {
        this.input = input;
        engine.setStream(input);
    }

    @Override protected YamlPullParser self() { return this; }

    public YamlTokenizer getTokenizer() {
        return engine.getTokenizer();
    }

    public YamlPullParser setOptions(YamlOptions options) {
        super.setOptions(options);
        engine.setOptions(options);
        return this;
    }

    @Override
    public YamlPullParser setReporter(Reporter reporter) {
        super.setReporter(reporter);
        engine.setReporter(reporter);
        return this;
    }

    // private void ensureEngine() {
    //     if (engine == null) {
    //         engine = new YamlStreamEngine();
    //         engine.setOptions(options);
    //         engine.setReporter(reporter);
    //         engine.setStream(input);
    //     }
    // }

    @Override
    public boolean hasNext() {
        try {
            // ensureEngine();
            return engine.hasNextEvent();
        } catch (ParserException e) {
            throw new RuntimeException("Error scanning for next streaming event", e);
        }
    }

    @Override
    public StreamingEvent next() {
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