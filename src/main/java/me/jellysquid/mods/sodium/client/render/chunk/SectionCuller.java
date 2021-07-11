package me.jellysquid.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.graph.ChunkGraphInfo;
import me.jellysquid.mods.sodium.client.render.chunk.graph.ChunkGraphIterationQueue;
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

public class SectionCuller implements Culler {
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
    private FrustumChecker frustumChecker;


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

    public SectionCuller(ClientWorld world, int renderDistance) {
        this.world = world;
        this.renderDistance = renderDistance;
    }

    @Override
    public void setCullerInteractor(CullerInteractor cullerInteractor) {
        this.cullerInteractor = cullerInteractor;
    }

    @Override
    public void setFrustumChecker(FrustumChecker frustumChecker) {
        this.frustumChecker = frustumChecker;
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
            ChunkGraphInfo section = queue.getRender(i);
            Direction flow = queue.getDirection(i);

            // TODO: @Kroppeb understand what this does
            this.cullerInteractor.schedulePendingUpdates(section.getRenderSection());

            for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
                if (this.isCulled(section, flow, dir)) {
                    continue;
                }

                ChunkGraphInfo adj = section.getAdjacent(dir);

                if (adj != null && this.isWithinRenderDistance(adj)) {
                    this.bfsEnqueue(section, adj, DirectionUtil.getOpposite(dir));
                }
            }
        }
    }

    private void bfsEnqueue(ChunkGraphInfo previous, ChunkGraphInfo info, Direction flow) {

        if (info.getLastVisibleFrame() == this.currentFrame) {
            return;
        }

        if (!this.frustumChecker.getVisibility(info, this.frustum)) {
            return;
        }

        info.setLastVisibleFrame(this.currentFrame);
        info.setCullingState(previous.getCullingState(), flow);

        this.addVisible(info, flow);
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

            this.addVisible(rootRender.getGraphInfo(), null);
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
                this.addVisible(render.getGraphInfo(), null);
            }
        }
    }

    private void addVisible(ChunkGraphInfo section, Direction flow) {
        this.iterationQueue.add(section, flow);

        RenderSection renderSection = section.getRenderSection();

        if (this.useFogCulling && renderSection.getSquaredDistanceXZ(this.cameraX, this.cameraZ) >= this.fogRenderCutoff) {
            return;
        }

        if (!renderSection.isEmpty()) {
            this.cullerInteractor.addChunkToVisible(renderSection);
            this.cullerInteractor.addEntitiesToRenderLists(renderSection);
        }
    }

    private boolean isCulled(ChunkGraphInfo node, Direction from, Direction to) {
        if (node.canCull(to)) {
            return true;
        }

        return this.useOcclusionCulling && from != null && !node.isVisibleThrough(from, to);
    }

    private boolean isWithinRenderDistance(ChunkGraphInfo adj) {
        int x = Math.abs(adj.getChunkX() - this.centerChunkX);
        int z = Math.abs(adj.getChunkZ() - this.centerChunkZ);

        return x <= this.renderDistance && z <= this.renderDistance;
    }

    public interface CullerInteractor {
        RenderSection resolveSection(int chunkX, int chunkY, int chunkZ);

        void addChunkToVisible(RenderSection render);

        void addEntitiesToRenderLists(RenderSection render);

        void schedulePendingUpdates(RenderSection section);
    }

    public interface FrustumChecker {
        void updateVisibility(FrustumExtended frustum);

        boolean getVisibility(ChunkGraphInfo section, FrustumExtended frustum);
    }
}
