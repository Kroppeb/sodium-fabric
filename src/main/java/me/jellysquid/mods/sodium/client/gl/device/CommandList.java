package me.jellysquid.mods.sodium.client.gl.device;

import me.jellysquid.mods.sodium.client.gl.array.GlVertexArray;
import me.jellysquid.mods.sodium.client.gl.buffer.*;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.gl.tessellation.TessellationBinding;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public interface CommandList extends AutoCloseable {
    GlVertexArray createVertexArray();

    GlMutableBuffer createMutableBuffer(GlBufferUsage usage);

    GlTessellation createTessellation(GlPrimitiveType primitiveType, TessellationBinding[] bindings);

    void bindVertexArray(GlVertexArray array);

    default void uploadData(GlMutableBuffer glBuffer, VertexData data) {
        this.uploadData(glBuffer, data.buffer);
    }

    default void uploadData(GlMutableBuffer glBuffer, ByteBuffer byteBuffer){
        this.uploadData(GlBufferTarget.ARRAY_BUFFER, glBuffer, byteBuffer);
    }

    default void uploadData(GlMutableBuffer glBuffer, ShortBuffer shortBuffer){
        this.uploadData(GlBufferTarget.ARRAY_BUFFER, glBuffer, shortBuffer);
    }

    void uploadData(GlBufferTarget target, GlMutableBuffer glBuffer, ByteBuffer byteBuffer);

    void uploadData(GlBufferTarget target, GlMutableBuffer glBuffer, ShortBuffer shortBuffer);

    void uploadDataBase(GlBufferTarget target, int index, GlMutableBuffer glBuffer, ByteBuffer byteBuffer);

    void createData(GlBufferTarget target, GlMutableBuffer buffer, int size);

    void createDataBase(GlBufferTarget target, int index, GlMutableBuffer buffer, int size);

    void copyBufferSubData(GlBuffer src, GlMutableBuffer dst, long readOffset, long writeOffset, long bytes);

    void bindBuffer(GlBufferTarget target, GlBuffer buffer);

    void bindBufferBase(GlBufferTarget target, int index, GlBuffer buffer);

    void unbindBuffer(GlBufferTarget target);

    void unbindVertexArray();

    void invalidateBuffer(GlMutableBuffer glBuffer);

    void allocateBuffer(GlBufferTarget target, GlMutableBuffer buffer, long bufferSize);

    void deleteBuffer(GlBuffer buffer);

    void deleteVertexArray(GlVertexArray vertexArray);

    void flush();

    DrawCommandList beginTessellating(GlTessellation tessellation);

    void deleteTessellation(GlTessellation tessellation);

    void dispatchCompute(int x, int y, int z);

    @Override
    default void close() {
        this.flush();
    }


}
