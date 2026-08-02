package com.gtnewhorizons.horizonqa.visual.editor;

import com.github.bsideup.jabel.Desugar;

final class SelectionGizmoMath {

    private static final double PICK_START = 0.22;
    private static final double PICK_END = 1.78;
    private static final double PICK_RADIUS = 0.18;
    static final double RESIZE_STEM_LENGTH = 0.72;
    private static final double RESIZE_PICK_RADIUS = 0.24;

    enum Direction {

        POSITIVE_X(1, 0, 0),
        NEGATIVE_X(-1, 0, 0),
        POSITIVE_Y(0, 1, 0),
        NEGATIVE_Y(0, -1, 0),
        POSITIVE_Z(0, 0, 1),
        NEGATIVE_Z(0, 0, -1);

        final int dx;
        final int dy;
        final int dz;

        Direction(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }

    private SelectionGizmoMath() {}

    static Direction pickDirection(double originX, double originY, double originZ, double rayX, double rayY,
        double rayZ, double centerX, double centerY, double centerZ, double scale) {
        double rayLength = Math.sqrt(rayX * rayX + rayY * rayY + rayZ * rayZ);
        if (rayLength < 1.0e-9 || scale <= 0.0) {
            return null;
        }
        rayX /= rayLength;
        rayY /= rayLength;
        rayZ /= rayLength;

        Direction best = null;
        double bestDistanceSquared = PICK_RADIUS * PICK_RADIUS * scale * scale;
        double bestRayDistance = Double.POSITIVE_INFINITY;
        for (Direction direction : Direction.values()) {
            double startX = centerX + direction.dx * PICK_START * scale;
            double startY = centerY + direction.dy * PICK_START * scale;
            double startZ = centerZ + direction.dz * PICK_START * scale;
            ClosestApproach approach = closestApproach(
                originX,
                originY,
                originZ,
                rayX,
                rayY,
                rayZ,
                startX,
                startY,
                startZ,
                direction.dx,
                direction.dy,
                direction.dz,
                (PICK_END - PICK_START) * scale);
            if (approach.distanceSquared < bestDistanceSquared
                || (approach.distanceSquared == bestDistanceSquared && approach.rayDistance < bestRayDistance)) {
                best = direction;
                bestDistanceSquared = approach.distanceSquared;
                bestRayDistance = approach.rayDistance;
            }
        }
        return best;
    }

    static Direction pickResizeDirection(double originX, double originY, double originZ, double rayX, double rayY,
        double rayZ, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double scale) {
        double rayLength = Math.sqrt(rayX * rayX + rayY * rayY + rayZ * rayZ);
        if (rayLength < 1.0e-9 || scale <= 0.0) return null;
        rayX /= rayLength;
        rayY /= rayLength;
        rayZ /= rayLength;

        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        Direction best = null;
        double bestDistanceSquared = RESIZE_PICK_RADIUS * RESIZE_PICK_RADIUS * scale * scale;
        double bestRayDistance = Double.POSITIVE_INFINITY;
        for (Direction direction : Direction.values()) {
            double startX = direction.dx > 0 ? maxX : direction.dx < 0 ? minX : centerX;
            double startY = direction.dy > 0 ? maxY : direction.dy < 0 ? minY : centerY;
            double startZ = direction.dz > 0 ? maxZ : direction.dz < 0 ? minZ : centerZ;
            ClosestApproach approach = closestApproach(
                originX,
                originY,
                originZ,
                rayX,
                rayY,
                rayZ,
                startX,
                startY,
                startZ,
                direction.dx,
                direction.dy,
                direction.dz,
                RESIZE_STEM_LENGTH * scale);
            if (approach.distanceSquared < bestDistanceSquared
                || (approach.distanceSquared == bestDistanceSquared && approach.rayDistance < bestRayDistance)) {
                best = direction;
                bestDistanceSquared = approach.distanceSquared;
                bestRayDistance = approach.rayDistance;
            }
        }
        return best;
    }

