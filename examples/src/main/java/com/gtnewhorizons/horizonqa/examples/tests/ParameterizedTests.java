package com.gtnewhorizons.horizonqa.examples.tests;

import java.util.Arrays;
import java.util.List;

import com.gtnewhorizons.horizonqa.api.GameTestArguments;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;
import com.gtnewhorizons.horizonqa.examples.ExamplesMod;

@GameTestHolder(ExamplesMod.MODID)
public class ParameterizedTests {

    private ParameterizedTests() {}

    @GameTest(timeoutTicks = 20)
    @MethodSource("voltageTiers")
    public static void voltageTierHasPositiveEu(GameTestHelper helper, int voltage, String tierName) {
        helper.assertTrue(voltage > 0, tierName + " voltage must be positive");
        helper.succeed();
    }

    public static List<GameTestArguments> voltageTiers() {
        return Arrays.asList(
            GameTestArguments.named("lv", 32, "LV"),
            GameTestArguments.named("mv", 128, "MV"),
            GameTestArguments.named("hv", 512, "HV"));
    }
}
