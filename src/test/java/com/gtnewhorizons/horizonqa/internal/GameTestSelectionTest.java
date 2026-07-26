package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.HorizonQAProperties.SelectorType;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.TestSelector;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;

public class GameTestSelectionTest {

    @Test
    public void selectsAllValidTestsWhenSelectorsAreAbsent() throws Exception {
        List<GameTestDefinition> validTests = Arrays
            .asList(definition("moda:Suite.first"), definition("modb:Suite.second"));

        GameTestSelection selection = GameTestSelection
            .from(validTests, Collections.emptyList(), Collections.emptyList(), true, Collections.emptyList());

        assertEquals(validTests, selection.selectedTests());
        assertTrue(
            selection.infrastructureIssues()
                .isEmpty());
    }

    @Test
    public void selectorsDeduplicateAndPreserveDiscoveryOrder() throws Exception {
        List<GameTestDefinition> validTests = Arrays
            .asList(definition("moda:Suite.first"), definition("moda:Suite.extra"), definition("modb:Suite.second"));
        List<TestSelector> selectors = Arrays.asList(
            new TestSelector(SelectorType.TEST_ID_PREFIX, "modb:Suite.second"),
            new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, "moda"),
            new TestSelector(SelectorType.TEST_ID_PREFIX, "moda:Suite.first"));

        GameTestSelection selection = GameTestSelection
            .from(validTests, Collections.emptyList(), Collections.emptyList(), false, selectors);

