package com.gtnewhorizons.horizonqa.item;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class ItemHorizonWandSelectionResizeTest {

    @Test
    public void expandsAndShrinksEachSelectedSide() {
        ItemStack wand = completeWand(4, 8, 12, 2, 6, 10);

        assertTrue(ItemHorizonWand.resizeSelection(wand, 1, 0, 0, 1));
        assertEquals(5, coordinate(wand, ItemHorizonWand.TAG_POS1_X));
        assertTrue(ItemHorizonWand.resizeSelection(wand, -1, 0, 0, 1));
        assertEquals(1, coordinate(wand, ItemHorizonWand.TAG_POS2_X));
        assertTrue(ItemHorizonWand.resizeSelection(wand, 0, 1, 0, -1));
        assertEquals(7, coordinate(wand, ItemHorizonWand.TAG_POS1_Y));
        assertTrue(ItemHorizonWand.resizeSelection(wand, 0, 0, -1, -1));
        assertEquals(11, coordinate(wand, ItemHorizonWand.TAG_POS2_Z));
    }

    @Test
    public void choosesDistinctSidesForASingleBlockSelection() {
        ItemStack wand = completeWand(5, 5, 5, 5, 5, 5);

        assertTrue(ItemHorizonWand.resizeSelection(wand, 1, 0, 0, 1));
        assertEquals(5, coordinate(wand, ItemHorizonWand.TAG_POS1_X));
        assertEquals(6, coordinate(wand, ItemHorizonWand.TAG_POS2_X));
        assertTrue(ItemHorizonWand.resizeSelection(wand, -1, 0, 0, 1));
        assertEquals(4, coordinate(wand, ItemHorizonWand.TAG_POS1_X));
    }

    @Test
    public void neverShrinksPastOneBlock() {
        ItemStack wand = completeWand(5, 5, 5, 5, 5, 5);

        assertFalse(ItemHorizonWand.resizeSelection(wand, 1, 0, 0, -1));
        assertFalse(ItemHorizonWand.resizeSelection(wand, -1, 0, 0, -1));
        assertEquals(5, coordinate(wand, ItemHorizonWand.TAG_POS1_X));
        assertEquals(5, coordinate(wand, ItemHorizonWand.TAG_POS2_X));
    }

    @Test
    public void rejectsWorldLimitAndInvalidResizeSteps() {
        ItemStack wand = completeWand(0, 254, 0, 0, 255, 0);

        assertFalse(ItemHorizonWand.resizeSelection(wand, 0, 1, 0, 1));
        assertFalse(ItemHorizonWand.resizeSelection(wand, 1, 1, 0, 1));
        assertFalse(ItemHorizonWand.resizeSelection(wand, 1, 0, 0, 2));
    }

    @Test
    public void leavesLabelsAtTheirWorldCoordinates() {
        ItemStack wand = completeWand(1, 2, 3, 4, 5, 6);
        ItemHorizonWand.setLabel(wand, "fixed", 4, 5, 6);

        assertTrue(ItemHorizonWand.resizeSelection(wand, 1, 0, 0, 1));

        assertArrayEquals(
            new int[] { 4, 5, 6 },
            ItemHorizonWand.getLabels(wand)
                .get("fixed"));
    }

    private static int coordinate(ItemStack wand, String tag) {
        return wand.getTagCompound()
            .getInteger(tag);
    }

    private static ItemStack completeWand(int x1, int y1, int z1, int x2, int y2, int z2) {
        ItemStack wand = new ItemStack(new ItemHorizonWand());
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setBoolean(ItemHorizonWand.TAG_POS1_SET, true);
        nbt.setInteger(ItemHorizonWand.TAG_POS1_X, x1);
        nbt.setInteger(ItemHorizonWand.TAG_POS1_Y, y1);
        nbt.setInteger(ItemHorizonWand.TAG_POS1_Z, z1);
        nbt.setBoolean(ItemHorizonWand.TAG_POS2_SET, true);
        nbt.setInteger(ItemHorizonWand.TAG_POS2_X, x2);
        nbt.setInteger(ItemHorizonWand.TAG_POS2_Y, y2);
        nbt.setInteger(ItemHorizonWand.TAG_POS2_Z, z2);
        wand.setTagCompound(nbt);
        return wand;
    }
}
