package me.jellysquid.mods.sodium.client.render.chunk.backend.region;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultRenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.base.BackendProvider;
import me.jellysquid.mods.sodium.client.render.chunk.base.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSectionContainer;
import me.jellysquid.mods.sodium.client.render.chunk.base.VisibilityTracker;
import me.jellysquid.mods.sodium.client.render.chunk.format.sfp.ModelVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import net.minecraft.client.world.ClientWorld;

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


    @Override
    public RenderSectionContainer createRenderSectionContainer(ChunkVertexType chunkVertexType) {
        return new RenderRegionManager(chunkVertexType);
    }

    @Override
    public DefaultRenderSectionManager createRenderSectionManager(
            VisibilityTracker visibilityTracker,
            ChunkVertexType chunkVertexType,
            BlockRenderPassManager renderPassManager,
            ClientWorld world,
            RenderSectionContainer renderSectionContainer) {
        return new DefaultRenderSectionManager(
                visibilityTracker,
                chunkVertexType,
                renderPassManager,
                world,
                renderSectionContainer);
    }
}
