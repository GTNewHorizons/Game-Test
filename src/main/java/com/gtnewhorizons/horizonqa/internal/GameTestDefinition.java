package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.IdentityHashMap;

public final class GameTestDefinition {

    private static final int MAX_ARRAY_NESTING = 64;
    private static final Comparator<GameTestDefinition> EXECUTION_ORDER = Comparator
        .comparing(GameTestDefinition::getBaseTestId)
        .thenComparingInt(GameTestDefinition::getCaseOrdinal)
        .thenComparing(GameTestDefinition::getTestId);

    private final String baseTestId;
    private final String caseName;
    private final int caseOrdinal;
    private final Method method;
    private final String templateName;
    private final int timeoutTicks;
    private final String batch;
    private final boolean required;
    private final int rotation;
    private final String holderClassName;
    private final String discoverySkipReason;
    private final boolean unresolvedCaseFamily;
    private final Object[] arguments;
    private final String argumentSummary;

    public GameTestDefinition(String testId, Method method, String templateName, int timeoutTicks, String batch,
        boolean required, int rotation) {
        this(testId, method, templateName, timeoutTicks, batch, required, rotation, new Object[0]);
    }

    public GameTestDefinition(String testId, Method method, String templateName, int timeoutTicks, String batch,
        boolean required, int rotation, Object[] arguments) {
        this(
            testId,
            "",
            0,
            method,
            templateName,
            timeoutTicks,
            batch,
            required,
            rotation,
            method == null ? ""
                : method.getDeclaringClass()
                    .getName(),
            "",
            false,
            arguments);
    }

    private GameTestDefinition(String baseTestId, String caseName, int caseOrdinal, Method method, String templateName,
        int timeoutTicks, String batch, boolean required, int rotation, String holderClassName,
        String discoverySkipReason, boolean unresolvedCaseFamily, Object[] arguments) {
        this.baseTestId = baseTestId;
        this.caseName = caseName == null ? "" : caseName;
        this.caseOrdinal = caseOrdinal;
        this.method = method;
        this.templateName = templateName;
        this.timeoutTicks = timeoutTicks;
        this.batch = batch;
        this.required = required;
        this.rotation = rotation;
        this.holderClassName = holderClassName == null ? "" : holderClassName;
        this.discoverySkipReason = discoverySkipReason == null ? "" : discoverySkipReason;
        this.unresolvedCaseFamily = unresolvedCaseFamily;
        this.arguments = arguments == null ? new Object[0] : copyArguments(arguments);
        this.argumentSummary = this.caseName.isEmpty() ? "" : summarizeArguments(this.arguments);
    }

    public static GameTestDefinition parameterized(String baseTestId, String caseName, int caseOrdinal, Method method,
        String templateName, int timeoutTicks, String batch, boolean required, int rotation, Object[] arguments) {
        if (caseName == null || caseName.isEmpty()) {
            throw new IllegalArgumentException("caseName must not be empty");
        }
        if (caseOrdinal < 0) {
            throw new IllegalArgumentException("caseOrdinal must be non-negative");
        }
        return new GameTestDefinition(
            baseTestId,
            caseName,
            caseOrdinal,
            method,
            templateName,
            timeoutTicks,
            batch,
            required,
            rotation,
            method == null ? ""
                : method.getDeclaringClass()
                    .getName(),
            "",
            false,
            arguments);
    }

    public static GameTestDefinition skippedAtDiscovery(String testId, String holderClassName, String templateName,
        int timeoutTicks, String batch, boolean required, int rotation, String skipReason) {
        return new GameTestDefinition(
            testId,
            "",
            0,
            null,
            templateName,
            timeoutTicks,
            batch,
            required,
            rotation,
            holderClassName,
            skipReason,
            false,
            new Object[0]);
    }

    public static GameTestDefinition parameterizedSkippedAtDiscovery(String testId, String holderClassName,
        String templateName, int timeoutTicks, String batch, boolean required, int rotation, String skipReason) {
        return new GameTestDefinition(
            testId,
            "",
            0,
            null,
            templateName,
            timeoutTicks,
            batch,
            required,
            rotation,
            holderClassName,
            skipReason,
            true,
            new Object[0]);
    }

    public String getTestId() {
        return isParameterized() ? baseTestId + "[" + caseName + "]" : baseTestId;
    }

    public String getBaseTestId() {
        return baseTestId;
    }

    public String getCaseName() {
        return caseName;
    }

    public int getCaseOrdinal() {
        return caseOrdinal;
    }