        assertEquals(validTests, selection.selectedTests());
        assertTrue(
            selection.infrastructureIssues()
                .isEmpty());
    }

    @Test
    public void holderSelectorsAcceptSimpleAndFullyQualifiedClassNames() throws Exception {
        GameTestDefinition dummy = definition("moda:DummyTests.first", DummyTests.class);
        GameTestDefinition other = definition("moda:OtherTests.second", OtherTests.class);
        List<GameTestDefinition> validTests = Arrays.asList(dummy, other);

        GameTestSelection simpleName = GameTestSelection.from(
            validTests,
            Collections.emptyList(),
            Collections.emptyList(),
            false,
            Collections.singletonList(new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, "DummyTests")));
        GameTestSelection canonicalName = GameTestSelection.from(
            validTests,
            Collections.emptyList(),
            Collections.emptyList(),
            false,
            Collections.singletonList(
                new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, OtherTests.class.getCanonicalName())));
        GameTestSelection binaryName = GameTestSelection.from(
            validTests,
            Collections.emptyList(),
            Collections.emptyList(),
            false,
            Collections.singletonList(new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, OtherTests.class.getName())));

        assertEquals(Collections.singletonList(dummy), simpleName.selectedTests());
        assertEquals(Collections.singletonList(other), canonicalName.selectedTests());
        assertEquals(Collections.singletonList(other), binaryName.selectedTests());
    }

    @Test
    public void holderSelectorsMatchDiscoverySkippedDefinitionsWithoutLoadingHolder() {
        GameTestDefinition skipped = GameTestDefinition.skippedAtDiscovery(
            "moda:OptionalTests.test",
            "moda.compat.OptionalTests",
            "",
            20,
            "",
            true,
            0,
            "Required mod is not loaded: optionalmod");

        assertEquals(
            Collections.singletonList(skipped),
            GameTestSelection.matchingValidTests(Collections.singletonList(skipped), "OptionalTests"));
        assertEquals(
            Collections.singletonList(skipped),
            GameTestSelection.matchingValidTests(Collections.singletonList(skipped), "moda.compat.OptionalTests"));
    }

    @Test
    public void testIdPrefixSelectsEveryMatchingMethod() throws Exception {
        List<GameTestDefinition> validTests = Arrays.asList(
            definition("moda:IOPortTests.fillModeImports"),
            definition("moda:IOPortTests.fillModeExports"),
            definition("moda:IOPortTests.emptyModeExports"),
            definition("moda:NetworkCoreTests.networkBoots"));

        List<GameTestDefinition> selected = GameTestSelection
            .matchingValidTests(validTests, "moda:IOPortTests.fillMode");

        assertEquals(validTests.subList(0, 2), selected);
    }

    @Test
    public void unmatchedSelectorsDescribeWhyNothingValidMatched() throws Exception {
        Method method = DummyTests.class.getMethod("test", GameTestHelper.class);
        List<GameTestDefinition> validTests = Collections.singletonList(definition("moda:Suite.first"));
        List<InvalidTestDefinition> invalidTests = Collections
            .singletonList(new InvalidTestDefinition("bad:Broken.test", method, Collections.emptyList()));
        List<DuplicateTestId> duplicateIds = Collections
            .singletonList(new DuplicateTestId("dupe:Suite.same", Collections.singletonList(method)));
        List<TestSelector> selectors = Arrays.asList(
            new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, "missing"),
            new TestSelector(SelectorType.TEST_ID_PREFIX, "bad:Broken.test"),
            new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, "dupe"));

        GameTestSelection selection = GameTestSelection.from(validTests, invalidTests, duplicateIds, false, selectors);

        assertEquals(Collections.emptyList(), selection.selectedTests());
        assertEquals(
            3,
            selection.infrastructureIssues()
                .size());
        assertEquals(
            "UNMATCHED_SELECTOR",
            selection.infrastructureIssues()
                .get(0)
                .kind());
        assertEquals(
            "INVALID_TEST_SELECTION",
            selection.infrastructureIssues()
                .get(1)
                .kind());
        assertEquals(
            "DUPLICATE_TEST_SELECTION",
            selection.infrastructureIssues()
                .get(2)
                .kind());
    }

    @Test
    public void holderSelectorsDiagnoseInvalidAndDuplicateDefinitions() throws Exception {
        Method invalidMethod = InvalidHolderTests.class.getMethod("test", GameTestHelper.class);
        Method duplicateMethod = DuplicateHolderTests.class.getMethod("test", GameTestHelper.class);
        List<InvalidTestDefinition> invalidTests = Collections.singletonList(
            new InvalidTestDefinition("moda:InvalidHolderTests.test", invalidMethod, Collections.emptyList()));
        List<DuplicateTestId> duplicateIds = Collections.singletonList(
            new DuplicateTestId("moda:DuplicateHolderTests.test", Collections.singletonList(duplicateMethod)));
        List<TestSelector> selectors = Arrays.asList(
            new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, "InvalidHolderTests"),
            new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, DuplicateHolderTests.class.getName()));

        GameTestSelection selection = GameTestSelection
            .from(Collections.emptyList(), invalidTests, duplicateIds, false, selectors);

        assertEquals(
            "INVALID_TEST_SELECTION",
            selection.infrastructureIssues()
                .get(0)
                .kind());
        assertEquals(
            "DUPLICATE_TEST_SELECTION",
            selection.infrastructureIssues()
                .get(1)
                .kind());
    }

    @Test
    public void namespaceAndHolderSelectorsDiagnoseAllGatedDuplicates() {
        DuplicateTestId duplicate = new DuplicateTestId(
            "moda:OptionalTests.test",
            Collections.emptyList(),
            Arrays.asList("compat.first.OptionalTests", "compat.second.OptionalTests"));
        List<TestSelector> selectors = Arrays.asList(
            new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, "moda"),
            new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, "OptionalTests"),
            new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, "compat.second.OptionalTests"));

        GameTestSelection selection = GameTestSelection.from(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.singletonList(duplicate),
            false,
            selectors);

        assertEquals(
            3,
            selection.infrastructureIssues()
                .size());
        for (GameTestSelection.SelectionIssue issue : selection.infrastructureIssues()) {
            assertEquals("DUPLICATE_TEST_SELECTION", issue.kind());
        }
    }

    @Test
    public void repeatedUnmatchedSelectorsEmitOneIssue() throws Exception {
        List<TestSelector> selectors = Arrays.asList(
            new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, "missing"),
            new TestSelector(SelectorType.NAMESPACE_OR_HOLDER, "missing"));

        GameTestSelection selection = GameTestSelection
            .from(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false, selectors);

        assertEquals(
            1,
            selection.infrastructureIssues()
                .size());
    }

    @Test
    public void noSelectedTestsDiagnosticIsSpecific() {
        GameTestSelection.SelectionIssue issue = GameTestSelection.noSelectedTests(true);

        assertEquals("selection:noTestsSelected", issue.id());
        assertEquals("NO_TESTS_SELECTED", issue.kind());
        assertEquals("<all valid tests>", issue.selector());
    }

    private static GameTestDefinition definition(String testId) throws Exception {
        return definition(testId, DummyTests.class);
    }

    private static GameTestDefinition definition(String testId, Class<?> holderClass) throws Exception {
        return new GameTestDefinition(testId, holderClass.getMethod("test", GameTestHelper.class), "", 20, "", true, 0);
    }

    public static final class DummyTests {

        public static void test(GameTestHelper helper) {}
    }

    public static final class OtherTests {

        public static void test(GameTestHelper helper) {}
    }

    public static final class InvalidHolderTests {

        public static void test(GameTestHelper helper) {}
    }

    public static final class DuplicateHolderTests {

        public static void test(GameTestHelper helper) {}
    }
}
