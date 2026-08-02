package com.gtnewhorizons.horizonqa.visual.editor;

import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;
import com.gtnewhorizons.horizonqa.network.HorizonQANetwork;
import com.gtnewhorizons.horizonqa.network.WandLabelMessage;
import com.gtnewhorizons.horizonqa.visual.editor.SelectionGizmoMath.Direction;

final class WandEditorScreen extends GuiScreen {

    private static final int PANEL_BACKGROUND = 0xF212171D;
    private static final int PANEL_ACCENT = 0xFF566579;
    private static final int TEXT_PRIMARY = 0xFFF2F5F7;
    private static final int TEXT_MUTED = 0xFFAAB3BC;
    private static final int TOOL_SELECTED = 0xCC253448;
    private static final int TOOL_IDLE = 0x66303A46;

    private boolean gizmoDragging;
    private boolean cameraMouseGrabbed;
    private boolean placingLabel;
    private int cameraDragMouseX;
    private int cameraDragMouseY;

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int sidebar = WandEditorLayout.sidebarWidth(width);
        boolean insideViewport = WandEditorLayout.isInsideViewport(mouseX, mouseY, width, height);
        WandFreecamController.INSTANCE.updateEditorCursor(mouseX, mouseY, width, height, insideViewport);

        // GuiIngameForge normally establishes this projection after its ALL pre-event. Keep the editor
        // self-contained so another overlay handler cannot accidentally project GUI rectangles into the world.
        mc.entityRenderer.setupOverlayRendering();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        drawRect(0, 0, width, WandEditorLayout.TOP_BAR_HEIGHT, PANEL_BACKGROUND);
        drawRect(0, height - WandEditorLayout.BOTTOM_BAR_HEIGHT, width, height, PANEL_BACKGROUND);
        drawRect(
            0,
            WandEditorLayout.TOP_BAR_HEIGHT,
            sidebar,
            height - WandEditorLayout.BOTTOM_BAR_HEIGHT,
            PANEL_BACKGROUND);
        drawRect(
            width - sidebar,
            WandEditorLayout.TOP_BAR_HEIGHT,
            width,
            height - WandEditorLayout.BOTTOM_BAR_HEIGHT,
            PANEL_BACKGROUND);
        drawViewportBorder(sidebar);

        fontRendererObj
            .drawStringWithShadow(StatCollector.translateToLocal("horizonqa.wand.editor.title"), 9, 10, TEXT_PRIMARY);
        String close = String.format(
            StatCollector.translateToLocal("horizonqa.wand.editor.close"),
            GameSettings.getKeyDisplayString(WandFreecamController.FREECAM_KEY.getKeyCode()));
        fontRendererObj.drawStringWithShadow(close, width - fontRendererObj.getStringWidth(close) - 9, 10, TEXT_MUTED);

        drawToolPanel(sidebar);
        drawLabelsPanel(sidebar);

