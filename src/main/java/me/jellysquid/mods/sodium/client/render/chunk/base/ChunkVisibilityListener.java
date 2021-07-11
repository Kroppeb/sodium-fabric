package me.jellysquid.mods.sodium.client.render.chunk.base;

/**
 * The ChunkVisibilityListener is a listener provided by the {@link ChunkRenderer}, so it can figure out which
 * chunk need to be drawn
 */
public interface ChunkVisibilityListener {
    void clear();

    void add(RenderSection render);

    int getCount();
}
