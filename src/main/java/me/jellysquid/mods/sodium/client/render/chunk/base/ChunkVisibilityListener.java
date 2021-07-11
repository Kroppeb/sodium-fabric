package me.jellysquid.mods.sodium.client.render.chunk.base;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;

public interface ChunkVisibilityListener {
    void clear();

    void add(RenderSection render);

    int getCount();
}
