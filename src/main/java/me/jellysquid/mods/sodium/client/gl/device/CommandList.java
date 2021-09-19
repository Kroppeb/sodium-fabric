package me.jellysquid.mods.sodium.client.gl.device;

import me.jellysquid.mods.sodium.client.gl.array.GlVertexArray;
import me.jellysquid.mods.sodium.client.gl.buffer.*;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.gl.tessellation.TessellationBinding;
import org.lwjgl.opengl.GL32C;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public interface CommandList extends AutoCloseable {
    GlMutableBuffer createMutableBuffer();

    GlImmutableBuffer createImmutableBuffer();

    GlTessellation createTessellation(GlPrimitiveType primitiveType, TessellationBinding[] bindings, GlBuffer indexBuffer);

    void bindVertexArray(GlVertexArray array);

    void uploadData(GlMutableBuffer glBuffer, ByteBuffer byteBuffer, GlBufferUsage usage);

    void uploadData(GlMutableBuffer glBuffer, ShortBuffer byteBuffer, GlBufferUsage usage);

    void uploadDataBase(GlBufferTarget target, int index, GlMutableBuffer glBuffer, ByteBuffer byteBuffer, GlBufferUsage usage);

    void createData(GlBufferTarget target, GlMutableBuffer buffer, long size, GlBufferUsage usage);

    void createDataBase(GlBufferTarget target, int index, GlMutableBuffer buffer, long size, GlBufferUsage usage);

    void copyBufferSubData(GlBuffer src, GlMutableBuffer dst, long readOffset, long writeOffset, long bytes);

    void bindBuffer(GlBufferTarget target, GlBuffer buffer);

    void bindBufferBase(GlBufferTarget target, int index, GlBuffer buffer);

    void bindBufferRange(GlBufferTarget target, int index, GlBuffer buffer, long offset, long size);

    void unbindVertexArray();

    @Deprecated
    default void allocateStorage(GlMutableBuffer buffer, long bufferSize, GlBufferUsage usage) {
        this.createData(GlBufferTarget.ARRAY_BUFFER, buffer, bufferSize, usage);
    }

    void deleteBuffer(GlBuffer buffer);

    void deleteVertexArray(GlVertexArray vertexArray);

    void dispatchCompute(int x, int y, int z);

    void flush();

    DrawCommandList beginTessellating(GlTessellation tessellation);

    void deleteTessellation(GlTessellation tessellation);

    @Override
    default void close() {
        this.flush();
    }

    long createFence();

    int clientWaitSync(long fence, long timeout);

    default boolean clientWaitSyncSuccess(long fence, long timeout) {
        int res = this.clientWaitSync(fence, timeout);
        return switch (res) {
            case GL32C.GL_ALREADY_SIGNALED -> true;
            case GL32C.GL_CONDITION_SATISFIED -> false;
            case GL32C.GL_TIMEOUT_EXPIRED -> throw new RuntimeException("Timeout");
            case GL32C.GL_WAIT_FAILED -> throw new RuntimeException("Opengl error");
            default -> throw new RuntimeException("Unknown return value");
        };
    }
}
