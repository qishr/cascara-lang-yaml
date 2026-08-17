package io.github.qishr.cascara.lang.yaml.internal;

import io.github.qishr.cascara.common.util.StringUtils;

public class DebugUtils {
    public static String debugStringBuilder(StringBuilder sb, int lines) {
        StringBuilder output = new StringBuilder();
        int length = sb.length();
        if (length < 2) {
            return StringUtils.debugString(sb.toString());
        }
        int line = 0;
        int lineEnd = length - 1;
        int preceedingNewline = -1;
        while ((lines < 0 || line < lines) && lineEnd > 0) {
            preceedingNewline = sb.lastIndexOf("\n", lineEnd - 1);
            output.insert(0, "\n");
            int end = lineEnd < length ? lineEnd + 1 : lineEnd;
            String b = sb.substring(preceedingNewline + 1, end);
            output.insert(0, StringUtils.debugString(b));
            lineEnd = preceedingNewline;
            line++;
        }
        return output.toString();
    }
}
