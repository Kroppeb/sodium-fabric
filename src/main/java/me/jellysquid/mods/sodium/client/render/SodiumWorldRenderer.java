package me.jellysquid.mods.sodium.client.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.base.*;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkModelVertexFormats;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheShared;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.client.world.ClientChunkManagerExtended;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;

import java.util.Collection;
import java.util.SortedSet;

/**
 * Provides an extension to vanilla's {@link WorldRenderer}.
 */
public final class SodiumWorldRenderer implements ChunkStatusListener {
    private static SodiumWorldRenderer instance;

    private final MinecraftClient client;

    private ClientWorld world;
    private int renderDistance;

    private double lastCameraX, lastCameraY, lastCameraZ;
    private double lastCameraPitch, lastCameraYaw;

    private boolean useEntityCulling;

    private Frustum frustum;
    @Deprecated
    private RenderSectionManager renderSectionManager;
    private Pipeline pipeline;
    private BlockRenderPassManager renderPassManager;


    /**
     * Instantiates Sodium's world renderer. This should be called at the time of the world renderer initialization.
     */
    public static SodiumWorldRenderer create() {
        if (instance == null) {
            instance = new SodiumWorldRenderer(MinecraftClient.getInstance());
        }

        return instance;
    }

    /**
     * @return The current instance of this type
     * @throws IllegalStateException If the renderer has not yet been created
     */
    public static SodiumWorldRenderer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Renderer not initialized");
        }

        return instance;
    }

    private SodiumWorldRenderer(MinecraftClient client) {
        this.client = client;
    }

    public void setWorld(ClientWorld world) {
        // Check that the world is actually changing
        if (this.world == world) {
            return;
        }

        // If we have a world is already loaded, unload the renderer
        if (this.world != null) {
            this.unloadWorld();
        }

        // If we're loading a new world, load the renderer
        if (world != null) {
            this.loadWorld(world);
        }
    }

    private void loadWorld(ClientWorld world) {
        this.world = world;

        ChunkRenderCacheShared.createRenderContext(this.world);

        this.initRenderer();

        ((ClientChunkManagerExtended) world.getChunkManager()).setListener(this);
    }

    private void unloadWorld() {
        ChunkRenderCacheShared.destroyRenderContext(this.world);

        if (this.pipeline != null) {
            this.pipeline.delete();
            this.pipeline = null;
            this.renderSectionManager = null;
        }

        this.world = null;
    }

    /**
     * @return The number of chunk renders which are visible in the current camera's frustum
     */
    public int getVisibleChunkCount() {
        return this.pipeline.getVisibleChunkCount();
    }

    /**
     * Notifies the chunk renderer that the graph scene has changed and should be re-computed. <br/>
     * Seems to be called when the screen size or fov changes, the perspective changes or (and
     * maybe the cause of the bug: when the render distance changes
     */
    public void scheduleTerrainUpdate() {
        // BUG: seems to be called before init
        if (this.renderSectionManager != null) {
            this.renderSectionManager.markGraphDirty();
        }
    }

    /**
     * @return True if no chunks are pending rebuilds
     */
    public boolean isTerrainRenderComplete() {
        return this.renderSectionManager.isBuildQueueEmpty();
    }

    /**
     * Called prior to any chunk rendering in order to update necessary state.
     */
    public void updateChunks(Camera camera, Frustum frustum, boolean hasForcedFrustum, int frame, boolean spectator) {
        NativeBuffer.reclaim(false);

        this.frustum = frustum;

        this.useEntityCulling = SodiumClientMod.options().advanced.useEntityCulling;

        if (this.client.options.viewDistance != this.renderDistance) {
            this.reload();
        }

        Profiler profiler = this.client.getProfiler();
        profiler.push("camera_setup");

        ClientPlayerEntity player = this.client.player;

        if (player == null) {
            throw new IllegalStateException("Client instance has no active player entity");
        }

        // TODO should these camera checks be handled by RenderSectionManager

        Vec3d pos = camera.getPos();
        float pitch = camera.getPitch();
        float yaw = camera.getYaw();

        boolean dirty = pos.x != this.lastCameraX || pos.y != this.lastCameraY || pos.z != this.lastCameraZ ||
                pitch != this.lastCameraPitch || yaw != this.lastCameraYaw;

        if (dirty) {
            this.renderSectionManager.markGraphDirty();
        }

        this.lastCameraX = pos.x;
        this.lastCameraY = pos.y;
        this.lastCameraZ = pos.z;
        this.lastCameraPitch = pitch;
        this.lastCameraYaw = yaw;

        profiler.swap("chunk_update");

        this.renderSectionManager.updateChunks();

        if (!hasForcedFrustum && this.renderSectionManager.isGraphDirty()) {
            profiler.swap("chunk_graph_rebuild");

            this.pipeline.update(camera, (FrustumExtended) frustum, frame, spectator);
        }

        profiler.swap("visible_chunk_tick");

        this.pipeline.tickVisibleRenders();

        profiler.pop();

        Entity.setRenderDistanceMultiplier(MathHelper.clamp((double) this.client.options.viewDistance / 8.0D, 1.0D, 2.5D) * (double) this.client.options.entityDistanceScaling);
    }

    /**
     * Performs a render pass for the given {@link RenderLayer} and draws all visible chunks for it.
     */
    public void drawChunkLayer(RenderLayer renderLayer, MatrixStack matrixStack, double x, double y, double z) {
        BlockRenderPass pass = this.renderPassManager.getRenderPassForLayer(renderLayer);
        pass.startDrawing();

        this.pipeline.renderLayer(matrixStack, pass, x, y, z);

        pass.endDrawing();
    }

    public void reload() {
        if (this.world == null) {
            return;
        }

        this.initRenderer();
    }

    private void initRenderer() {
        if (this.pipeline != null) {
            this.pipeline.delete();
            this.pipeline = null;
            this.renderSectionManager = null;
        }

        RenderDevice device = RenderDevice.INSTANCE;

        this.renderDistance = this.client.options.viewDistance;

        this.renderPassManager = BlockRenderPassManager.createDefaultMappings();

        var backendProvider = BackendProvider.getBackend(SodiumClientMod.options());


        ChunkRenderer chunkRenderer = backendProvider.createChunkRenderer(device, ChunkModelVertexFormats.DEFAULT);
        VisibilityTracker visibilityTracker = chunkRenderer.createVisibilityTracker(this.world, this.renderDistance);
        this.renderSectionManager = backendProvider.createRenderSectionManager(
                visibilityTracker,
                ChunkModelVertexFormats.DEFAULT,
                this.renderPassManager,
                this.world,
                backendProvider.createRenderSectionContainer(ChunkModelVertexFormats.DEFAULT));

        this.renderSectionManager.loadChunks();
        this.pipeline = new Pipeline(this.renderSectionManager, visibilityTracker, chunkRenderer);
    }

    public void renderTileEntities(MatrixStack matrices, BufferBuilderStorage bufferBuilders, Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
                                   Camera camera, float tickDelta) {
        VertexConsumerProvider.Immediate immediate = bufferBuilders.getEntityVertexConsumers();

        Vec3d cameraPos = camera.getPos();
        double x = cameraPos.getX();
        double y = cameraPos.getY();
        double z = cameraPos.getZ();

        BlockEntityRenderDispatcher blockEntityRenderer = MinecraftClient.getInstance().getBlockEntityRenderDispatcher();

        for (BlockEntity blockEntity : this.pipeline.getVisibleBlockEntities()) {
            BlockPos pos = blockEntity.getPos();

            matrices.push();
            matrices.translate((double) pos.getX() - x, (double) pos.getY() - y, (double) pos.getZ() - z);

            VertexConsumerProvider consumer = immediate;
            SortedSet<BlockBreakingInfo> breakingInfos = blockBreakingProgressions.get(pos.asLong());

            if (breakingInfos != null && !breakingInfos.isEmpty()) {
                int stage = breakingInfos.last().getStage();

                if (stage >= 0) {
                    MatrixStack.Entry entry = matrices.peek();
                    VertexConsumer transformer = new OverlayVertexConsumer(bufferBuilders.getEffectVertexConsumers().getBuffer(ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.get(stage)), entry.getModel(), entry.getNormal());
                    consumer = (layer) -> layer.hasCrumbling() ? VertexConsumers.union(transformer, immediate.getBuffer(layer)) : immediate.getBuffer(layer);
                }
            }


            blockEntityRenderer.render(blockEntity, tickDelta, matrices, consumer);

            matrices.pop();
        }

        for (BlockEntity blockEntity : this.renderSectionManager.getGlobalBlockEntities()) {
            BlockPos pos = blockEntity.getPos();

            matrices.push();
            matrices.translate((double) pos.getX() - x, (double) pos.getY() - y, (double) pos.getZ() - z);

            blockEntityRenderer.render(blockEntity, tickDelta, matrices, immediate);

            matrices.pop();
        }
    }

    @Override
    public void onChunkAdded(int x, int z) {
        this.renderSectionManager.onChunkAdded(x, z);
    }

    @Override
    public void onChunkRemoved(int x, int z) {
        this.renderSectionManager.onChunkRemoved(x, z);
    }

    /**
     * Returns whether or not the entity intersects with any visible chunks in the graph.
     *
     * @return True if the entity is visible, otherwise false
     */
    public boolean isEntityVisible(Entity entity) {
        if (!this.useEntityCulling) {
            return true;
        }

        Box box = entity.getVisibilityBoundingBox();

        // Entities outside the valid world height will never map to a rendered chunk
        // Always render these entities or they'll be culled incorrectly!
        if (box.maxY < 0.5D || box.minY > 255.5D) {
            return true;
        }

        int minX = MathHelper.floor(box.minX - 0.5D) >> 4;
        int minY = MathHelper.floor(box.minY - 0.5D) >> 4;
        int minZ = MathHelper.floor(box.minZ - 0.5D) >> 4;

        int maxX = MathHelper.floor(box.maxX + 0.5D) >> 4;
        int maxY = MathHelper.floor(box.maxY + 0.5D) >> 4;
        int maxZ = MathHelper.floor(box.maxZ + 0.5D) >> 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (this.pipeline.getVisibilityTracker().isSectionVisible(x, y, z)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * @return The frustum of the current player's camera used to cull chunks
     */
    public Frustum getFrustum() {
        return this.frustum;
    }

    public String getChunksDebugString() {
        // C: visible/total
        // TODO: add dirty and queued counts
        return String.format("C: %s/%s",
                this.pipeline.getVisibilityTracker().getVisibleChunkCount(), this.renderSectionManager.getTotalSections());
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified block region.
     */
    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        this.scheduleRebuildForChunks(minX >> 4, minY >> 4, minZ >> 4, maxX >> 4, maxY >> 4, maxZ >> 4, important);
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified chunk region.
     */
    public void scheduleRebuildForChunks(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkY = minY; chunkY <= maxY; chunkY++) {
                for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                    this.scheduleRebuildForChunk(chunkX, chunkY, chunkZ, important);
                }
            }
        }
    }

    /**
     * Schedules a chunk rebuild for the render belonging to the given chunk section position.
     */
    public void scheduleRebuildForChunk(int x, int y, int z, boolean important) {
        this.renderSectionManager.scheduleRebuild(x, y, z, important);
    }

    public Collection<? extends String> getMemoryDebugStrings() {
        return this.renderSectionManager.getRenderSectionContainerMemoryDebugStrings();
    }

    private static long toMib(long x) {
        return x / 1024L / 1024L;
    }
}
