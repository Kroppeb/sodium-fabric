package me.jellysquid.mods.sodium.client.render.chunk;

import me.jellysquid.mods.sodium.client.render.chunk.backend.region.RenderRegion;

import java.util.List;
import java.util.Map;

public interface ChunkRenderList {
    void clear();

    void add(RenderSection render);

    int getCount();
}
