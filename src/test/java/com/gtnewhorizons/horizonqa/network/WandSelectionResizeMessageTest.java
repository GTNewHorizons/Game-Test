package com.gtnewhorizons.horizonqa.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WandSelectionResizeMessageTest {

    @Test
    public void acceptsOnlyAUnitSideAndUnitAmount() {
        assertTrue(WandSelectionResizeMessage.isValidStep(1, 0, 0, 1));
        assertTrue(WandSelectionResizeMessage.isValidStep(0, -1, 0, -1));
        assertFalse(WandSelectionResizeMessage.isValidStep(0, 0, 0, 1));
        assertFalse(WandSelectionResizeMessage.isValidStep(1, 1, 0, 1));
        assertFalse(WandSelectionResizeMessage.isValidStep(1, 0, 0, 0));
        assertFalse(WandSelectionResizeMessage.isValidStep(1, 0, 0, 2));
    }
}
