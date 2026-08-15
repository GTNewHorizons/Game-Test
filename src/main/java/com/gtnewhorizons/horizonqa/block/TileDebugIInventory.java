package com.gtnewhorizons.horizonqa.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;

public class TileDebugIInventory extends TileEntity implements ISidedInventory {

    public final ItemStack inv[] = new ItemStack[3];
    public final ArrayList<String> allowedItemsIns[] = new ArrayList[2];
    public final boolean allowedSidesIns[] = new boolean[6];
    public final ArrayList<String> allowedItemsExt[] = new ArrayList[2];
    public final boolean allowedSidesExt[] = new boolean[6];
    public boolean disallowItemsExToggle = true;
    public boolean disallowItemsInToggle = true;
    public boolean disallowItemsToggle = true;
    public final ArrayList<String> allowedItems[] = new ArrayList[2];
    public int inventoryStackLimit = 64;
    public boolean failCheckIfNull = true;
    public boolean returnNullForNoItem = true;
    public boolean machineMode = true;

    public TileDebugIInventory() {
        allowedSidesIns[0] = true;
        allowedSidesIns[1] = true;
        allowedSidesIns[2] = true;
        allowedSidesIns[3] = true;
        allowedSidesIns[4] = true;
        allowedSidesIns[5] = true;
        allowedItemsIns[0] = new ArrayList<>();
        allowedItemsIns[1] = new ArrayList<>();
        allowedSidesExt[0] = true;
        allowedSidesExt[1] = true;
        allowedSidesExt[2] = true;
        allowedSidesExt[3] = true;
        allowedSidesExt[4] = true;
        allowedSidesExt[5] = true;
        allowedItemsExt[0] = new ArrayList<>();
        allowedItemsExt[1] = new ArrayList<>();
        allowedItems[0] = new ArrayList<>();
        allowedItems[1] = new ArrayList<>();
    }

    public enum Error {
        InvalidItemIns0,
        InvalidItemIns1,
        InvalidItemIns2,
        InvalidItemExt0,
        InvalidItemExt1,
        InvalidItemExt2,
    }

    public Error lastError = null;

    @Override
    public int[] getAccessibleSlotsFromSide(int p_94128_1_) {
        return new int[]{0, 1, 2};
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack item, int side) {
        if ( slot > 2 || slot < 0 || side < 0 || side >= 6 ) return false; //todo: log negative itemslot
        if ( item == null ) return slot != 2 && allowedSidesExt[side] && !failCheckIfNull;
        return slot < 2 && allowedSidesIns[side] && Boolean.logicalXor(item.getItem() != null && allowedItemsIns[slot].contains(item.getItem().delegate.name()), disallowItemsInToggle);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack item, int side) {
        if ( slot > 2 || slot < 0 || side < 0 || side >= 6 ) return false; //todo: log negative itemslot
        if ( slot == 2 && machineMode && (inv[0] == null || inv[1] == null)) return false;
        if ( item == null ) return allowedSidesExt[side] && !failCheckIfNull;
        return slot == 2 || slot < 2 && allowedSidesExt[side] && Boolean.logicalXor(item.getItem() != null && allowedItemsExt[slot].contains(item.getItem().delegate.name()), disallowItemsExToggle);
    }

    @Override
    public int getSizeInventory() {
        return 3;
    }

    @Override
    public ItemStack getStackInSlot(int slotIn) {
        if ( slotIn < 0 || slotIn > 2 )return null; //todo: log
        if ( slotIn == 2 ) {
            ItemStack stack = getOutputItem();
            stack.setStackDisplayName("getStackInSlot: " + (inv[0] == null ? null : ((inv[0].getItem() == null ? null : inv[0].getItem().delegate.name())+" (" + inv[0].stackSize + ")")) + " | " + (inv[1] == null ? null : ((inv[1].getItem() == null ? null : inv[1].getItem().delegate.name())+" (" + inv[1].stackSize + ")")));
            return stack;
        }
        ItemStack stack = inv[slotIn];
        if ( stack == null ) return returnNullForNoItem ? null : getOutputItem().setStackDisplayName("getStackInSlot: null");
        stack.setStackDisplayName("getStackInSlot: " + (inv[slotIn] == null ? null : ((inv[slotIn].getItem() == null ? null : inv[slotIn].getItem().delegate.name())+" (" + inv[slotIn].stackSize + ")")));
        return stack;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int index) {
        return null; //todo
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {

    }

    @Override
    public String getInventoryName() {
        return "Debug Inventory";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
    }

    @Override
    public int getInventoryStackLimit() {
        return this.inventoryStackLimit;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {

    }

    @Override
    public void closeInventory() {

    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if ( slot < 0 || slot > 2 ) return false; //todo: log
        return slot < 2 && Boolean.logicalXor(stack.getItem() != null && allowedItems[slot].contains(stack.getItem().delegate.name()), disallowItemsToggle);
    }

    // gets a blank output item, not necessarily the item in slot 2
    public ItemStack getOutputItem() {
        return null;
    }

}
