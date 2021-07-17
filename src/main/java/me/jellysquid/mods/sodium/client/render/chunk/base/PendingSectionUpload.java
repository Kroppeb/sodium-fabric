package me.jellysquid.mods.sodium.client.render.chunk.base;

import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;

public record PendingSectionUpload(
            RenderSection section,
            ChunkMeshData meshData,
            GlBufferArena.PendingUpload vertexUpload,
            GlBufferArena.PendingUpload indicesUpload) {
    }