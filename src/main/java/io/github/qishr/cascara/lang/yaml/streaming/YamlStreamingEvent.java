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


package io.github.qishr.cascara.lang.yaml.streaming;

import io.github.qishr.cascara.common.lang.streaming.StreamingEvent;
import io.github.qishr.cascara.common.lang.streaming.StreamingEventType;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.util.NodeStyle;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;

public class YamlStreamingEvent implements StreamingEvent<YamlStreamingEventType> {
	private final int lineNumber;
	private final int columnNumber;
	private final YamlStreamingEventType type;
	private final NodeStyle nodeStyle;
	private final ScalarStyle scalarStyle;
	private final String tag;
	private final String resolvedTag;
	private final String anchor;
	private final String lexeme;
	private final String content;

    public YamlStreamingEvent(int lineNumber, int columnNumber, YamlStreamingEventType type, NodeStyle nodeStyle, ScalarStyle scalarStyle, String tag, String resolvedTag, String anchor, String lexeme, String content) {
		this.lineNumber = lineNumber;
		this.columnNumber = columnNumber;
		this.type = type;
		this.nodeStyle = nodeStyle;
		this.scalarStyle = scalarStyle;
		this.tag = tag;
		this.resolvedTag = resolvedTag;
		this.anchor = anchor;
		this.lexeme = lexeme != null ? lexeme : "";
		this.content = content;
    }

	@Override
	public YamlStreamingEventType getType() {
		return type;
	}

	public NodeStyle getNodeStyle() {
		return nodeStyle;
	}

	public ScalarStyle getScalarStyle() {
		return scalarStyle;
	}

	@Override
	public String getContent() {
		return content;
	}

	public String getLexeme() {
		return lexeme;
	}

	public String getTag() {
		return tag;
	}

	public String getResolvedTag() {
		return resolvedTag;
	}

	public String getAnchor() {
		return anchor;
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
