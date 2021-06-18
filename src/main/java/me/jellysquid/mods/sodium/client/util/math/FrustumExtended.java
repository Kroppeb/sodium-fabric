package me.jellysquid.mods.sodium.client.util.math;

import java.nio.FloatBuffer;

public interface FrustumExtended {
    boolean fastAabbTest(float minX, float minY, float minZ, float maxX, float maxY, float maxZ);

    void writeToBuffer(FloatBuffer buffer);
}
