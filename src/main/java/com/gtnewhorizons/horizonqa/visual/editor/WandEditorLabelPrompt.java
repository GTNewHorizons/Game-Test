package com.gtnewhorizons.horizonqa.visual.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;
import com.gtnewhorizons.horizonqa.item.ItemHorizonWand.LabelMutationResult;
import com.gtnewhorizons.horizonqa.network.HorizonQANetwork;
import com.gtnewhorizons.horizonqa.network.WandLabelMessage;

/** Label creation and rename modal owned exclusively by the wand editor. */
final class WandEditorLabelPrompt extends GuiScreen {

    private static final int BUTTON_SAVE = 0;
    private static final int BUTTON_REMOVE = 1;
    private static final int BUTTON_CANCEL = 2;

    private final int x;
    private final int y;
    private final int z;
    private final String existingName;
    private final WandEditorScreen editorParent;
    private GuiTextField input;
    private String error = "";

    WandEditorLabelPrompt(int x, int y, int z, String existingName, WandEditorScreen editorParent) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.existingName = existingName;
        this.editorParent = editorParent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int fieldWidth = 180;
        input = new GuiTextField(fontRendererObj, (width - fieldWidth) / 2, height / 2 - 6, fieldWidth, 20);
        input.setMaxStringLength(64);
        input.setFocused(true);
        input.setText(existingName != null ? existingName : "");
        int buttonY = height / 2 + 38;
        if (existingName != null) {
            buttonList.add(
                new GuiButton(
                    BUTTON_SAVE,
                    width / 2 - 125,
                    buttonY,
                    80,
                    20,
                    StatCollector.translateToLocal("horizonqa.wand.editor.label_save")));
            buttonList.add(
                new GuiButton(
                    BUTTON_CANCEL,
                    width / 2 - 40,
                    buttonY,
                    80,
                    20,
                    StatCollector.translateToLocal("horizonqa.wand.editor.label_cancel")));
            buttonList.add(
                new GuiButton(
                    BUTTON_REMOVE,
                    width / 2 + 45,
                    buttonY,
                    80,
                    20,
                    StatCollector.translateToLocal("horizonqa.wand.editor.label_delete")));
        } else {
            buttonList.add(
                new GuiButton(
                    BUTTON_SAVE,
                    width / 2 - 82,
                    buttonY,
                    80,
                    20,
                    StatCollector.translateToLocal("horizonqa.wand.editor.label_save")));
            buttonList.add(
                new GuiButton(
                    BUTTON_CANCEL,
                    width / 2 + 2,
                    buttonY,
                    80,
                    20,
                    StatCollector.translateToLocal("horizonqa.wand.editor.label_cancel")));
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closePrompt();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            submit();
            return;
        }
        input.textboxKeyTyped(typedChar, keyCode);
        validateCurrentName();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        input.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_SAVE) {
            submit();
        } else if (button.id == BUTTON_CANCEL) {
            closePrompt();
        } else if (button.id == BUTTON_REMOVE && existingName != null) {
            ItemStack wand = currentWand();
            if (wand != null) ItemHorizonWand.removeLabel(wand, existingName);
            editorParent.editorLabelRemoved(existingName);
            HorizonQANetwork.CHANNEL.sendToServer(new WandLabelMessage(existingName, x, y, z, true));
            closePrompt();
        }
    }

    @Override
    public void updateScreen() {
        input.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        String title = StatCollector.translateToLocal(
            existingName != null ? "horizonqa.wand.editor.label_rename_title"
                : "horizonqa.wand.editor.label_create_title");
        drawCenteredString(fontRendererObj, title, width / 2, height / 2 - 34, 0xFFFFFF);
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("horizonqa.wand.editor.label_target") + " " + x + ", " + y + ", " + z,
            width / 2,
            height / 2 - 22,
            0xAAAAAA);
        input.drawTextBox();
        if (!error.isEmpty()) {
            drawCenteredString(fontRendererObj, error, width / 2, height / 2 + 20, 0xFF5555);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void submit() {
        String name = input.getText()
            .trim();
        if (!ItemHorizonWand.isValidLabelName(name)) {
            error = StatCollector.translateToLocal("horizonqa.wand.editor.label_invalid");
            return;
        }
        ItemStack wand = currentWand();
        if (wand == null) {
            closePrompt();
            return;
        }
        LabelMutationResult result = ItemHorizonWand.setLabel(wand, name, x, y, z);
        if (result.status == LabelMutationResult.Status.DUPLICATE_NAME) {
            error = StatCollector.translateToLocal("horizonqa.wand.editor.label_duplicate");
            return;
        }
        if (result.status != LabelMutationResult.Status.SUCCESS) {
            error = StatCollector.translateToLocal("horizonqa.wand.editor.label_invalid");
            return;
        }
        editorParent.editorLabelSaved(name);
        HorizonQANetwork.CHANNEL.sendToServer(new WandLabelMessage(name, x, y, z));
        closePrompt();
    }

    private void validateCurrentName() {
        String name = input.getText()
            .trim();
        error = name.isEmpty() || ItemHorizonWand.isValidLabelName(name) ? ""
            : StatCollector.translateToLocal("horizonqa.wand.editor.label_invalid");
    }

    private ItemStack currentWand() {
        Minecraft mc = Minecraft.getMinecraft();
        ItemStack held = mc.thePlayer != null ? mc.thePlayer.getHeldItem() : null;
        return held != null && held.getItem() instanceof ItemHorizonWand ? held : null;
    }

    private void closePrompt() {
        Minecraft.getMinecraft()
            .displayGuiScreen(editorParent);
    }
}
