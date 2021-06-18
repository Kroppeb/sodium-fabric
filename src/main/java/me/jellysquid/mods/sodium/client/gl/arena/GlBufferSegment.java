package me.jellysquid.mods.sodium.client.gl.arena;

import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;

public class GlBufferSegment {
    private final GlBufferArena arena;
    private final int start;
    private final int len;

    GlBufferSegment(GlBufferArena arena, int start, int len) {
        this.arena = arena;
        this.start = start;
        this.len = len;
    }

    public int getStart() {
        return this.start;
    }

    public int getLength() {
        return this.len;
    }

    public void delete() {
        this.arena.free(this);
    }

    @Deprecated
    public void uploadBuffer(
            CommandList commandList,
            GlBuffer readBuffer,
            int readOffset,
            int byteCount) {
        commandList.copyBufferSubData(readBuffer, this.arena.internalBuffer, readOffset, this.start, byteCount);
    }
}
