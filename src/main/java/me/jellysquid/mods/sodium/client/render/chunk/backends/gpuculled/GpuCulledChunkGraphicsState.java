package me.jellysquid.mods.sodium.client.render.chunk.backends.gpuculled;

import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.util.BufferSlice;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.region.ChunkRegion;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.util.math.Direction;

import java.util.Map;

public class GpuCulledChunkGraphicsState extends ChunkGraphicsState {

    private final GlBufferSegment segment;
    private final long[] parts;
    private final int occlusionData;

    int id;

    public GpuCulledChunkGraphicsState(
            ChunkRenderContainer<?> container,
            GlBufferSegment segment,
            ChunkMeshData meshData,
            GlVertexFormat<?> vertexFormat,
            int occlusionData
    ) {
        super(container);

        this.segment = segment;
        this.occlusionData = occlusionData;

        this.parts = new long[ModelQuadFacing.COUNT];

        for (Map.Entry<ModelQuadFacing, BufferSlice> entry : meshData.getSlices()) {
            ModelQuadFacing facing = entry.getKey();
            BufferSlice slice = entry.getValue();

            int start = (segment.getStart() + slice.start) / vertexFormat.getStride();
            int count = slice.len / vertexFormat.getStride();

            this.parts[facing.ordinal()] = BufferSlice.pack(start, count);
        }
    }


    public static int readOcclusionData(ChunkOcclusionData occlusionData) {
        int i = 1;
        int result = 0;
        for (Direction value : Direction.values()) {
            if (!occlusionData.isVisibleThrough(value, value)) {
                result |= i;
            }
            i <<= 1;
        }
        return result;
    }

    @Override
    public void delete(CommandList commandList) {
        this.segment.delete();
    }

    public long getModelPart(int facing) {
        return this.parts[facing];
    }

    public int getOcclusionData() {
        return occlusionData;
    }
}
