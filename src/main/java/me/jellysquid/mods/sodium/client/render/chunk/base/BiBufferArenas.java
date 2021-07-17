package me.jellysquid.mods.sodium.client.render.chunk.base;

import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;

public class BiBufferArenas {
    public final GlBufferArena vertexBuffers;
    public final GlBufferArena indexBuffers;

    public GlTessellation tessellation;

    public BiBufferArenas(CommandList commandList, ChunkVertexType vertexType, long size) {
        this.vertexBuffers = new GlBufferArena(commandList, 24 * size, vertexType.getBufferVertexFormat().getStride());
        this.indexBuffers = new GlBufferArena(commandList, 36 * size, 4);
    }

    public void delete(CommandList commandList) {
        this.vertexBuffers.delete(commandList);
        this.indexBuffers.delete(commandList);

        if (this.tessellation != null) {
            commandList.deleteTessellation(this.tessellation);
        }
    }

    public void setTessellation(GlTessellation tessellation) {
        this.tessellation = tessellation;
    }

    public GlTessellation getTessellation() {
        return this.tessellation;
    }

    public boolean isEmpty() {
        return this.vertexBuffers.isEmpty() && this.indexBuffers.isEmpty();
    }

    public long getUsedMemory() {
        return this.vertexBuffers.getUsedMemory() + this.indexBuffers.getUsedMemory();
    }

    public long getAllocatedMemory() {
        return this.vertexBuffers.getAllocatedMemory() + this.indexBuffers.getAllocatedMemory();
    }

    public void invalidateTessellation(CommandList commandList) {
        if (this.tessellation != null) {
            commandList.deleteTessellation(this.tessellation);

            this.tessellation = null;
        }
    }
}
