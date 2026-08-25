package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

/**
 * A sequence step completed or failed.
 *
 * @param tick         logical event-log tick
 * @param index        one-based step index
 * @param totalSteps   number of declared sequence steps
 * @param label        author label, or declaration source when unlabeled
 * @param outcome      {@code completed} or {@code failed}
 * @param attempts     number of action or condition attempts
 * @param elapsedTicks inclusive outer-test tick count from first attempt to completion
 */
@Experimental
@Desugar
public record SequenceStepFinished(int tick, int index, int totalSteps, String label, String outcome, int attempts,
    long elapsedTicks) implements TestEvent {

    @Override
    public String category() {
        return Category.SEQUENCE;
    }

    @Override
    public String summary() {
        return capitalize(outcome) + " sequence step "
            + index
            + '/'
            + totalSteps
            + " '"
            + label
            + "' after "
            + attempts
            + " attempt(s) over "
            + elapsedTicks
            + " tick(s)";
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "Finished";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
