package com.gtnewhorizons.horizonqa.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.IIcon;

public class BlockDebugIInventory extends Block  {

    public static BlockDebugIInventory INSTANCE;

    public BlockDebugIInventory() {
        super(Material.sponge);
        setBlockTextureName("horizonqa:debug_iinv");
        setCreativeTab(CreativeTabs.tabInventory);
        setHardness(1.0f);
        setResistance(6f);
        setBlockName("Debug IInventory");
    }

}
