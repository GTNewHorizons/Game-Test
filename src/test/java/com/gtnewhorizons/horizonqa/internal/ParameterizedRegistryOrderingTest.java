package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestArguments;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;

import cpw.mods.fml.common.discovery.ASMDataTable;

public class ParameterizedRegistryOrderingTest {

    @After
    public void clearAsmData() {
        GameTestRegistry.setAsmData(null);
    }

    @Test
    public void registryPreservesMethodSourceEncounterOrder() {
        GameTestRegistry.setAsmData(holderAsmData(EncounterOrderTests.class));

        DiscoveryResult discovery = GameTestRegistry.discoverTests(modId -> true);

        assertTrue(
            discovery.issues()
                .isEmpty());
        assertTrue(
            discovery.duplicateIds()
                .isEmpty());
        assertEquals(
            Arrays.asList(
                "matrix:EncounterOrderTests.inSourceOrder[zeta]",
                "matrix:EncounterOrderTests.inSourceOrder[alpha]",
                "matrix:EncounterOrderTests.inSourceOrder[middle]"),
            testIds(discovery.validTests()));
        assertEquals(
            Arrays.asList(0, 1, 2),
            Arrays.asList(
                discovery.validTests()
                    .get(0)
                    .getCaseOrdinal(),
                discovery.validTests()
                    .get(1)
                    .getCaseOrdinal(),
                discovery.validTests()
                    .get(2)
                    .getCaseOrdinal()));
    }

    @Test
    public void batchBuilderPreservesParameterizedEncounterOrder() throws Exception {
        Method testMethod = EncounterOrderTests.class.getMethod("inSourceOrder", GameTestHelper.class, int.class);
        String baseTestId = "matrix:EncounterOrderTests.inSourceOrder";
        List<GameTestDefinition> definitions = Arrays.asList(
            parameterized(baseTestId, "zeta", 0, testMethod),
            parameterized(baseTestId, "alpha", 1, testMethod),
            parameterized(baseTestId, "middle", 2, testMethod));

        Method buildBatches = ReportedRun.class
            .getDeclaredMethod("buildBatches", List.class, java.util.Map.class, java.util.Map.class);
        buildBatches.setAccessible(true);
        List<?> batches = (List<?>) buildBatches
            .invoke(null, definitions, Collections.emptyMap(), Collections.emptyMap());
        Field tests = batches.get(0)
            .getClass()
            .getDeclaredField("tests");
        tests.setAccessible(true);

        assertEquals(definitions, tests.get(batches.get(0)));
    }

    @Test
    public void duplicateBaseIdsAreRejectedEvenWhenCaseKeysDoNotOverlap() {
        GameTestRegistry.setAsmData(holderAsmData(OverloadedParameterizedTests.class));

        DiscoveryResult discovery = GameTestRegistry.discoverTests(modId -> true);

        assertTrue(
            discovery.validTests()
                .isEmpty());
        assertEquals(
            1,
            discovery.duplicateIds()
                .size());
        assertEquals(
            "matrix:OverloadedParameterizedTests.collides",
            discovery.duplicateIds()
                .get(0)
                .testId());
        String diagnostic = discovery.issues()
            .get(0)
            .message();
        assertTrue(diagnostic.contains(GameTestHelper.class.getName()));
        assertTrue(diagnostic.contains(String.class.getName()));
    }

    @Test
    public void duplicateBaseIdsAreRejectedBeforeEitherSourceIsResolved() {
        MixedValidityDuplicateTests.validProviderInvoked = false;
        GameTestRegistry.setAsmData(holderAsmData(MixedValidityDuplicateTests.class));

        DiscoveryResult discovery = GameTestRegistry.discoverTests(modId -> true);

        assertTrue(
            discovery.validTests()
                .isEmpty());
        assertEquals(
            1,
            discovery.duplicateIds()
                .size());
        assertTrue(
            discovery.invalidTests()
                .isEmpty());
        assertFalse(MixedValidityDuplicateTests.validProviderInvoked);
    }

    private static GameTestDefinition parameterized(String baseTestId, String caseName, int caseOrdinal,
        Method method) {
        return GameTestDefinition.parameterized(
            baseTestId,
            caseName,
            caseOrdinal,
            method,
            "",
            20,
            "matrix",
            true,
            0,
            new Object[] { caseOrdinal });
    }

    private static List<String> testIds(List<GameTestDefinition> definitions) {
        java.util.ArrayList<String> ids = new java.util.ArrayList<>(definitions.size());
        for (GameTestDefinition definition : definitions) {
            ids.add(definition.getTestId());
        }
        return ids;
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

    @GameTestHolder("matrix")
    public static final class EncounterOrderTests {

        @GameTest
        @MethodSource("rows")
        public static void inSourceOrder(GameTestHelper helper, int value) {}

        public static List<GameTestArguments> rows() {
            return Arrays.asList(
                GameTestArguments.named("zeta", 0),
                GameTestArguments.named("alpha", 1),
                GameTestArguments.named("middle", 2));
        }
    }

    @GameTestHolder("matrix")
    public static final class OverloadedParameterizedTests {

        @GameTest
        @MethodSource("integerRows")
        public static void collides(GameTestHelper helper, int value) {}

        @GameTest
        @MethodSource("stringRows")
        public static void collides(GameTestHelper helper, String value) {}

        public static List<GameTestArguments> integerRows() {
            return Collections.singletonList(GameTestArguments.named("integer", 1));
        }

        public static List<GameTestArguments> stringRows() {
            return Collections.singletonList(GameTestArguments.named("string", "one"));
        }
    }

    @GameTestHolder("matrix")
    public static final class MixedValidityDuplicateTests {

        static boolean validProviderInvoked;

        @GameTest
        @MethodSource("validRows")
        public static void collides(GameTestHelper helper, int value) {}

        @GameTest
        @MethodSource("missingRows")
        public static void collides(GameTestHelper helper, String value) {}

        public static List<GameTestArguments> validRows() {
            validProviderInvoked = true;
            return Collections.singletonList(GameTestArguments.named("valid", 1));
        }
    }
}
