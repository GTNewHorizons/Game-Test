package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Method;

public final class GameTestDefinition {

    private final String testId;
    private final Method method;
    private final String templateName;
    private final int timeoutTicks;
    private final String batch;
    private final boolean required;
    private final int rotation;
    private final String holderClassName;
    private final String discoverySkipReason;

    public GameTestDefinition(String testId, Method method, String templateName, int timeoutTicks, String batch,
        boolean required, int rotation) {
        this(
            testId,
            method,
            templateName,
            timeoutTicks,
            batch,
            required,
            rotation,
            method == null ? ""
                : method.getDeclaringClass()
                    .getName(),
            "");
    }

    private GameTestDefinition(String testId, Method method, String templateName, int timeoutTicks, String batch,
        boolean required, int rotation, String holderClassName, String discoverySkipReason) {
        this.testId = testId;
        this.method = method;
        this.templateName = templateName;
        this.timeoutTicks = timeoutTicks;
        this.batch = batch;
        this.required = required;
        this.rotation = rotation;
        this.holderClassName = holderClassName == null ? "" : holderClassName;
        this.discoverySkipReason = discoverySkipReason == null ? "" : discoverySkipReason;
    }

    public static GameTestDefinition skippedAtDiscovery(String testId, String holderClassName, String templateName,
        int timeoutTicks, String batch, boolean required, int rotation, String skipReason) {
        return new GameTestDefinition(
            testId,
            null,
            templateName,
            timeoutTicks,
            batch,
            required,
            rotation,
            holderClassName,
            skipReason);
    }

    public String getTestId() {
        return testId;
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

    @Override
    public String toString() {
        return testId;
    }
}
