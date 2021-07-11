package me.jellysquid.mods.sodium.client.render.chunk;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.base.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.base.ChunkVisibilityListener;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderBuildTask;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderEmptyBuildTask;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderRebuildTask;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.client.world.ClientChunkManagerExtended;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext;
import me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import me.jellysquid.mods.sodium.common.util.ListUtil;
import me.jellysquid.mods.sodium.common.util.collections.FutureQueueDrainingIterator;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RenderSectionManager implements ChunkStatusListener, SectionCuller.CullerInteractor {
    /**
     * The maximum distance a chunk can be from the player's camera in order to be eligible for blocking updates.
     */
    private static final double NEARBY_CHUNK_DISTANCE = Math.pow(48, 2.0);


    private final ChunkBuilder builder;
    private final ChunkRenderer chunkRenderer;

    private final RenderSectionContainer renderSectionContainer;
    private final ClonedChunkSectionCache sectionCache;

    private final Map<ChunkUpdateType, PriorityQueue<RenderSection>> rebuildQueues = new EnumMap<>(ChunkUpdateType.class);

    private final ChunkAdjacencyMap adjacencyMap = new ChunkAdjacencyMap();

    private final ChunkVisibilityListener chunkRenderList;

    private final ObjectList<RenderSection> tickableChunks = new ObjectArrayList<>();
    private final ObjectList<BlockEntity> visibleBlockEntities = new ObjectArrayList<>();

    private final Set<BlockEntity> globalBlockEntities = new ObjectOpenHashSet<>();

    private final ClientWorld world;

    private float cameraX, cameraY, cameraZ;

    private boolean needsUpdate;

    private int currentFrame = 0;

    private final Culler culler;

    public RenderSectionManager(
            ChunkRenderer chunkRenderer,
            BlockRenderPassManager renderPassManager,
            ClientWorld world,
            Culler culler,
            RenderSectionContainer renderSectionContainer) {
        this.chunkRenderer = chunkRenderer;
        this.world = world;

        this.builder = new ChunkBuilder(chunkRenderer.getVertexType());
        this.chunkRenderList = chunkRenderer.getChunkVisibilityListener();
        this.builder.init(world, renderPassManager);

        this.needsUpdate = true;

        // todo: provide dynamically
        this.renderSectionContainer = renderSectionContainer;
        this.sectionCache = new ClonedChunkSectionCache(this.world);

        for (ChunkUpdateType type : ChunkUpdateType.values()) {
            this.rebuildQueues.put(type, new ObjectArrayFIFOQueue<>());
        }

        this.culler = culler;
        this.culler.setCullerInteractor(this);
        this.culler.setFrustumChecker(renderSectionContainer);
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
        this.culler.setup(camera);
        this.currentFrame = frame;
        this.culler.iterateChunks(camera, frustum, frame, spectator);

        this.needsUpdate = false;
    }

    private void setup(Camera camera) {
        Vec3d cameraPos = camera.getPos();

        this.cameraX = (float) cameraPos.x;
        this.cameraY = (float) cameraPos.y;
        this.cameraZ = (float) cameraPos.z;
    }

    public void schedulePendingUpdates(RenderSection section) {
        if (section.getPendingUpdate() == null || !this.adjacencyMap.hasNeighbors(section.getChunkX(), section.getChunkZ())) {
            return;
        }

        PriorityQueue<RenderSection> queue = this.rebuildQueues.get(section.getPendingUpdate());

        if (queue.size() >= 32) {
            return;
        }

        queue.enqueue(section);
    }

    @Override
    public RenderSection resolveSection(int chunkX, int chunkY, int chunkZ) {
        return this.getRenderSection(chunkX, chunkY, chunkZ);
    }

    public void addChunkToVisible(RenderSection render) {
        this.chunkRenderList.add(render);

        if (render.isTickable()) {
            this.tickableChunks.add(render);
        }
    }

    public void addEntitiesToRenderLists(RenderSection render) {
        Collection<BlockEntity> blockEntities = render.getData().getBlockEntities();

        if (!blockEntities.isEmpty()) {
            this.visibleBlockEntities.addAll(blockEntities);
        }
    }

    private void resetLists() {
        for (PriorityQueue<RenderSection> queue : this.rebuildQueues.values()) {
            queue.clear();
        }

        this.visibleBlockEntities.clear();
        this.chunkRenderList.clear();
        this.tickableChunks.clear();
    }

    public Collection<BlockEntity> getVisibleBlockEntities() {
        return this.visibleBlockEntities;
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
        RenderSection render = this.renderSectionContainer.createSection(this, x, y, z);

        Chunk chunk = this.world.getChunk(x, z);
        ChunkSection section = chunk.getSectionArray()[this.world.sectionCoordToIndex(y)];

        if (ChunkSection.isEmpty(section)) {
            render.setData(ChunkRenderData.EMPTY);
        } else {
            render.markForUpdate(ChunkUpdateType.INITIAL_BUILD);
        }

        this.connectNeighborNodes(render);

        return true;
    }

    private boolean unloadSection(int x, int y, int z) {
        RenderSection chunk = this.renderSectionContainer.remove(x, y, z);

        if (chunk == null) {
            throw new IllegalStateException("Chunk is not loaded: " + ChunkSectionPos.asLong(x, y, z));
        }

        chunk.delete();

        this.disconnectNeighborNodes(chunk);

        return true;
    }

    public void renderLayer(MatrixStack matrixStack, BlockRenderPass pass, double x, double y, double z) {
        RenderDevice device = RenderDevice.INSTANCE;
        CommandList commandList = device.createCommandList();

        this.chunkRenderer.render(matrixStack, commandList, pass, new ChunkCameraContext(x, y, z));

        commandList.flush();
    }

    public void tickVisibleRenders() {
        for (RenderSection render : this.tickableChunks) {
            render.tick();
        }
    }

    public boolean isSectionVisible(int x, int y, int z) {
        RenderSection render = this.getRenderSection(x, y, z);

        if (render == null) {
            return false;
        }

        return render.getGraphInfo()
                .getLastVisibleFrame() == this.currentFrame;
    }

    public void updateChunks() {
        PriorityQueue<CompletableFuture<ChunkBuildResult>> blockingFutures = this.submitRebuildTasks(ChunkUpdateType.IMPORTANT_REBUILD);

        this.submitRebuildTasks(ChunkUpdateType.INITIAL_BUILD);
        this.submitRebuildTasks(ChunkUpdateType.REBUILD);

        // Try to complete some other work on the main thread while we wait for rebuilds to complete
        this.needsUpdate |= this.performPendingUploads();

        if (!blockingFutures.isEmpty()) {
            this.renderSectionContainer.upload(
                    RenderDevice.INSTANCE.createCommandList(),
                    new FutureQueueDrainingIterator<>(blockingFutures));
        }

        this.renderSectionContainer.cleanup();
    }

    private PriorityQueue<CompletableFuture<ChunkBuildResult>> submitRebuildTasks(ChunkUpdateType filterType) {
        int budget = filterType.isImportant() ? Integer.MAX_VALUE : this.builder.getSchedulingBudget();

        PriorityQueue<CompletableFuture<ChunkBuildResult>> immediateFutures = new ObjectArrayFIFOQueue<>();
        PriorityQueue<RenderSection> queue = this.rebuildQueues.get(filterType);

        while (budget > 0 && !queue.isEmpty()) {
            RenderSection section = queue.dequeue();

            if (section.isDisposed()) {
                continue;
            }

            if (section.getPendingUpdate() != filterType) {
                SodiumClientMod.logger().warn("{} changed update type to {} while in queue for {}, skipping",
                        section, section.getPendingUpdate(), filterType);

                continue;
            }

            ChunkRenderBuildTask task = this.createRebuildTask(section);
            CompletableFuture<?> future;

            if (filterType.isImportant()) {
                CompletableFuture<ChunkBuildResult> immediateFuture = this.builder.schedule(task);
                immediateFutures.enqueue(immediateFuture);

                future = immediateFuture;
            } else {
                future = this.builder.scheduleDeferred(task);
            }

            section.onBuildSubmitted(future);

            budget--;
        }

        return immediateFutures;
    }

    private boolean performPendingUploads() {
        Iterator<ChunkBuildResult> it = this.builder.createDeferredBuildResultDrain();

        if (!it.hasNext()) {
            return false;
        }

        this.renderSectionContainer.upload(RenderDevice.INSTANCE.createCommandList(), it);

        return true;
    }

    public ChunkRenderBuildTask createRebuildTask(RenderSection render) {
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

    public ChunkBuilder getBuilder() {
        return this.builder;
    }

    public void destroy() {
        this.resetLists();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.renderSectionContainer.delete(commandList);
        }

        this.builder.stopWorkers();
    }

    public int getTotalSections() {
        return this.renderSectionContainer.getTotalSectionCount();
    }

    public int getVisibleChunkCount() {
        return this.chunkRenderList.getCount();
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

    public boolean isChunkPrioritized(RenderSection render) {
        return render.getSquaredDistance(this.cameraX, this.cameraY, this.cameraZ) <= NEARBY_CHUNK_DISTANCE;
    }

    public void onChunkRenderUpdated(int x, int y, int z, ChunkRenderData meshBefore, ChunkRenderData meshAfter) {
        ListUtil.updateList(this.globalBlockEntities, meshBefore.getGlobalBlockEntities(), meshAfter.getGlobalBlockEntities());

        this.onChunkRenderUpdates(x, y, z, meshAfter);
    }

    public void onChunkRenderUpdates(int x, int y, int z, ChunkRenderData data) {
        RenderSection node = this.getRenderSection(x, y, z);

        if (node != null) {
            node.setOcclusionData(data.getOcclusionData());
        }
    }


    private void connectNeighborNodes(RenderSection render) {
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            RenderSection adj = this.getRenderSection(render.getChunkX() + dir.getOffsetX(),
                    render.getChunkY() + dir.getOffsetY(),
                    render.getChunkZ() + dir.getOffsetZ());

            if (adj != null) {
                adj.setAdjacentNode(DirectionUtil.getOpposite(dir), render);
                render.setAdjacentNode(dir, adj);
            }
        }
    }

    private void disconnectNeighborNodes(RenderSection render) {
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            RenderSection adj = render.getAdjacent(dir);

            if (adj != null) {
                adj.setAdjacentNode(DirectionUtil.getOpposite(dir), null);
                render.setAdjacentNode(dir, null);
            }
        }
    }

    private RenderSection getRenderSection(int x, int y, int z) {
        return this.renderSectionContainer.get(x, y, z);
    }

    public RenderSectionContainer getRenderSectionContainer() {
        return this.renderSectionContainer;
    }

    public Set<? extends BlockEntity> getGlobalBlockEntities() {
        return this.globalBlockEntities;
    }
}
