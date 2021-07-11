package me.jellysquid.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.chunk.graph.ChunkGraphInfo;
import me.jellysquid.mods.sodium.client.render.chunk.graph.ChunkGraphIterationQueue;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionVisibility;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RegionCuller implements Culler {
    /**
     * The minimum distance the culling plane can be from the player's camera. This helps to prevent mathematical
     * errors that occur when the fog distance is less than 8 blocks in width, such as when using a blindness potion.
     */
    private static final float FOG_PLANE_MIN_DISTANCE = (float) Math.pow(8.0f, 2.0);

    /**
     * The distance past the fog's far plane at which to begin culling. Distance calculations use the center of each
     * chunk from the camera's position, and as such, special care is needed to ensure that the culling plane is pushed
     * back far enough. I'm sure there's a mathematical formula that should be used here in place of the constant,
     * but this value works fine in testing.
     */
    private static final float FOG_PLANE_OFFSET = 12.0f;

    // lateinit
    private CullerInteractor cullerInteractor;
    private final ClientWorld world;

    private int currentFrame = 0;
    private FrustumExtended frustum;
    private final ChunkGraphIterationQueue iterationQueue = new ChunkGraphIterationQueue();
    private final int renderDistance;


    private float cameraX, cameraY, cameraZ;
    private int centerChunkX, centerChunkZ;

    private boolean useFogCulling;
    private boolean useOcclusionCulling;

    private double fogRenderCutoff;

    public RegionCuller(ClientWorld world, int renderDistance) {
        this.world = world;
        this.renderDistance = renderDistance;
    }

    @Override
    public void setCullerInteractor(CullerInteractor cullerInteractor) {
        this.cullerInteractor = cullerInteractor;
    }

    @Override
    public void setup(Camera camera) {
        Vec3d cameraPos = camera.getPos();

        this.cameraX = (float) cameraPos.x;
        this.cameraY = (float) cameraPos.y;
        this.cameraZ = (float) cameraPos.z;

        this.useFogCulling = false;

        if (SodiumClientMod.options().advanced.useFogOcclusion) {
            float dist = RenderSystem.getShaderFogEnd() + FOG_PLANE_OFFSET;

            if (dist != 0.0f) {
                this.useFogCulling = true;
                this.fogRenderCutoff = Math.max(FOG_PLANE_MIN_DISTANCE, dist * dist);
            }
        }
    }

    @Override
    public void iterateChunks(Camera camera, FrustumExtended frustum, int frame, boolean spectator) {
        this.initSearch(camera, frustum, frame, spectator);

        ChunkGraphIterationQueue queue = this.iterationQueue;

        for (int i = 0; i < queue.size(); i++) {
            RenderSection section = queue.getRender(i);
            Direction flow = queue.getDirection(i);

            this.cullerInteractor.schedulePendingUpdates(section);

            for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
                if (this.isCulled(section.getGraphInfo(), flow, dir)) {
                    continue;
                }

                RenderSection adj = section.getAdjacent(dir);

                if (adj != null && this.isWithinRenderDistance(adj)) {
                    this.bfsEnqueue(section, adj, DirectionUtil.getOpposite(dir));
                }
            }
        }
    }

    private void bfsEnqueue(RenderSection parent, RenderSection render, Direction flow) {
        ChunkGraphInfo info = render.getGraphInfo();

        if (info.getLastVisibleFrame() == this.currentFrame) {
            return;
        }

        RenderRegionVisibility parentVisibility = parent.getRegion().getVisibility();

        if (parentVisibility == RenderRegionVisibility.CULLED) {
            return;
        } else if (parentVisibility == RenderRegionVisibility.VISIBLE && info.isCulledByFrustum(this.frustum)) {
            return;
        }

        info.setLastVisibleFrame(this.currentFrame);
        info.setCullingState(parent.getGraphInfo().getCullingState(), flow);

        this.addVisible(render, flow);
    }

    private void initSearch(Camera camera, FrustumExtended frustum, int frame, boolean spectator) {
        this.currentFrame = frame;
        this.frustum = frustum;
        this.useOcclusionCulling = MinecraftClient.getInstance().chunkCullingEnabled;

        this.iterationQueue.clear();

        BlockPos origin = camera.getBlockPos();

        int chunkX = origin.getX() >> 4;
        int chunkY = origin.getY() >> 4;
        int chunkZ = origin.getZ() >> 4;

        this.centerChunkX = chunkX;
        this.centerChunkZ = chunkZ;

        RenderSection rootRender = this.cullerInteractor.resolveSection(chunkX, chunkY, chunkZ);

        if (rootRender != null) {
            ChunkGraphInfo rootInfo = rootRender.getGraphInfo();
            rootInfo.resetCullingState();
            rootInfo.setLastVisibleFrame(frame);

            if (spectator && this.world.getBlockState(origin).isOpaqueFullCube(this.world, origin)) {
                this.useOcclusionCulling = false;
            }

            this.addVisible(rootRender, null);
        } else {
            chunkY = MathHelper.clamp(origin.getY() >> 4, this.world.getBottomSectionCoord(), this.world.getTopSectionCoord() - 1);

            List<RenderSection> sorted = new ArrayList<>();

            for (int x2 = -this.renderDistance; x2 <= this.renderDistance; ++x2) {
                for (int z2 = -this.renderDistance; z2 <= this.renderDistance; ++z2) {
                    RenderSection render = this.cullerInteractor.resolveSection(chunkX + x2, chunkY, chunkZ + z2);

                    if (render == null) {
                        continue;
                    }

                    ChunkGraphInfo info = render.getGraphInfo();

                    if (info.isCulledByFrustum(frustum)) {
                        continue;
                    }

                    info.resetCullingState();
                    info.setLastVisibleFrame(frame);

                    sorted.add(render);
                }
            }

            sorted.sort(Comparator.comparingDouble(node -> node.getSquaredDistance(origin)));

            for (RenderSection render : sorted) {
                this.addVisible(render, null);
            }
        }
    }

    private void addVisible(RenderSection render, Direction flow) {
        this.iterationQueue.add(render, flow);

        if (this.useFogCulling && render.getSquaredDistanceXZ(this.cameraX, this.cameraZ) >= this.fogRenderCutoff) {
            return;
        }

        if (!render.isEmpty()) {
            this.cullerInteractor.addChunkToVisible(render);
            this.cullerInteractor.addEntitiesToRenderLists(render);
        }
    }

    private boolean isCulled(ChunkGraphInfo node, Direction from, Direction to) {
        if (node.canCull(to)) {
            return true;
        }

        return this.useOcclusionCulling && from != null && !node.isVisibleThrough(from, to);
    }

    private boolean isWithinRenderDistance(RenderSection adj) {
        int x = Math.abs(adj.getChunkX() - this.centerChunkX);
        int z = Math.abs(adj.getChunkZ() - this.centerChunkZ);

        return x <= this.renderDistance && z <= this.renderDistance;
    }

    interface CullerInteractor {
        RenderSection resolveSection(int chunkX, int chunkY, int chunkZ);

        void addChunkToVisible(RenderSection render);

        void addEntitiesToRenderLists(RenderSection render);

        void schedulePendingUpdates(RenderSection section);
    }
}
