package me.jellysquid.mods.sodium.client.render.chunk.base;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import net.minecraft.client.util.math.MatrixStack;

/**
 * TODO: update this
 * The chunk render backend takes care of managing the graphics resource state of chunk render containers. This includes
 * the handling of uploading their data to the graphics card and rendering responsibilities.
 * <p>
 * // new doc
 *
 * The ChunkRenderer is responsible dispatching the required drawcalls to render the given chunks on screen
 * and manage the resources required to do so
 *
 * Which chunks should be drawn is communicated through {@link #getChunkVisibilityListener()}
 */
public interface ChunkRenderer {
    /**
     * Renders the given chunk render list to the active framebuffer.
     *
     * @param matrixStack The current matrix stack to be used for rendering
     * @param commandList The command list which OpenGL commands should be serialized to
     * @param pass        The block render pass to execute
     * @param camera      The camera context containing chunk offsets for the current render
     */
    void render(MatrixStack matrixStack, CommandList commandList, BlockRenderPass pass, ChunkCameraContext camera);

    ChunkVisibilityListener getChunkVisibilityListener();

    /**
     * Deletes this renderer and any resources attached to it.
     */
    void delete();

    // TODO why does the Chunk renderer need to provide the vertex type

    /**
     * Returns the vertex format used by this chunk render backend for rendering meshes.
     */
    @Deprecated
    ChunkVertexType getVertexType();
}
