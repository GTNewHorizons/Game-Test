package com.gtnewhorizons.horizonqa.internal;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.internal.InvalidBatchHook.HookPhase;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.report.IssueResult;
import com.gtnewhorizons.horizonqa.report.RunReportWriter;
import com.gtnewhorizons.horizonqa.report.RunResult;

import cpw.mods.fml.common.FMLCommonHandler;

public class GameTestBatchRunner {

    private static final Logger LOG = LogManager.getLogger("GameTest");
    private static final Comparator<Method> METHOD_ORDER = Comparator.comparing(
        (Method m) -> m.getDeclaringClass()
            .getName())
        .thenComparing(Method::getName);
    private static boolean batchRunning;

    private final List<Batch> batches;
    private final GameTestRunner runner;
    private final FixturePreparation fixturePreparation;
    private final List<ResultEntry> resultEntries = new ArrayList<>();
    private final List<IssueResult> issues = new ArrayList<>();
    private final Consumer<RunResult> onComplete;

    public GameTestBatchRunner(List<GameTestDefinition> tests, Map<String, List<Method>> beforeBatchMethods,
        Map<String, List<Method>> afterBatchMethods) {
        this(tests, beforeBatchMethods, afterBatchMethods, Collections.emptyList(), null);
    }

    public GameTestBatchRunner(List<GameTestDefinition> tests, Map<String, List<Method>> beforeBatchMethods,
        Map<String, List<Method>> afterBatchMethods, List<IssueResult> issues) {
        this(tests, beforeBatchMethods, afterBatchMethods, issues, null);
    }

    public GameTestBatchRunner(List<GameTestDefinition> tests, Map<String, List<Method>> beforeBatchMethods,
        Map<String, List<Method>> afterBatchMethods, List<IssueResult> issues, Consumer<RunResult> onComplete) {
        runner = new GameTestRunner();
        fixturePreparation = new FixturePreparation();
        List<GameTestDefinition> runnableTests = new ArrayList<>();
        for (GameTestDefinition test : tests) {
            if (test.isSkippedAtDiscovery()) {
                resultEntries.add(
                    ResultEntry.result(
                        CaseResult.skipped(test, test.getDiscoverySkipReason(), CaseResult.MISSING_REQUIRED_MOD)));
            } else {
                runnableTests.add(test);
            }
        }
        batches = buildBatches(runnableTests, beforeBatchMethods, afterBatchMethods);
        this.onComplete = onComplete;
        if (issues != null) {
            this.issues.addAll(issues);
        }
    }

    public void start() {
        if (!markBatchStarted()) {
            throw new IllegalStateException("A GameTest batch is already running.");
        }
        try {
            runner.register();
            if (batches.isEmpty()) {
                onAllBatchesDone();
                return;
            }
            // Placement and getTileEntity are unreliable during FMLServerStartingEvent (before the first server
            // tick). Defer until the world has ticked once, matching /gametest runAll during normal gameplay.
            runner.scheduleOnFirstTick(() -> runBatchSafely(0));
        } catch (RuntimeException | Error e) {
            runner.unregister();
            markBatchFinished();
            throw e;
        }
    }

    public static synchronized boolean isBatchRunning() {
        return batchRunning;
    }

    public static synchronized void resetBatchRunningState() {
        batchRunning = false;
    }

    private static synchronized boolean markBatchStarted() {
        if (batchRunning) {
            return false;
        }
        batchRunning = true;
        return true;
    }

    private static synchronized void markBatchFinished() {
        batchRunning = false;
    }

    private void runBatchSafely(int idx) {
        try {
            runBatch(idx);
        } catch (RuntimeException | Error e) {
            cleanupAfterUnexpectedFailure();
            throw e;
        }
    }

    private void cleanupAfterUnexpectedFailure() {
        runner.unregister();
        HorizonQAMod.CHUNK_LOADER.releaseAll();
        markBatchFinished();
    }

