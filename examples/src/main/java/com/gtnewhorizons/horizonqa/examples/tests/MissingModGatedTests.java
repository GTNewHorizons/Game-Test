package com.gtnewhorizons.horizonqa.examples.tests;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.examples.ExamplesMod;

@GameTestHolder(value = ExamplesMod.MODID, requiredMods = "horizonqa_missing_example_mod")
public class MissingModGatedTests {

    @GameTest(timeoutTicks = 20)
    public static void holderIsNotLoadedOrExecuted(GameTestHelper helper) {
        throw new AssertionError("A holder with a missing required mod must not be loaded or executed");
    }
}
