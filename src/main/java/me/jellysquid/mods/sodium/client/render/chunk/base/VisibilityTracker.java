package me.jellysquid.mods.sodium.client.render.chunk.base;

import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;

public interface VisibilityTracker extends SectionListener{
    void update(Camera camera, FrustumExtended frustum, int frame, boolean spectator);

    int getVisibleChunkCount();

    Iterable<? extends RenderSection> getVisibleTickableSections();
    Iterable<? extends BlockEntity> getVisibleBlockEntities();

    boolean isSectionVisible(int x, int y, int z);

    default boolean isAnySectionVisible(int minX, int minY, int minZ, int maxX, int maxY, int maxZ){
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (this.isSectionVisible(x, y, z)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // Used to schedule builds
    void setRenderSectionManager(RenderSectionManager renderSectionManager);
}
