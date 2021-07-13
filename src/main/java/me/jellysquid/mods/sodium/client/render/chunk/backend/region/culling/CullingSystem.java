package me.jellysquid.mods.sodium.client.render.chunk.backend.region.culling;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.jellysquid.mods.sodium.client.render.chunk.backend.region.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.base.SectionListener;
import me.jellysquid.mods.sodium.client.render.chunk.base.VisibilityTracker;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;

import java.util.Collection;
import java.util.List;

public class CullingSystem implements VisibilityTracker {
    private final SectionCuller culler;
    private final SectionListener cullerSectionListener;
    private final ChunkRenderList chunkRenderList;


    private final List<RenderSection> tickableChunks = new ObjectArrayList<>();
    private final List<BlockEntity> visibleBlockEntities = new ObjectArrayList<>();

    private int currentFrame = 0;

    private RenderSectionManager renderSectionManager;

    public CullingSystem(
            ChunkRenderList chunkRenderList,
            ClientWorld world,
            int renderDistance) {
        this.culler = new SectionCuller(world, renderDistance, this);
        this.cullerSectionListener = this.culler.getListener();
        this.chunkRenderList = chunkRenderList;
    }

    @Override
    public void update(Camera camera, FrustumExtended frustum, int frame, boolean spectator) {
        this.visibleBlockEntities.clear();
        this.chunkRenderList.clear();
        this.tickableChunks.clear();

        this.currentFrame = frame;

        this.culler.setup(camera);
        this.culler.iterateChunks(camera, frustum, frame, spectator);
    }

    @Override
    public int getVisibleChunkCount() {
        return this.chunkRenderList.getCount();
    }

    @Override
    public Iterable<? extends RenderSection> getVisibleTickableSections() {
        return this.tickableChunks;
    }

    @Override
    public Iterable<? extends BlockEntity> getVisibleBlockEntities() {
        return this.visibleBlockEntities;
    }

    void addVisibleChunk(RenderSection renderSection) {
        this.chunkRenderList.add(renderSection);

        if (renderSection.isTickable()) {
            this.tickableChunks.add(renderSection);
        }

        Collection<BlockEntity> blockEntities = renderSection.getData().getBlockEntities();

        if (!blockEntities.isEmpty()) {
            this.visibleBlockEntities.addAll(blockEntities);
        }
    }

    @Override
    public void setRenderSectionManager(RenderSectionManager renderSectionManager) {
        this.renderSectionManager = renderSectionManager;
    }

    void schedulePendingUpdates(RenderSection section){
        this.renderSectionManager.schedulePendingUpdates(section);
    }

    @Override
    public boolean isSectionVisible(int x, int y, int z) {
        return this.culler.isSectionVisible(x, y, z, this.currentFrame);
    }

    @Override
    public void addSection(RenderSection renderSection) {
        this.cullerSectionListener.addSection(renderSection);
    }

    @Override
    public void removeSection(RenderSection renderSection) {
        this.cullerSectionListener.removeSection(renderSection);
    }

    @Override
    public void updateSection(RenderSection renderSection) {
        this.cullerSectionListener.updateSection(renderSection);
    }
}
