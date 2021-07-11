package me.jellysquid.mods.sodium.client.render.chunk.backend;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.Culler;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.AdvancedBackendProvider;
import me.jellysquid.mods.sodium.client.render.chunk.backend.region.RegionBackendProvider;
import me.jellysquid.mods.sodium.client.render.chunk.format.sfp.ModelVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import net.minecraft.client.world.ClientWorld;

public interface BackendProvider {
    boolean isSupported(SodiumGameOptions options);



    static BackendProvider getBackend(SodiumGameOptions options) {
        if (AdvancedBackendProvider.INSTANCE.isSupported(options)) {
            return AdvancedBackendProvider.INSTANCE;
        } else {
            return RegionBackendProvider.INSTANCE;
        }
    }

    ChunkRenderer createChunkRenderer(RenderDevice device, ModelVertexType vertexType);
    Culler createCuller(
            ClientWorld world,
            int renderDistance
    );
    RenderSectionManager createRenderSectionManager(
            ChunkRenderer chunkRenderer,
            BlockRenderPassManager renderPassManager,
            ClientWorld world,
            Culler culler);
}
