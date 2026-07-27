package com.gtnewhorizons.horizonqa.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GameTestArgumentsTest {

    @Test
    public void namedArgumentsExposeAStableNameAndDefensiveCopies() {
        Object[] rest = { "LV" };
        GameTestArguments arguments = GameTestArguments.named("lv", 32, rest);
        rest[0] = "changed";

        assertTrue(arguments.isNamed());
        assertEquals("lv", arguments.name());
        assertArrayEquals(new Object[] { 32, "LV" }, arguments.arguments());

        Object[] returned = arguments.arguments();
        returned[0] = 0;
        assertArrayEquals(new Object[] { 32, "LV" }, arguments.arguments());
    }

    @Test
    public void unnamedArgumentsLeaveNamingToTheirSourceIndex() {
        GameTestArguments arguments = GameTestArguments.of("water");

        assertFalse(arguments.isNamed());
        assertNull(arguments.name());
    }

    @Test(expected = IllegalArgumentException.class)
    public void namesMustBeSafeForTestIds() {
        GameTestArguments.named("not safe", 32);
    }

    @Test
    public void arrayValueIsPreservedAsOneArgument() {
        String[] value = { "water", "lava" };

        GameTestArguments arguments = GameTestArguments.named("fluids", value);

        assertEquals(1, arguments.arguments().length);
        assertTrue(arguments.arguments()[0] instanceof String[]);
        assertArrayEquals(value, (String[]) arguments.arguments()[0]);
    }

    @Test
    public void nullValueIsPreservedAsOneArgument() {
        GameTestArguments arguments = GameTestArguments.named("missing", null);

        assertArrayEquals(new Object[] { null }, arguments.arguments());
    }

    @Test
    public void explicitValuesPreserveTrailingNullAndArrayArguments() {
        String[] fluids = { "water", "lava" };
        GameTestArguments arguments = GameTestArguments.namedValues("mixed", new Object[] { 2, fluids, null });

        Object[] values = arguments.arguments();
        assertEquals(3, values.length);
        assertEquals(2, values[0]);
        assertArrayEquals(fluids, (String[]) values[1]);
        assertNull(values[2]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void namesHaveABoundedLength() {
        GameTestArguments.named(repeat('a', 129), 32);
    }

    private static String repeat(char value, int count) {
        char[] repeated = new char[count];
        java.util.Arrays.fill(repeated, value);
        return new String(repeated);
    }
}
