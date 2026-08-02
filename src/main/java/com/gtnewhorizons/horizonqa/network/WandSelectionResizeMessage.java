package com.gtnewhorizons.horizonqa.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class WandSelectionResizeMessage implements IMessage {

    private int sideX;
    private int sideY;
    private int sideZ;
    private int amount;

    @SuppressWarnings("unused")
    public WandSelectionResizeMessage() {}

    public WandSelectionResizeMessage(int sideX, int sideY, int sideZ, int amount) {
        this.sideX = sideX;
        this.sideY = sideY;
        this.sideZ = sideZ;
        this.amount = amount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sideX = buf.readByte();
        sideY = buf.readByte();
        sideZ = buf.readByte();
        amount = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(sideX);
        buf.writeByte(sideY);
        buf.writeByte(sideZ);
        buf.writeByte(amount);
    }

    static boolean isValidStep(int sideX, int sideY, int sideZ, int amount) {
        return Math.abs(sideX) + Math.abs(sideY) + Math.abs(sideZ) == 1 && Math.abs(amount) == 1;
    }

    public static final class Handler implements IMessageHandler<WandSelectionResizeMessage, IMessage> {

        @Override
        public IMessage onMessage(WandSelectionResizeMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || !isValidStep(message.sideX, message.sideY, message.sideZ, message.amount)) {
                return null;
            }
            ItemStack held = player.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemHorizonWand)) {
                return null;
            }
            if (ItemHorizonWand.resizeSelection(held, message.sideX, message.sideY, message.sideZ, message.amount)) {
                player.inventoryContainer.detectAndSendChanges();
            }
            return null;
        }
    }
}