    static double axisParameter(double originX, double originY, double originZ, double rayX, double rayY, double rayZ,
        double axisOriginX, double axisOriginY, double axisOriginZ, Direction direction) {
        double rayLength = Math.sqrt(rayX * rayX + rayY * rayY + rayZ * rayZ);
        if (rayLength < 1.0e-9) return Double.NaN;
        rayX /= rayLength;
        rayY /= rayLength;
        rayZ /= rayLength;

        double wx = originX - axisOriginX;
        double wy = originY - axisOriginY;
        double wz = originZ - axisOriginZ;
        double rayAxis = rayX * direction.dx + rayY * direction.dy + rayZ * direction.dz;
        double rayOrigin = rayX * wx + rayY * wy + rayZ * wz;
        double axisOrigin = direction.dx * wx + direction.dy * wy + direction.dz * wz;
        double denominator = 1.0 - rayAxis * rayAxis;
        if (denominator < 0.0064) return Double.NaN;

        double rayDistance = (rayAxis * axisOrigin - rayOrigin) / denominator;
        if (rayDistance < 0.0) return Double.NaN;
        return (axisOrigin - rayAxis * rayOrigin) / denominator;
    }

    private static ClosestApproach closestApproach(double originX, double originY, double originZ, double rayX,
        double rayY, double rayZ, double startX, double startY, double startZ, double axisX, double axisY, double axisZ,
        double segmentLength) {
        ClosestApproach best = new ClosestApproach(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        double wx = originX - startX;
        double wy = originY - startY;
        double wz = originZ - startZ;
        double rayAxis = rayX * axisX + rayY * axisY + rayZ * axisZ;
        double rayOrigin = rayX * wx + rayY * wy + rayZ * wz;
        double axisOrigin = axisX * wx + axisY * wy + axisZ * wz;
        double denominator = 1.0 - rayAxis * rayAxis;

        if (denominator > 1.0e-9) {
            double rayDistance = (rayAxis * axisOrigin - rayOrigin) / denominator;
            double segmentDistance = (axisOrigin - rayAxis * rayOrigin) / denominator;
            if (rayDistance >= 0.0 && segmentDistance >= 0.0 && segmentDistance <= segmentLength) {
                best = evaluate(
                    originX,
                    originY,
                    originZ,
                    rayX,
                    rayY,
                    rayZ,
                    startX,
                    startY,
                    startZ,
                    axisX,
                    axisY,
                    axisZ,
                    rayDistance,
                    segmentDistance,
                    best);
            }
        }

        best = evaluateEndpoint(originX, originY, originZ, rayX, rayY, rayZ, startX, startY, startZ, best);
        best = evaluateEndpoint(
            originX,
            originY,
            originZ,
            rayX,
            rayY,
            rayZ,
            startX + axisX * segmentLength,
            startY + axisY * segmentLength,
            startZ + axisZ * segmentLength,
            best);

        double segmentAtRayOrigin = clamp(axisOrigin, 0.0, segmentLength);
        return evaluate(
            originX,
            originY,
            originZ,
            rayX,
            rayY,
            rayZ,
            startX,
            startY,
            startZ,
            axisX,
            axisY,
            axisZ,
            0.0,
            segmentAtRayOrigin,
            best);
    }

    private static ClosestApproach evaluateEndpoint(double originX, double originY, double originZ, double rayX,
        double rayY, double rayZ, double pointX, double pointY, double pointZ, ClosestApproach best) {
        double rayDistance = Math
            .max(0.0, (pointX - originX) * rayX + (pointY - originY) * rayY + (pointZ - originZ) * rayZ);
        double dx = originX + rayX * rayDistance - pointX;
        double dy = originY + rayY * rayDistance - pointY;
        double dz = originZ + rayZ * rayDistance - pointZ;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared < best.distanceSquared) {
            return new ClosestApproach(distanceSquared, rayDistance);
        }
        return best;
    }

    private static ClosestApproach evaluate(double originX, double originY, double originZ, double rayX, double rayY,
        double rayZ, double startX, double startY, double startZ, double axisX, double axisY, double axisZ,
        double rayDistance, double segmentDistance, ClosestApproach best) {
        double dx = originX + rayX * rayDistance - startX - axisX * segmentDistance;
        double dy = originY + rayY * rayDistance - startY - axisY * segmentDistance;
        double dz = originZ + rayZ * rayDistance - startZ - axisZ * segmentDistance;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared < best.distanceSquared) {
            return new ClosestApproach(distanceSquared, rayDistance);
        }
        return best;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Desugar
    private record ClosestApproach(double distanceSquared, double rayDistance) {

    }
}
