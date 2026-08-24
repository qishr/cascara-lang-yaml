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
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;


public class OnDemandTokenBuffer implements TokenBuffer {

    private static final int MAX_DEBUG_STRING_LENGTH = 20;

    private final Object lock = new Object();

    private final int capacity;
    private final YamlToken[] ring;
    private final long[] globalIndexRing;

    private int start = 0;
    private int count = 0;

    private long globalCounter = 0;
    private long lastNewlineOrComment = -1;

    private YamlTokenizer tokenizer = new YamlTokenizer();

    public OnDemandTokenBuffer(int capacity) {
        this.capacity = Math.max(8, capacity);
        this.ring = new YamlToken[this.capacity];
        this.globalIndexRing = new long[this.capacity];
    }

    @Override
    public void setReporter(Reporter reporter) {
    }

    @Override
    public void setTokenizer(YamlTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public YamlTokenizer getTokenizer() {
        return tokenizer;
    }

    @Override
    public void open(String text) {
        tokenizer.open(text);
        start = 0;
        count = 0;
        globalCounter = 0;
        lastNewlineOrComment = -1;
    }

    @Override
    public void open(byte[] data) {
        open(new String(data));
    }

    @Override
    public void open(Reader reader) {
        tokenizer.open(reader);
        start = 0;
        count = 0;
        globalCounter = 0;
        lastNewlineOrComment = -1;
    }

    @Override
    public void open(InputStream is) {
        tokenizer.open(is);
        start = 0;
        count = 0;
        globalCounter = 0;
        lastNewlineOrComment = -1;
    }

    private int physicalIndex(int logicalIndex) {
        return (start + logicalIndex) % capacity;
    }

    private void trackTrailing(YamlToken t, long globalIndex) {
        if (t.getType() == YamlTokenType.NEWLINE ||
            t.getType() == YamlTokenType.COMMENT) {
            lastNewlineOrComment = globalIndex;
        }
    }

    // @Override
    // public void ensureBuffered(int ahead) {
    //     synchronized (lock) {
    //         // int needed = ahead + 1;
    //         int needed = ahead + 2;

    //         while (count < needed) {
    //             YamlToken next = tokenizer.nextToken();
    //             if (next == null) break;

    //             long globalIndex = globalCounter++;
    //             int insertIndex = physicalIndex(count);

    //             ring[insertIndex] = next;
    //             globalIndexRing[insertIndex] = globalIndex;

    //             trackTrailing(next, globalIndex);

    //             if (count < capacity) {
    //                 count++;
    //             } else {
    //                 start = (start + 1) % capacity;
    //             }

    //             if (next.getType() == YamlTokenType.EOF ||
    //                 next.getType() == YamlTokenType.STREAM_END) {
    //                 break;
    //             }
    //         }
    //     }
    // }

    @Override
    public void ensureBuffered(int ahead) {
        synchronized (lock) {
            int needed = ahead + 2;

            while (count < needed) {
                YamlToken next = tokenizer.nextToken();
                if (next == null) break;

                long globalIndex = globalCounter++;
                int insertIndex = physicalIndex(count);

                ring[insertIndex] = next;
                globalIndexRing[insertIndex] = globalIndex;

                trackTrailing(next, globalIndex);

                if (count < capacity) {
                    count++;
                } else {
                    start = (start + 1) % capacity;
                }

                // Only stop *after* we've buffered both EOF and STREAM_END
                if (next.getType() == YamlTokenType.STREAM_END) {
                    break;
                }
            }
        }
    }

    @Override
    public boolean isEmpty() {
        synchronized (lock) {
            ensureBuffered(0);
            return count == 0;
        }
    }

    @Override
    public int offset() {
        synchronized (lock) {
            ensureBuffered(0);
            if (count == 0) return 0;
            return (int) globalIndexRing[physicalIndex(0)];
        }
    }

    @Override
    public boolean isAtEnd(int ahead) {
        synchronized (lock) {
            ensureBuffered(ahead);
            if (ahead >= count) return true;
            YamlToken t = ring[physicalIndex(ahead)];
            return t.getType() == YamlTokenType.EOF ||
                t.getType() == YamlTokenType.STREAM_END;
        }
    }

    @Override
    public boolean isAtEnd() {
        synchronized (lock) {
            return isAtEnd(0);
        }
    }

    @Override
    public YamlToken peekAhead(int ahead) {
        synchronized (lock) {
            ensureBuffered(ahead);
            if (ahead >= count) {
                return count == 0 ? null : ring[physicalIndex(count - 1)];
            }
            return ring[physicalIndex(ahead)];
        }
    }

    @Override
    public YamlToken peek() {
        synchronized (lock) {
            ensureBuffered(0);
            return ring[physicalIndex(0)];
        }
    }

    @Override
    public int size() {
        synchronized (lock) {
            return count;
        }
    }

    @Override
    public YamlToken previous() {
        synchronized (lock) {
            if (count == capacity) {
                return ring[(start + capacity - 1) % capacity];
            }
            if (start == 0) return null;
            return ring[start - 1];
        }
    }

    @Override
    public YamlToken advance() {
        synchronized (lock) {
            ensureBuffered(1);

            if (isAtEnd()) {
                return peek();
            }

            YamlToken consumed = ring[start];

            start = (start + 1) % capacity;
            count--;

            return consumed;
        }
    }

    @Override
    public boolean isTrailingStreamComment() {
        synchronized (lock) {
            YamlToken tok = peek();
            if (tok == null) return false;

            long globalIndex = globalIndexRing[physicalIndex(0)];
            return globalIndex >= lastNewlineOrComment;
        }
    }

    //
    // Diagnostics
    //

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLUE  = "\u001B[34m";
    private static final String ANSI_WHITE = "\u001B[37m";

    @Override
    public String upcomingTokens() {
        synchronized (lock) {
            ensureBuffered(4);
            StringBuilder sb = new StringBuilder();

            int limit = Math.min(count, 4);
            for (int i = 0; i < limit; i++) {
                if (i > 0) sb.append(", ");

                YamlToken t = ring[physicalIndex(i)];
                if (t == null) continue;
                sb.append(ANSI_BLUE).append(t.getType()).append(ANSI_RESET);

                if (t.getType() == YamlTokenType.SCALAR ||
                    t.getType() == YamlTokenType.ANCHOR ||
                    t.getType() == YamlTokenType.ALIAS ||
                    t.getType() == YamlTokenType.TAG ||
                    t.getType() == YamlTokenType.DIRECTIVE) {

                    String lex = StringUtils.debugString(t.getContent());
                    sb.append("(").append(ANSI_WHITE);

                    if (lex.length() <= MAX_DEBUG_STRING_LENGTH) {
                        sb.append(lex);
                    } else {
                        sb.append(lex.substring(0, MAX_DEBUG_STRING_LENGTH - 1))
                        .append(StringUtils.ELLIPSIS);
                    }

                    sb.append(ANSI_RESET).append(")");
                }
            }

            if (count > limit) sb.append(", …");
            return sb.toString();
        }
    }
}
