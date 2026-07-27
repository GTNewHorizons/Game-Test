package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestArguments;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.AfterBatch;
import com.gtnewhorizons.horizonqa.api.annotation.BeforeBatch;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;
import com.gtnewhorizons.horizonqa.internal.InvalidBatchHook.HookPhase;

import cpw.mods.fml.common.discovery.ASMDataTable;

public class GameTestRegistryTest {

    @After
    public void clearAsmData() {
        GameTestRegistry.setAsmData(null);
    }

    @Test
    public void beforeBatchHooksMustReturnVoid() throws Exception {
        Method method = Hooks.class.getMethod("nonVoidBefore");

        List<DiscoveryIssue> issues = collectBatchMethodIssues(method, HookPhase.BEFORE);

        assertEquals(1, issues.size());
        assertEquals(
            "discovery:invalidHook:before:" + Hooks.class.getName() + "#nonVoidBefore:returnType",
            issues.get(0)
                .id());
        assertEquals(
            "DISCOVERY_ERROR",
            issues.get(0)
                .kind());
        assertTrue(
            issues.get(0)
                .message()
                .contains("must return void"));
    }

    @Test
    public void afterBatchHooksMustReturnVoid() throws Exception {
        Method method = Hooks.class.getMethod("nonVoidAfter");

        List<DiscoveryIssue> issues = collectBatchMethodIssues(method, HookPhase.AFTER);

        assertEquals(1, issues.size());
        assertEquals(
            "discovery:invalidHook:after:" + Hooks.class.getName() + "#nonVoidAfter:returnType",
            issues.get(0)
                .id());
        assertEquals(
            "DISCOVERY_ERROR",
            issues.get(0)
                .kind());
        assertTrue(
            issues.get(0)
                .message()
                .contains("must return void"));
    }

    @Test
    public void publicStaticVoidNoArgBatchHooksAreValid() throws Exception {
        Method method = Hooks.class.getMethod("validBefore");

        List<DiscoveryIssue> issues = collectBatchMethodIssues(method, HookPhase.BEFORE);

        assertTrue(issues.isEmpty());
    }

    @Test
    public void missingRequiredModGatesHolderFromAsmWithoutLoadingItsDefinition() {
        String holderClassName = GameTestRegistryTest.class.getName() + "$ModGatedTests";
        ASMDataTable table = gatedAsmData(holderClassName);
        GameTestRegistry.setAsmData(table);

        DiscoveryResult discovery = GameTestRegistry.discoverTests(modId -> false);

        assertTrue(
            discovery.issues()
                .isEmpty());
        assertEquals(
            1,
            discovery.validTests()
                .size());
        GameTestDefinition definition = discovery.validTests()
            .get(0);
        assertEquals("testmod:ModGatedTests.gated", definition.getTestId());
        assertEquals(holderClassName, definition.getHolderClassName());
        assertEquals("testmod:compat/cell", definition.getTemplateName());
        assertEquals("compat", definition.getBatch());
        assertFalse(definition.isRequired());
        assertEquals(2, definition.getRotation());
        assertNull(definition.getMethod());
        assertTrue(definition.isSkippedAtDiscovery());
        assertEquals("Required mod is not loaded: optionalmod", definition.getDiscoverySkipReason());
    }

    @Test
    public void presentRequiredModLoadsAndReflectivelyValidatesHolder() {
        String holderClassName = ModGatedTests.class.getName();
        GameTestRegistry.setAsmData(gatedAsmData(holderClassName));

        DiscoveryResult discovery = GameTestRegistry.discoverTests("optionalmod"::equals);

        assertTrue(
            discovery.issues()
                .isEmpty());
        assertEquals(
            1,
            discovery.validTests()
                .size());
        GameTestDefinition definition = discovery.validTests()
            .get(0);
        assertFalse(definition.isSkippedAtDiscovery());
        assertEquals(
            ModGatedTests.class,
            definition.getMethod()
                .getDeclaringClass());
    }

    @Test
    public void missingRequiredModRetainsParameterizedCaseFamilyFromAsm() {
        String holderClassName = "missing.mod.ParameterizedGatedTests";
        ASMDataTable table = gatedAsmData(holderClassName);
        table.addASMData(
            null,
            MethodSource.class.getName(),
            holderClassName,
            "gated(Lcom/gtnewhorizons/horizonqa/api/GameTestHelper;)V",
            Collections.emptyMap());
        GameTestRegistry.setAsmData(table);

        DiscoveryResult discovery = GameTestRegistry.discoverTests(modId -> false);

        assertEquals(
            1,
            discovery.validTests()
                .size());
        assertTrue(
            discovery.validTests()
                .get(0)
                .isUnresolvedCaseFamily());
    }

