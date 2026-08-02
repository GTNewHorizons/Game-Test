package com.gtnewhorizons.horizonqa.network;

import com.gtnewhorizons.horizonqa.HorizonQAMod;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class HorizonQANetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(HorizonQAMod.MODID);

    private HorizonQANetwork() {}

    public static void init() {
        CHANNEL.registerMessage(WandLabelMessage.Handler.class, WandLabelMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(WandSelectionMoveMessage.Handler.class, WandSelectionMoveMessage.class, 1, Side.SERVER);
        CHANNEL.registerMessage(
            WandSelectionResizeMessage.Handler.class,
            WandSelectionResizeMessage.class,
            2,
            Side.SERVER);
        CHANNEL.registerMessage(WandLabelMoveMessage.Handler.class, WandLabelMoveMessage.class, 3, Side.SERVER);
    }
}
