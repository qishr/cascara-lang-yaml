package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.lang.streaming.Event;
import io.github.qishr.cascara.common.lang.streaming.EventType;

public class StreamingEvent implements Event {
	private final int lineNumber;
	private final int columnNumber;
	private final String text;
	private final EventType type;

    public StreamingEvent(int lineNumber, int columnNumber, EventType type, String text) {
		this.lineNumber = lineNumber;
		this.columnNumber = columnNumber;
		this.type = type;
		this.text = text;
    }

	@Override
	public EventType getType() {
		return type;
	}

	@Override
	public String getText() {
		return text;
	}

	@Override
	public long getLineNumber() {
		return lineNumber;
	}

	@Override
	public long getColumnNumber() {
		return columnNumber;
	}
}
