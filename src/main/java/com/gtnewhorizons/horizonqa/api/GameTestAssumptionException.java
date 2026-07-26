package com.gtnewhorizons.horizonqa.api;

import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

/**
 * Signals that a runtime precondition for a game test is not satisfied. Horizon-QA treats this as a
 * skipped test, not a test failure.
 */
@Experimental
public class GameTestAssumptionException extends RuntimeException {

    public GameTestAssumptionException(String reason) {
        super(reason == null || reason.isEmpty() ? "Runtime assumption was not satisfied" : reason);
    }
}
