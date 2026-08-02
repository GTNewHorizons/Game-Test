package com.gtnewhorizons.horizonqa.item;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.ItemStack;

import org.junit.Test;

public class ItemHorizonWandLabelMoveTest {

    @Test
    public void movesOnlyTheNamedLabel() {
        ItemStack wand = new ItemStack(new ItemHorizonWand());
        ItemHorizonWand.setLabel(wand, "controller", 4, 5, 6);
        ItemHorizonWand.setLabel(wand, "bus", 8, 9, 10);

        assertTrue(ItemHorizonWand.moveLabel(wand, "controller", -1, 0, 0));

        assertArrayEquals(
            new int[] { 3, 5, 6 },
            ItemHorizonWand.getLabels(wand)
                .get("controller"));
        assertArrayEquals(
            new int[] { 8, 9, 10 },
            ItemHorizonWand.getLabels(wand)
                .get("bus"));
    }

    @Test
    public void rejectsInvalidMovesAndOccupiedCoordinates() {
        ItemStack wand = new ItemStack(new ItemHorizonWand());
        ItemHorizonWand.setLabel(wand, "first", 1, 2, 3);
        ItemHorizonWand.setLabel(wand, "second", 2, 2, 3);

        assertFalse(ItemHorizonWand.moveLabel(wand, "first", 1, 0, 0));
        assertFalse(ItemHorizonWand.moveLabel(wand, "missing", 0, 1, 0));
        assertFalse(ItemHorizonWand.moveLabel(wand, "first", 1, 1, 0));
        assertArrayEquals(
            new int[] { 1, 2, 3 },
            ItemHorizonWand.getLabels(wand)
                .get("first"));
    }

    @Test
    public void respectsWorldHeight() {
        ItemStack wand = new ItemStack(new ItemHorizonWand());
        ItemHorizonWand.setLabel(wand, "bottom", 1, 0, 3);

        assertFalse(ItemHorizonWand.moveLabel(wand, "bottom", 0, -1, 0));
    }
}
