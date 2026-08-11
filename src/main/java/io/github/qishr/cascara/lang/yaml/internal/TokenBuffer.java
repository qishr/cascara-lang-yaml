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

package io.github.qishr.cascara.lang.yaml.internal;

import java.io.InputStream;
import java.io.Reader;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

public interface TokenBuffer {

    void setReporter(Reporter reporter);

    void setTokenizer(YamlTokenizer tokenizer);

    YamlTokenizer getTokenizer();

    // The highest number this is called with is 4. It's usually called with 1.
    /// Ensures that the lookahead buffer has retrieved tokens up to the requested
    /// lookahead index offset.
    void ensureBuffered(int ahead);

    boolean isEmpty();

    // This is only used by the parser to check it hasn't got stuck on a token
    int offset();

    boolean isAtEnd(int ahead);

    boolean isAtEnd();

    YamlToken peekAhead(int ahead);

    YamlToken peek();

    int size();

    YamlToken previous();

    YamlToken advance();

    boolean isTrailingStreamComment();

    // Get next 4 tokens as a string.
    String upcomingTokens();



    void open(String text);
    void open(byte[] data);
    void open(Reader reader);
    void open(InputStream is);
}