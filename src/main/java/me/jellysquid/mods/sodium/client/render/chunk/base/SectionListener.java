package me.jellysquid.mods.sodium.client.render.chunk.base;

public interface SectionListener {
    void addSection(RenderSection section);

    void removeSection(RenderSection section);

    void updateSection(RenderSection section);
}
