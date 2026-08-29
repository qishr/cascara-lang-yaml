package io.github.qishr.cascara.lang.yaml.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.annotation.Nullable;
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

    @Nullable
    public static String getTestName() {
        Method testMethod = getTestMethod();
        if (testMethod == null) {
            return null;
        }
        return testMethod.getName();
    }

    @Nullable
    public static Method getTestMethod() {
        StackTraceElement[] callStack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : callStack) {
            String className = frame.getClassName();
            String methodName = frame.getMethodName();
            Class<?> clazz;
            try {
                clazz = Class.forName(className);
                List<Method> methods = getMethodsByName(clazz, methodName);
                for (Method method : methods) {
                    if (hasTestAnnotation(method)) {
                        return method;
                    }
                }
            } catch (ClassNotFoundException e) {
                break;
            }
        }
        return null;
    }

    private static boolean hasTestAnnotation(Method method) {
        Annotation[] annotations = method.getDeclaredAnnotations();
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> type = annotation.annotationType();
            String name = type.getName();
            if (name.startsWith("org.junit.jupiter.api.Test")) {
                return true;
            }
        }
        return false;
    }

    private static List<Method> getMethodsByName(Class<?> clazz, String name) {
        List<Method> methods = new ArrayList<>();
        for (Method method : clazz.getMethods()){
            if(method.getName().equals(name)){
                // System.out.println("Possible match : " + method);
                methods.add(method);
            }
        }
        for (Method method : clazz.getDeclaredMethods()){
            if(method.getName().equals(name)){
                // System.out.println("Possible match : " + method);
                methods.add(method);
            }
        }
        return methods;
    }
}
