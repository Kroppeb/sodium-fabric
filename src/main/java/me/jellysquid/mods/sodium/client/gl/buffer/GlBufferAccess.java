package me.jellysquid.mods.sodium.client.gl.buffer;

import org.lwjgl.opengl.GL46C;

public enum GlBufferAccess {
    MAP_READ(GL46C.GL_MAP_READ_BIT),
    MAP_WRITE(GL46C.GL_MAP_WRITE_BIT),
    MAP_INVALIDATE_RANGE(GL46C.GL_MAP_INVALIDATE_RANGE_BIT),
    MAP_INVALIDATE_BUFFER(GL46C.GL_MAP_INVALIDATE_BUFFER_BIT),
    MAP_FLUSH_EXPLICIT(GL46C.GL_MAP_FLUSH_EXPLICIT_BIT),
    MAP_UNSYNCHRONIZED(GL46C.GL_MAP_UNSYNCHRONIZED_BIT),

    DYNAMIC_STORAGE(GL46C.GL_DYNAMIC_STORAGE_BIT),
    MAP_COHERENT(GL46C.GL_MAP_COHERENT_BIT),
    MAP_PERSISTENT(GL46C.GL_MAP_PERSISTENT_BIT),
    CLIENT_STORAGE(GL46C.GL_CLIENT_STORAGE_BIT);

    private final int flag;

    GlBufferAccess(int flag) {
        this.flag = flag;
    }

    public int getFlag() {
        return this.flag;
    }

    public static int getFlags(GlBufferAccess... flags) {
        int res = 0;
        for (GlBufferAccess glBufferAccess : flags) {
            int glBufferAccessFlag = glBufferAccess.getFlag();
            res |= glBufferAccessFlag;
        }
        return res;
    }
}
