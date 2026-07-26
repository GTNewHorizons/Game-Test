package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestArguments;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;
import com.gtnewhorizons.horizonqa.internal.MethodSourceResolver.MethodSourceException;
import com.gtnewhorizons.horizonqa.internal.MethodSourceResolver.ResolvedArguments;

public class MethodSourceResolverTest {

    @Test
    public void explicitRowsPreserveEncounterOrderAndOrdinals() throws Exception {
        List<ResolvedArguments> resolved = resolve(ValidSources.class, "ordered");

        assertEquals(2, resolved.size());
        assertEquals(
            "second",
            resolved.get(0)
                .name());
        assertEquals(
            0,
            resolved.get(0)
                .ordinal());
        assertArrayEquals(
            new Object[] { "b", 2 },
            resolved.get(0)
                .arguments());
        assertEquals(
            "first",
            resolved.get(1)
                .name());
        assertEquals(
            1,
            resolved.get(1)
                .ordinal());
    }

    @Test
    public void arrayArgumentRemainsOneSuppliedValue() throws Exception {
        List<ResolvedArguments> resolved = resolve(ValidSources.class, "arrayValue");

        assertEquals(
            1,
            resolved.get(0)
                .arguments().length);
        assertArrayEquals(
            new String[] { "water", "lava" },
            (String[]) resolved.get(0)
                .arguments()[0]);
    }

    @Test
    public void rawObjectArrayRowsAreRejectedEvenForOneArrayParameter() throws Exception {
        MethodSourceException error = expectFailure(InvalidSources.class, "rawArrayRows");

        assertTrue(
            error.getMessage(),
            error.getMessage()
                .contains("every row must be GameTestArguments"));
        assertTrue(
            error.getMessage(),
            error.getMessage()
                .contains("[Ljava.lang.String;"));
    }

    @Test
    public void primitiveArrayElementsAreRejectedAsRows() throws Exception {
        MethodSourceException error = expectFailure(InvalidSources.class, "primitiveRows");

        assertTrue(
            error.getMessage(),
            error.getMessage()
                .contains("java.lang.Integer"));
        assertTrue(
            error.getMessage(),
            error.getMessage()
                .contains("every row must be GameTestArguments"));
    }

    @Test
    public void duplicateNamesAreRejected() throws Exception {
        MethodSourceException error = expectFailure(InvalidSources.class, "duplicates");

        assertTrue(
            error.getMessage(),
            error.getMessage()
                .contains("duplicate case name 'same'"));
    }

    @Test
    public void oversizedSourcesFailAtTheConfiguredLimit() throws Exception {
        MethodSourceException error = expectFailure(InvalidSources.class, "unbounded");

        assertTrue(
            error.getMessage(),
            error.getMessage()
                .contains("more than 256 argument rows"));
    }

    @Test
    public void providerFailuresPreserveTheirCause() throws Exception {
        MethodSourceException error = expectFailure(InvalidSources.class, "throwsFromProvider");

        assertTrue(error.getCause() instanceof IllegalStateException);
        assertEquals(
            "provider failed",
            error.getCause()
                .getMessage());
    }

    @Test
    public void classInitializationErrorsBecomeControlledFailures() throws Exception {
        MethodSourceException error = expectFailure(InitializerFailureSources.class, "initializationFailure");

        assertTrue(error.getCause() instanceof ExceptionInInitializerError);
        assertNotNull(
            error.getCause()
                .getCause());
        assertEquals(
            "initialization failed",
            error.getCause()
                .getCause()
                .getMessage());
    }

    @Test
    public void streamCloseFailureDoesNotMaskRowFailure() throws Exception {
        MethodSourceException error = expectFailure(InvalidSources.class, "rowAndCloseFailure");

        assertTrue(
            error.getMessage(),
            error.getMessage()
                .contains("every row must be GameTestArguments"));
        assertEquals(1, error.getSuppressed().length);
        assertTrue(error.getSuppressed()[0] instanceof IllegalStateException);
        assertEquals("close failed", error.getSuppressed()[0].getMessage());
    }

    @Test
    public void providerThreadDeathIsRethrown() throws Exception {
        try {
            resolve(FatalSources.class, "providerThreadDeath");
            fail("Expected ThreadDeath to be rethrown");
        } catch (ThreadDeath expected) {
            // Fatal control-flow errors must never be downgraded to discovery diagnostics.
        }
    }

