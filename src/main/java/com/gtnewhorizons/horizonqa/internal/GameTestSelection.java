package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.SelectorType;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.TestSelector;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;

@Desugar
public record GameTestSelection(List<GameTestDefinition> selectedTests, List<SelectionIssue> infrastructureIssues) {

    public static GameTestSelection from(DiscoveryResult discovery) {
        return from(
            discovery.validTests(),
            discovery.invalidTests(),
            discovery.duplicateIds(),
            HorizonQAProperties.selectsAllTests(),
            HorizonQAProperties.testSelectors());
    }

    public static GameTestSelection from(List<GameTestDefinition> validTests, List<InvalidTestDefinition> invalidTests,
        List<DuplicateTestId> duplicateIds, boolean selectsAllTests, List<TestSelector> selectors) {

        if (selectsAllTests) {
            return new GameTestSelection(immutableList(validTests), Collections.emptyList());
        }

        Set<String> selectedIds = new LinkedHashSet<>();
        List<SelectionIssue> issues = new ArrayList<>();
        Set<String> emittedIssueIds = new HashSet<>();

        for (TestSelector selector : selectors) {
            boolean matchedValid = false;
            for (GameTestDefinition def : validTests) {
                if (matches(selector, def)) {
                    matchedValid = true;
                    selectedIds.add(def.getTestId());
                }
            }

            if (!matchedValid) {
                InvalidTestDefinition matchedInvalid = matchingInvalid(selector, invalidTests);
                SelectionIssue issue = unmatchedIssue(
                    selector,
                    matchedInvalid,
                    matchesDuplicate(selector, duplicateIds));
                if (emittedIssueIds.add(issue.id())) {
                    issues.add(issue);
                }
            }
        }

        List<GameTestDefinition> selected = new ArrayList<>();
        for (GameTestDefinition def : validTests) {
            if (selectedIds.contains(def.getTestId())) {
                selected.add(def);
            }
        }

        return new GameTestSelection(immutableList(selected), immutableList(issues));
    }

    public static List<GameTestDefinition> matchingValidTests(List<GameTestDefinition> validTests,
        String selectorValue) {

        SelectorType type = selectorValue.indexOf(':') >= 0 ? SelectorType.TEST_ID_PREFIX
            : SelectorType.NAMESPACE_OR_HOLDER;
        TestSelector selector = new TestSelector(type, selectorValue);
        List<GameTestDefinition> selected = new ArrayList<>();
        for (GameTestDefinition def : validTests) {
            if (matches(selector, def)) {
                selected.add(def);
            }
        }
        return immutableList(selected);
    }

    public static SelectionIssue noSelectedTests(boolean selectedAllTests) {
        String selector = selectedAllTests ? "<all valid tests>" : HorizonQAProperties.rawTests();
        String message = selectedAllTests ? "No valid tests were discovered."
            : "No valid tests were selected by -D" + HorizonQAProperties.TESTS_PROPERTY + "=" + selector + ".";
        return new SelectionIssue("selection:noTestsSelected", "NO_TESTS_SELECTED", selector, message);
    }

    private static boolean matches(TestSelector selector, String testId, Method holderMethod) {
        return matches(
            selector,
            testId,
            holderMethod == null ? ""
                : holderMethod.getDeclaringClass()
                    .getName());
    }

    private static boolean matches(TestSelector selector, GameTestDefinition definition) {
        if (matches(selector, definition.getTestId(), definition.getHolderClassName())) {
            return true;
        }
        return definition.isUnresolvedCaseFamily() && isCaseSelectorForBase(selector, definition.getBaseTestId());
    }

    private static boolean matches(TestSelector selector, String testId, String holderClassName) {
        if (selector.type() == SelectorType.TEST_ID_PREFIX) {
            return testId.startsWith(selector.value());
        }
        if (testId.startsWith(selector.value() + ":")) {
            return true;
        }
        if (holderClassName == null || holderClassName.isEmpty()) {
            return false;
        }
        int nested = holderClassName.lastIndexOf('$');
        int separator = nested >= 0 ? nested : holderClassName.lastIndexOf('.');
        String simpleName = separator >= 0 ? holderClassName.substring(separator + 1) : holderClassName;
        String canonicalName = holderClassName.replace('$', '.');
        return simpleName.equals(selector.value()) || canonicalName.equals(selector.value())
            || holderClassName.equals(selector.value());
    }

