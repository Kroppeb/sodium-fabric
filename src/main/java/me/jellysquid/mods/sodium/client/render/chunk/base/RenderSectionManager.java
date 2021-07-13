package me.jellysquid.mods.sodium.client.render.chunk.base;

import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;

import java.util.Collection;

/**
 * Tasked to go general communication of {@link RenderSection} stored and created by the {@link RenderSectionContainer},
 * dispatching the building of these chunks in an appropriate way,
 */
public interface RenderSectionManager extends ChunkStatusListener {
    void destroy();

    /**
     * @return True if no chunks are pending rebuilds
     * @deprecated idk if I like this here
     */
    @Deprecated
    boolean isBuildQueueEmpty();

    /**
     * @deprecated the {@link RenderSectionManager} is not responsible for tracking the visibility of chunks
     */
    @Deprecated
    void markGraphDirty();

    /**
     * start
     */
    void loadChunks();

    /**
     * @deprecated this waits for the important chunks to be build and schedules the others
     */
    @Deprecated
    void updateChunks();

    /**
     * @deprecated
     */
    @Deprecated
    boolean isGraphDirty();

    void update(Camera camera, FrustumExtended frustum, int frame, boolean spectator);

    Iterable<? extends BlockEntity> getGlobalBlockEntities();

    @Deprecated
    Collection<? extends String> getRenderSectionContainerMemoryDebugStrings();

    void scheduleRebuild(int x, int y, int z, boolean important);

    int getTotalSections();

    void schedulePendingUpdates(RenderSection section);
}