    private void runBatch(int idx) {
        Batch batch = batches.get(idx);
        LOG.info("--- Batch '{}' ({} test(s)) ---", batch.name, batch.tests.size());

        List<IssueResult> beforeIssues = invokeHooks(
            batch.beforeMethods,
            HookPhase.BEFORE,
            batch.name,
            true,
            batch.tests.size());
        if (!beforeIssues.isEmpty()) {
            IssueResult rootIssue = beforeIssues.get(0);
            issues.add(rootIssue);
            for (CaseResult skippedCase : skippedCasesForBeforeFailure(batch.tests, rootIssue)) {
                resultEntries.add(ResultEntry.result(skippedCase));
            }
            runNextBatchOrFinish(idx);
            return;
        }

        WorldServer world = MinecraftServer.getServer()
            .worldServerForDimension(0);
        if (world == null) {
            IssueResult rootIssue = worldUnavailableIssue(batch.name, remainingTestCount(idx));
            LOG.error(rootIssue.message());
            issues.add(rootIssue);
            for (CaseResult skippedCase : skippedCasesForIssue(remainingTests(idx), rootIssue, "WORLD_UNAVAILABLE")) {
                resultEntries.add(ResultEntry.result(skippedCase));
            }
            onAllBatchesDone();
            return;
        }

        List<FixturePreparation.Result> prepared;
        try {
            prepared = fixturePreparation.prepare(world, batch.tests);
        } catch (GameTestInfrastructureException e) {
            IssueResult rootIssue = fixturePreparationIssue(batch.name, batch.tests.size(), e);
            issues.add(rootIssue);
            for (CaseResult skippedCase : skippedCasesForIssue(batch.tests, rootIssue, CaseResult.TEMPLATE_ERROR)) {
                resultEntries.add(ResultEntry.result(skippedCase));
            }
            issues.addAll(invokeHooks(batch.afterMethods, HookPhase.AFTER, batch.name, false, 0));
            runNextBatchOrFinish(idx);
            return;
        }

        List<GameTestInstance> batchInstances = new ArrayList<>(prepared.size());
        for (FixturePreparation.Result result : prepared) {
            if (!result.isReady()) {
                Throwable cause = result.failure()
                    .getCause();
                resultEntries.add(
                    ResultEntry.result(
                        CaseResult.templateError(
                            result.definition(),
                            result.failure()
                                .getMessage(),
                            cause != null ? cause : result.failure())));
                continue;
            }
            GameTestInstance inst = result.instance();
            inst.start(world);
            batchInstances.add(inst);
            resultEntries.add(ResultEntry.instance(inst));
        }

        runner.run(batchInstances, () -> {
            issues.addAll(invokeHooks(batch.afterMethods, HookPhase.AFTER, batch.name, false, 0));
            runNextBatchOrFinish(idx);
        });
    }

    private void onAllBatchesDone() {
        RunResult result;
        try {
            runner.unregister();
            HorizonQAMod.CHUNK_LOADER.releaseAll();

            File reportFile = HorizonQAProperties.junitReportFile();
            result = RunResult
                .completedCases(HorizonQAProperties.modeName(), collectCaseResults(), issues, reportFile.getPath());

            result = RunReportWriter.write(result, LOG);

            if (HorizonQAProperties.stopServerAfterRun()) {
                LOG.info(
                    "Stopping server with code {} ({} required test failure/timeout(s), {} incomplete test(s), {} infrastructure issue(s)).",
                    result.exitCode(),
                    result.requiredFailures(),
                    result.incomplete(),
                    result.infrastructureErrors());
            }
            if (onComplete != null) {
                onComplete.accept(result);
            }
        } finally {
            markBatchFinished();
        }
        if (HorizonQAProperties.stopServerAfterRun()) {
            FMLCommonHandler.instance()
                .exitJava(result.exitCode(), false);
        }
    }

    private void runNextBatchOrFinish(int idx) {
        int next = idx + 1;
        if (next < batches.size()) {
            runBatchSafely(next);
        } else {
            onAllBatchesDone();
        }
    }

