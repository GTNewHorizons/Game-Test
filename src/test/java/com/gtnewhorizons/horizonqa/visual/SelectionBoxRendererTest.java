package com.gtnewhorizons.horizonqa.visual;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;

import cpw.mods.fml.common.eventhandler.Event;

public class SelectionBoxRendererTest {

    @Test
    public void rightClickingBlockRoutesInteractionToWand() {
        PlayerInteractEvent event = event(PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK);

        SelectionBoxRenderer.routeRightClickToWand(event, new ItemStack(new ItemHorizonWand()));

        assertEquals(Event.Result.DENY, event.useBlock);
        assertEquals(Event.Result.ALLOW, event.useItem);
        assertFalse(event.isCanceled());
    }

    @Test
    public void rightClickingBlockWithAnotherItemKeepsDefaultInteraction() {
        PlayerInteractEvent event = event(PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK);

        SelectionBoxRenderer.routeRightClickToWand(event, new ItemStack(new Item()));

        assertEquals(Event.Result.DEFAULT, event.useBlock);
        assertEquals(Event.Result.DEFAULT, event.useItem);
        assertFalse(event.isCanceled());
    }

    private static PlayerInteractEvent event(PlayerInteractEvent.Action action) {
        return new PlayerInteractEvent(null, action, 1, 2, 3, 1, null);
    }
}
