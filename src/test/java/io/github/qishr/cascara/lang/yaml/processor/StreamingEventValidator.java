package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import io.github.qishr.cascara.common.lang.streaming.StreamingEvent;
import io.github.qishr.cascara.common.lang.streaming.StreamingEventType;

public class StreamingEventValidator {
    boolean verbose = false;
    Deque<StreamingEvent> events;
    Deque<Expectation> expectations;

    public StreamingEventValidator(List<StreamingEvent> events) {
        this.events = new ArrayDeque<>();
        this.events.addAll(events);
        expectations = new ArrayDeque<>();
    }
    public StreamingEventValidator expect(StreamingEventType type) {
        expectations.add(new Expectation(type, false, null));
        return this;
    }
    public StreamingEventValidator expect(StreamingEventType type, String value) {
        expectations.add(new Expectation(type, true, value));
        return this;

    }
    public StreamingEventValidator verbose() {
        verbose = true;
        return this;
    }
    public void validate() {
        while (!expectations.isEmpty()) {
            Expectation ex = expectations.poll();
            StreamingEvent ev = events.poll();
            if (verbose) {
                System.out.print("Expected: " + ex.type);
                System.out.println(", Got: " + ev.getType());
            }
            assertNotNull(ev, "Missing event");
            assertEquals(ex.type, ev.getType(), "Wrong event type");
            if (ex.hasValue) {
                assertEquals(ex.value, ev.getContent(), "Wrong content");
            }
        }
    }
    public static class Expectation {
        public StreamingEventType type;
        public boolean hasValue;
        public String value;
        public Expectation(StreamingEventType type, boolean hasValue, String value) {
            this.type = type;
            this.hasValue = hasValue;
            this.value = value;
        }
    }
}
