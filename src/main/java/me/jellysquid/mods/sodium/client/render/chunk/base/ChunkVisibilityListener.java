package me.jellysquid.mods.sodium.client.render.chunk.base;

public interface ChunkVisibilityListener {
    void clear();

    void add(RenderSection render);

    int getCount();
}
