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


package io.github.qishr.cascara.lang.yaml.token;

import io.github.qishr.cascara.common.lang.token.Token;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;

public class YamlToken implements Token {
    private int line;
    private int column;
    private int offset;
    private int firstLineIndent;
    private int blockIndent;
    private boolean startsOnNewLine;
    private YamlTokenType type;
    private String lexeme;
    private String content;
    private final ScalarStyle scalarStyle;

    /// Structural Token
    public YamlToken(
        int line,
        int column,
        int startOffset,
        YamlTokenType type)
    {
        this(line, column, startOffset, type, null);
    }

    public YamlToken(
        int line,
        int column,
        int startOffset,
        YamlTokenType type,
        String content)
    {
        this(line, column, startOffset, type, null, content, ScalarStyle.PLAIN, -1, -1);
    }

    public YamlToken(
        int line,
        int column,
        int startOffset,
        YamlTokenType type,
        String lexeme,
        String content,
        ScalarStyle scalarStyle,
        int firstLineIndent,
        int blockIndent)
    {
        this.line = line;
        this.column = column;
        this.offset = startOffset;
        this.type = type;
        this.lexeme = lexeme;
        this.content = content;
        this.scalarStyle = scalarStyle;
        this.firstLineIndent = firstLineIndent;
        this.blockIndent = blockIndent;
    }

    @Override
    public int getStartLine() {
        return line;
    }

    @Override
    public int getStartColumn() {
        return column;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    public int getFirstLineIndent() {
        return firstLineIndent;
    }

    public int getBlockIndent() {
        return blockIndent;
    }

    @Override
    public YamlTokenType getType() {
        return type;
    }

    @Override
    public String getLexeme() {
        return lexeme;
    }

    @Override
    public String getContent() {
        return content;
    }

    public ScalarStyle getScalarStyle() {
        return scalarStyle;
    }

    // public boolean hasPreceedingWhitespace() {
    //     return hasPreceedingWhitespace;
    // }

    // public YamlToken setHasPreceedingWhitespace(boolean b) {
    //     hasPreceedingWhitespace = b;
    //     return this;
    // }

    public boolean startsOnNewLine() {
        return startsOnNewLine;
    }

    public YamlToken setStartsOnNewLine(boolean b) {
        startsOnNewLine = b;
        return this;
    }

    public void setType(YamlTokenType type) { this.type = type; }

    @Override
    public String toString() {
        String displayLexeme = StringUtils.debugString(16, lexeme);
        // String displayContent = StringUtils.debugString(16, content);
        // String displayLexeme = lexeme.replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
        // String valuePart = (content != null) ? " (Value: " + content + ")" : "";

        return String.format("[%s %d:%d %s]",
            type,
            line,
            column,
            displayLexeme
        );
    }
}