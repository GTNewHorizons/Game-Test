package com.gtnewhorizons.horizonqa.internal;

public enum GameTestStatus {

    NOT_STARTED,
    RUNNING,
    SKIPPED,
    PASSED,
    FAILED,
    ERROR,
    TIMED_OUT;

    public boolean isDone() {
        return this == SKIPPED || this == PASSED || this == FAILED || this == ERROR || this == TIMED_OUT;
    }
}
