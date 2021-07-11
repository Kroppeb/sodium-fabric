package me.jellysquid.mods.sodium.client.render.chunk.backend.region;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;

public class RegionalRenderSection extends RenderSection {
    private final RenderRegion region;

    public RegionalRenderSection(RenderSectionManager renderSectionManager, int chunkX, int chunkY, int chunkZ, RenderRegion region) {
        super(renderSectionManager, chunkX, chunkY, chunkZ);

        this.region = region;
    }

    public RenderRegion getRegion() {
        return this.region;
    }
}
