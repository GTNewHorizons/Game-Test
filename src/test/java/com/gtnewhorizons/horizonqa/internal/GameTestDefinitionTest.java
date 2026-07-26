package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class GameTestDefinitionTest {

    @Test
    public void arrayArgumentsAreSnapshottedAndCopiedForEveryLaunch() {
        String[] sourceValue = { "water", "lava" };
        GameTestDefinition definition = GameTestDefinition.parameterized(
            "test:Holder.acceptsFluids",
            "both",
            0,
            null,
            "",
            20,
            "",
            true,
            0,
            new Object[] { sourceValue });

        sourceValue[0] = "changed-after-discovery";
        Object[] firstLaunch = definition.getArguments();
        assertArrayEquals(new String[] { "water", "lava" }, (String[]) firstLaunch[0]);

        ((String[]) firstLaunch[0])[1] = "changed-by-test";
        Object[] secondLaunch = definition.getArguments();
        assertArrayEquals(new String[] { "water", "lava" }, (String[]) secondLaunch[0]);
        assertEquals("[[water, lava]]", definition.getArgumentSummary());
    }

    @Test
    public void cyclicArraysAreCopiedAndSummarizedWithoutRecursingForever() {
        Object[] cyclic = new Object[1];
        cyclic[0] = cyclic;

        GameTestDefinition definition = definition(new Object[] { cyclic });

        Object[] invocation = definition.getArguments();
        Object[] copiedCycle = (Object[]) invocation[0];
        assertSame(copiedCycle, copiedCycle[0]);
        assertEquals("[[[...]]]", definition.getArgumentSummary());
    }

    @Test
    public void argumentSummaryContainsThrowingToStringMethods() {
        Object unsafe = new Object() {

            @Override
            public String toString() {
                throw new IllegalStateException("must not be called");
            }
        };

        String summary = definition(new Object[] { unsafe }).getArgumentSummary();

        assertEquals("[<unprintable arguments>]", summary);
    }

    private static GameTestDefinition definition(Object[] arguments) {
        return GameTestDefinition.parameterized("test:Holder.case", "row", 0, null, "", 20, "", true, 0, arguments);
    }
}
