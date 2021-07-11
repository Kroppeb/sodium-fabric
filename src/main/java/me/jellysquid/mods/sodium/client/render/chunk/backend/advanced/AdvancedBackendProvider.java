package me.jellysquid.mods.sodium.client.render.chunk.backend.advanced;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.render.chunk.*;
import me.jellysquid.mods.sodium.client.render.chunk.base.BackendProvider;
import me.jellysquid.mods.sodium.client.render.chunk.base.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSectionContainer;
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
    public Culler createCuller(ClientWorld world, int renderDistance) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RenderSectionContainer createRenderSectionContainer(ChunkRenderer chunkRenderer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RenderSectionManager createRenderSectionManager(ChunkRenderer chunkRenderer, BlockRenderPassManager renderPassManager, ClientWorld world, Culler culler, RenderSectionContainer renderSectionContainer) {
        throw new UnsupportedOperationException();
    }
}
