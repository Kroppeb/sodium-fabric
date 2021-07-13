package me.jellysquid.mods.sodium.client.render.chunk;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.base.*;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderBuildTask;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderEmptyBuildTask;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderRebuildTask;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.client.world.ClientChunkManagerExtended;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext;
import me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import me.jellysquid.mods.sodium.common.util.ListUtil;
import me.jellysquid.mods.sodium.common.util.collections.FutureQueueDrainingIterator;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DefaultRenderSectionManager implements RenderSectionManager {
    /**
     * The maximum distance a chunk can be from the player's camera in order to be eligible for blocking updates.
     */
    private static final double NEARBY_CHUNK_DISTANCE = Math.pow(48, 2.0);


    private final ChunkBuilder builder;

    private final RenderSectionContainer renderSectionContainer;
    private final ClonedChunkSectionCache sectionCache;

    private final PrioritizableBuilder prioritizableBuilder;

    private final ChunkAdjacencyMap adjacencyMap = new ChunkAdjacencyMap();

    private final Set<BlockEntity> globalBlockEntities = new ObjectOpenHashSet<>();

    private final ClientWorld world;

    private float cameraX, cameraY, cameraZ;

    private boolean needsUpdate;

    private int currentFrame = 0;

    private final VisibilityTracker visibilityTracker;

    public DefaultRenderSectionManager(
            VisibilityTracker visibilityTracker,
            ChunkVertexType chunkVertexType,
            BlockRenderPassManager renderPassManager,
            ClientWorld world,
            RenderSectionContainer renderSectionContainer) {
        this.world = world;

        this.builder = new ChunkBuilder(chunkVertexType);
        this.prioritizableBuilder = new PrioritizableBuilder(this.builder, this::createRebuildTask);
        this.visibilityTracker = visibilityTracker;
        this.visibilityTracker.setRenderSectionManager(this);
        this.builder.init(world, renderPassManager);

        this.needsUpdate = true;

        // todo: provide dynamically
        this.renderSectionContainer = renderSectionContainer;
        this.sectionCache = new ClonedChunkSectionCache(this.world);
    }

    public void loadChunks() {
        LongIterator it = ((ClientChunkManagerExtended) this.world.getChunkManager())
                .getLoadedChunks()
                .iterator();

        while (it.hasNext()) {
            long pos = it.nextLong();

            this.onChunkAdded(ChunkPos.getPackedX(pos), ChunkPos.getPackedZ(pos));
        }
    }

    public void update(Camera camera, FrustumExtended frustum, int frame, boolean spectator) {
        this.resetLists();

        this.renderSectionContainer.updateVisibility(frustum);

        this.setup(camera);
        this.currentFrame = frame;

        this.needsUpdate = false;
    }

    private void setup(Camera camera) {
        Vec3d cameraPos = camera.getPos();

        this.cameraX = (float) cameraPos.x;
        this.cameraY = (float) cameraPos.y;
        this.cameraZ = (float) cameraPos.z;
    }

    @Override
    public void schedulePendingUpdates(RenderSection section) {
        if (section.getPendingUpdate() == null || !this.adjacencyMap.hasNeighbors(section.getChunkX(), section.getChunkZ())) {
            return;
        }

        this.prioritizableBuilder.schedulePendingUpdates(section);
    }


    private void resetLists() {
        this.prioritizableBuilder.clear();
    }

    @Override
    public void onChunkAdded(int x, int z) {
        this.adjacencyMap.onChunkLoaded(x, z);

        for (int y = this.world.getBottomSectionCoord(); y < this.world.getTopSectionCoord(); y++) {
            this.needsUpdate |= this.loadSection(x, y, z);
        }
    }

    @Override
    public void onChunkRemoved(int x, int z) {
        this.adjacencyMap.onChunkUnloaded(x, z);

        for (int y = this.world.getBottomSectionCoord(); y < this.world.getTopSectionCoord(); y++) {
            this.needsUpdate |= this.unloadSection(x, y, z);
        }
    }

    private boolean loadSection(int x, int y, int z) {
        RenderSection renderSection = this.renderSectionContainer.createSection(this, x, y, z);

        this.visibilityTracker.addSection(renderSection);

        Chunk chunk = this.world.getChunk(x, z);
        ChunkSection section = chunk.getSectionArray()[this.world.sectionCoordToIndex(y)];

        if (ChunkSection.isEmpty(section)) {
            renderSection.setData(ChunkRenderData.EMPTY);
        } else {
            renderSection.markForUpdate(ChunkUpdateType.INITIAL_BUILD);
        }

        return true;
    }

    private boolean unloadSection(int x, int y, int z) {
        RenderSection renderSection = this.renderSectionContainer.remove(x, y, z);

        if (renderSection == null) {
            throw new IllegalStateException("Chunk is not loaded: " + ChunkSectionPos.asLong(x, y, z));
        }

        this.visibilityTracker.removeSection(renderSection);

        return true;
    }

    @Override
    @Deprecated
    public Collection<? extends String> getRenderSectionContainerMemoryDebugStrings() {
        return this.renderSectionContainer.getMemoryDebugStrings();
    }

    @Override
    public void updateChunks() {
        PriorityQueue<? extends CompletableFuture<? extends ChunkBuildResult>> blockingFutures = this.prioritizableBuilder.submitRebuildTasks(ChunkUpdateType.IMPORTANT_REBUILD);

        // TODO this is before we
        this.prioritizableBuilder.submitRebuildTasks(ChunkUpdateType.INITIAL_BUILD);
        this.prioritizableBuilder.submitRebuildTasks(ChunkUpdateType.REBUILD);

        // Try to complete some other work on the main thread while we wait for rebuilds to complete
        this.needsUpdate |= this.performPendingUploads();

        if (!blockingFutures.isEmpty()) {
            this.needsUpdate = true;
            this.renderSectionContainer.upload(
                    RenderDevice.INSTANCE.createCommandList(),
                    new FutureQueueDrainingIterator<>(blockingFutures));
        }

        // TODO: can't this be moved before the upload, while we might be waiting?
        this.renderSectionContainer.cleanup();
    }



    private boolean performPendingUploads() {
        Iterator<? extends ChunkBuildResult> it = this.builder.createDeferredBuildResultDrain();

        if (!it.hasNext()) {
            return false;
        }

        this.renderSectionContainer.upload(RenderDevice.INSTANCE.createCommandList(), it);

        return true;
    }

    private ChunkRenderBuildTask createRebuildTask(RenderSection render) {
        ChunkRenderContext context = WorldSlice.prepare(this.world, render.getChunkPos(), this.sectionCache);
        int frame = this.currentFrame;

        if (context == null) {
            return new ChunkRenderEmptyBuildTask(render, frame);
        }

        return new ChunkRenderRebuildTask(render, context, frame);
    }

    public void markGraphDirty() {
        this.needsUpdate = true;
    }

    public boolean isGraphDirty() {
        return this.needsUpdate;
    }


    @Override
    public void destroy() {
        this.resetLists();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.renderSectionContainer.delete(commandList);
        }

        this.builder.stopWorkers();
    }

    @Override
    public boolean isBuildQueueEmpty() {
        return this.builder.isBuildQueueEmpty();
    }

    public int getTotalSections() {
        return this.renderSectionContainer.getTotalSectionCount();
    }

    public void scheduleRebuild(int x, int y, int z, boolean important) {
        this.sectionCache.invalidate(x, y, z);

        RenderSection section = this.renderSectionContainer.get(x, y, z);

        if (section != null && section.isBuilt()) {
            if (important || this.isChunkPrioritized(section)) {
                section.markForUpdate(ChunkUpdateType.IMPORTANT_REBUILD);
            } else {
                section.markForUpdate(ChunkUpdateType.REBUILD);
            }
        }

        this.needsUpdate = true;
    }

    private boolean isChunkPrioritized(RenderSection render) {
        return render.getSquaredDistance(this.cameraX, this.cameraY, this.cameraZ) <= NEARBY_CHUNK_DISTANCE;
    }

    public void onChunkRenderUpdated(int x, int y, int z, ChunkRenderData meshBefore, ChunkRenderData meshAfter) {
        ListUtil.updateList(this.globalBlockEntities, meshBefore.getGlobalBlockEntities(), meshAfter.getGlobalBlockEntities());

        this.onChunkRenderUpdates(x, y, z, meshAfter);
    }

    private void onChunkRenderUpdates(int x, int y, int z, ChunkRenderData data) {
        RenderSection node = this.getRenderSection(x, y, z);

        if (node != null) {
            // todo: can this ever be not null?
            this.visibilityTracker.updateSection(node);
        }
    }

    private RenderSection getRenderSection(int x, int y, int z) {
        return this.renderSectionContainer.get(x, y, z);
    }

    public Set<? extends BlockEntity> getGlobalBlockEntities() {
        return this.globalBlockEntities;
    }
}
