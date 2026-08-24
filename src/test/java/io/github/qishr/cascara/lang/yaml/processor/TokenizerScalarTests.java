package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.util.SourceStringBuffer;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;

public class TokenizerScalarTests {

    @Test
    void test4Q9F(){
        TestUtils.assertEquals("ab cd\nef\n\ngh\n", blockScalar(">\n ab\n cd\n\n ef\n\n\n gh\n"));
    }

    @Test
    void test4QFQa(){
        TestUtils.assertEquals("detected\n", blockScalar("|\n detected\n", 1));
    }

    @Test
    void test4QFQb(){
        TestUtils.assertEquals("\n\n# detected\n", blockScalar(">\n\n\n  # detected\n", 1));
    }

    @Test
    void test4QFQc(){
        TestUtils.assertEquals(" explicit\n", blockScalar("|1\n  explicit\n", 1));
    }

    @Test
    void test4QFQd(){
        TestUtils.assertEquals("detected\n", blockScalar(">\n detected\n", 1));
    }

    @Test
    void test4WAa(){
        TestUtils.assertEquals("xxx\n", blockScalar("|2\n    xxx\n", 3));
    }

    @Test
    void test4WAb(){
        TestUtils.assertEquals("xxx\n", blockScalar("|\n    xxx\n", 3));
    }


    //
    // Helpers
    //

    String blockScalar(String source) {
        return blockScalar(source, 0);
    }

    String blockScalar(String source, int indent) {
        YamlTokenizer tokenizer = new YamlTokenizer();

        char header = source.charAt(0);
        ScalarStyle style = header == '>'
            ? ScalarStyle.FOLDED
            : ScalarStyle.LITERAL;
        // String string = source.substring(1);

        SourceStringBuffer buffer = new SourceStringBuffer(source);

        tokenizer.indentationLevels.push(indent);
        tokenizer.buffer = buffer;
        tokenizer.scanScalar(style);

        assertEquals(1, tokenizer.pendingTokens.size());
        YamlToken token = tokenizer.pendingTokens.peek();
        return token.getContent();
    }
}
