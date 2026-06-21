package com.summerbuddies.custommissions.mission;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * A point of interest a mission cares about: a position in one dimension with a trigger {@code radius}
 * and the label/color to show on the quest marker. Reused by {@code reach_location} objectives (where it
 * drives both completion and the waypoint) and as a mission's optional headline location.
 */
public record MissionLocation(ResourceLocation dimension, int x, int y, int z, int radius,
                              String waypointName, int waypointRgb) {

    public static final int DEFAULT_RADIUS = 6;
    public static final int DEFAULT_RGB = 0xE8C170; // gold, the server's accent

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }

    /** True when {@code (px,py,pz)} in {@code dim} is within {@code radius} blocks (3D) of this point. */
    public boolean within(ResourceLocation dim, double px, double py, double pz) {
        if (!dimension.equals(dim)) {
            return false;
        }
        double dx = px - (x + 0.5);
        double dy = py - (y + 0.5);
        double dz = pz - (z + 0.5);
        return (dx * dx + dy * dy + dz * dz) <= (double) radius * radius;
    }
}
