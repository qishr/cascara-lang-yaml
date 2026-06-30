package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.lang.streaming.Event;
import io.github.qishr.cascara.common.lang.streaming.EventType;

public class StreamingEvent implements Event {
	private final int lineNumber;
	private final int columnNumber;
	private final String content;
	private final EventType type;

    public StreamingEvent(int lineNumber, int columnNumber, EventType type, String content) {
		this.lineNumber = lineNumber;
		this.columnNumber = columnNumber;
		this.type = type;
		this.content = content != null ? content : "";
    }

	@Override
	public EventType getType() {
		return type;
	}

	@Override
	public String getContent() {
		return content;
	}

	@Override
	public long getLineNumber() {
		return lineNumber;
	}

	@Override
	public long getColumnNumber() {
		return columnNumber;
	}

	@Override
    public String toString() {
        return String.format("[%d:%d] %s -> %s", lineNumber, columnNumber, type, content.isEmpty() ? "EMPTY" : content);
    }
}
