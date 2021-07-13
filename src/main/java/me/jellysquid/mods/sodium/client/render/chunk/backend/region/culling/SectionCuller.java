package me.jellysquid.mods.sodium.client.render.chunk.backend.region.culling;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.chunk.backend.region.culling.graph.ChunkGraphInfoManager;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.base.SectionListener;
import me.jellysquid.mods.sodium.client.render.chunk.backend.region.culling.graph.ChunkGraphInfo;
import me.jellysquid.mods.sodium.client.render.chunk.backend.region.culling.graph.ChunkGraphIterationQueue;
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

public class SectionCuller {
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


    private final ClientWorld world;

    private int currentFrame = 0;
    private FrustumExtended frustum;
    private final ChunkGraphIterationQueue iterationQueue = new ChunkGraphIterationQueue();
    private final int renderDistance;


    private float cameraX, cameraZ;
    private int centerChunkX, centerChunkZ;

    private boolean useFogCulling;
    private boolean useOcclusionCulling;

    private double fogRenderCutoff;

    private final ChunkGraphInfoManager chunkGraphInfoManager = new ChunkGraphInfoManager();

    private final CullingSystem cullingSystem;

    public SectionCuller(ClientWorld world, int renderDistance, CullingSystem cullingSystem) {
        this.world = world;
        this.renderDistance = renderDistance;
        this.cullingSystem = cullingSystem;
    }


    public SectionListener getListener() {
        return this.chunkGraphInfoManager;
    }


    public boolean isSectionVisible(int x, int y, int z, int currentFrame) {
        ChunkGraphInfo chunkGraphInfo = this.chunkGraphInfoManager.get(x, y, z);
        return (chunkGraphInfo != null) && chunkGraphInfo.getLastVisibleFrame() == currentFrame;
    }

    public void setup(Camera camera) {
        Vec3d cameraPos = camera.getPos();

        this.cameraX = (float) cameraPos.x;
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


    public void iterateChunks(Camera camera, FrustumExtended frustum, int frame, boolean spectator) {
        this.initSearch(camera, frustum, frame, spectator);

        ChunkGraphIterationQueue queue = this.iterationQueue;

        for (int i = 0; i < queue.size(); i++) {
            ChunkGraphInfo section = queue.getSection(i);
            Direction flow = queue.getDirection(i);

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

        if (!info.isInsideFrustum(this.frustum)) {
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

        ChunkGraphInfo rootInfo = this.chunkGraphInfoManager.get(chunkX, chunkY, chunkZ);

        if (rootInfo != null) {
            rootInfo.resetCullingState();
            rootInfo.setLastVisibleFrame(frame);

            if (spectator && this.world.getBlockState(origin).isOpaqueFullCube(this.world, origin)) {
                this.useOcclusionCulling = false;
            }

            this.addVisible(rootInfo, null);
        } else {
            chunkY = MathHelper.clamp(origin.getY() >> 4, this.world.getBottomSectionCoord(), this.world.getTopSectionCoord() - 1);

            List<ChunkGraphInfo> sorted = new ArrayList<>();

            for (int x2 = -this.renderDistance; x2 <= this.renderDistance; ++x2) {
                for (int z2 = -this.renderDistance; z2 <= this.renderDistance; ++z2) {
                    ChunkGraphInfo section = this.chunkGraphInfoManager.get(chunkX + x2, chunkY, chunkZ + z2);

                    if (section == null) {
                        continue;
                    }

                    if (!section.isInsideFrustum(frustum)) {
                        continue;
                    }

                    section.resetCullingState();
                    section.setLastVisibleFrame(frame);

                    sorted.add(section);
                }
            }

            sorted.sort(Comparator.comparingDouble(node -> node.getSquaredDistance(origin)));

            for (ChunkGraphInfo info : sorted) {
                this.addVisible(info, null);
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
            this.cullingSystem.addVisibleChunk(renderSection);
        }

        // TODO: figure out if we also need to do this even if the section is empty
        this.cullingSystem.schedulePendingUpdates(renderSection);
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
        void schedulePendingUpdates(RenderSection section);
    }
}
