package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
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
            new Object[0]);
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
        return new GameTestDefinition(baseTestId, method, templateName, timeoutTicks, batch, required, rotation)
            .withArguments(caseName, caseOrdinal, arguments);
    }

    GameTestDefinition withArguments(String newCaseName, int newCaseOrdinal, Object[] newArguments) {
        if (newCaseName == null || newCaseName.isEmpty()) {
            throw new IllegalArgumentException("caseName must not be empty");
        }
        if (newCaseOrdinal < 0) {
            throw new IllegalArgumentException("caseOrdinal must be non-negative");
        }
        return new GameTestDefinition(
            baseTestId,
            newCaseName,
            newCaseOrdinal,
            method,
            templateName,
            timeoutTicks,
            batch,
            required,
            rotation,
            holderClassName,
            discoverySkipReason,
            unresolvedCaseFamily,
            newArguments);
    }

    public static GameTestDefinition skippedAtDiscovery(String testId, String holderClassName, String templateName,
        int timeoutTicks, String batch, boolean required, int rotation, String skipReason) {
        return skippedAtDiscovery(
            testId,
            holderClassName,
            templateName,
            timeoutTicks,
            batch,
            required,
            rotation,
            skipReason,
            false);
    }

    public static GameTestDefinition parameterizedSkippedAtDiscovery(String testId, String holderClassName,
        String templateName, int timeoutTicks, String batch, boolean required, int rotation, String skipReason) {
        return skippedAtDiscovery(
            testId,
            holderClassName,
            templateName,
            timeoutTicks,
            batch,
            required,
            rotation,
            skipReason,
            true);
    }

    private static GameTestDefinition skippedAtDiscovery(String testId, String holderClassName, String templateName,
        int timeoutTicks, String batch, boolean required, int rotation, String skipReason,
        boolean unresolvedCaseFamily) {
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
            unresolvedCaseFamily,
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
        try {
            return Arrays.deepToString(values);
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable ignored) {
            return "[<unprintable arguments>]";
        }
    }

    @Override
    public String toString() {
        return getTestId();
    }
}