        String controls = StatCollector.translateToLocal(
            placingLabel ? "horizonqa.wand.editor.label_place_controls" : "horizonqa.wand.editor.controls");
        drawCenteredString(fontRendererObj, controls, width / 2, height - 16, TEXT_MUTED);
        super.drawScreen(mouseX, mouseY, partialTicks);
        GL11.glPopAttrib();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && placingLabel) {
            placingLabel = false;
        } else if (keyCode == Keyboard.KEY_ESCAPE || keyCode == WandFreecamController.FREECAM_KEY.getKeyCode()) {
            WandFreecamController.INSTANCE.closeEditor(true);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && handleLabelPanelClick(mouseX, mouseY)) return;
        if (button == 0) {
            WandEditorTool tool = WandEditorLayout.toolAt(mouseX, mouseY, width);
            if (tool != null) {
                WandFreecamController.INSTANCE.setEditorTool(tool);
                return;
            }
        }
        if (!WandEditorLayout.isInsideViewport(mouseX, mouseY, width, height)) return;

        if (button == 0) {
            if (placingLabel) {
                int[] target = WandFreecamController.INSTANCE.editorLabelTarget(isShiftKeyDown());
                if (target != null) {
                    placingLabel = false;
                    ItemStack wand = mc.thePlayer != null ? mc.thePlayer.getHeldItem() : null;
                    String existing = ItemHorizonWand.getLabelAt(wand, target[0], target[1], target[2]);
                    mc.displayGuiScreen(new WandEditorLabelPrompt(target[0], target[1], target[2], existing, this));
                }
                return;
            }
            gizmoDragging = WandFreecamController.INSTANCE.beginEditorGizmoDrag(mouseX, mouseY, width, height);
        } else if (button == 1) {
            grabCameraMouse();
            WandFreecamController.INSTANCE.beginEditorCameraDrag();
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long heldTime) {
        if (button == 0 && gizmoDragging) {
            WandFreecamController.INSTANCE.updateEditorCursor(
                mouseX,
                mouseY,
                width,
                height,
                WandEditorLayout.isInsideViewport(mouseX, mouseY, width, height));
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (button == 0 && gizmoDragging) {
            WandFreecamController.INSTANCE.endEditorGizmoDrag();
            gizmoDragging = false;
        } else if (button == 1) {
            WandFreecamController.INSTANCE.endEditorCameraDrag();
            releaseCameraMouse();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        WandFreecamController.INSTANCE.endEditorCameraDrag();
        releaseCameraMouse();
    }

    private void grabCameraMouse() {
        if (Boolean.parseBoolean(System.getProperty("fml.noGrab", "false"))) return;
        cameraDragMouseX = Mouse.getX();
        cameraDragMouseY = Mouse.getY();
        Mouse.setGrabbed(true);
        cameraMouseGrabbed = true;
    }

    private void releaseCameraMouse() {
        if (!cameraMouseGrabbed) return;
        Mouse.setCursorPosition(cameraDragMouseX, cameraDragMouseY);
        Mouse.setGrabbed(false);
        cameraMouseGrabbed = false;
    }

    private void drawViewportBorder(int sidebar) {
        int right = width - sidebar;
        int bottom = height - WandEditorLayout.BOTTOM_BAR_HEIGHT;
        drawRect(sidebar - 1, WandEditorLayout.TOP_BAR_HEIGHT, sidebar, bottom, PANEL_ACCENT);
        drawRect(right, WandEditorLayout.TOP_BAR_HEIGHT, right + 1, bottom, PANEL_ACCENT);
        drawRect(
            sidebar - 1,
            WandEditorLayout.TOP_BAR_HEIGHT - 1,
            right + 1,
            WandEditorLayout.TOP_BAR_HEIGHT,
            PANEL_ACCENT);
        drawRect(sidebar - 1, bottom, right + 1, bottom + 1, PANEL_ACCENT);
    }

    private void drawToolPanel(int sidebar) {
        int x = 9;
        int y = WandEditorLayout.TOP_BAR_HEIGHT + 10;
        fontRendererObj
            .drawStringWithShadow(StatCollector.translateToLocal("horizonqa.wand.editor.tools"), x, y, TEXT_PRIMARY);
        y = WandEditorLayout.TOOL_BUTTON_TOP;
        drawToolButton(sidebar, y, WandEditorTool.MOVE, "horizonqa.wand.editor.move_tool");
        y += WandEditorLayout.TOOL_BUTTON_HEIGHT + WandEditorLayout.TOOL_BUTTON_GAP;
        drawToolButton(sidebar, y, WandEditorTool.RESIZE, "horizonqa.wand.editor.resize_tool");
        y += WandEditorLayout.TOOL_BUTTON_HEIGHT + WandEditorLayout.TOOL_BUTTON_GAP;
        drawToolButton(sidebar, y, WandEditorTool.MOVE_LABEL, "horizonqa.wand.editor.move_label_tool");
        y += WandEditorLayout.TOOL_BUTTON_HEIGHT + 10;
        fontRendererObj
            .drawStringWithShadow(StatCollector.translateToLocal("horizonqa.wand.editor.axes"), x, y, TEXT_MUTED);
        y += 14;
        drawAxisLegend(x, y, "X", 0xFFFF5555);
        drawAxisLegend(x, y + 13, "Y", 0xFF55FF77);
        drawAxisLegend(x, y + 26, "Z", 0xFF5588FF);
        y += 51;

        ItemStack wand = mc.thePlayer != null ? mc.thePlayer.getHeldItem() : null;
        fontRendererObj.drawStringWithShadow(
            StatCollector.translateToLocal("horizonqa.wand.editor.selection"),
            x,
            y,
            TEXT_PRIMARY);
        y += 14;
        if (!ItemHorizonWand.hasCompleteSelection(wand)) {
            fontRendererObj.drawSplitString(
                StatCollector.translateToLocal("horizonqa.wand.editor.selection_incomplete"),
                x,
                y,
                sidebar - 18,
                TEXT_MUTED);
            drawHoverStatus(x, y + 30, sidebar);
            return;
        }

        NBTTagCompound nbt = wand.getTagCompound();
        String pos1 = coordinates(
            nbt,
            ItemHorizonWand.TAG_POS1_X,
            ItemHorizonWand.TAG_POS1_Y,
            ItemHorizonWand.TAG_POS1_Z);
        String pos2 = coordinates(
            nbt,
            ItemHorizonWand.TAG_POS2_X,
            ItemHorizonWand.TAG_POS2_Y,
            ItemHorizonWand.TAG_POS2_Z);
        fontRendererObj.drawStringWithShadow("P1  " + pos1, x, y, TEXT_MUTED);
        y += 12;
        fontRendererObj.drawStringWithShadow("P2  " + pos2, x, y, TEXT_MUTED);
        y += 12;
        int sizeX = Math.abs(nbt.getInteger(ItemHorizonWand.TAG_POS2_X) - nbt.getInteger(ItemHorizonWand.TAG_POS1_X))
            + 1;
        int sizeY = Math.abs(nbt.getInteger(ItemHorizonWand.TAG_POS2_Y) - nbt.getInteger(ItemHorizonWand.TAG_POS1_Y))
            + 1;
        int sizeZ = Math.abs(nbt.getInteger(ItemHorizonWand.TAG_POS2_Z) - nbt.getInteger(ItemHorizonWand.TAG_POS1_Z))
            + 1;
        fontRendererObj.drawStringWithShadow(
            String.format(StatCollector.translateToLocal("horizonqa.wand.editor.size"), sizeX, sizeY, sizeZ),
            x,
            y,
            TEXT_MUTED);
        y += 19;
        drawHoverStatus(x, y, sidebar);
    }

    private void drawHoverStatus(int x, int y, int sidebar) {
        Direction hovered = WandFreecamController.INSTANCE.hoveredDirection();
        String hoverText;
        if (hovered != null) {
            hoverText = directionName(hovered);
        } else if (WandFreecamController.INSTANCE.editorTool() == WandEditorTool.RESIZE) {
            hoverText = StatCollector.translateToLocal("horizonqa.wand.editor.hover_resize_none");
        } else if (WandFreecamController.INSTANCE.editorTool() == WandEditorTool.MOVE_LABEL) {
            hoverText = WandFreecamController.INSTANCE.selectedLabelName() == null
                ? StatCollector.translateToLocal("horizonqa.wand.editor.select_label_first")
                : StatCollector.translateToLocal("horizonqa.wand.editor.hover_none");
        } else {
            hoverText = StatCollector.translateToLocal("horizonqa.wand.editor.hover_none");
        }
        fontRendererObj.drawSplitString(hoverText, x, y, sidebar - 18, hovered == null ? TEXT_MUTED : 0xFFFFFF55);
    }

    private void drawToolButton(int sidebar, int y, WandEditorTool tool, String translationKey) {
        boolean selected = WandFreecamController.INSTANCE.editorTool() == tool;
        drawRect(6, y, sidebar - 6, y + WandEditorLayout.TOOL_BUTTON_HEIGHT, selected ? TOOL_SELECTED : TOOL_IDLE);
        fontRendererObj.drawStringWithShadow(
            StatCollector.translateToLocal(translationKey),
            9,
            y + 6,
            selected ? TEXT_PRIMARY : TEXT_MUTED);
    }

    private void drawLabelsPanel(int sidebar) {
        int x = width - sidebar + 9;
        int y = WandEditorLayout.TOP_BAR_HEIGHT + 10;
        ItemStack wand = mc.thePlayer != null ? mc.thePlayer.getHeldItem() : null;
        Map<String, int[]> labels = ItemHorizonWand.getLabels(wand);
        String selected = WandFreecamController.INSTANCE.selectedLabelName();
        if (selected != null && !labels.containsKey(selected)) {
            WandFreecamController.INSTANCE.clearEditorLabelSelection(selected);
            selected = null;
        }
        fontRendererObj.drawStringWithShadow(
            String.format(StatCollector.translateToLocal("horizonqa.wand.editor.labels"), labels.size()),
            x,
            y,
            TEXT_PRIMARY);

        drawRect(
            width - sidebar + 6,
            WandEditorLayout.LABEL_ADD_TOP,
            width - 6,
            WandEditorLayout.LABEL_ADD_TOP + WandEditorLayout.TOOL_BUTTON_HEIGHT,
            placingLabel ? TOOL_SELECTED : TOOL_IDLE);
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal(
                placingLabel ? "horizonqa.wand.editor.label_click_block" : "horizonqa.wand.editor.label_add"),
            width - sidebar / 2,
            WandEditorLayout.LABEL_ADD_TOP + 6,
            placingLabel ? TEXT_PRIMARY : TEXT_MUTED);

        y = WandEditorLayout.LABEL_LIST_TOP;
        if (labels.isEmpty()) {
            fontRendererObj.drawSplitString(
                StatCollector.translateToLocal("horizonqa.wand.editor.no_labels"),
                x,
                y,
                sidebar - 18,
                TEXT_MUTED);
            return;
        }

        int availableRows = WandEditorLayout.availableLabelRows(height);
        int rendered = 0;
        for (Map.Entry<String, int[]> entry : labels.entrySet()) {
            if (rendered >= availableRows) break;
            int[] position = entry.getValue();
            int color = ItemHorizonWand.isInsideSelection(wand, position[0], position[1], position[2]) ? TEXT_PRIMARY
                : 0xFFFF7777;
            if (entry.getKey()
                .equals(selected)) {
                drawRect(
                    width - sidebar + 6,
                    y - 2,
                    width - 6,
                    y + WandEditorLayout.LABEL_ROW_HEIGHT - 2,
                    TOOL_SELECTED);
            }
            fontRendererObj.drawStringWithShadow(trimToWidth(entry.getKey(), sidebar - 18), x, y, color);
            fontRendererObj
                .drawStringWithShadow(position[0] + ", " + position[1] + ", " + position[2], x, y + 11, TEXT_MUTED);
            y += WandEditorLayout.LABEL_ROW_HEIGHT;
            rendered++;
        }
        if (rendered < labels.size()) {
            fontRendererObj.drawStringWithShadow(
                String.format(
                    StatCollector.translateToLocal("horizonqa.wand.editor.more_labels"),
                    labels.size() - rendered),
                x,
                WandEditorLayout.labelActionTop(height) - 12,
                TEXT_MUTED);
        }
        if (selected != null) {
            drawLabelActions(sidebar);
        }
    }

    private void drawLabelActions(int sidebar) {
        int actionTop = WandEditorLayout.labelActionTop(height);
        int left = width - sidebar + 6;
        int right = width - 6;
        int middle = (left + right) / 2;
        drawRect(left, actionTop, middle - 1, actionTop + WandEditorLayout.TOOL_BUTTON_HEIGHT, TOOL_IDLE);
        drawRect(middle + 1, actionTop, right, actionTop + WandEditorLayout.TOOL_BUTTON_HEIGHT, 0x66913737);
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("horizonqa.wand.editor.label_rename"),
            (left + middle - 1) / 2,
            actionTop + 6,
            TEXT_PRIMARY);
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("horizonqa.wand.editor.label_delete"),
            (middle + 1 + right) / 2,
            actionTop + 6,
            0xFFFFAAAA);
    }

    private boolean handleLabelPanelClick(int mouseX, int mouseY) {
        ItemStack wand = mc.thePlayer != null ? mc.thePlayer.getHeldItem() : null;
        Map<String, int[]> labels = ItemHorizonWand.getLabels(wand);
        if (WandEditorLayout.isLabelAddButton(mouseX, mouseY, width)) {
            placingLabel = !placingLabel;
            return true;
        }

        String selected = WandFreecamController.INSTANCE.selectedLabelName();
        if (selected != null && WandEditorLayout.isLabelRenameButton(mouseX, mouseY, width, height)) {
            int[] position = labels.get(selected);
            if (position != null) {
                mc.displayGuiScreen(new WandEditorLabelPrompt(position[0], position[1], position[2], selected, this));
            }
            return true;
        }
        if (selected != null && WandEditorLayout.isLabelDeleteButton(mouseX, mouseY, width, height)) {
            int[] position = labels.get(selected);
            if (position != null && ItemHorizonWand.removeLabel(wand, selected)) {
                HorizonQANetwork.CHANNEL
                    .sendToServer(new WandLabelMessage(selected, position[0], position[1], position[2], true));
                editorLabelRemoved(selected);
            }
            return true;
        }

        int row = WandEditorLayout.labelRowAt(mouseX, mouseY, width, height, labels.size());
        if (row < 0) return false;
        int index = 0;
        for (String name : labels.keySet()) {
            if (index++ == row) {
                placingLabel = false;
                WandFreecamController.INSTANCE.selectEditorLabel(name);
                return true;
            }
        }
        return false;
    }

    void editorLabelSaved(String newName) {
        placingLabel = false;
        WandFreecamController.INSTANCE.selectEditorLabel(newName);
    }

    void editorLabelRemoved(String name) {
        placingLabel = false;
        WandFreecamController.INSTANCE.clearEditorLabelSelection(name);
    }

    private void drawAxisLegend(int x, int y, String axis, int color) {
        drawRect(x, y + 1, x + 8, y + 9, color);
        fontRendererObj.drawStringWithShadow(axis, x + 13, y + 1, TEXT_MUTED);
    }

    private String trimToWidth(String text, int availableWidth) {
        if (fontRendererObj.getStringWidth(text) <= availableWidth) return text;
        return fontRendererObj.trimStringToWidth(text, Math.max(0, availableWidth - 8)) + "…";
    }

    private static String directionName(Direction direction) {
        String axis = direction.dx != 0 ? "X" : direction.dy != 0 ? "Y" : "Z";
        boolean positive = direction.dx > 0 || direction.dy > 0 || direction.dz > 0;
        return StatCollector.translateToLocal("horizonqa.wand.editor.hover") + " " + axis + (positive ? "+" : "−");
    }

    private static String coordinates(NBTTagCompound nbt, String xTag, String yTag, String zTag) {
        return nbt.getInteger(xTag) + ", " + nbt.getInteger(yTag) + ", " + nbt.getInteger(zTag);
    }
}
