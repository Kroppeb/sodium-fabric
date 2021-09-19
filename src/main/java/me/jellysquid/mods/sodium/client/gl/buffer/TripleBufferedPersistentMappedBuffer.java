package me.jellysquid.mods.sodium.client.gl.buffer;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import org.lwjgl.opengl.GL46C;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class TripleBufferedPersistentMappedBuffer {
    private final int size;
    private final GlImmutableBuffer immutableBuffer;
    private final ByteBuffer byteBuffer;
    private int current = -1;
    private boolean checkFences = false;
    private final long[] fences = new long[3];

    private TripleBufferedPersistentMappedBuffer(int size, GlImmutableBuffer immutableBuffer, ByteBuffer byteBuffer) {
        this.size = size;
        this.immutableBuffer = immutableBuffer;
        this.byteBuffer = byteBuffer;
    }

    public ByteBuffer start(CommandList commandList) {
        this.moveNext();

        if (this.checkFences && !commandList.clientWaitSyncSuccess(this.fences[this.current], 10_000_000)) {
            SodiumClientMod.logger().warn("Renderthread stalled.");
        }

        return this.byteBuffer;
    }

    public void flush(CommandList commandList) {
        this.flushCurrent(commandList);
    }

    public void done(CommandList commandList) {
        long fence = commandList.createFence();
        this.fences[this.current] = fence;
    }

    private void moveNext() {
        this.current++;

        // loop around
        if (this.current >= 3) {
            this.current = 0;
            this.checkFences = true;
        }

        int start = this.current * this.size;
        this.byteBuffer.position(start);
        this.byteBuffer.limit(start + this.size);
        this.byteBuffer.mark();
    }

    private void flushCurrent(CommandList commandList) {
        commandList.bindBuffer(GlBufferTarget.ARRAY_BUFFER, this.immutableBuffer);
        GL46C.glFlushMappedBufferRange(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), (long) this.current * this.size, this.size);
    }


    private static final int CREATE_FLAGS = GlBufferAccess.getFlags(
            GlBufferAccess.MAP_PERSISTENT,
            // according to sources, letting the driver figure out when to flush, has negligible overhead on some/most(?) hardware
            // GlBufferAccess.MAP_COHERENT,
            GlBufferAccess.MAP_WRITE);

    private static final int MAPPING_FLAGS = GlBufferAccess.getFlags(
            GlBufferAccess.MAP_PERSISTENT,
            // according to sources, letting the driver figure out when to flush, has negligible overhead on some/most(?) hardware
            // GlBufferAccess.MAP_COHERENT,
            GlBufferAccess.MAP_WRITE,
            GlBufferAccess.MAP_INVALIDATE_BUFFER, // shouldn't matter I think?
            GlBufferAccess.MAP_FLUSH_EXPLICIT
    );

    public static TripleBufferedPersistentMappedBuffer create(CommandList commandList, int size, boolean readable, boolean writeable) {
        GlImmutableBuffer buffer = commandList.createImmutableBuffer();
        commandList.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);

        long tripleSize = 3L * size;

        GL46C.glBufferStorage(
                GlBufferTarget.ARRAY_BUFFER.getTargetParameter(),
                tripleSize,
                CREATE_FLAGS);

        ByteBuffer byteBuffer = GL46C.glMapBufferRange(
                GlBufferTarget.ARRAY_BUFFER.getTargetParameter(),
                0,
                tripleSize,
                MAPPING_FLAGS);

        Objects.requireNonNull(byteBuffer);

        return new TripleBufferedPersistentMappedBuffer(size, buffer, byteBuffer);
    }

    public void bind(CommandList commandList, GlBufferTarget bufferTarget, int index) {
        commandList.bindBufferRange(
                bufferTarget,
                index,
                this.immutableBuffer,
                (long) this.current * this.size,
                this.size
        );
    }
}
