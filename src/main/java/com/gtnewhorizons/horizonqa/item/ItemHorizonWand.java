package com.gtnewhorizons.horizonqa.item;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.gtnewhorizons.horizonqa.structure.StructureAnnotations;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemHorizonWand extends Item {

    public static ItemHorizonWand INSTANCE;

    public static final String TAG_POS1_X = "pos1X";
    public static final String TAG_POS1_Y = "pos1Y";
    public static final String TAG_POS1_Z = "pos1Z";
    public static final String TAG_POS1_SET = "pos1Set";
    public static final String TAG_POS2_X = "pos2X";
    public static final String TAG_POS2_Y = "pos2Y";
    public static final String TAG_POS2_Z = "pos2Z";
    public static final String TAG_POS2_SET = "pos2Set";
    public static final String TAG_PENDING = "pending";
    public static final String TAG_LABELS = "labels";
    public static final String TAG_EXPORT_NAME = "exportName";
    private static final String TAG_LABEL_X = "x";
    private static final String TAG_LABEL_Y = "y";
    private static final String TAG_LABEL_Z = "z";

    // dx/dy/dz offsets indexed by face side (0=down,1=up,2=north,3=south,4=west,5=east)
    private static final int[][] FACE_NORMALS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };

    public ItemHorizonWand() {
        super();
        setUnlocalizedName("horizonqa.wand");
        setTextureName("minecraft:blaze_rod");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            int tx = x, ty = y, tz = z;
            if (player.isSneaking() && side >= 0 && side < 6) {
                tx += FACE_NORMALS[side][0];
                ty += FACE_NORMALS[side][1];
                tz += FACE_NORMALS[side][2];
            }
            NBTTagCompound nbt = getOrCreateNBT(stack);
            if (nbt.getBoolean(TAG_PENDING)) {
                setPos2(stack, player, tx, ty, tz);
            } else {
                setPos1(stack, player, tx, ty, tz);
            }
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            int[] pos = getTargetedPosition(player);
            NBTTagCompound nbt = getOrCreateNBT(stack);
            if (nbt.getBoolean(TAG_PENDING)) {
                setPos2(stack, player, pos[0], pos[1], pos[2]);
            } else {
                setPos1(stack, player, pos[0], pos[1], pos[2]);
            }
        }
        return stack;
    }

    public static int[] getTargetedPosition(EntityPlayer player) {
        return getTargetedPosition(player, true);
    }

    public static int[] getTargetedPositionFromHit(int x, int y, int z, int side, boolean sneaking) {
        if (sneaking && side >= 0 && side < 6) {
            return new int[] { x + FACE_NORMALS[side][0], y + FACE_NORMALS[side][1], z + FACE_NORMALS[side][2] };
        }
        return new int[] { x, y, z };
    }

    private static int[] getTargetedPosition(EntityPlayer player, boolean includeSurfaceOffset) {
        double dist = getBlockReachDistance(player);

        Vec3 start = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        Vec3 end = Vec3.createVectorHelper(
            start.xCoord + look.xCoord * dist,
            start.yCoord + look.yCoord * dist,
            start.zCoord + look.zCoord * dist);

        MovingObjectPosition hit = player.worldObj.rayTraceBlocks(start, end);

        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            int tx = hit.blockX;
            int ty = hit.blockY;
            int tz = hit.blockZ;
            if (includeSurfaceOffset && player.isSneaking() && hit.sideHit >= 0 && hit.sideHit < 6) {
                tx += FACE_NORMALS[hit.sideHit][0];
                ty += FACE_NORMALS[hit.sideHit][1];
                tz += FACE_NORMALS[hit.sideHit][2];
            }
            return new int[] { tx, ty, tz };
        } else {
            return new int[] { MathHelper.floor_double(end.xCoord), MathHelper.floor_double(end.yCoord),
                MathHelper.floor_double(end.zCoord) };
        }
    }

    private static double getBlockReachDistance(EntityPlayer player) {
        if (player.worldObj.isRemote) {
            return getClientBlockReachDistance();
        }
        if (player instanceof EntityPlayerMP) {
            return ((EntityPlayerMP) player).theItemInWorldManager.getBlockReachDistance();
        }
        return 5.0;
    }

    @SideOnly(Side.CLIENT)
    private static double getClientBlockReachDistance() {
        return Minecraft.getMinecraft().playerController.getBlockReachDistance();
    }

    public static void setPos1(ItemStack stack, EntityPlayer player, int x, int y, int z) {
        NBTTagCompound nbt = getOrCreateNBT(stack);
        nbt.setInteger(TAG_POS1_X, x);
        nbt.setInteger(TAG_POS1_Y, y);
        nbt.setInteger(TAG_POS1_Z, z);
        nbt.setBoolean(TAG_POS1_SET, true);
        nbt.setBoolean(TAG_POS2_SET, false);
        nbt.setBoolean(TAG_PENDING, true);
        nbt.removeTag(TAG_LABELS);
        nbt.removeTag(TAG_EXPORT_NAME);
        player.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN
                    + String.format(StatCollector.translateToLocal("horizonqa.wand.pos1.set"), x, y, z)));
    }

    public static void setPos2(ItemStack stack, EntityPlayer player, int x, int y, int z) {
        NBTTagCompound nbt = getOrCreateNBT(stack);
        nbt.setInteger(TAG_POS2_X, x);
        nbt.setInteger(TAG_POS2_Y, y);
        nbt.setInteger(TAG_POS2_Z, z);
        nbt.setBoolean(TAG_POS2_SET, true);
        nbt.setBoolean(TAG_PENDING, false);
        player.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.AQUA
                    + String.format(StatCollector.translateToLocal("horizonqa.wand.pos2.set"), x, y, z)));
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        NBTTagCompound nbt = stack.getTagCompound();

        if (nbt == null || !nbt.getBoolean(TAG_POS1_SET)) {
            list.add(StatCollector.translateToLocal("horizonqa.wand.tooltip.pos1.unset"));
        } else {
            list.add(
                String.format(
                    StatCollector.translateToLocal("horizonqa.wand.tooltip.pos1"),
                    nbt.getInteger(TAG_POS1_X),
                    nbt.getInteger(TAG_POS1_Y),
                    nbt.getInteger(TAG_POS1_Z)));
        }

        boolean pending = nbt != null && nbt.getBoolean(TAG_PENDING);
        if (nbt == null || !nbt.getBoolean(TAG_POS2_SET)) {
            list.add(
                StatCollector.translateToLocal(
                    pending ? "horizonqa.wand.tooltip.pos2.pending" : "horizonqa.wand.tooltip.pos2.unset"));
        } else {
            list.add(
                String.format(
                    StatCollector.translateToLocal("horizonqa.wand.tooltip.pos2"),
                    nbt.getInteger(TAG_POS2_X),
                    nbt.getInteger(TAG_POS2_Y),
                    nbt.getInteger(TAG_POS2_Z)));
        }

        if (nbt != null && nbt.getBoolean(TAG_POS1_SET) && nbt.getBoolean(TAG_POS2_SET)) {
            int dx = Math.abs(nbt.getInteger(TAG_POS2_X) - nbt.getInteger(TAG_POS1_X)) + 1;
            int dy = Math.abs(nbt.getInteger(TAG_POS2_Y) - nbt.getInteger(TAG_POS1_Y)) + 1;
            int dz = Math.abs(nbt.getInteger(TAG_POS2_Z) - nbt.getInteger(TAG_POS1_Z)) + 1;
            list.add(String.format(StatCollector.translateToLocal("horizonqa.wand.tooltip.size"), dx, dy, dz));
        }

        list.add(StatCollector.translateToLocal("horizonqa.wand.tooltip.surface_mode"));
        list.add(StatCollector.translateToLocal("horizonqa.wand.tooltip.label_key"));
        list.add(StatCollector.translateToLocal("horizonqa.wand.tooltip.freecam_key"));
    }

    public static NBTTagCompound getOrCreateNBT(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    public static boolean isValidLabelName(String name) {
        return StructureAnnotations.isValidLabelName(name);
    }

    public static int labelCount(ItemStack stack) {
        return getLabels(stack).size();
    }

    public static Map<String, int[]> getLabels(ItemStack stack) {
        TreeMap<String, int[]> labels = new TreeMap<>();
        if (stack == null || !stack.hasTagCompound()) {
            return labels;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (!root.hasKey(TAG_LABELS)) {
            return labels;
        }
        NBTTagCompound labelsTag = root.getCompoundTag(TAG_LABELS);
        Set<String> keys = labelsTag.func_150296_c();
        for (String name : keys) {
            NBTTagCompound labelTag = labelsTag.getCompoundTag(name);
            labels.put(
                name,
                new int[] { labelTag.getInteger(TAG_LABEL_X), labelTag.getInteger(TAG_LABEL_Y),
                    labelTag.getInteger(TAG_LABEL_Z) });
        }
        return labels;
    }

    public static String getLabelAt(ItemStack stack, int x, int y, int z) {
        for (Map.Entry<String, int[]> entry : getLabels(stack).entrySet()) {
            int[] pos = entry.getValue();
            if (pos[0] == x && pos[1] == y && pos[2] == z) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static LabelMutationResult setLabel(ItemStack stack, String name, int x, int y, int z) {
        if (!isValidLabelName(name)) {
            return LabelMutationResult.invalidName(name);
        }
        NBTTagCompound root = getOrCreateNBT(stack);
        NBTTagCompound labels = root.getCompoundTag(TAG_LABELS);
        String oldNameAtPos = null;

        Set<String> keys = labels.func_150296_c();
        for (String existingName : keys) {
            NBTTagCompound labelTag = labels.getCompoundTag(existingName);
            int lx = labelTag.getInteger(TAG_LABEL_X);
            int ly = labelTag.getInteger(TAG_LABEL_Y);
            int lz = labelTag.getInteger(TAG_LABEL_Z);
            boolean samePos = lx == x && ly == y && lz == z;
            if (existingName.equals(name) && !samePos) {
                return LabelMutationResult.duplicateName(name, lx, ly, lz);
            }
            if (samePos) {
                oldNameAtPos = existingName;
            }
        }

        if (oldNameAtPos != null && !oldNameAtPos.equals(name)) {
            labels.removeTag(oldNameAtPos);
        }

        NBTTagCompound label = new NBTTagCompound();
        label.setInteger(TAG_LABEL_X, x);
        label.setInteger(TAG_LABEL_Y, y);
        label.setInteger(TAG_LABEL_Z, z);
        labels.setTag(name, label);
        root.setTag(TAG_LABELS, labels);
        return LabelMutationResult.success(oldNameAtPos, name, x, y, z);
    }

    public static boolean removeLabel(ItemStack stack, String name) {
        if (stack == null || !stack.hasTagCompound()) {
            return false;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (!root.hasKey(TAG_LABELS)) {
            return false;
        }
        NBTTagCompound labels = root.getCompoundTag(TAG_LABELS);
        if (!labels.hasKey(name)) {
            return false;
        }
        labels.removeTag(name);
        if (labels.func_150296_c()
            .isEmpty()) {
            root.removeTag(TAG_LABELS);
        } else {
            root.setTag(TAG_LABELS, labels);
        }
        return true;
    }

    /** Moves one named label by one block along a single axis without changing the selection. */
    public static boolean moveLabel(ItemStack stack, String name, int dx, int dy, int dz) {
        if (stack == null || !stack.hasTagCompound()
            || Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1
            || !isValidLabelName(name)) {
            return false;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (!root.hasKey(TAG_LABELS)) return false;
        NBTTagCompound labels = root.getCompoundTag(TAG_LABELS);
        if (!labels.hasKey(name)) return false;

        NBTTagCompound label = labels.getCompoundTag(name);
        int x = label.getInteger(TAG_LABEL_X);
        int y = label.getInteger(TAG_LABEL_Y);
        int z = label.getInteger(TAG_LABEL_Z);
        if (!canMoveCoordinate(x, dx, -30_000_000, 30_000_000) || !canMoveCoordinate(y, dy, 0, 255)
            || !canMoveCoordinate(z, dz, -30_000_000, 30_000_000)) {
            return false;
        }

        int movedX = x + dx;
        int movedY = y + dy;
        int movedZ = z + dz;
        for (String otherName : labels.func_150296_c()) {
            if (name.equals(otherName)) continue;
            NBTTagCompound other = labels.getCompoundTag(otherName);
            if (other.getInteger(TAG_LABEL_X) == movedX && other.getInteger(TAG_LABEL_Y) == movedY
                && other.getInteger(TAG_LABEL_Z) == movedZ) {
                return false;
            }
        }

        label.setInteger(TAG_LABEL_X, movedX);
        label.setInteger(TAG_LABEL_Y, movedY);
        label.setInteger(TAG_LABEL_Z, movedZ);
        labels.setTag(name, label);
        root.setTag(TAG_LABELS, labels);
        return true;
    }

    public static int clearLabels(ItemStack stack) {
        int count = labelCount(stack);
        if (stack != null && stack.hasTagCompound()) {
            stack.getTagCompound()
                .removeTag(TAG_LABELS);
        }
        return count;
    }

    public static boolean hasCompleteSelection(ItemStack stack) {
        NBTTagCompound nbt = stack != null ? stack.getTagCompound() : null;
        return nbt != null && nbt.getBoolean(TAG_POS1_SET) && nbt.getBoolean(TAG_POS2_SET);
    }

    /**
     * Moves a complete wand selection and all of its labels by one block along a single axis.
     *
     * @return {@code true} when the selection was moved, or {@code false} for an incomplete selection, an invalid
     *         offset, or a move outside Minecraft's coordinate limits
     */
    public static boolean moveSelection(ItemStack stack, int dx, int dy, int dz) {
        if (!hasCompleteSelection(stack) || Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
            return false;
        }

        NBTTagCompound nbt = stack.getTagCompound();
        if (!canMoveCoordinate(nbt.getInteger(TAG_POS1_X), dx, -30_000_000, 30_000_000)
            || !canMoveCoordinate(nbt.getInteger(TAG_POS1_Y), dy, 0, 255)
            || !canMoveCoordinate(nbt.getInteger(TAG_POS1_Z), dz, -30_000_000, 30_000_000)
            || !canMoveCoordinate(nbt.getInteger(TAG_POS2_X), dx, -30_000_000, 30_000_000)
            || !canMoveCoordinate(nbt.getInteger(TAG_POS2_Y), dy, 0, 255)
            || !canMoveCoordinate(nbt.getInteger(TAG_POS2_Z), dz, -30_000_000, 30_000_000)) {
            return false;
        }

        NBTTagCompound labels = nbt.hasKey(TAG_LABELS) ? nbt.getCompoundTag(TAG_LABELS) : null;
        if (labels != null) {
            for (String name : labels.func_150296_c()) {
                NBTTagCompound label = labels.getCompoundTag(name);
                if (!canMoveCoordinate(label.getInteger(TAG_LABEL_X), dx, -30_000_000, 30_000_000)
                    || !canMoveCoordinate(label.getInteger(TAG_LABEL_Y), dy, 0, 255)
                    || !canMoveCoordinate(label.getInteger(TAG_LABEL_Z), dz, -30_000_000, 30_000_000)) {
                    return false;
                }
            }
        }

        nbt.setInteger(TAG_POS1_X, nbt.getInteger(TAG_POS1_X) + dx);
        nbt.setInteger(TAG_POS1_Y, nbt.getInteger(TAG_POS1_Y) + dy);
        nbt.setInteger(TAG_POS1_Z, nbt.getInteger(TAG_POS1_Z) + dz);
        nbt.setInteger(TAG_POS2_X, nbt.getInteger(TAG_POS2_X) + dx);
        nbt.setInteger(TAG_POS2_Y, nbt.getInteger(TAG_POS2_Y) + dy);
        nbt.setInteger(TAG_POS2_Z, nbt.getInteger(TAG_POS2_Z) + dz);

        if (labels != null) {
            for (String name : labels.func_150296_c()) {
                NBTTagCompound label = labels.getCompoundTag(name);
                label.setInteger(TAG_LABEL_X, label.getInteger(TAG_LABEL_X) + dx);
                label.setInteger(TAG_LABEL_Y, label.getInteger(TAG_LABEL_Y) + dy);
                label.setInteger(TAG_LABEL_Z, label.getInteger(TAG_LABEL_Z) + dz);
                labels.setTag(name, label);
            }
            nbt.setTag(TAG_LABELS, labels);
        }
        return true;
    }

    /**
     * Resizes one side of a complete wand selection by one block. A positive {@code amount} moves the requested side
     * outward and a negative amount moves it inward. Labels remain at their world coordinates.
     *
     * @return {@code true} when the side was resized, or {@code false} for invalid input, an incomplete selection, a
     *         side outside Minecraft's coordinate limits, or an attempt to shrink below one block
     */
    public static boolean resizeSelection(ItemStack stack, int sideX, int sideY, int sideZ, int amount) {
        if (!hasCompleteSelection(stack) || Math.abs(sideX) + Math.abs(sideY) + Math.abs(sideZ) != 1
            || Math.abs(amount) != 1) {
            return false;
        }

        NBTTagCompound nbt = stack.getTagCompound();
        if (sideX != 0) {
            return resizeAxis(nbt, TAG_POS1_X, TAG_POS2_X, sideX > 0, amount, -30_000_000, 30_000_000);
        }
        if (sideY != 0) {
            return resizeAxis(nbt, TAG_POS1_Y, TAG_POS2_Y, sideY > 0, amount, 0, 255);
        }
        return resizeAxis(nbt, TAG_POS1_Z, TAG_POS2_Z, sideZ > 0, amount, -30_000_000, 30_000_000);
    }

    private static boolean resizeAxis(NBTTagCompound nbt, String pos1Tag, String pos2Tag, boolean positiveSide,
        int amount, int worldMin, int worldMax) {
        int pos1 = nbt.getInteger(pos1Tag);
        int pos2 = nbt.getInteger(pos2Tag);
        int boundary = positiveSide ? Math.max(pos1, pos2) : Math.min(pos1, pos2);
        int opposite = positiveSide ? Math.min(pos1, pos2) : Math.max(pos1, pos2);
        int coordinateOffset = positiveSide ? amount : -amount;
        if (!canMoveCoordinate(boundary, coordinateOffset, worldMin, worldMax)) {
            return false;
        }

        int resized = boundary + coordinateOffset;
        if ((positiveSide && resized < opposite) || (!positiveSide && resized > opposite)) {
            return false;
        }

        boolean resizePos1 = positiveSide ? pos1 > pos2 : pos1 <= pos2;
        nbt.setInteger(resizePos1 ? pos1Tag : pos2Tag, resized);
        return true;
    }

    private static boolean canMoveCoordinate(int coordinate, int offset, int min, int max) {
        long moved = (long) coordinate + offset;
        return moved >= min && moved <= max;
    }

    public static boolean isInsideSelection(ItemStack stack, int x, int y, int z) {
        if (!hasCompleteSelection(stack)) {
            return false;
        }
        NBTTagCompound nbt = stack.getTagCompound();
        int minX = Math.min(nbt.getInteger(TAG_POS1_X), nbt.getInteger(TAG_POS2_X));
        int minY = Math.min(nbt.getInteger(TAG_POS1_Y), nbt.getInteger(TAG_POS2_Y));
        int minZ = Math.min(nbt.getInteger(TAG_POS1_Z), nbt.getInteger(TAG_POS2_Z));
        int maxX = Math.max(nbt.getInteger(TAG_POS1_X), nbt.getInteger(TAG_POS2_X));
        int maxY = Math.max(nbt.getInteger(TAG_POS1_Y), nbt.getInteger(TAG_POS2_Y));
        int maxZ = Math.max(nbt.getInteger(TAG_POS1_Z), nbt.getInteger(TAG_POS2_Z));
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public static int outsideSelectionLabelCount(ItemStack stack) {
        if (!hasCompleteSelection(stack)) {
            return 0;
        }
        int count = 0;
        for (int[] pos : getLabels(stack).values()) {
            if (!isInsideSelection(stack, pos[0], pos[1], pos[2])) {
                count++;
            }
        }
        return count;
    }

    public static final class LabelMutationResult {

        public enum Status {
            SUCCESS,
            INVALID_NAME,
            DUPLICATE_NAME
        }

        public final Status status;
        public final String oldName;
        public final String name;
        public final int x;
        public final int y;
        public final int z;

        private LabelMutationResult(Status status, String oldName, String name, int x, int y, int z) {
            this.status = status;
            this.oldName = oldName;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static LabelMutationResult success(String oldName, String name, int x, int y, int z) {
            return new LabelMutationResult(Status.SUCCESS, oldName, name, x, y, z);
        }

        private static LabelMutationResult invalidName(String name) {
            return new LabelMutationResult(Status.INVALID_NAME, null, name, 0, 0, 0);
        }

        private static LabelMutationResult duplicateName(String name, int x, int y, int z) {
            return new LabelMutationResult(Status.DUPLICATE_NAME, null, name, x, y, z);
        }
    }
}