    @Test
    public void iterationVirtualMachineErrorIsRethrown() throws Exception {
        try {
            resolve(FatalSources.class, "iterationVirtualMachineError");
            fail("Expected VirtualMachineError to be rethrown");
        } catch (TestVirtualMachineError expected) {
            assertEquals("iteration fatal", expected.getMessage());
        }
    }

    private static List<ResolvedArguments> resolve(Class<?> holder, String methodName) throws Exception {
        Method testMethod = Arrays.stream(holder.getDeclaredMethods())
            .filter(
                method -> method.getName()
                    .equals(methodName))
            .filter(method -> method.getParameterCount() > 0)
            .findFirst()
            .orElseThrow(() -> new AssertionError("test method not found: " + methodName));
        return MethodSourceResolver.resolve(testMethod, testMethod.getAnnotation(MethodSource.class));
    }

    private static MethodSourceException expectFailure(Class<?> holder, String methodName) throws Exception {
        try {
            resolve(holder, methodName);
            fail("Expected method source resolution to fail");
            return null;
        } catch (MethodSourceException e) {
            return e;
        }
    }

    public static final class ValidSources {

        @MethodSource
        public static void ordered(GameTestHelper helper, String value, int number) {}

        public static List<GameTestArguments> ordered() {
            return Arrays.asList(GameTestArguments.named("second", "b", 2), GameTestArguments.named("first", "a", 1));
        }

        @MethodSource
        public static void arrayValue(GameTestHelper helper, String[] values) {}

        public static GameTestArguments[] arrayValue() {
            return new GameTestArguments[] { GameTestArguments.named("fluids", new String[] { "water", "lava" }) };
        }
    }

    public static final class InvalidSources {

        @MethodSource
        public static void rawArrayRows(GameTestHelper helper, String[] values) {}

        public static List<String[]> rawArrayRows() {
            return Collections.singletonList(new String[] { "water", "lava" });
        }

        @MethodSource
        public static void primitiveRows(GameTestHelper helper, int value) {}

        public static int[] primitiveRows() {
            return new int[] { 1, 2 };
        }

        @MethodSource
        public static void duplicates(GameTestHelper helper, int value) {}

        public static List<GameTestArguments> duplicates() {
            return Arrays.asList(GameTestArguments.named("same", 1), GameTestArguments.named("same", 2));
        }

        @MethodSource
        public static void unbounded(GameTestHelper helper, int value) {}

        public static Stream<GameTestArguments> unbounded() {
            return Stream.generate(() -> GameTestArguments.of(1));
        }

        @MethodSource
        public static void throwsFromProvider(GameTestHelper helper, int value) {}

        public static List<GameTestArguments> throwsFromProvider() {
            throw new IllegalStateException("provider failed");
        }

        @MethodSource
        public static void rowAndCloseFailure(GameTestHelper helper, int value) {}

        public static Stream<?> rowAndCloseFailure() {
            return Stream.of("raw row")
                .onClose(() -> { throw new IllegalStateException("close failed"); });
        }
    }

    public static final class InitializerFailureSources {

        static {
            if (shouldFailInitialization()) {
                throw new IllegalStateException("initialization failed");
            }
        }

        @MethodSource
        public static void initializationFailure(GameTestHelper helper, int value) {}

        public static List<GameTestArguments> initializationFailure() {
            return Arrays.asList(GameTestArguments.of(1));
        }

        private static boolean shouldFailInitialization() {
            return true;
        }
    }

    public static final class FatalSources {

        @MethodSource
        public static void providerThreadDeath(GameTestHelper helper, int value) {}

        public static List<GameTestArguments> providerThreadDeath() {
            throw new ThreadDeath();
        }

        @MethodSource
        public static void iterationVirtualMachineError(GameTestHelper helper, int value) {}

        public static Stream<GameTestArguments> iterationVirtualMachineError() {
            return Stream.generate(() -> { throw new TestVirtualMachineError("iteration fatal"); });
        }
    }

    private static final class TestVirtualMachineError extends VirtualMachineError {

        TestVirtualMachineError(String message) {
            super(message);
        }
    }
}
