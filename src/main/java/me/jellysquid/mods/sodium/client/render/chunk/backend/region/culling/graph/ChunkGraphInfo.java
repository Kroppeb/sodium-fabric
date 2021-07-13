package me.jellysquid.mods.sodium.client.render.chunk.backend.region.culling.graph;

import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class ChunkGraphInfo {
    private static final long DEFAULT_VISIBILITY_DATA = calculateVisibilityData(ChunkRenderData.EMPTY.getOcclusionData());

    private final RenderSection renderSection;

    private int lastVisibleFrame = -1;

    private long visibilityData;
    private byte cullingState;

    private final ChunkGraphInfo[] adjacent = new ChunkGraphInfo[DirectionUtil.ALL_DIRECTIONS.length];

    public ChunkGraphInfo(RenderSection renderSection) {
        this.renderSection = renderSection;
        this.visibilityData = DEFAULT_VISIBILITY_DATA;
    }

    public void setLastVisibleFrame(int frame) {
        this.lastVisibleFrame = frame;
    }

    public int getLastVisibleFrame() {
        return this.lastVisibleFrame;
    }

    public void setOcclusionData(ChunkOcclusionData occlusionData) {
        this.visibilityData = calculateVisibilityData(occlusionData);
    }

    private static long calculateVisibilityData(ChunkOcclusionData occlusionData) {
        long visibilityData = 0;

        for (Direction from : DirectionUtil.ALL_DIRECTIONS) {
            for (Direction to : DirectionUtil.ALL_DIRECTIONS) {
                if (occlusionData == null || occlusionData.isVisibleThrough(from, to)) {
                    visibilityData |= (1L << ((from.ordinal() << 3) + to.ordinal()));
                }
            }
        }

        return visibilityData;
    }

    public boolean isVisibleThrough(Direction from, Direction to) {
        return ((this.visibilityData & (1L << ((from.ordinal() << 3) + to.ordinal()))) != 0L);
    }

    public void setCullingState(byte parent, Direction dir) {
        this.cullingState = (byte) (parent | (1 << dir.ordinal()));
    }

    public boolean canCull(Direction dir) {
        return (this.cullingState & 1 << dir.ordinal()) != 0;
    }

    public byte getCullingState() {
        return this.cullingState;
    }

    public void resetCullingState() {
        this.cullingState = 0;
    }

    public boolean isInsideFrustum(FrustumExtended frustum) {
        return this.renderSection.isInsideFrustum(frustum);
    }

    /**
     * @return The x-coordinate of the origin position of this chunk render
     */
    public int getOriginX() {
        return this.getChunkX() << 4;
    }

    /**
     * @return The y-coordinate of the origin position of this chunk render
     */
    public int getOriginY() {
        return this.getChunkY() << 4;
    }

    /**
     * @return The z-coordinate of the origin position of this chunk render
     */
    public int getOriginZ() {
        return this.getChunkZ() << 4;
    }

    public RenderSection getRenderSection() {
        return this.renderSection;
    }

    public int getChunkX() {
        return this.renderSection.getChunkX();
    }

    public int getChunkY() {
        return this.renderSection.getChunkY();
    }

    public int getChunkZ() {
        return this.renderSection.getChunkZ();
    }


    public ChunkGraphInfo getAdjacent(Direction dir) {
        return this.adjacent[dir.ordinal()];
    }

    private void disconnectNeighborNodes() {
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            ChunkGraphInfo adj = this.getAdjacent(dir);

            if (adj != null) {
                adj.setAdjacentNode(DirectionUtil.getOpposite(dir), null);

                // TODO: Is this really needed?
                this.setAdjacentNode(dir, null);
            }
        }
    }


    public void setAdjacentNode(Direction dir, ChunkGraphInfo node) {
        this.adjacent[dir.ordinal()] = node;
    }

    public double getSquaredDistance(BlockPos origin) {
        return this.renderSection.getSquaredDistance(origin);
    }

    public void delete() {
        this.disconnectNeighborNodes();
    }
}
