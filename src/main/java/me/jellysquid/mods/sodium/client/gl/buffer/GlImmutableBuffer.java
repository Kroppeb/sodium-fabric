package me.jellysquid.mods.sodium.client.gl.buffer;

public class GlImmutableBuffer extends GlBuffer{
    private long size = 0L;

    public GlImmutableBuffer() {
        super();
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getSize() {
        return this.size;
    }
}