    private List<CaseResult> collectCaseResults() {
        List<CaseResult> cases = new ArrayList<>(resultEntries.size());
        for (ResultEntry entry : resultEntries) {
            cases.add(entry.toCaseResult());
        }
        return cases;
    }

    static List<IssueResult> invokeHooks(List<Method> methods, HookPhase phase, String batch, boolean stopOnFailure,
        int affectedTests) {
        List<IssueResult> failures = new ArrayList<>();
        List<Method> orderedMethods = methods == null ? Collections.emptyList() : methods;
        for (Method m : orderedMethods) {
            try {
                m.invoke(null);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                IssueResult issue = hookIssue(phase, batch, m, cause, affectedTests);
                logHookIssue(phase, batch, m, cause);
                failures.add(issue);
                if (stopOnFailure) {
                    return failures;
                }
            } catch (IllegalAccessException | IllegalArgumentException e) {
                IssueResult issue = hookIssue(phase, batch, m, e, affectedTests);
                logHookIssue(phase, batch, m, e);
                failures.add(issue);
                if (stopOnFailure) {
                    return failures;
                }
            }
        }
        return failures;
    }

    static List<CaseResult> skippedCasesForBeforeFailure(List<GameTestDefinition> tests, IssueResult rootIssue) {
        return skippedCasesForIssue(tests, rootIssue, "BATCH_HOOK_ERROR");
    }

    static List<CaseResult> skippedCasesForIssue(List<GameTestDefinition> tests, IssueResult rootIssue,
        String failureType) {
        List<CaseResult> skipped = new ArrayList<>();
        for (GameTestDefinition test : tests) {
            skipped.add(CaseResult.skippedByIssue(test, rootIssue.id(), rootIssue.message(), failureType));
        }
        return skipped;
    }

    static List<Method> sortedHookMethods(List<Method> methods) {
        if (methods == null || methods.isEmpty()) {
            return Collections.emptyList();
        }
        List<Method> sorted = new ArrayList<>(methods);
        sorted.sort(METHOD_ORDER);
        return sorted;
    }

    private int remainingTestCount(int batchIndex) {
        int count = 0;
        for (int i = batchIndex; i < batches.size(); i++) {
            count += batches.get(i).tests.size();
        }
        return count;
    }

    private List<GameTestDefinition> remainingTests(int batchIndex) {
        List<GameTestDefinition> tests = new ArrayList<>();
        for (int i = batchIndex; i < batches.size(); i++) {
            tests.addAll(batches.get(i).tests);
        }
        return tests;
    }

    private static List<Batch> buildBatches(List<GameTestDefinition> tests, Map<String, List<Method>> beforeMethods,
        Map<String, List<Method>> afterMethods) {

        Map<String, List<GameTestDefinition>> testsByBatch = new TreeMap<>();
        for (GameTestDefinition def : tests) {
            testsByBatch.computeIfAbsent(def.getBatch(), k -> new ArrayList<>())
                .add(def);
        }

        List<Batch> result = new ArrayList<>();
        for (Map.Entry<String, List<GameTestDefinition>> entry : testsByBatch.entrySet()) {
            entry.getValue()
                .sort(GameTestDefinition.executionOrder());
            String name = entry.getKey();
            List<Method> before = sortedHookMethods(beforeMethods == null ? null : beforeMethods.get(name));
            List<Method> after = sortedHookMethods(afterMethods == null ? null : afterMethods.get(name));
            result.add(new Batch(name, entry.getValue(), before, after));
        }
        return result;
    }

