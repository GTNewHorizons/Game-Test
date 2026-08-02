package com.gtnewhorizons.horizonqa.item;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.ItemStack;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand.LabelMutationResult;

public class ItemHorizonWandLabelEditTest {

    @Test
    public void createsRenamesAndRemovesALabelAtOneCoordinate() {
        ItemStack wand = new ItemStack(new ItemHorizonWand());

        assertEquals(LabelMutationResult.Status.SUCCESS, ItemHorizonWand.setLabel(wand, "old_name", 2, 3, 4).status);
        assertEquals(LabelMutationResult.Status.SUCCESS, ItemHorizonWand.setLabel(wand, "new_name", 2, 3, 4).status);
        assertNull(
            ItemHorizonWand.getLabels(wand)
                .get("old_name"));
        assertArrayEquals(
            new int[] { 2, 3, 4 },
            ItemHorizonWand.getLabels(wand)
                .get("new_name"));
        assertTrue(ItemHorizonWand.removeLabel(wand, "new_name"));
        assertFalse(ItemHorizonWand.removeLabel(wand, "new_name"));
    }

    @Test
    public void rejectsRenamingToANameUsedAtAnotherCoordinate() {
        ItemStack wand = new ItemStack(new ItemHorizonWand());
        ItemHorizonWand.setLabel(wand, "first", 1, 2, 3);
        ItemHorizonWand.setLabel(wand, "second", 4, 5, 6);

        LabelMutationResult result = ItemHorizonWand.setLabel(wand, "second", 1, 2, 3);

        assertEquals(LabelMutationResult.Status.DUPLICATE_NAME, result.status);
        assertArrayEquals(
            new int[] { 1, 2, 3 },
            ItemHorizonWand.getLabels(wand)
                .get("first"));
    }
}
