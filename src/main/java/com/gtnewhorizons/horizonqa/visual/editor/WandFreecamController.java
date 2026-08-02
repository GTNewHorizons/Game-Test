package com.gtnewhorizons.horizonqa.visual.editor;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;
import com.gtnewhorizons.horizonqa.network.HorizonQANetwork;
import com.gtnewhorizons.horizonqa.network.WandLabelMoveMessage;
import com.gtnewhorizons.horizonqa.network.WandSelectionMoveMessage;
import com.gtnewhorizons.horizonqa.network.WandSelectionResizeMessage;
import com.gtnewhorizons.horizonqa.visual.editor.SelectionGizmoMath.Direction;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.RenderTickEvent;

public final class WandFreecamController {

    public static final WandFreecamController INSTANCE = new WandFreecamController();
    public static final KeyBinding FREECAM_KEY = new KeyBinding(
        "key.horizonqa.freecam",
        Keyboard.KEY_F10,
        "key.categories.horizonqa");

    private static final double NORMAL_SPEED = 0.65;
    private static final double FAST_SPEED = 1.8;
    private static final double LABEL_PICK_DISTANCE = 256.0;
    private static final double DRAG_PIXELS_PER_BLOCK = 14.0;
    private static final double RESIZE_HANDLE_HALF_SIZE = 0.16;
    private static final int ARROW_SEGMENTS = 12;

    private EntityOtherPlayerMP camera;
    private EntityClientPlayerMP anchoredPlayer;
    private World cameraWorld;
    private EntityLivingBase previousViewEntity;
    private MovementInput previousMovementInput;
    private MovementInput frozenMovementInput;
    private int previousThirdPersonView;
    private float anchoredYaw;
    private float anchoredPitch;
    private float anchoredPreviousYaw;
    private float anchoredPreviousPitch;
    private float anchoredYawHead;
    private float anchoredRenderYawOffset;
    private Direction hoveredDirection;
    private Direction draggingDirection;
    private WandEditorTool editorTool = WandEditorTool.MOVE;
    private String selectedLabelName;
    private double dragPixels;
    private boolean dragMoved;
    private double dragAxisOriginX;
    private double dragAxisOriginY;
    private double dragAxisOriginZ;
    private double dragStartAxisParameter;
    private int dragAppliedBlocks;
    private float dragCameraYaw;
    private float dragCameraPitch;
    private boolean editorCameraDragging;
    private CursorRay editorCursorRay;
    private int editorMouseX;
    private int editorMouseY;
    private int editorWidth;
    private int editorHeight;
    private boolean cursorInsideViewport;
    private final FloatBuffer modelViewMatrix = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projectionMatrix = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer nearPoint = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer farPoint = BufferUtils.createFloatBuffer(4);

    private WandFreecamController() {}

    public static void registerKeyBinding() {
        ClientRegistry.registerKeyBinding(FREECAM_KEY);
    }

    public static boolean isActive() {
        return INSTANCE.camera != null;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase == Phase.START) {
            if (camera != null) {
                suppressWorldInteractionKeys(mc);
            }
            return;
        }

        while (FREECAM_KEY.isPressed()) {
            if (camera != null) {
                leaveFreecam(mc, true);
            } else if (mc.currentScreen == null && isWandHeld(mc)) {
                enterFreecam(mc);
            }
        }

        if (camera == null) return;
        if (!isSessionValid(mc) || !isWandHeld(mc) || !isEditorUi(mc)) {
            leaveFreecam(mc, false);
            return;
        }