    private static InvalidTestDefinition matchingInvalid(TestSelector selector,
        List<InvalidTestDefinition> invalidTests) {
        for (InvalidTestDefinition invalidTest : invalidTests) {
            if (matches(selector, invalidTest.intendedTestId(), invalidTest.method())) {
                return invalidTest;
            }
            Method method = invalidTest.method();
            if (method != null && method.getAnnotation(MethodSource.class) != null
                && isCaseSelectorForBase(selector, invalidTest.intendedTestId())) {
                return invalidTest;
            }
        }
        return null;
    }

    private static boolean matchesDuplicate(TestSelector selector, List<DuplicateTestId> duplicateIds) {
        for (DuplicateTestId duplicateId : duplicateIds) {
            if (matches(selector, duplicateId.testId(), "")) {
                return true;
            }
            if (duplicateId.parameterized() && isCaseSelectorForBase(selector, duplicateId.testId())) {
                return true;
            }
            for (String holderClassName : duplicateId.holderClassNames()) {
                if (matches(selector, duplicateId.testId(), holderClassName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCaseSelectorForBase(TestSelector selector, String baseTestId) {
        return selector.type() == SelectorType.TEST_ID_PREFIX && selector.value()
            .startsWith(baseTestId + "[");
    }

    private static SelectionIssue unmatchedIssue(TestSelector selector, InvalidTestDefinition matchedInvalid,
        boolean matchedDuplicate) {
        boolean invalid = matchedInvalid != null;
        String selectorKind = selector.type() == SelectorType.NAMESPACE_OR_HOLDER ? "namespace or holder"
            : "test id prefix";
        String issueKind;
        String diagnosticKind;
        String message;
        if (invalid && matchedDuplicate) {
            issueKind = "excludedOnly";
            diagnosticKind = "EXCLUDED_TEST_SELECTION";
            message = "The " + selectorKind
                + " selector '"
                + selector.value()
                + "' matched only tests excluded during discovery; fix the discovery diagnostics before selecting it.";
        } else if (invalid) {
            issueKind = "invalidOnly";
            diagnosticKind = "INVALID_TEST_SELECTION";
            message = "The " + selectorKind
                + " selector '"
                + selector.value()
                + "' matched only invalid test definitions; fix the discovery diagnostics before selecting it.";
        } else if (matchedDuplicate) {
            issueKind = "duplicateOnly";
            diagnosticKind = "DUPLICATE_TEST_SELECTION";
            message = "The " + selectorKind
                + " selector '"
                + selector.value()
                + "' matched only duplicate test ids excluded during discovery; fix the duplicate id diagnostics before selecting it.";
        } else {
            issueKind = "unmatched";
            diagnosticKind = "UNMATCHED_SELECTOR";
            message = "The " + selectorKind + " selector '" + selector.value() + "' did not match any valid tests.";
        }
        message = appendDiscoveryDiagnostics(message, matchedInvalid);

        String selectorType = selector.type() == SelectorType.NAMESPACE_OR_HOLDER ? "namespace" : "test";
        return new SelectionIssue(
            "selection:" + issueKind + ":" + selectorType + ":" + selector.value(),
            diagnosticKind,
            selector.value(),
            message);
    }

    private static String appendDiscoveryDiagnostics(String message, InvalidTestDefinition invalidTest) {
        if (invalidTest == null || invalidTest.issues()
            .isEmpty()) {
            return message;
        }
        StringBuilder detailed = new StringBuilder(message).append(" Discovery diagnostic");
        if (invalidTest.issues()
            .size() > 1) {
            detailed.append('s');
        }
        detailed.append(": ");
        for (int i = 0; i < invalidTest.issues()
            .size(); i++) {
            if (i > 0) {
                detailed.append(" | ");
            }
            detailed.append(
                invalidTest.issues()
                    .get(i)
                    .message());
        }
        return detailed.toString();
    }

    private static <T> List<T> immutableList(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    @Desugar
    public record SelectionIssue(String id, String kind, String selector, String message) {

    }
}
