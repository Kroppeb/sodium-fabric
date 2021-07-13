package me.jellysquid.mods.sodium.client.render.chunk;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.base.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkFogMode;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkProgram;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderManager;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import net.minecraft.client.util.math.MatrixStack;

import java.util.Map;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private final Map<BlockRenderPass, Map<ChunkShaderOptions, ChunkProgram>> programs = new Object2ObjectOpenHashMap<>();

    protected final ChunkVertexType vertexType;
    protected final GlVertexFormat<ChunkMeshAttribute> vertexFormat;

    protected final RenderDevice device;

    protected ChunkProgram activeProgram;

    protected final ChunkShaderManager chunkShaderManager = new ChunkShaderManager();

    public ShaderChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        this.device = device;
        this.vertexType = vertexType;
        this.vertexFormat = vertexType.getCustomVertexFormat();
    }

    // TODO: Generalize shader options
    protected ChunkProgram compileProgram(BlockRenderPass pass, ChunkShaderOptions options) {
        Map<ChunkShaderOptions, ChunkProgram> programs = this.programs.get(pass);

        if (programs == null) {
            this.programs.put(pass, programs = new Object2ObjectOpenHashMap<>());
        }

        ChunkProgram program = programs.get(options);

        if (program == null) {
            programs.put(options, program = this.chunkShaderManager.createShader(
                    this.device, this.getShaderName(pass), options));
        }

        return program;
    }

    // TODO: Define these in the render pass itself
    protected String getShaderName(BlockRenderPass pass) {
        return switch (pass) {
            case CUTOUT -> "blocks/block_layer_cutout";
            case CUTOUT_MIPPED -> "blocks/block_layer_cutout_mipped";
            case TRANSLUCENT, TRIPWIRE -> "blocks/block_layer_translucent";
            default -> "blocks/block_layer_solid";
        };
    }



    protected void begin(BlockRenderPass pass, MatrixStack matrixStack) {
        ChunkShaderOptions options = new ChunkShaderOptions(ChunkFogMode.SMOOTH);

        this.activeProgram = this.compileProgram(pass, options);
        this.activeProgram.bind();
        this.activeProgram.setup(matrixStack, this.vertexType);
    }

    protected void end() {
        this.activeProgram.unbind();
        this.activeProgram = null;
    }

    @Override
    public void delete() {
        this.programs.clear();
        this.chunkShaderManager.delete();
    }
}