    private static IssueResult hookIssue(HookPhase phase, String batch, Method method, Throwable error,
        int affectedTests) {
        String phaseName = phaseName(phase);
        String methodRef = methodRef(method);
        String id = "batchHook:" + phase.name()
            .toLowerCase() + ":" + batchId(batch) + ":" + methodRef;
        String message = "@" + phaseName
            + " method '"
            + methodRef
            + "' failed for batch '"
            + batchName(batch)
            + "': "
            + errorMessage(error);
        StringBuilder details = new StringBuilder();
        details.append("issue.id=")
            .append(id)
            .append('\n');
        details.append("phase=")
            .append(phaseName)
            .append('\n');
        details.append("batch=")
            .append(batchName(batch))
            .append('\n');
        details.append("method=")
            .append(methodRef)
            .append('\n');
        if (phase == HookPhase.BEFORE) {
            details.append("affectedTests=")
                .append(affectedTests)
                .append('\n');
        }

        return new IssueResult(
            id,
            phase == HookPhase.BEFORE ? "BEFORE_BATCH_ERROR" : "AFTER_BATCH_ERROR",
            "horizonqa.infrastructure",
            "batch-hook:" + phase.name()
                .toLowerCase() + ":" + batchName(batch) + ":" + methodRef,
            message,
            details.toString(),
            true,
            stackTrace(error));
    }

    private static IssueResult worldUnavailableIssue(String batch, int affectedTests) {
        String id = "runner:worldUnavailable:dimension0";
        String message = "World dimension 0 is null; cannot start batch '" + batchName(batch) + "' or remaining tests.";
        String details = "issue.id=" + id
            + "\nkind=WORLD_UNAVAILABLE\nbatch="
            + batchName(batch)
            + "\ndimension=0\naffectedTests="
            + affectedTests
            + "\n";
        return new IssueResult(
            id,
            "WORLD_UNAVAILABLE",
            "horizonqa.infrastructure",
            "world:dimension0",
            message,
            details,
            true);
    }

    private static IssueResult fixturePreparationIssue(String batch, int affectedTests,
        GameTestInfrastructureException error) {
        String id = "runner:fixturePreparation:" + batchId(batch);
        String message = "Fixture preparation failed for batch '" + batchName(batch) + "': " + errorMessage(error);
        String details = "issue.id=" + id
            + "\nkind="
            + CaseResult.TEMPLATE_ERROR
            + "\nbatch="
            + batchName(batch)
            + "\naffectedTests="
            + affectedTests
            + "\n";
        return new IssueResult(
            id,
            CaseResult.TEMPLATE_ERROR,
            "horizonqa.infrastructure",
            "fixture-preparation:" + batchName(batch),
            message,
            details,
            true,
            stackTrace(error));
    }

    private static void logHookIssue(HookPhase phase, String batch, Method method, Throwable error) {
        LOG.error(
            "Exception in @{} method '{}' for batch '{}': {}",
            phaseName(phase),
            methodRef(method),
            batchName(batch),
            errorMessage(error),
            error);
    }

    private static String phaseName(HookPhase phase) {
        return phase == HookPhase.BEFORE ? "BeforeBatch" : "AfterBatch";
    }

    static String batchName(String batch) {
        return batch == null || batch.isEmpty() ? "default" : batch;
    }

    static String batchId(String batch) {
        return batchName(batch);
    }

    private static String methodRef(Method method) {
        return method.getDeclaringClass()
            .getName() + "#"
            + method.getName();
    }

    private static String errorMessage(Throwable error) {
        if (error == null) {
            return "unknown hook error";
        }
        String message = error.getMessage();
        if (message == null || message.isEmpty()) {
            return error.getClass()
                .getName();
        }
        return message;
    }

    private static String stackTrace(Throwable error) {
        if (error == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static final class Batch {

        final String name;
        final List<GameTestDefinition> tests;
        final List<Method> beforeMethods;
        final List<Method> afterMethods;

        Batch(String name, List<GameTestDefinition> tests, List<Method> before, List<Method> after) {
            this.name = name;
            this.tests = tests;
            this.beforeMethods = before;
            this.afterMethods = after;
        }
    }

    @Desugar
    private record ResultEntry(GameTestInstance instance, CaseResult result) {

        static ResultEntry instance(GameTestInstance instance) {
            return new ResultEntry(instance, null);
        }

        static ResultEntry result(CaseResult result) {
            return new ResultEntry(null, result);
        }

        CaseResult toCaseResult() {
            return result != null ? result : CaseResult.from(instance);
        }
    }

}