    @Test
    public void duplicateGatedTestsRetainHolderNamesForSelectionDiagnostics() {
        String firstHolder = "missing.first.ModGatedTests";
        String secondHolder = "missing.second.ModGatedTests";
        ASMDataTable table = gatedAsmData(firstHolder);
        addGatedAsmData(table, secondHolder);
        GameTestRegistry.setAsmData(table);

        DiscoveryResult discovery = GameTestRegistry.discoverTests(modId -> false);

        assertTrue(
            discovery.validTests()
                .isEmpty());
        assertEquals(
            1,
            discovery.duplicateIds()
                .size());
        DuplicateTestId duplicate = discovery.duplicateIds()
            .get(0);
        assertTrue(
            duplicate.holderClassNames()
                .contains(firstHolder));
        assertTrue(
            duplicate.holderClassNames()
                .contains(secondHolder));
    }

    @Test
    public void methodSourceExpandsNamedRowsAndPassesArgumentsToEachInvocation() {
        GameTestRegistry.setAsmData(holderAsmData(ParameterizedTests.class));

        DiscoveryResult discovery = GameTestRegistry.discoverTests(modId -> true);

        assertTrue(
            discovery.issues()
                .isEmpty());
        assertEquals(
            2,
            discovery.validTests()
                .size());
        assertEquals(
            "matrix:ParameterizedTests.acceptsVoltage[lv]",
            discovery.validTests()
                .get(0)
                .getTestId());
        assertEquals(
            "matrix:ParameterizedTests.acceptsVoltage[mv]",
            discovery.validTests()
                .get(1)
                .getTestId());

        ParameterizedTests.observed.clear();
        for (GameTestDefinition definition : discovery.validTests()) {
            GameTestInstance instance = new GameTestInstance(definition, 0, 0, 0);
            instance.start(null);
            assertEquals(GameTestStatus.PASSED, instance.getStatus());
        }
        assertEquals(Arrays.asList("LV=32", "MV=128"), ParameterizedTests.observed);
    }

    @Test
    public void emptyMethodSourceValueUsesTestMethodNameAndIndexedCaseIds() {
        GameTestRegistry.setAsmData(holderAsmData(DefaultNamedSourceTests.class));

        DiscoveryResult discovery = GameTestRegistry.discoverTests(modId -> true);

        assertTrue(
            discovery.issues()
                .isEmpty());
        assertEquals(
            Arrays.asList("matrix:DefaultNamedSourceTests.fluid[0]", "matrix:DefaultNamedSourceTests.fluid[1]"),
            Arrays.asList(
                discovery.validTests()
                    .get(0)
                    .getTestId(),
                discovery.validTests()
                    .get(1)
                    .getTestId()));
        assertEquals(
            "water",
            discovery.validTests()
                .get(0)
                .getArguments()[0]);
    }

    @Test
    public void duplicateMethodSourceNamesExcludeTheParameterizedMethod() {
        GameTestRegistry.setAsmData(holderAsmData(DuplicateSourceNames.class));

        DiscoveryResult discovery = GameTestRegistry.discoverTests(modId -> true);

        assertTrue(
            discovery.validTests()
                .isEmpty());
        assertEquals(
            1,
            discovery.invalidTests()
                .size());
        assertTrue(
            discovery.issues()
                .get(0)
                .message()
                .contains("duplicate case name 'same'"));
    }

    @Test
    public void incompatibleMethodSourceArgumentsExcludeTheParameterizedMethod() {
        GameTestRegistry.setAsmData(holderAsmData(IncompatibleSourceArguments.class));

        DiscoveryResult discovery = GameTestRegistry.discoverTests(modId -> true);

        assertTrue(
            discovery.validTests()
                .isEmpty());
        assertEquals(
            1,
            discovery.invalidTests()
                .size());
        assertTrue(
            discovery.issues()
                .get(0)
                .message()
                .contains("java.lang.String, which cannot be passed to int"));
    }

    @Test
    public void unsafeArgumentMaterializationBecomesADiscoveryIssue() {
        GameTestRegistry.setAsmData(holderAsmData(DeeplyNestedSourceArguments.class));

        DiscoveryResult discovery = GameTestRegistry.discoverTests(modId -> true);

        assertTrue(
            discovery.validTests()
                .isEmpty());
        assertEquals(
            1,
            discovery.invalidTests()
                .size());
        assertTrue(
            discovery.issues()
                .get(0)
                .message()
                .contains("argument arrays may be nested at most 64 levels"));
    }