        transferPlayerLook();
        suppressWorldInteractionKeys(mc);
        moveCamera(mc);
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent event) {
        if (camera == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!isSessionValid(mc)) {
            leaveFreecam(mc, false);
            return;
        }
        if (event.phase == Phase.START && editorCameraDragging && mc.currentScreen instanceof WandEditorScreen) {
            rotateCameraFromEditor(Mouse.getDX(), Mouse.getDY());
        }
        transferPlayerLook();
        if (event.phase == Phase.END) {
            restorePlayerLook();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseInput(MouseEvent event) {
        if (camera == null || Minecraft.getMinecraft().currentScreen != null) return;

        if (event.button < 0) {
            if (draggingDirection != null) {
                dragSelection(event.dx, event.dy);
                event.setCanceled(true);
            }
            return;
        }

        if (event.button == 0) {
            if (event.buttonstate) {
                startDragging();
            } else {
                stopDragging(true);
            }
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (camera != null && mc.currentScreen == null) {
            suppressWorldInteractionKeys(mc);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (camera == null || event.entityPlayer == null || !event.entityPlayer.worldObj.isRemote) return;
        if (event.entityPlayer == Minecraft.getMinecraft().thePlayer) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackEntity(AttackEntityEvent event) {
        if (isLocalClientPlayer(event.entityPlayer)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(EntityInteractEvent event) {
        if (isLocalClientPlayer(event.entityPlayer)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderEditorOverlay(RenderGameOverlayEvent.Pre event) {
        // GuiIngameForge sets up the GUI projection only after ALL is allowed through.
        if (camera != null && event.type != RenderGameOverlayEvent.ElementType.ALL) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        if (camera != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (camera == null || anchoredPlayer == null) return;
        ItemStack wand = anchoredPlayer.getHeldItem();

        float partialTicks = event.partialTicks;
        double viewX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        double viewY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        double viewZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;

        GL11.glPushMatrix();
        GL11.glTranslated(-viewX, -viewY, -viewZ);
        editorCursorRay = editorCursorRayFromMatrices();
        if (draggingDirection != null && editorCursorRay != null) {
            updateEditorSelectionDrag(editorCursorRay);
        }

        SelectionGeometry selection = ItemHorizonWand.hasCompleteSelection(wand)
            ? SelectionGeometry.from(wand.getTagCompound())
            : null;
        double[] labelCenter = editorTool == WandEditorTool.MOVE_LABEL ? selectedLabelCenter(wand) : null;
        if ((editorTool == WandEditorTool.MOVE_LABEL && labelCenter == null)
            || (editorTool != WandEditorTool.MOVE_LABEL && selection == null)) {
            hoveredDirection = null;
            GL11.glPopMatrix();
            return;
        }
        double gizmoCenterX = labelCenter != null ? labelCenter[0] : selection.centerX;
        double gizmoCenterY = labelCenter != null ? labelCenter[1] : selection.centerY;
        double gizmoCenterZ = labelCenter != null ? labelCenter[2] : selection.centerZ;
        double eyeX = camera.posX;
        double eyeY = camera.posY + camera.getEyeHeight();
        double eyeZ = camera.posZ;
        double scale = gizmoScale(distance(eyeX, eyeY, eyeZ, gizmoCenterX, gizmoCenterY, gizmoCenterZ));
        hoveredDirection = draggingDirection != null ? draggingDirection
            : pickEditorDirection(selection, labelCenter, scale);
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        renderGizmo(selection, labelCenter, scale, 0.22f, 2.0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        renderGizmo(selection, labelCenter, scale, 0.95f, 3.0f);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private void enterFreecam(Minecraft mc) {
        anchoredPlayer = mc.thePlayer;
        cameraWorld = mc.theWorld;
        previousViewEntity = mc.renderViewEntity;
        previousMovementInput = anchoredPlayer.movementInput;
        frozenMovementInput = new MovementInput();
        previousThirdPersonView = mc.gameSettings.thirdPersonView;
        savePlayerLook();

        camera = new EntityOtherPlayerMP(cameraWorld, anchoredPlayer.getGameProfile());
        camera.setPositionAndRotation(
            anchoredPlayer.posX,
            anchoredPlayer.posY,
            anchoredPlayer.posZ,
            anchoredPlayer.rotationYaw,
            anchoredPlayer.rotationPitch);
        camera.lastTickPosX = camera.prevPosX = camera.posX;
        camera.lastTickPosY = camera.prevPosY = camera.posY;
        camera.lastTickPosZ = camera.prevPosZ = camera.posZ;
        camera.prevRotationYaw = camera.rotationYaw;
        camera.prevRotationPitch = camera.rotationPitch;
        camera.noClip = true;
        camera.setInvisible(true);

        anchoredPlayer.movementInput = frozenMovementInput;
        mc.gameSettings.thirdPersonView = 0;
        mc.renderViewEntity = camera;
        mc.displayGuiScreen(new WandEditorScreen());
        anchoredPlayer.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("horizonqa.wand.freecam.enabled")));
    }

    private void leaveFreecam(Minecraft mc, boolean notify) {
        if (camera == null) return;
        boolean closeEditorScreen = isEditorUi(mc);
        restorePlayerLook();

        if (anchoredPlayer != null && anchoredPlayer.movementInput == frozenMovementInput) {
            anchoredPlayer.movementInput = previousMovementInput;
        }
        if (mc.renderViewEntity == camera) {
            if (mc.theWorld == cameraWorld && previousViewEntity != null) {
                mc.renderViewEntity = previousViewEntity;
            } else {
                mc.renderViewEntity = mc.thePlayer;
            }
        }
        mc.gameSettings.thirdPersonView = previousThirdPersonView;

        EntityClientPlayerMP playerToNotify = anchoredPlayer;
        camera = null;
        anchoredPlayer = null;
        cameraWorld = null;
        previousViewEntity = null;
        previousMovementInput = null;
        frozenMovementInput = null;
        hoveredDirection = null;
        draggingDirection = null;
        editorTool = WandEditorTool.MOVE;
        selectedLabelName = null;
        dragPixels = 0.0;
        dragMoved = false;
        dragAxisOriginX = 0.0;
        dragAxisOriginY = 0.0;
        dragAxisOriginZ = 0.0;
        dragStartAxisParameter = 0.0;
        dragAppliedBlocks = 0;
        dragCameraYaw = 0.0f;
        dragCameraPitch = 0.0f;
        editorCameraDragging = false;
        editorCursorRay = null;
        editorMouseX = 0;
        editorMouseY = 0;
        editorWidth = 0;
        editorHeight = 0;
        cursorInsideViewport = false;
        if (closeEditorScreen) {
            mc.displayGuiScreen(null);
        }
        if (notify && playerToNotify != null) {
            playerToNotify.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GRAY + StatCollector.translateToLocal("horizonqa.wand.freecam.disabled")));
        }
    }

    private boolean isSessionValid(Minecraft mc) {
        return anchoredPlayer != null && mc.thePlayer == anchoredPlayer
            && cameraWorld != null
            && mc.theWorld == cameraWorld
            && mc.renderViewEntity == camera;
    }

    private static boolean isWandHeld(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return false;
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemHorizonWand;
    }

    private static boolean isEditorUi(Minecraft mc) {
        return mc.currentScreen instanceof WandEditorScreen || mc.currentScreen instanceof WandEditorLabelPrompt;
    }

    private boolean isLocalClientPlayer(EntityPlayer player) {
        return camera != null && player != null
            && player.worldObj.isRemote
            && player == Minecraft.getMinecraft().thePlayer;
    }

    private void savePlayerLook() {
        anchoredYaw = anchoredPlayer.rotationYaw;
        anchoredPitch = anchoredPlayer.rotationPitch;
        anchoredPreviousYaw = anchoredPlayer.prevRotationYaw;
        anchoredPreviousPitch = anchoredPlayer.prevRotationPitch;
        anchoredYawHead = anchoredPlayer.rotationYawHead;
        anchoredRenderYawOffset = anchoredPlayer.renderYawOffset;
    }

    private void transferPlayerLook() {
        if (anchoredPlayer == null || camera == null) return;
        float yawDelta = anchoredPlayer.rotationYaw - anchoredYaw;
        float pitchDelta = anchoredPlayer.rotationPitch - anchoredPitch;
        if (draggingDirection != null) {
            camera.rotationYaw = dragCameraYaw;
            camera.rotationPitch = dragCameraPitch;
            camera.prevRotationYaw = dragCameraYaw;
            camera.prevRotationPitch = dragCameraPitch;
        } else if (yawDelta != 0.0f || pitchDelta != 0.0f) {
            camera.rotationYaw += yawDelta;
            camera.rotationPitch = clamp(camera.rotationPitch + pitchDelta, -90.0f, 90.0f);
            camera.prevRotationYaw = camera.rotationYaw;
            camera.prevRotationPitch = camera.rotationPitch;
        }
        restorePlayerLook();
    }

    private void restorePlayerLook() {
        if (anchoredPlayer == null) return;
        anchoredPlayer.rotationYaw = anchoredYaw;
        anchoredPlayer.rotationPitch = anchoredPitch;
        anchoredPlayer.prevRotationYaw = anchoredPreviousYaw;
        anchoredPlayer.prevRotationPitch = anchoredPreviousPitch;
        anchoredPlayer.rotationYawHead = anchoredYawHead;
        anchoredPlayer.renderYawOffset = anchoredRenderYawOffset;
    }

    private void moveCamera(Minecraft mc) {
        camera.lastTickPosX = camera.prevPosX = camera.posX;
        camera.lastTickPosY = camera.prevPosY = camera.posY;
        camera.lastTickPosZ = camera.prevPosZ = camera.posZ;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof WandEditorScreen)) return;
        double forward = physicalKeyDown(mc.gameSettings.keyBindForward) ? 1.0 : 0.0;
        forward -= physicalKeyDown(mc.gameSettings.keyBindBack) ? 1.0 : 0.0;
        double right = physicalKeyDown(mc.gameSettings.keyBindRight) ? 1.0 : 0.0;
        right -= physicalKeyDown(mc.gameSettings.keyBindLeft) ? 1.0 : 0.0;
        double vertical = physicalKeyDown(mc.gameSettings.keyBindJump) ? 1.0 : 0.0;
        vertical -= physicalKeyDown(mc.gameSettings.keyBindSneak) ? 1.0 : 0.0;
        double magnitude = Math.sqrt(forward * forward + right * right + vertical * vertical);
        if (magnitude == 0.0) return;

        double speed = physicalKeyDown(mc.gameSettings.keyBindSprint) ? FAST_SPEED : NORMAL_SPEED;
        forward = forward / magnitude * speed;
        right = right / magnitude * speed;
        vertical = vertical / magnitude * speed;
        double yaw = Math.toRadians(camera.rotationYaw);
        double dx = -Math.sin(yaw) * forward - Math.cos(yaw) * right;
        double dz = Math.cos(yaw) * forward - Math.sin(yaw) * right;

        camera.setPosition(camera.posX + dx, camera.posY + vertical, camera.posZ + dz);
    }

    private static boolean physicalKeyDown(KeyBinding binding) {
        int keyCode = binding.getKeyCode();
        if (keyCode >= 0) {
            return Keyboard.isKeyDown(keyCode);
        }
        int mouseButton = keyCode + 100;
        return mouseButton >= 0 && Mouse.isButtonDown(mouseButton);
    }

    private static void suppressWorldInteractionKeys(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindPickBlock.getKeyCode(), false);
    }

    private void startDragging() {
        if (anchoredPlayer == null || camera == null) return;
        ItemStack wand = anchoredPlayer.getHeldItem();
        SelectionGeometry selection = ItemHorizonWand.hasCompleteSelection(wand)
            ? SelectionGeometry.from(wand.getTagCompound())
            : null;
        double[] labelCenter = editorTool == WandEditorTool.MOVE_LABEL ? selectedLabelCenter(wand) : null;
        if ((editorTool == WandEditorTool.MOVE_LABEL && labelCenter == null)
            || (editorTool != WandEditorTool.MOVE_LABEL && selection == null)) return;

        double centerX = labelCenter != null ? labelCenter[0] : selection.centerX;
        double centerY = labelCenter != null ? labelCenter[1] : selection.centerY;
        double centerZ = labelCenter != null ? labelCenter[2] : selection.centerZ;
        double eyeX = camera.posX;
        double eyeY = camera.posY + camera.getEyeHeight();
        double eyeZ = camera.posZ;
        double scale = gizmoScale(distance(eyeX, eyeY, eyeZ, centerX, centerY, centerZ));
        if (Minecraft.getMinecraft().currentScreen instanceof WandEditorScreen) {
            draggingDirection = cursorInsideViewport ? hoveredDirection : null;
            if (draggingDirection != null && editorCursorRay == null) {
                draggingDirection = null;
            } else if (draggingDirection != null) {
                double[] axisOrigin = labelCenter != null ? labelCenter : dragAxisOrigin(selection, draggingDirection);
                dragAxisOriginX = axisOrigin[0];
                dragAxisOriginY = axisOrigin[1];
                dragAxisOriginZ = axisOrigin[2];
                dragStartAxisParameter = SelectionGizmoMath.axisParameter(
                    editorCursorRay.originX,
                    editorCursorRay.originY,
                    editorCursorRay.originZ,
                    editorCursorRay.directionX,
                    editorCursorRay.directionY,
                    editorCursorRay.directionZ,
                    dragAxisOriginX,
                    dragAxisOriginY,
                    dragAxisOriginZ,
                    draggingDirection);
                if (Double.isNaN(dragStartAxisParameter)) {
                    draggingDirection = null;
                }
            }
        } else {
            draggingDirection = pickDirection(eyeX, eyeY, eyeZ, centerX, centerY, centerZ, scale);
        }
        dragPixels = 0.0;
        dragMoved = false;
        dragAppliedBlocks = 0;
        if (draggingDirection != null) {
            dragCameraYaw = camera.rotationYaw;
            dragCameraPitch = camera.rotationPitch;
        }
    }

    private void dragSelection(int mouseDx, int mouseDy) {
        if (draggingDirection == null || camera == null) return;
        Vec3 look = camera.getLookVec();
        double yaw = Math.toRadians(camera.rotationYaw);
        double rightX = -Math.cos(yaw);
        double rightY = 0.0;
        double rightZ = -Math.sin(yaw);
        double upX = rightY * look.zCoord - rightZ * look.yCoord;
        double upY = rightZ * look.xCoord - rightX * look.zCoord;
        double upZ = rightX * look.yCoord - rightY * look.xCoord;
        double screenX = draggingDirection.dx * rightX + draggingDirection.dy * rightY + draggingDirection.dz * rightZ;
        double screenY = draggingDirection.dx * upX + draggingDirection.dy * upY + draggingDirection.dz * upZ;
        double screenLength = Math.sqrt(screenX * screenX + screenY * screenY);
        if (screenLength < 0.08) return;

        dragPixels += (mouseDx * screenX + mouseDy * screenY) / screenLength;
        int moves = 0;
        while (Math.abs(dragPixels) >= DRAG_PIXELS_PER_BLOCK && moves < 16) {
            int sign = dragPixels > 0.0 ? 1 : -1;
            if (!sendSelectionMove(draggingDirection, sign)) {
                dragPixels = 0.0;
                break;
            }
            dragPixels -= sign * DRAG_PIXELS_PER_BLOCK;
            dragMoved = true;
            moves++;
        }
    }

    private void stopDragging(boolean nudgeIfClick) {
        if (draggingDirection != null && nudgeIfClick && !dragMoved) {
            sendSelectionEdit(draggingDirection, 1);
        }
        draggingDirection = null;
        dragPixels = 0.0;
        dragMoved = false;
        dragAppliedBlocks = 0;
    }

    private void updateEditorSelectionDrag(CursorRay ray) {
        double parameter = SelectionGizmoMath.axisParameter(
            ray.originX,
            ray.originY,
            ray.originZ,
            ray.directionX,
            ray.directionY,
            ray.directionZ,
            dragAxisOriginX,
            dragAxisOriginY,
            dragAxisOriginZ,
            draggingDirection);
        if (Double.isNaN(parameter)) return;

        double worldDelta = parameter - dragStartAxisParameter;
        if (Math.abs(worldDelta) > 0.05) {
            dragMoved = true;
        }
        int targetBlocks = (int) Math.round(worldDelta);
        int remaining = targetBlocks - dragAppliedBlocks;
        for (int edits = 0; remaining != 0 && edits < 64; edits++) {
            int sign = remaining > 0 ? 1 : -1;
            if (!sendSelectionEdit(draggingDirection, sign)) break;
            dragAppliedBlocks += sign;
            remaining -= sign;
            dragMoved = true;
        }
    }

    private boolean sendSelectionEdit(Direction direction, int sign) {
        if (editorTool == WandEditorTool.RESIZE) return sendSelectionResize(direction, sign);
        if (editorTool == WandEditorTool.MOVE_LABEL) return sendLabelMove(direction, sign);
        return sendSelectionMove(direction, sign);
    }

    private boolean sendSelectionMove(Direction direction, int sign) {
        if (anchoredPlayer == null) return false;
        ItemStack wand = anchoredPlayer.getHeldItem();
        int dx = direction.dx * sign;
        int dy = direction.dy * sign;
        int dz = direction.dz * sign;

        if (ItemHorizonWand.moveSelection(wand, dx, dy, dz)) {
            hoveredDirection = direction;
            HorizonQANetwork.CHANNEL.sendToServer(new WandSelectionMoveMessage(dx, dy, dz));
            return true;
        }
        return false;
    }

    private boolean sendSelectionResize(Direction direction, int amount) {
        if (anchoredPlayer == null) return false;
        ItemStack wand = anchoredPlayer.getHeldItem();
        if (ItemHorizonWand.resizeSelection(wand, direction.dx, direction.dy, direction.dz, amount)) {
            hoveredDirection = direction;
            HorizonQANetwork.CHANNEL
                .sendToServer(new WandSelectionResizeMessage(direction.dx, direction.dy, direction.dz, amount));
            return true;
        }
        return false;
    }

    private boolean sendLabelMove(Direction direction, int sign) {
        if (anchoredPlayer == null || selectedLabelName == null) return false;
        ItemStack wand = anchoredPlayer.getHeldItem();
        int dx = direction.dx * sign;
        int dy = direction.dy * sign;
        int dz = direction.dz * sign;
        if (ItemHorizonWand.moveLabel(wand, selectedLabelName, dx, dy, dz)) {
            hoveredDirection = direction;
            HorizonQANetwork.CHANNEL.sendToServer(new WandLabelMoveMessage(selectedLabelName, dx, dy, dz));
            return true;
        }
        return false;
    }

    private Direction pickDirection(double eyeX, double eyeY, double eyeZ, double centerX, double centerY,
        double centerZ, double scale) {
        Vec3 look = camera.getLookVec();
        return SelectionGizmoMath
            .pickDirection(eyeX, eyeY, eyeZ, look.xCoord, look.yCoord, look.zCoord, centerX, centerY, centerZ, scale);
    }

    private CursorRay editorCursorRayFromMatrices() {
        if (!cursorInsideViewport || editorWidth <= 0 || editorHeight <= 0) {
            return null;
        }

        modelViewMatrix.clear();
        projectionMatrix.clear();
        viewport.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewMatrix);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projectionMatrix);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

        int viewportX = viewport.get(0);
        int viewportY = viewport.get(1);
        int viewportWidth = viewport.get(2);
        int viewportHeight = viewport.get(3);
        float windowX = (float) (viewportX + (editorMouseX + 0.5) / editorWidth * viewportWidth);
        float windowY = (float) (viewportY + (editorHeight - editorMouseY - 0.5) / editorHeight * viewportHeight);
        nearPoint.clear();
        rewindProjectionBuffers();
        if (!GLU.gluUnProject(windowX, windowY, 0.0f, modelViewMatrix, projectionMatrix, viewport, nearPoint)) {
            return null;
        }
        farPoint.clear();
        rewindProjectionBuffers();
        if (!GLU.gluUnProject(windowX, windowY, 1.0f, modelViewMatrix, projectionMatrix, viewport, farPoint)) {
            return null;
        }

        double originX = nearPoint.get(0);
        double originY = nearPoint.get(1);
        double originZ = nearPoint.get(2);
        return new CursorRay(
            originX,
            originY,
            originZ,
            farPoint.get(0) - originX,
            farPoint.get(1) - originY,
            farPoint.get(2) - originZ);
    }

    private Direction pickEditorDirection(SelectionGeometry selection, double[] labelCenter, double scale) {
        if (editorCursorRay == null) return null;
        if (editorTool == WandEditorTool.RESIZE) {
            return SelectionGizmoMath.pickResizeDirection(
                editorCursorRay.originX,
                editorCursorRay.originY,
                editorCursorRay.originZ,
                editorCursorRay.directionX,
                editorCursorRay.directionY,
                editorCursorRay.directionZ,
                selection.minX,
                selection.minY,
                selection.minZ,
                selection.maxX,
                selection.maxY,
                selection.maxZ,
                scale);
        }
        double centerX = labelCenter != null ? labelCenter[0] : selection.centerX;
        double centerY = labelCenter != null ? labelCenter[1] : selection.centerY;
        double centerZ = labelCenter != null ? labelCenter[2] : selection.centerZ;
        return SelectionGizmoMath.pickDirection(
            editorCursorRay.originX,
            editorCursorRay.originY,
            editorCursorRay.originZ,
            editorCursorRay.directionX,
            editorCursorRay.directionY,
            editorCursorRay.directionZ,
            centerX,
            centerY,
            centerZ,
            scale);
    }

    private void rewindProjectionBuffers() {
        modelViewMatrix.rewind();
        projectionMatrix.rewind();
        viewport.rewind();
    }

    void updateEditorCursor(int mouseX, int mouseY, int screenWidth, int screenHeight, boolean insideViewport) {
        editorMouseX = mouseX;
        editorMouseY = mouseY;
        editorWidth = screenWidth;
        editorHeight = screenHeight;
        cursorInsideViewport = insideViewport;
    }

    boolean beginEditorGizmoDrag(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        updateEditorCursor(mouseX, mouseY, screenWidth, screenHeight, true);
        startDragging();
        return draggingDirection != null;
    }

    void endEditorGizmoDrag() {
        stopDragging(true);
    }

    void beginEditorCameraDrag() {
        Mouse.getDX();
        Mouse.getDY();
        editorCameraDragging = true;
    }

    void endEditorCameraDrag() {
        editorCameraDragging = false;
    }

    void rotateCameraFromEditor(int rawMouseDx, int rawMouseDy) {
        if (camera == null || draggingDirection != null) return;
        Minecraft mc = Minecraft.getMinecraft();
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        float scale = sensitivity * sensitivity * sensitivity * 8.0f * 0.15f;
        float pitchDirection = mc.gameSettings.invertMouse ? 1.0f : -1.0f;
        camera.rotationYaw += rawMouseDx * scale;
        camera.rotationPitch = clamp(camera.rotationPitch + rawMouseDy * scale * pitchDirection, -90.0f, 90.0f);
        camera.prevRotationYaw = camera.rotationYaw;
        camera.prevRotationPitch = camera.rotationPitch;
    }

    Direction hoveredDirection() {
        return hoveredDirection;
    }

    WandEditorTool editorTool() {
        return editorTool;
    }

    String selectedLabelName() {
        return selectedLabelName;
    }

    void selectEditorLabel(String name) {
        selectedLabelName = name;
        stopDragging(false);
        editorTool = WandEditorTool.MOVE_LABEL;
        hoveredDirection = null;
    }

    void clearEditorLabelSelection(String name) {
        if (name == null || name.equals(selectedLabelName)) {
            selectedLabelName = null;
            stopDragging(false);
            hoveredDirection = null;
        }
    }

    int[] editorLabelTarget(boolean adjacentSurface) {
        if (cameraWorld == null || editorCursorRay == null) return null;
        double length = Math.sqrt(
            editorCursorRay.directionX * editorCursorRay.directionX
                + editorCursorRay.directionY * editorCursorRay.directionY
                + editorCursorRay.directionZ * editorCursorRay.directionZ);
        if (length < 1.0e-9) return null;
        double scale = LABEL_PICK_DISTANCE / length;
        Vec3 start = Vec3.createVectorHelper(editorCursorRay.originX, editorCursorRay.originY, editorCursorRay.originZ);
        Vec3 end = Vec3.createVectorHelper(
            editorCursorRay.originX + editorCursorRay.directionX * scale,
            editorCursorRay.originY + editorCursorRay.directionY * scale,
            editorCursorRay.originZ + editorCursorRay.directionZ * scale);
        MovingObjectPosition hit = cameraWorld.rayTraceBlocks(start, end);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        return ItemHorizonWand
            .getTargetedPositionFromHit(hit.blockX, hit.blockY, hit.blockZ, hit.sideHit, adjacentSurface);
    }

    void setEditorTool(WandEditorTool tool) {
        if (tool == null || tool == editorTool) return;
        stopDragging(false);
        editorTool = tool;
        hoveredDirection = null;
    }

    void closeEditor(boolean notify) {
        while (FREECAM_KEY.isPressed()) {
            // Consume the GUI key press so the client-tick handler cannot immediately reopen the editor.
        }
        leaveFreecam(Minecraft.getMinecraft(), notify);
    }

    private void renderGizmo(SelectionGeometry selection, double[] labelCenter, double scale, float alpha,
        float lineWidth) {
        if (editorTool == WandEditorTool.RESIZE) {
            renderResizeHandles(selection, scale, alpha, lineWidth);
        } else {
            double centerX = labelCenter != null ? labelCenter[0] : selection.centerX;
            double centerY = labelCenter != null ? labelCenter[1] : selection.centerY;
            double centerZ = labelCenter != null ? labelCenter[2] : selection.centerZ;
            renderArrows(centerX, centerY, centerZ, scale, alpha, lineWidth);
        }
    }

    private void renderResizeHandles(SelectionGeometry selection, double scale, float alpha, float lineWidth) {
        GL11.glLineWidth(lineWidth);
        for (Direction direction : Direction.values()) {
            boolean hovered = direction == hoveredDirection;
            float[] color = color(direction, hovered);
            double[] start = resizeHandleStart(selection, direction);
            renderResizeHandle(start[0], start[1], start[2], scale, direction, color[0], color[1], color[2], alpha);
        }
    }

    private static void renderResizeHandle(double startX, double startY, double startZ, double scale,
        Direction direction, float red, float green, float blue, float alpha) {
        double endX = startX + direction.dx * SelectionGizmoMath.RESIZE_STEM_LENGTH * scale;
        double endY = startY + direction.dy * SelectionGizmoMath.RESIZE_STEM_LENGTH * scale;
        double endZ = startZ + direction.dz * SelectionGizmoMath.RESIZE_STEM_LENGTH * scale;
        double halfSize = RESIZE_HANDLE_HALF_SIZE * scale;

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA_F(red, green, blue, alpha);
        tess.addVertex(startX, startY, startZ);
        tess.addVertex(endX, endY, endZ);
        tess.draw();

        tess.startDrawing(GL11.GL_QUADS);
        tess.setColorRGBA_F(red, green, blue, alpha);
        addCube(
            tess,
            endX - halfSize,
            endY - halfSize,
            endZ - halfSize,
            endX + halfSize,
            endY + halfSize,
            endZ + halfSize);
        tess.draw();
    }

    private static void addCube(Tessellator tess, double x0, double y0, double z0, double x1, double y1, double z1) {
        addQuad(tess, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
        addQuad(tess, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0);
        addQuad(tess, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);
        addQuad(tess, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0);
        addQuad(tess, x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1);
        addQuad(tess, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0);
    }

    private static void addQuad(Tessellator tess, double ax, double ay, double az, double bx, double by, double bz,
        double cx, double cy, double cz, double dx, double dy, double dz) {
        tess.addVertex(ax, ay, az);
        tess.addVertex(bx, by, bz);
        tess.addVertex(cx, cy, cz);
        tess.addVertex(dx, dy, dz);
    }

    private void renderArrows(double centerX, double centerY, double centerZ, double scale, float alpha,
        float lineWidth) {
        GL11.glLineWidth(lineWidth);
        for (Direction direction : Direction.values()) {
            boolean hovered = direction == hoveredDirection;
            float[] color = color(direction, hovered);
            renderArrow(centerX, centerY, centerZ, scale, direction, color[0], color[1], color[2], alpha);
        }
    }

    private static void renderArrow(double centerX, double centerY, double centerZ, double scale, Direction direction,
        float red, float green, float blue, float alpha) {
        double shaftEnd = 1.38 * scale;
        double tipEnd = 1.78 * scale;
        double radius = 0.15 * scale;

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA_F(red, green, blue, alpha);
        tess.addVertex(centerX, centerY, centerZ);
        tess.addVertex(
            centerX + direction.dx * shaftEnd,
            centerY + direction.dy * shaftEnd,
            centerZ + direction.dz * shaftEnd);
        tess.draw();

        double[] perpendicularA = perpendicularA(direction);
        double[] perpendicularB = perpendicularB(direction);
        double baseX = centerX + direction.dx * shaftEnd;
        double baseY = centerY + direction.dy * shaftEnd;
        double baseZ = centerZ + direction.dz * shaftEnd;
        double tipX = centerX + direction.dx * tipEnd;
        double tipY = centerY + direction.dy * tipEnd;
        double tipZ = centerZ + direction.dz * tipEnd;

        tess.startDrawing(GL11.GL_TRIANGLES);
        tess.setColorRGBA_F(red, green, blue, alpha);
        for (int i = 0; i < ARROW_SEGMENTS; i++) {
            double angle0 = Math.PI * 2.0 * i / ARROW_SEGMENTS;
            double angle1 = Math.PI * 2.0 * (i + 1) / ARROW_SEGMENTS;
            double ring0X = baseX
                + radius * (perpendicularA[0] * Math.cos(angle0) + perpendicularB[0] * Math.sin(angle0));
            double ring0Y = baseY
                + radius * (perpendicularA[1] * Math.cos(angle0) + perpendicularB[1] * Math.sin(angle0));
            double ring0Z = baseZ
                + radius * (perpendicularA[2] * Math.cos(angle0) + perpendicularB[2] * Math.sin(angle0));
            double ring1X = baseX
                + radius * (perpendicularA[0] * Math.cos(angle1) + perpendicularB[0] * Math.sin(angle1));
            double ring1Y = baseY
                + radius * (perpendicularA[1] * Math.cos(angle1) + perpendicularB[1] * Math.sin(angle1));
            double ring1Z = baseZ
                + radius * (perpendicularA[2] * Math.cos(angle1) + perpendicularB[2] * Math.sin(angle1));
            tess.addVertex(tipX, tipY, tipZ);
            tess.addVertex(ring0X, ring0Y, ring0Z);
            tess.addVertex(ring1X, ring1Y, ring1Z);
            tess.addVertex(baseX, baseY, baseZ);
            tess.addVertex(ring1X, ring1Y, ring1Z);
            tess.addVertex(ring0X, ring0Y, ring0Z);
        }
        tess.draw();
    }

    private static float[] color(Direction direction, boolean hovered) {
        if (hovered) return new float[] { 1.0f, 0.92f, 0.28f };
        if (direction.dx != 0) return new float[] { 0.95f, 0.18f, 0.18f };
        if (direction.dy != 0) return new float[] { 0.2f, 0.9f, 0.3f };
        return new float[] { 0.22f, 0.45f, 1.0f };
    }

    private static double[] perpendicularA(Direction direction) {
        if (direction.dx != 0) return new double[] { 0.0, 1.0, 0.0 };
        return new double[] { 1.0, 0.0, 0.0 };
    }

    private static double[] perpendicularB(Direction direction) {
        if (direction.dx != 0) return new double[] { 0.0, 0.0, 1.0 };
        if (direction.dy != 0) return new double[] { 0.0, 0.0, 1.0 };
        return new double[] { 0.0, 1.0, 0.0 };
    }

    private double[] dragAxisOrigin(SelectionGeometry selection, Direction direction) {
        return editorTool == WandEditorTool.RESIZE ? resizeHandleStart(selection, direction)
            : new double[] { selection.centerX, selection.centerY, selection.centerZ };
    }

    private static double[] resizeHandleStart(SelectionGeometry selection, Direction direction) {
        return new double[] { direction.dx > 0 ? selection.maxX : direction.dx < 0 ? selection.minX : selection.centerX,
            direction.dy > 0 ? selection.maxY : direction.dy < 0 ? selection.minY : selection.centerY,
            direction.dz > 0 ? selection.maxZ : direction.dz < 0 ? selection.minZ : selection.centerZ };
    }

    private double[] selectedLabelCenter(ItemStack wand) {
        if (selectedLabelName == null) return null;
        int[] position = ItemHorizonWand.getLabels(wand)
            .get(selectedLabelName);
        if (position == null) {
            selectedLabelName = null;
            return null;
        }
        return new double[] { position[0] + 0.5, position[1] + 0.5, position[2] + 0.5 };
    }

    private static double selectionCenter(NBTTagCompound nbt, String pos1Tag, String pos2Tag) {
        return (nbt.getInteger(pos1Tag) + nbt.getInteger(pos2Tag) + 1.0) * 0.5;
    }

    private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double gizmoScale(double distance) {
        return Math.max(0.75, Math.min(4.0, distance * 0.055));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Desugar
    private record CursorRay(double originX, double originY, double originZ, double directionX, double directionY,
        double directionZ) {

    }

    private static final class SelectionGeometry {

        final double minX;
        final double minY;
        final double minZ;
        final double maxX;
        final double maxY;
        final double maxZ;
        final double centerX;
        final double centerY;
        final double centerZ;

        private SelectionGeometry(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            centerX = (minX + maxX) * 0.5;
            centerY = (minY + maxY) * 0.5;
            centerZ = (minZ + maxZ) * 0.5;
        }

        static SelectionGeometry from(NBTTagCompound nbt) {
            return new SelectionGeometry(
                Math.min(nbt.getInteger(ItemHorizonWand.TAG_POS1_X), nbt.getInteger(ItemHorizonWand.TAG_POS2_X)),
                Math.min(nbt.getInteger(ItemHorizonWand.TAG_POS1_Y), nbt.getInteger(ItemHorizonWand.TAG_POS2_Y)),
                Math.min(nbt.getInteger(ItemHorizonWand.TAG_POS1_Z), nbt.getInteger(ItemHorizonWand.TAG_POS2_Z)),
                Math.max(nbt.getInteger(ItemHorizonWand.TAG_POS1_X), nbt.getInteger(ItemHorizonWand.TAG_POS2_X)) + 1.0,
                Math.max(nbt.getInteger(ItemHorizonWand.TAG_POS1_Y), nbt.getInteger(ItemHorizonWand.TAG_POS2_Y)) + 1.0,
                Math.max(nbt.getInteger(ItemHorizonWand.TAG_POS1_Z), nbt.getInteger(ItemHorizonWand.TAG_POS2_Z)) + 1.0);
        }
    }
}
