package me.jellysquid.mods.sodium.client.render.chunk.base;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;

public final class Pipeline {
    private final RenderSectionManager renderSectionManager;
    private final ChunkRenderer chunkRenderer;
    private final VisibilityTracker visibilityTracker;

    public Pipeline(
            RenderSectionManager renderSectionManager,
            VisibilityTracker visibilityTracker,
            ChunkRenderer chunkRenderer) {
        this.renderSectionManager = renderSectionManager;
        this.chunkRenderer = chunkRenderer;
        this.visibilityTracker = visibilityTracker;
    }

    public int getVisibleChunkCount() {
        return this.visibilityTracker.getVisibleChunkCount();
    }

    public void delete() {
        this.renderSectionManager.destroy();
        this.chunkRenderer.delete();
    }

    public void tickVisibleRenders() {
        for (RenderSection render : this.visibilityTracker.getVisibleTickableSections()) {
            render.tick();
        }
    }

    public void update(Camera camera, FrustumExtended frustum, int frame, boolean spectator) {
        this.renderSectionManager.update(camera, frustum, frame, spectator);
        this.visibilityTracker.update(camera, frustum, frame, spectator);
    }

    public Iterable<? extends BlockEntity> getVisibleBlockEntities() {
        return this.visibilityTracker.getVisibleBlockEntities();
    }

    public void renderLayer(MatrixStack matrixStack, BlockRenderPass pass, double x, double y, double z) {
        RenderDevice device = RenderDevice.INSTANCE;
        CommandList commandList = device.createCommandList();

        this.chunkRenderer.render(matrixStack, commandList, pass, new ChunkCameraContext(x, y, z));

        commandList.flush();
    }

    public VisibilityTracker getVisibilityTracker() {
        return this.visibilityTracker;
    }
}
