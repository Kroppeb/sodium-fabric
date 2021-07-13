package me.jellysquid.mods.sodium.client.render.chunk.backend.advanced;

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

public final class AdvancedBackendProvider implements BackendProvider {
    public static final AdvancedBackendProvider INSTANCE = new AdvancedBackendProvider();

    private AdvancedBackendProvider() {
    }

    @Override
    public boolean isSupported(SodiumGameOptions options) {
        return false;
    }

    @Override
    public ChunkRenderer createChunkRenderer(RenderDevice device, ModelVertexType vertexType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RenderSectionContainer createRenderSectionContainer(ChunkVertexType chunkVertexType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DefaultRenderSectionManager createRenderSectionManager(VisibilityTracker visibilityTracker, ChunkVertexType chunkVertexType, BlockRenderPassManager renderPassManager, ClientWorld world, RenderSectionContainer renderSectionContainer) {
        throw new UnsupportedOperationException();
    }
}
