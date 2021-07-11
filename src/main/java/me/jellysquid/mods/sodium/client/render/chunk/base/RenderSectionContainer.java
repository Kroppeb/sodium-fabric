package me.jellysquid.mods.sodium.client.render.chunk.base;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.SectionCuller;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;

import java.util.Collection;
import java.util.Iterator;

public interface RenderSectionContainer extends SectionCuller.FrustumChecker {
    // TODO: figure out why this doesn't need the camera
    void updateVisibility(FrustumExtended frustum);

    RenderSection createSection(RenderSectionManager renderSectionManager, int x, int y, int z);

    RenderSection remove(int x, int y, int z);

    RenderSection get(int x, int y, int z);


    void upload(CommandList commandList, Iterator<? extends ChunkBuildResult> chunkBuildResults);

    void cleanup();

    void delete(CommandList commandList);

    int getTotalSectionCount();

    Collection<? extends String> getMemoryDebugStrings();
}
