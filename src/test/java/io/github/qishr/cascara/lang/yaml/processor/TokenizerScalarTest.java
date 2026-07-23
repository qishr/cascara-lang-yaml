package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.lang.util.SourceStringBuffer;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

public class TokenizerScalarTest {

    @Test
    void test4Q9F(){
        StringUtil.assertEquals("ab cd\nef\n\ngh\n", blockScalar(">\n ab\n cd\n\n ef\n\n\n gh\n"));
    }

    @Test
    void test4QFQa(){
        StringUtil.assertEquals("detected\n", blockScalar("|\n detected\n", 1));
    }

    @Test
    void test4QFQb(){
        StringUtil.assertEquals("\n\n# detected\n", blockScalar(">\n\n\n  # detected\n", 1));
    }

    @Test
    void test4QFQc(){
        StringUtil.assertEquals(" explicit\n", blockScalar("|1\n  explicit\n", 1));
    }

    @Test
    void test4QFQd(){
        StringUtil.assertEquals("detected\n", blockScalar(">\n detected\n", 1));
    }

    //
    // Helpers
    //

    String blockScalar(String source) {
        return blockScalar(source, 0);
    }

    String blockScalar(String source, int indent) {
        YamlTokenizer tokenizer = new YamlTokenizer();
        tokenizer.setReporter(new StandardReporter().setLevel(Level.TRACE));

        char header = source.charAt(0);
        String string = source.substring(1);

        SourceStringBuffer buffer = new SourceStringBuffer(string);

        tokenizer.indentationLevels.push(indent);
        tokenizer.buffer = buffer;
        tokenizer.scanBlockScalar(header);

        assertEquals(1, tokenizer.pendingTokens.size());
        YamlToken token = tokenizer.pendingTokens.peek();
        return token.getContent();
    }
}
