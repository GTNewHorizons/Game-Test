package com.gtnewhorizons.horizonqa.visual.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.visual.editor.SelectionGizmoMath.Direction;

public class SelectionGizmoMathTest {

    @Test
    public void picksPositiveXAxisFromTheCrosshairRay() {
        assertEquals(Direction.POSITIVE_X, pickFrom(0.9, 0.0, 0.0));
    }

    @Test
    public void picksNegativeYAxisFromTheCrosshairRay() {
        assertEquals(Direction.NEGATIVE_Y, pickFrom(0.0, -1.1, 0.0));
    }

    @Test
    public void choosesTheFirstArrowHitWhenLookingAlongAnAxis() {
        assertEquals(
            Direction.NEGATIVE_X,
            SelectionGizmoMath.pickDirection(-5.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0));
    }

    @Test
    public void returnsNoDirectionWhenTheRayMissesEveryHandle() {
        assertNull(SelectionGizmoMath.pickDirection(0.0, 0.0, -5.0, 0.4, 0.4, 5.0, 0.0, 0.0, 0.0, 1.0));
    }

    @Test
    public void mapsTheCursorRayBackOntoTheDraggedWorldAxis() {
        double parameter = SelectionGizmoMath
            .axisParameter(0.0, 0.0, -5.0, 2.0, 0.0, 5.0, 0.0, 0.0, 0.0, Direction.POSITIVE_X);

        assertEquals(2.0, parameter, 1.0e-9);
    }

    @Test
    public void refusesAnAxisThatProjectsToAViewDirectionPoint() {
        double parameter = SelectionGizmoMath
            .axisParameter(0.0, 0.0, -5.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, Direction.POSITIVE_Z);

        assertTrue(Double.isNaN(parameter));
    }

    @Test
    public void picksAResizeHandleAtThePositiveSelectionFace() {
        assertEquals(
            Direction.POSITIVE_X,
            SelectionGizmoMath
                .pickResizeDirection(0.0, 0.0, -5.0, 1.72, 0.5, 5.0, -1.0, -1.0, 0.0, 1.0, 2.0, 1.0, 1.0));
    }

    private static Direction pickFrom(double targetX, double targetY, double targetZ) {
        double originX = 0.0;
        double originY = 0.0;
        double originZ = -5.0;
        return SelectionGizmoMath.pickDirection(
            originX,
            originY,
            originZ,
            targetX - originX,
            targetY - originY,
            targetZ - originZ,
            0.0,
            0.0,
            0.0,
            1.0);
    }
}
