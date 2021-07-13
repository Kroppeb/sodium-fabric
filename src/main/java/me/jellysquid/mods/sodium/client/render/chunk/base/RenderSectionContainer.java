package me.jellysquid.mods.sodium.client.render.chunk.base;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultRenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;

import java.util.Collection;
import java.util.Iterator;

/**
 * The RenderSectionContainer is responsible for managing (or delegating) the tracking of {@link RenderSection} and
 * their resources
 */
public interface RenderSectionContainer {
    // TODO: figure out why this doesn't need the camera
    // TODO: don't really like this here
    @Deprecated
    default void updateVisibility(FrustumExtended frustum){

    }

    RenderSection createSection(DefaultRenderSectionManager renderSectionManager, int x, int y, int z);

    RenderSection remove(int x, int y, int z);

    RenderSection get(int x, int y, int z);


    void upload(CommandList commandList, Iterator<? extends ChunkBuildResult> chunkBuildResults);

    void cleanup();

    void delete(CommandList commandList);

    int getTotalSectionCount();

    Collection<? extends String> getMemoryDebugStrings();
}
