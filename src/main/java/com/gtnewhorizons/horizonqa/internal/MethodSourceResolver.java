package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.BaseStream;
import java.util.stream.IntStream;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.GameTestArguments;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;

final class MethodSourceResolver {

    private static final int MAX_CASES = 256;
    private static final int MAX_CASE_NAME_LENGTH = 128;

    private MethodSourceResolver() {}

    static List<ResolvedArguments> resolve(Method testMethod, MethodSource annotation) throws MethodSourceException {

        String sourceName = annotation.value()
            .isEmpty() ? testMethod.getName() : annotation.value();
        Method provider = findProvider(testMethod, sourceName);
        validateProvider(provider);

        Object source = invokeProvider(provider);
        if (source == null) {
            throw new MethodSourceException("method source '" + sourceName + "' returned null");
        }

        BaseStream<?, ?> stream = source instanceof BaseStream<?, ?> ? (BaseStream<?, ?>) source : null;
        List<ResolvedArguments> resolved = new ArrayList<>();
        Set<String> names = new HashSet<>();
        try (BaseStream<?, ?> ignored = stream) {
            Iterator<?> iterator = createIterator(source, sourceName);
            int index = 0;
            while (iterator.hasNext()) {
                if (index >= MAX_CASES) {
                    throw new MethodSourceException(
                        "method source '" + sourceName + "' produced more than " + MAX_CASES + " argument rows");
                }
                ResolvedArguments invocation = resolveElement(testMethod, iterator.next(), index);
                if (!names.add(invocation.name())) {
                    throw new MethodSourceException(
                        "method source '" + sourceName + "' produced duplicate case name '" + invocation.name() + "'");
                }
                resolved.add(invocation);
                index++;
            }
            if (resolved.isEmpty()) {
                throw new MethodSourceException("method source '" + sourceName + "' produced no arguments");
            }
            return resolved;
        } catch (MethodSourceException e) {
            throw e;
        } catch (RuntimeException | Error e) {
            throw wrapFailure("method source '" + sourceName + "' failed while producing arguments: ", e);
        }
    }

    private static Method findProvider(Method testMethod, String sourceName) throws MethodSourceException {
        Method provider = null;
        boolean foundNamedMethod = false;
        for (Method candidate : testMethod.getDeclaringClass()
            .getDeclaredMethods()) {
            if (!candidate.getName()
                .equals(sourceName)) {
                continue;
            }
            foundNamedMethod = true;
            if (candidate.getParameterCount() != 0) {
                continue;
            }
            provider = candidate;
            break;
        }
        if (provider == null) {
            String detail = foundNamedMethod ? "must take no parameters" : "was not found in the test holder";
            throw new MethodSourceException("method source '" + sourceName + "' " + detail);
        }
        return provider;
    }

