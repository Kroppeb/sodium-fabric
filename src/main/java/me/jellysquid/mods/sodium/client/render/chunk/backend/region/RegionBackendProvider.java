package me.jellysquid.mods.sodium.client.render.chunk.backend.region;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.render.chunk.*;
import me.jellysquid.mods.sodium.client.render.chunk.backend.BackendProvider;
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
    public Culler createCuller(ClientWorld world, int renderDistance) {
        return new SectionCuller(world, renderDistance);
    }

    @Override
    public RenderSectionContainer createRenderSectionContainer(ChunkRenderer chunkRenderer) {
        return new RenderRegionManager(chunkRenderer);
    }

    @Override
    public ChunkRenderList createChunkRenderList() {
        return new RegionalChunkRenderList();
    }

    @Override
    public RenderSectionManager createRenderSectionManager(
            ChunkRenderer chunkRenderer,
            BlockRenderPassManager renderPassManager,
            ClientWorld world,
            Culler culler,
            RenderSectionContainer renderSectionContainer,
            ChunkRenderList chunkRenderList) {
        return new RenderSectionManager(
                chunkRenderer,
                renderPassManager,
                world,
                culler,
                renderSectionContainer,
                chunkRenderList);
    }
}
