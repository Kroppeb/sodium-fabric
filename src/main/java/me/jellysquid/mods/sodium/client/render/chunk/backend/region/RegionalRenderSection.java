package me.jellysquid.mods.sodium.client.render.chunk.backend.region;

import me.jellysquid.mods.sodium.client.render.chunk.DefaultRenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSection;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;

public class RegionalRenderSection extends RenderSection {
    final RenderRegion region;

    public RegionalRenderSection(DefaultRenderSectionManager renderSectionManager, int chunkX, int chunkY, int chunkZ, RenderRegion region) {
        super(renderSectionManager, chunkX, chunkY, chunkZ);

        this.region = region;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    @Override
    public boolean isInsideFrustum(FrustumExtended frustum) {
        RenderRegionVisibility parentVisibility = this.region.getVisibility();
        return parentVisibility == RenderRegionVisibility.FULLY_VISIBLE ||
                parentVisibility == RenderRegionVisibility.VISIBLE &&
                        super.isInsideFrustum(frustum);
    }
}