    private static void validateProvider(Method provider) throws MethodSourceException {
        int modifiers = provider.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            throw new MethodSourceException("method source '" + provider.getName() + "' must be public static");
        }
        if (provider.getReturnType() == Void.TYPE) {
            throw new MethodSourceException(
                "method source '" + provider.getName() + "' must return a stream, iterable, iterator, or array");
        }
    }

    private static Object invokeProvider(Method provider) throws MethodSourceException {
        try {
            return provider.invoke(null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw wrapFailure("method source '" + provider.getName() + "' threw ", cause);
        } catch (ReflectiveOperationException | RuntimeException | Error e) {
            throw wrapFailure("could not invoke method source '" + provider.getName() + "': ", e);
        }
    }

    private static Iterator<?> createIterator(Object source, String sourceName) throws MethodSourceException {
        try {
            return sourceIterator(source, sourceName);
        } catch (MethodSourceException e) {
            throw e;
        } catch (RuntimeException | Error e) {
            throw wrapFailure("method source '" + sourceName + "' failed while creating its iterator: ", e);
        }
    }

    private static Iterator<?> sourceIterator(Object source, String sourceName) throws MethodSourceException {
        if (source instanceof BaseStream<?, ?>stream) {
            return stream.iterator();
        }
        if (source instanceof Iterable<?>iterable) {
            return iterable.iterator();
        }
        if (source instanceof Iterator<?>iterator) {
            return iterator;
        }
        if (source.getClass()
            .isArray()) {
            return IntStream.range(0, Array.getLength(source))
                .mapToObj(index -> Array.get(source, index))
                .iterator();
        }
        throw new MethodSourceException(
            "method source '" + sourceName
                + "' returned "
                + source.getClass()
                    .getName()
                + "; expected a stream, iterable, iterator, or array");
    }

    private static ResolvedArguments resolveElement(Method testMethod, Object element, int index)
        throws MethodSourceException {

        String name = Integer.toString(index);
        if (!(element instanceof GameTestArguments supplied)) {
            String actual = element == null ? "null"
                : element.getClass()
                    .getName();
            throw new MethodSourceException(
                "argument row " + index + " has type " + actual + "; every row must be GameTestArguments");
        }
        if (supplied.isNamed()) {
            name = supplied.name();
        }
        if (name.length() > MAX_CASE_NAME_LENGTH) {
            throw new MethodSourceException(
                "argument row " + index + " case name exceeds " + MAX_CASE_NAME_LENGTH + " characters");
        }
        Object[] arguments = supplied.arguments();

        validateArguments(testMethod, arguments, index);
        return new ResolvedArguments(name, index, arguments);
    }

    private static void validateArguments(Method testMethod, Object[] arguments, int row) throws MethodSourceException {

        Class<?>[] parameterTypes = testMethod.getParameterTypes();
        int expected = parameterTypes.length - 1;
        if (arguments.length != expected) {
            throw new MethodSourceException(
                "argument row " + row
                    + " supplied "
                    + arguments.length
                    + " value(s), but the test requires "
                    + expected);
        }
        for (int i = 0; i < arguments.length; i++) {
            if (!accepts(parameterTypes[i + 1], arguments[i])) {
                String actual = arguments[i] == null ? "null"
                    : arguments[i].getClass()
                        .getName();
                throw new MethodSourceException(
                    "argument row " + row
                        + " value "
                        + i
                        + " has type "
                        + actual
                        + ", which cannot be passed to "
                        + parameterTypes[i + 1].getName());
            }
        }
    }

    private static boolean accepts(Class<?> target, Object value) {
        if (value == null) {
            return !target.isPrimitive();
        }
        if (!target.isPrimitive()) {
            return target.isInstance(value);
        }
        Class<?> actual = value.getClass();
        if (target == Boolean.TYPE) return actual == Boolean.class;
        if (target == Character.TYPE) return actual == Character.class;
        if (target == Byte.TYPE) return actual == Byte.class;
        if (target == Short.TYPE) return actual == Byte.class || actual == Short.class;
        if (target == Integer.TYPE) return actual == Byte.class || actual == Short.class
            || actual == Character.class
            || actual == Integer.class;
        if (target == Long.TYPE) return actual == Byte.class || actual == Short.class
            || actual == Character.class
            || actual == Integer.class
            || actual == Long.class;
        if (target == Float.TYPE) return actual == Byte.class || actual == Short.class
            || actual == Character.class
            || actual == Integer.class
            || actual == Long.class
            || actual == Float.class;
        if (target == Double.TYPE) return actual == Byte.class || actual == Short.class
            || actual == Character.class
            || actual == Integer.class
            || actual == Long.class
            || actual == Float.class
            || actual == Double.class;
        return false;
    }

    private static String describe(Throwable cause) {
        String message = cause.getMessage();
        return cause.getClass()
            .getName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    private static MethodSourceException wrapFailure(String context, Throwable failure) {
        rethrowIfFatal(failure);
        return new MethodSourceException(context + describe(failure), failure);
    }

    private static void rethrowIfFatal(Throwable failure) {
        if (failure instanceof ThreadDeath death) {
            throw death;
        }
        if (failure instanceof VirtualMachineError fatal) {
            throw fatal;
        }
    }

    @Desugar
    record ResolvedArguments(String name, int ordinal, Object[] arguments) {

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ResolvedArguments that)) return false;
            return ordinal == that.ordinal && Objects.equals(name, that.name)
                && Arrays.deepEquals(arguments, that.arguments);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(name, ordinal) + Arrays.deepHashCode(arguments);
        }

        @Override
        public String toString() {
            return "ResolvedArguments[name=" + name
                + ", ordinal="
                + ordinal
                + ", arguments="
                + Arrays.deepToString(arguments)
                + ']';
        }
    }

    static final class MethodSourceException extends Exception {

        MethodSourceException(String message) {
            super(message);
        }

        MethodSourceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
