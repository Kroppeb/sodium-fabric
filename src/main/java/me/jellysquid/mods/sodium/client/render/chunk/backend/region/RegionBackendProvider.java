package me.jellysquid.mods.sodium.client.render.chunk.backend.region;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RegionChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.backend.BackendProvider;
import me.jellysquid.mods.sodium.client.render.chunk.format.sfp.ModelVertexType;

public final class RegionBackendProvider implements BackendProvider {
    public static final RegionBackendProvider INSTANCE = new RegionBackendProvider();

    private RegionBackendProvider() {
    }

    @Override
    public boolean isSupported(SodiumGameOptions options) {
        return true;
    }

    @Override
    public ChunkRenderer createChunkRenderer(RenderDevice device, ModelVertexType vertexType) {
        return new RegionChunkRenderer(device, vertexType);
    }
}
