package com.gtnewhorizons.horizonqa.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class WandSelectionMoveMessage implements IMessage {

    private int dx;
    private int dy;
    private int dz;

    @SuppressWarnings("unused")
    public WandSelectionMoveMessage() {}

    public WandSelectionMoveMessage(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dx = buf.readByte();
        dy = buf.readByte();
        dz = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(dx);
        buf.writeByte(dy);
        buf.writeByte(dz);
    }

    static boolean isUnitAxisOffset(int dx, int dy, int dz) {
        return Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1;
    }

    public static final class Handler implements IMessageHandler<WandSelectionMoveMessage, IMessage> {

        @Override
        public IMessage onMessage(WandSelectionMoveMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || !isUnitAxisOffset(message.dx, message.dy, message.dz)) {
                return null;
            }
            ItemStack held = player.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemHorizonWand)) {
                return null;
            }
            if (ItemHorizonWand.moveSelection(held, message.dx, message.dy, message.dz)) {
                player.inventoryContainer.detectAndSendChanges();
            }
            return null;
        }
    }
}
