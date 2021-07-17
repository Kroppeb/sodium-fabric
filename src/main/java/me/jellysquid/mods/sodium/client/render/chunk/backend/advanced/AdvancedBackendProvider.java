package me.jellysquid.mods.sodium.client.render.chunk.backend.advanced;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultRenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.gpuculled.TornadoChunkRender;
import me.jellysquid.mods.sodium.client.render.chunk.base.BackendProvider;
import me.jellysquid.mods.sodium.client.render.chunk.base.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSectionContainer;
import me.jellysquid.mods.sodium.client.render.chunk.base.VisibilityTracker;
import me.jellysquid.mods.sodium.client.render.chunk.format.sfp.ModelVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

public final class AdvancedBackendProvider implements BackendProvider {
    public static final AdvancedBackendProvider INSTANCE = new AdvancedBackendProvider();

    private AdvancedBackendProvider() {
    }

    @Override
    public boolean isSupported(SodiumGameOptions options) {
        GLCapabilities capabilities = GL.getCapabilities();
        return /*capabilities.OpenGL46 && capabilities.GL_ARB_indirect_parameters &&*/ options.advanced.use46Renderer;
    }


    @Override
    public ChunkRenderer createChunkRenderer(RenderDevice device, ModelVertexType vertexType) {
        return new TornadoChunkRender(device, vertexType);
    }

    @Override
    public RenderSectionContainer createRenderSectionContainer(ChunkVertexType chunkVertexType, int renderDistance) {
        return new CombinedRenderSectionContainer(chunkVertexType, renderDistance);
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