    public boolean isParameterized() {
        return !caseName.isEmpty();
    }

    public String getArgumentSummary() {
        return argumentSummary;
    }

    public String getReportClassName() {
        int separator = reportSplitIndex();
        return separator > 0 ? baseTestId.substring(0, separator) : "horizonqa";
    }

    public String getReportName() {
        int separator = reportSplitIndex();
        String methodName = separator > 0 ? baseTestId.substring(separator + 1) : baseTestId;
        return isParameterized() ? methodName + "[" + caseName + "]" : methodName;
    }

    public static Comparator<GameTestDefinition> executionOrder() {
        return EXECUTION_ORDER;
    }

    public Method getMethod() {
        return method;
    }

    public String getTemplateName() {
        return templateName;
    }

    public int getTimeoutTicks() {
        return timeoutTicks;
    }

    public String getBatch() {
        return batch;
    }

    public boolean isRequired() {
        return required;
    }

    public int getRotation() {
        return rotation;
    }

    public Object[] getArguments() {
        return copyArguments(arguments);
    }

    public String getHolderClassName() {
        return holderClassName;
    }

    public String getHolderSimpleName() {
        int nested = holderClassName.lastIndexOf('$');
        int separator = nested >= 0 ? nested : holderClassName.lastIndexOf('.');
        return separator >= 0 ? holderClassName.substring(separator + 1) : holderClassName;
    }

    public boolean isSkippedAtDiscovery() {
        return !discoverySkipReason.isEmpty();
    }

    public String getDiscoverySkipReason() {
        return discoverySkipReason;
    }

    public boolean isUnresolvedCaseFamily() {
        return unresolvedCaseFamily;
    }

    private int reportSplitIndex() {
        return Math.max(baseTestId.lastIndexOf('.'), baseTestId.lastIndexOf('#'));
    }

    private static Object[] copyArguments(Object[] source) {
        return (Object[]) copyArray(source, new IdentityHashMap<>(), 0);
    }

    private static Object copyArray(Object value, IdentityHashMap<Object, Object> copies, int depth) {
        if (value == null || !value.getClass()
            .isArray()) {
            return value;
        }
        Object existing = copies.get(value);
        if (existing != null) {
            return existing;
        }
        if (depth > MAX_ARRAY_NESTING) {
            throw new IllegalArgumentException(
                "argument arrays may be nested at most " + MAX_ARRAY_NESTING + " levels");
        }
        int length = Array.getLength(value);
        Object copy = Array.newInstance(
            value.getClass()
                .getComponentType(),
            length);
        copies.put(value, copy);
        if (value instanceof Object[]objects) {
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, copyArray(objects[i], copies, depth + 1));
            }
        } else {
            System.arraycopy(value, 0, copy, 0, length);
        }
        return copy;
    }

    private static String summarizeArguments(Object[] values) {
        StringBuilder summary = new StringBuilder().append('[');
        IdentityHashMap<Object, Boolean> arrays = new IdentityHashMap<>();
        arrays.put(values, Boolean.TRUE);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) summary.append(", ");
            appendSummary(summary, values[i], arrays);
        }
        return summary.append(']')
            .toString();
    }

    private static void appendSummary(StringBuilder summary, Object value, IdentityHashMap<Object, Boolean> arrays) {
        if (value == null) {
            summary.append("null");
            return;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            if (arrays.put(value, Boolean.TRUE) != null) {
                summary.append("[...]");
                return;
            }
            summary.append('[');
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) summary.append(", ");
                appendSummary(summary, Array.get(value, i), arrays);
            }
            summary.append(']');
            arrays.remove(value);
            return;
        }
        if (value instanceof String || value instanceof Byte
            || value instanceof Short
            || value instanceof Integer
            || value instanceof Long
            || value instanceof Float
            || value instanceof Double
            || value instanceof Boolean
            || value instanceof Character) {
            summary.append(value);
            return;
        }
        if (value instanceof Enum<?>enumeration) {
            summary.append(enumeration.name());
            return;
        }
        if (value instanceof Class<?>argumentClass) {
            summary.append(argumentClass.getName());
            return;
        }
        try {
            summary.append(String.valueOf(value));
        } catch (ThreadDeath | OutOfMemoryError fatal) {
            throw fatal;
        } catch (Throwable ignored) {
            summary.append('<')
                .append(type.getName())
                .append('>');
        }
    }

    @Override
    public String toString() {
        return getTestId();
    }
}
