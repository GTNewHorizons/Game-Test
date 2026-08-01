package com.gtnewhorizons.horizonqa.visual;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class SelectionBoxRenderer {

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        EntityPlayer player = event.entityPlayer;
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemHorizonWand)) return;

        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            routeRightClickToWand(event, held);
            return;
        }
        if (event.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        if (player.worldObj.isRemote) return;

        ItemHorizonWand.setPos1(held, player, event.x, event.y, event.z);
        event.setCanceled(true);
    }

    static void routeRightClickToWand(PlayerInteractEvent event, ItemStack held) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || held == null
            || !(held.getItem() instanceof ItemHorizonWand)) {
            return;
        }

        event.useBlock = Event.Result.DENY;
        event.useItem = Event.Result.ALLOW;
    }
}
