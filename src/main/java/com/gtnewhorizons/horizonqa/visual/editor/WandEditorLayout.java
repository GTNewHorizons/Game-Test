package com.gtnewhorizons.horizonqa.visual.editor;

final class WandEditorLayout {

    static final int TOP_BAR_HEIGHT = 28;
    static final int BOTTOM_BAR_HEIGHT = 24;
    static final int TOOL_BUTTON_TOP = TOP_BAR_HEIGHT + 22;
    static final int TOOL_BUTTON_HEIGHT = 21;
    static final int TOOL_BUTTON_GAP = 2;
    static final int LABEL_ADD_TOP = TOP_BAR_HEIGHT + 22;
    static final int LABEL_LIST_TOP = LABEL_ADD_TOP + TOOL_BUTTON_HEIGHT + 7;
    static final int LABEL_ROW_HEIGHT = 24;

    private WandEditorLayout() {}

    static int sidebarWidth(int screenWidth) {
        return Math.max(118, Math.min(210, screenWidth / 5));
    }

    static boolean isInsideViewport(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int sidebar = sidebarWidth(screenWidth);
        return mouseX >= sidebar && mouseX < screenWidth - sidebar
            && mouseY >= TOP_BAR_HEIGHT
            && mouseY < screenHeight - BOTTOM_BAR_HEIGHT;
    }

    static WandEditorTool toolAt(int mouseX, int mouseY, int screenWidth) {
        int sidebar = sidebarWidth(screenWidth);
        if (mouseX < 6 || mouseX >= sidebar - 6) return null;
        if (mouseY >= TOOL_BUTTON_TOP && mouseY < TOOL_BUTTON_TOP + TOOL_BUTTON_HEIGHT) {
            return WandEditorTool.MOVE;
        }
        int resizeTop = TOOL_BUTTON_TOP + TOOL_BUTTON_HEIGHT + TOOL_BUTTON_GAP;
        if (mouseY >= resizeTop && mouseY < resizeTop + TOOL_BUTTON_HEIGHT) return WandEditorTool.RESIZE;
        int labelTop = resizeTop + TOOL_BUTTON_HEIGHT + TOOL_BUTTON_GAP;
        return mouseY >= labelTop && mouseY < labelTop + TOOL_BUTTON_HEIGHT ? WandEditorTool.MOVE_LABEL : null;
    }

    static boolean isLabelAddButton(int mouseX, int mouseY, int screenWidth) {
        return isInsideRightPanel(mouseX, screenWidth) && mouseY >= LABEL_ADD_TOP
            && mouseY < LABEL_ADD_TOP + TOOL_BUTTON_HEIGHT;
    }

    static int labelRowAt(int mouseX, int mouseY, int screenWidth, int screenHeight, int labelCount) {
        if (!isInsideRightPanel(mouseX, screenWidth) || mouseY < LABEL_LIST_TOP) return -1;
        int index = (mouseY - LABEL_LIST_TOP) / LABEL_ROW_HEIGHT;
        int rows = Math.min(labelCount, availableLabelRows(screenHeight));
        return index >= 0 && index < rows ? index : -1;
    }

    static int availableLabelRows(int screenHeight) {
        return Math.max(1, (labelActionTop(screenHeight) - 4 - LABEL_LIST_TOP) / LABEL_ROW_HEIGHT);
    }

    static int labelActionTop(int screenHeight) {
        return screenHeight - BOTTOM_BAR_HEIGHT - TOOL_BUTTON_HEIGHT - 3;
    }

    static boolean isLabelRenameButton(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int sidebar = sidebarWidth(screenWidth);
        int left = screenWidth - sidebar + 6;
        int middle = (left + screenWidth - 6) / 2;
        return mouseX >= left && mouseX < middle - 1
            && mouseY >= labelActionTop(screenHeight)
            && mouseY < labelActionTop(screenHeight) + TOOL_BUTTON_HEIGHT;
    }

    static boolean isLabelDeleteButton(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int sidebar = sidebarWidth(screenWidth);
        int left = screenWidth - sidebar + 6;
        int middle = (left + screenWidth - 6) / 2;
        return mouseX >= middle + 1 && mouseX < screenWidth - 6
            && mouseY >= labelActionTop(screenHeight)
            && mouseY < labelActionTop(screenHeight) + TOOL_BUTTON_HEIGHT;
    }

    private static boolean isInsideRightPanel(int mouseX, int screenWidth) {
        int sidebar = sidebarWidth(screenWidth);
        return mouseX >= screenWidth - sidebar + 6 && mouseX < screenWidth - 6;
    }
}
