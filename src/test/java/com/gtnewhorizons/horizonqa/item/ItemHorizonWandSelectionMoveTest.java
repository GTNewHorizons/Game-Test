package com.gtnewhorizons.horizonqa.item;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class ItemHorizonWandSelectionMoveTest {

    @Test
    public void movesBothCornersAndAllLabelsAlongOneAxis() {
        ItemStack wand = completeWand(10, 20, 30, 12, 24, 36);
        assertEquals(
            ItemHorizonWand.LabelMutationResult.Status.SUCCESS,
            ItemHorizonWand.setLabel(wand, "inside", 11, 21, 31).status);
        assertEquals(
            ItemHorizonWand.LabelMutationResult.Status.SUCCESS,
            ItemHorizonWand.setLabel(wand, "outside", 100, 40, -8).status);

        assertTrue(ItemHorizonWand.moveSelection(wand, -1, 0, 0));

        NBTTagCompound nbt = wand.getTagCompound();
        assertEquals(9, nbt.getInteger(ItemHorizonWand.TAG_POS1_X));
        assertEquals(11, nbt.getInteger(ItemHorizonWand.TAG_POS2_X));
        assertEquals(20, nbt.getInteger(ItemHorizonWand.TAG_POS1_Y));
        assertEquals(36, nbt.getInteger(ItemHorizonWand.TAG_POS2_Z));
        Map<String, int[]> labels = ItemHorizonWand.getLabels(wand);
        assertArrayEquals(new int[] { 10, 21, 31 }, labels.get("inside"));
        assertArrayEquals(new int[] { 99, 40, -8 }, labels.get("outside"));
    }

    @Test
    public void rejectsDiagonalAndMultiBlockOffsets() {
        ItemStack wand = completeWand(1, 2, 3, 4, 5, 6);

        assertFalse(ItemHorizonWand.moveSelection(wand, 1, 1, 0));
        assertFalse(ItemHorizonWand.moveSelection(wand, 2, 0, 0));
        assertFalse(ItemHorizonWand.moveSelection(wand, 0, 0, 0));

        assertEquals(
            1,
            wand.getTagCompound()
                .getInteger(ItemHorizonWand.TAG_POS1_X));
        assertEquals(
            5,
            wand.getTagCompound()
                .getInteger(ItemHorizonWand.TAG_POS2_Y));
    }

    @Test
    public void rejectsMovesOutsideWorldHeight() {
        ItemStack wand = completeWand(1, 0, 3, 4, 5, 6);
        assertFalse(ItemHorizonWand.moveSelection(wand, 0, -1, 0));

        wand = completeWand(1, 250, 3, 4, 255, 6);
        assertFalse(ItemHorizonWand.moveSelection(wand, 0, 1, 0));
    }

    @Test
    public void rejectsMoveWhenALabelWouldLeaveWorldHeight() {
        ItemStack wand = completeWand(1, 10, 3, 4, 15, 6);
        assertEquals(
            ItemHorizonWand.LabelMutationResult.Status.SUCCESS,
            ItemHorizonWand.setLabel(wand, "top", 2, 255, 4).status);

        assertFalse(ItemHorizonWand.moveSelection(wand, 0, 1, 0));

        assertEquals(
            10,
            wand.getTagCompound()
                .getInteger(ItemHorizonWand.TAG_POS1_Y));
        assertArrayEquals(
            new int[] { 2, 255, 4 },
            ItemHorizonWand.getLabels(wand)
                .get("top"));
    }

    @Test
    public void rejectsIncompleteSelections() {
        ItemStack wand = new ItemStack(new ItemHorizonWand());
        wand.setTagCompound(new NBTTagCompound());

        assertFalse(ItemHorizonWand.moveSelection(wand, 1, 0, 0));
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
