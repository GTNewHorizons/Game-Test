package com.gtnewhorizons.horizonqa.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WandLabelMoveMessageTest {

    @Test
    public void validatesTheNameAndUnitOffset() {
        assertTrue(WandLabelMoveMessage.isValid("controller", 0, 1, 0));
        assertFalse(WandLabelMoveMessage.isValid("bad name", 0, 1, 0));
        assertFalse(WandLabelMoveMessage.isValid("controller", 1, 1, 0));
        assertFalse(WandLabelMoveMessage.isValid("controller", 0, 0, 0));
    }
}
