package com.gtnewhorizons.horizonqa.internal;

public final class TestCell {

    public final String testId;
    public final int originX, originY, originZ;
    public final int minX, minY, minZ, maxX, maxY, maxZ;

    public TestCell(String testId, int originX, int originY, int originZ, int minX, int minY, int minZ, int maxX,
        int maxY, int maxZ) {
        this.testId = testId;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }
}
