package com.gtnewhorizons.horizonqa.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WandSelectionMoveMessageTest {

    @Test
    public void acceptsOnlySingleBlockSingleAxisOffsets() {
        assertTrue(WandSelectionMoveMessage.isUnitAxisOffset(1, 0, 0));
        assertTrue(WandSelectionMoveMessage.isUnitAxisOffset(0, -1, 0));
        assertTrue(WandSelectionMoveMessage.isUnitAxisOffset(0, 0, 1));
        assertFalse(WandSelectionMoveMessage.isUnitAxisOffset(0, 0, 0));
        assertFalse(WandSelectionMoveMessage.isUnitAxisOffset(1, 1, 0));
        assertFalse(WandSelectionMoveMessage.isUnitAxisOffset(2, 0, 0));
    }
}
