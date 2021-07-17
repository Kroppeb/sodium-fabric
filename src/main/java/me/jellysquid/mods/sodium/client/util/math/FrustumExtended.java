package me.jellysquid.mods.sodium.client.util.math;

import me.jellysquid.mods.sodium.client.render.chunk.backend.region.RenderRegionVisibility;
import net.minecraft.util.math.Matrix4f;

import java.nio.FloatBuffer;

public interface FrustumExtended {
    boolean fastAabbTest(float minX, float minY, float minZ, float maxX, float maxY, float maxZ);

    RenderRegionVisibility aabbTest(float minX, float minY, float minZ, float maxX, float maxY, float maxZ);

    void writeToBuffer(FloatBuffer buffer);

    Matrix4f getModelViewMatrix();

    Matrix4f getProjectionMatrix();

    Matrix4f getProjectionModelViewMatrix();
}
