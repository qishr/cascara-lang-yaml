package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.AssertionFailureBuilder;

public class StringUtil {
    public static String debugString(String input) {
        StringBuilder sb = new StringBuilder();
        for (int codePoint : input.codePoints().toArray()) {
            sb.append(currentChar( codePoint ));
        }
        return sb.toString();
    }

    private static String currentChar(int c) {
        switch (c) {
            case ' ':
                return "␣";
            case '\t':
                return "⇥";
            case '\r':
                return "␍";
            case '\n':
                return "↵";
            default:
                return Character.toString(c);
        }
    }

    public static void assertEquals(String expected, String actual) {
        if (expected == actual) return;
        if (expected == null || !expected.equals(actual)) {
            AssertionFailureBuilder.assertionFailure()
                // .message(error(expected, actual))
				.expected(debugString(expected))
				.actual(debugString(actual))
				.buildAndThrow();
        }
    }

    private static String error(String expected, String actual) {
        return String.format(
            "Expected: %s  Actual: %s",
            StringUtil.debugString(expected),
            StringUtil.debugString(actual)
        );
    }

}
