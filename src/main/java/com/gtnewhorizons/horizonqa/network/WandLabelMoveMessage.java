package com.gtnewhorizons.horizonqa.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class WandLabelMoveMessage implements IMessage {

    private String name;
    private int dx;
    private int dy;
    private int dz;

    @SuppressWarnings("unused")
    public WandLabelMoveMessage() {}

    public WandLabelMoveMessage(String name, int dx, int dy, int dz) {
        this.name = name;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        name = ByteBufUtils.readUTF8String(buf);
        dx = buf.readByte();
        dy = buf.readByte();
        dz = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
        buf.writeByte(dx);
        buf.writeByte(dy);
        buf.writeByte(dz);
    }

    static boolean isValid(String name, int dx, int dy, int dz) {
        return ItemHorizonWand.isValidLabelName(name) && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1;
    }

    public static final class Handler implements IMessageHandler<WandLabelMoveMessage, IMessage> {

        @Override
        public IMessage onMessage(WandLabelMoveMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || !isValid(message.name, message.dx, message.dy, message.dz)) return null;
            ItemStack held = player.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemHorizonWand)) return null;
            if (ItemHorizonWand.moveLabel(held, message.name, message.dx, message.dy, message.dz)) {
                player.inventoryContainer.detectAndSendChanges();
            }
            return null;
        }
    }
}
