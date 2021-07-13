package me.jellysquid.mods.sodium.client.render.chunk.backend.region.culling.graph;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.base.SectionListener;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;

public class ChunkGraphInfoManager implements SectionListener {
    private final Long2ReferenceMap<ChunkGraphInfo> sections = new Long2ReferenceOpenHashMap<>();

    @Override
    public void addSection(RenderSection section) {
        ChunkGraphInfo value = new ChunkGraphInfo(section);
        ChunkGraphInfo old = this.sections.put(section.getChunkPosAsLong(), value);
        if (old != null) {
            throw new IllegalStateException("tried adding a section that was already loaded");
        }
        this.connectNeighborNodes(value);
    }

    @Override
    public void removeSection(RenderSection section) {
        ChunkGraphInfo remove = this.sections.remove(section.getChunkPosAsLong());
        if (remove == null) {
            throw new IllegalStateException("tried removing a section that wasn't loaded");
        }
        remove.delete();
    }

    @Override
    public void updateSection(RenderSection section) {
        SodiumClientMod.logger().info("updating occlusion data for chunk {}, {}, {}", section.getChunkX(), section.getChunkY(), section.getChunkZ());
        ChunkGraphInfo chunkGraphInfo = this.sections.get(section.getChunkPosAsLong());
        chunkGraphInfo.setOcclusionData(section.getData().getOcclusionData());
    }

    private void connectNeighborNodes(ChunkGraphInfo chunkGraphInfo) {
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            ChunkGraphInfo adj = this.get(chunkGraphInfo.getChunkX() + dir.getOffsetX(),
                    chunkGraphInfo.getChunkY() + dir.getOffsetY(),
                    chunkGraphInfo.getChunkZ() + dir.getOffsetZ());

            if (adj != null) {
                adj.setAdjacentNode(DirectionUtil.getOpposite(dir), chunkGraphInfo);
                chunkGraphInfo.setAdjacentNode(dir, adj);
            }
        }
    }

    public ChunkGraphInfo get(int x, int y, int z) {
        return this.sections.get(ChunkSectionPos.asLong(x, y, z));
    }
}
