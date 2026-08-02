package com.gtnewhorizons.horizonqa.visual.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WandEditorScreenTest {

    @Test
    public void usesEqualResponsiveSidebars() {
        assertEquals(200, WandEditorLayout.sidebarWidth(1_000));
        assertEquals(118, WandEditorLayout.sidebarWidth(320));
        assertEquals(210, WandEditorLayout.sidebarWidth(2_000));
    }

    @Test
    public void cursorInteractionIsLimitedToTheCenterViewport() {
        assertTrue(WandEditorLayout.isInsideViewport(500, 300, 1_000, 600));
        assertFalse(WandEditorLayout.isInsideViewport(199, 300, 1_000, 600));
        assertFalse(WandEditorLayout.isInsideViewport(800, 300, 1_000, 600));
        assertFalse(WandEditorLayout.isInsideViewport(500, 27, 1_000, 600));
        assertFalse(WandEditorLayout.isInsideViewport(500, 576, 1_000, 600));
    }

    @Test
    public void toolButtonsSelectMoveAndResizeFromTheLeftSidebar() {
        assertEquals(WandEditorTool.MOVE, WandEditorLayout.toolAt(20, WandEditorLayout.TOOL_BUTTON_TOP + 5, 1_000));
        assertEquals(
            WandEditorTool.RESIZE,
            WandEditorLayout.toolAt(
                20,
                WandEditorLayout.TOOL_BUTTON_TOP + WandEditorLayout.TOOL_BUTTON_HEIGHT
                    + WandEditorLayout.TOOL_BUTTON_GAP
                    + 5,
                1_000));
        assertEquals(
            WandEditorTool.MOVE_LABEL,
            WandEditorLayout.toolAt(
                20,
                WandEditorLayout.TOOL_BUTTON_TOP
                    + (WandEditorLayout.TOOL_BUTTON_HEIGHT + WandEditorLayout.TOOL_BUTTON_GAP) * 2
                    + 5,
                1_000));
        assertEquals(null, WandEditorLayout.toolAt(500, WandEditorLayout.TOOL_BUTTON_TOP + 5, 1_000));
    }

    @Test
    public void mapsLabelPanelControlsAndRows() {
        assertTrue(WandEditorLayout.isLabelAddButton(900, WandEditorLayout.LABEL_ADD_TOP + 5, 1_000));
        assertEquals(0, WandEditorLayout.labelRowAt(900, WandEditorLayout.LABEL_LIST_TOP + 5, 1_000, 600, 3));
        assertEquals(1, WandEditorLayout.labelRowAt(900, WandEditorLayout.LABEL_LIST_TOP + 29, 1_000, 600, 3));
        assertTrue(WandEditorLayout.isLabelRenameButton(820, 553, 1_000, 600));
        assertTrue(WandEditorLayout.isLabelDeleteButton(920, 553, 1_000, 600));
    }
}
