package me.jellysquid.mods.sodium.client.render.chunk.backend.advanced;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.gpuculled.TornadoChunkRender;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.base.VisibilityTracker;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;

import java.util.*;

public class OcclusionTracker implements VisibilityTracker {
    private final Long2ReferenceMap<RenderSection> nonEmptySections = new Long2ReferenceOpenHashMap<>();
    private final Set<RenderSection> visibleTickableSections = new HashSet<>();
    private final Map<RenderSection, List<BlockEntity>> visibleBlockEntities = new HashMap<>();

    private final Long2IntMap occlusionData = new Long2IntOpenHashMap();
    private final TornadoChunkRender tornadoChunkRender;
    private RenderSectionManager renderSectionManager;

    private boolean occlusionDataDirty = false;

    public OcclusionTracker(TornadoChunkRender tornadoChunkRender) {
        this.occlusionData.defaultReturnValue(Integer.MIN_VALUE);
        this.tornadoChunkRender = tornadoChunkRender;
    }

    @Override
    public void update(Camera camera, FrustumExtended frustum, int frame, boolean spectator) {
        // TODO think

        // temp
        this.todo.forEach(this.renderSectionManager::schedulePendingUpdates);

        this.tornadoChunkRender.update(camera, frustum, frame, spectator);
    }

    @Override
    public int getVisibleChunkCount() {
        return this.nonEmptySections.size();
    }

    @Override
    public Iterable<? extends RenderSection> getVisibleTickableSections() {
        return this.visibleTickableSections;
    }

    @Override
    public Iterable<? extends BlockEntity> getVisibleBlockEntities() {
        // Why do I need the type info here?
        return this.visibleBlockEntities.values().stream().<BlockEntity>mapMulti(List::forEach).toList();
    }

    @Override
    public boolean isSectionVisible(int x, int y, int z) {
        return this.nonEmptySections.containsKey(ChunkSectionPos.asLong(x, y, z));
    }

    @Override
    public void setRenderSectionManager(RenderSectionManager renderSectionManager) {
        this.renderSectionManager = renderSectionManager;
    }

    private final Set<RenderSection> todo = new HashSet<>();

    private void updateOcclusionData(RenderSection section, int data){
        // assert data == data & 0b111_111;
        if(data != this.occlusionData.put(section.getChunkPosAsLong(), data)){
            this.occlusionDataDirty = true;
        }
    }

    @Override
    public void addSection(RenderSection section) {
        this.updateOcclusionData(section, 0b111_111);
        this.todo.add(section);
    }

    @Override
    public void removeSection(RenderSection section) {
        this.todo.remove(section);
        this.nonEmptySections.remove(section.getChunkPosAsLong());
        this.occlusionData.remove(section.getChunkPosAsLong());
        this.occlusionDataDirty = true;
    }

    @Override
    public void updateSection(RenderSection section) {
        if (section.isEmpty()) {
            this.nonEmptySections.remove(section.getChunkPosAsLong());
            this.updateOcclusionData(section, 0);
        } else {
            this.nonEmptySections.put(section.getChunkPosAsLong(), section);
            this.updateOcclusionData(section, convertDataToOcclusion(section.getData().getOcclusionData()));
        }
    }

    private static int convertDataToOcclusion(ChunkOcclusionData data) {
        if (data == null) {
            // TODO: shouldn't this be 0b111_111?
            return -1; // absent, all blocked
        }

        int i = 0;
        int j = 1;

        for (Direction direction : DirectionUtil.ALL_DIRECTIONS) {
            if (!data.isVisibleThrough(direction, direction)) {
                i |= j;
            }

            j <<= 1;
        }

        return i;
    }

    public Long2IntMap getOcclusionData() {
        return this.occlusionData;
    }

    public Long2ReferenceMap<? extends RenderSection> getNonEmptySections() {
        return this.nonEmptySections;
    }
}