    private static ASMDataTable gatedAsmData(String holderClassName) {
        ASMDataTable table = new ASMDataTable();
        addGatedAsmData(table, holderClassName);
        return table;
    }

    private static ASMDataTable holderAsmData(Class<?> holderClass) {
        ASMDataTable table = new ASMDataTable();
        table.addASMData(
            null,
            GameTestHolder.class.getName(),
            holderClass.getName(),
            holderClass.getName(),
            Collections.emptyMap());
        return table;
    }

    private static void addGatedAsmData(ASMDataTable table, String holderClassName) {
        Map<String, Object> holderInfo = new HashMap<>();
        holderInfo.put("value", "testmod");
        holderInfo.put("templatePrefix", "compat");
        holderInfo.put("requiredMods", Collections.singletonList("optionalmod"));
        table.addASMData(null, GameTestHolder.class.getName(), holderClassName, holderClassName, holderInfo);

        Map<String, Object> testInfo = new HashMap<>();
        testInfo.put("template", "cell");
        testInfo.put("timeoutTicks", 40);
        testInfo.put("batch", "compat");
        testInfo.put("required", false);
        testInfo.put("rotation", 2);
        table.addASMData(
            null,
            GameTest.class.getName(),
            holderClassName,
            "gated(Lcom/gtnewhorizons/horizonqa/api/GameTestHelper;)V",
            testInfo);
    }

    private static List<DiscoveryIssue> collectBatchMethodIssues(Method method, HookPhase phase) throws Exception {
        Method validator = GameTestRegistry.class
            .getDeclaredMethod("collectBatchMethodIssues", Method.class, HookPhase.class, List.class);
        validator.setAccessible(true);

        List<DiscoveryIssue> issues = new ArrayList<>();
        validator.invoke(null, method, phase, issues);
        return issues;
    }

    public static final class Hooks {

        @BeforeBatch("setup")
        public static void validBefore() {}

        @BeforeBatch("setup")
        public static boolean nonVoidBefore() {
            return true;
        }

        @AfterBatch("setup")
        public static String nonVoidAfter() {
            return "done";
        }
    }

    @GameTestHolder(value = "testmod", templatePrefix = "compat", requiredMods = "optionalmod")
    public static final class ModGatedTests {

        @GameTest(template = "cell", timeoutTicks = 40, batch = "compat", required = false, rotation = 2)
        public static void gated(GameTestHelper helper) {}
    }

    @GameTestHolder("matrix")
    public static final class ParameterizedTests {

        static final List<String> observed = new ArrayList<>();

        @GameTest
        @MethodSource("voltages")
        public static void acceptsVoltage(GameTestHelper helper, int voltage, String tier) {
            observed.add(tier + "=" + voltage);
            helper.succeed();
        }

        public static Stream<GameTestArguments> voltages() {
            return Stream.of(GameTestArguments.named("lv", 32, "LV"), GameTestArguments.named("mv", 128, "MV"));
        }
    }

    @GameTestHolder("matrix")
    public static final class DefaultNamedSourceTests {

        @GameTest
        @MethodSource
        public static void fluid(GameTestHelper helper, String fluidName) {}

        public static GameTestArguments[] fluid() {
            return new GameTestArguments[] { GameTestArguments.of("water"), GameTestArguments.of("lava") };
        }
    }

    @GameTestHolder("matrix")
    public static final class DuplicateSourceNames {

        @GameTest
        @MethodSource("rows")
        public static void duplicate(GameTestHelper helper, int voltage) {}

        public static List<GameTestArguments> rows() {
            return Arrays.asList(GameTestArguments.named("same", 32), GameTestArguments.named("same", 128));
        }
    }

    @GameTestHolder("matrix")
    public static final class IncompatibleSourceArguments {

        @GameTest
        @MethodSource("rows")
        public static void wrongType(GameTestHelper helper, int voltage) {}

        public static List<GameTestArguments> rows() {
            return Collections.singletonList(GameTestArguments.of("not a voltage"));
        }
    }

    @GameTestHolder("matrix")
    public static final class DeeplyNestedSourceArguments {

        @GameTest
        @MethodSource("rows")
        public static void nested(GameTestHelper helper, Object value) {}

        public static List<GameTestArguments> rows() {
            Object nested = "leaf";
            for (int i = 0; i < 70; i++) {
                nested = new Object[] { nested };
            }
            return Collections.singletonList(GameTestArguments.of(nested));
        }
    }
}
