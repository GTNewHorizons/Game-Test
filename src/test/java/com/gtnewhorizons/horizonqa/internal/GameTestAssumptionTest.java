package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.After;
import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestAssumptionException;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.event.TestFinished;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.report.RunResult;

public class GameTestAssumptionTest {

    private static boolean reachedAfterAssumption;
    private static boolean cleanupRan;

    @After
    public void resetState() {
        reachedAfterAssumption = false;
        cleanupRan = false;
    }

    @Test
    public void failedAssumptionAbortsAndSkipsWithoutFailingRun() throws Exception {
        GameTestInstance instance = instance("assumptionSkips");

        instance.start(null);

        assertEquals(GameTestStatus.SKIPPED, instance.getStatus());
        assertTrue(instance.isDone());
        assertFalse(reachedAfterAssumption);
        assertTrue(cleanupRan);
        TestFinished finished = (TestFinished) instance.getRecorder()
            .snapshot()
            .get(
                instance.getRecorder()
                    .snapshot()
                    .size() - 1);
        assertEquals("skipped", finished.status());

        CaseResult resultCase = CaseResult.from(instance);
        assertEquals(CaseResult.Status.SKIPPED, resultCase.status());
        assertEquals("Optional runtime capability is unavailable", resultCase.skipReason());
        assertEquals(CaseResult.ASSUMPTION_FAILED, resultCase.failureType());
        assertTrue(
            resultCase.failureTrace()
                .isEmpty());

        RunResult result = RunResult
            .completedCases("ci", Collections.singletonList(resultCase), Collections.emptyList(), "TEST.xml");
        assertEquals(0, result.exitCode());
        assertEquals(1, result.skipped());
        assertEquals(1, result.junitSkipped());
        assertEquals(0, result.junitFailures());
        assertEquals(0, result.junitErrors());
    }

    @Test
    public void cleanupFailureOverridesSkippedOutcomeAsInfrastructureError() throws Exception {
        GameTestInstance instance = instance("assumptionThenCleanupFails");

        instance.start(null);

        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        CaseResult resultCase = CaseResult.from(instance);
        assertEquals(CaseResult.Status.ERROR, resultCase.status());
        assertEquals(CaseResult.CLEANUP_ERROR, resultCase.failureType());

        RunResult result = RunResult
            .completedCases("ci", Collections.singletonList(resultCase), Collections.emptyList(), "TEST.xml");
        assertEquals(2, result.exitCode());
    }

    @Test
    public void assumeFalseUsesTheSameSkipSignal() {
        GameTestHelper helper = new GameTestHelper(null, null, 0, 0, 0);

        GameTestAssumptionException skipped = assertThrows(
            GameTestAssumptionException.class,
            () -> helper.assumeFalse(true, "Feature must be disabled"));

        assertEquals("Feature must be disabled", skipped.getMessage());
    }

    private static GameTestInstance instance(String methodName) throws Exception {
        Method method = TestDefinitions.class.getMethod(methodName, GameTestHelper.class);
        GameTestDefinition definition = new GameTestDefinition(
            "mod:AssumptionTests." + methodName,
            method,
            "",
            20,
            "",
            true,
            0);
        return new GameTestInstance(definition, 0, 0, 0);
    }

    public static final class TestDefinitions {

        public static void assumptionSkips(GameTestHelper helper) {
            helper.afterTest(() -> cleanupRan = true);
            helper.assumeTrue(false, "Optional runtime capability is unavailable");
            reachedAfterAssumption = true;
        }

        public static void assumptionThenCleanupFails(GameTestHelper helper) {
            helper.afterTest(() -> { throw new AssertionError("cleanup broke"); });
            helper.assumeTrue(false, "Optional runtime capability is unavailable");
        }
    }
}
