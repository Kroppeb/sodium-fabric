package me.jellysquid.mods.sodium.client.gl.device;

import me.jellysquid.mods.sodium.client.gl.array.GlVertexArray;
import me.jellysquid.mods.sodium.client.gl.buffer.*;
import me.jellysquid.mods.sodium.client.gl.state.GlStateTracker;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlVertexArrayTessellation;
import me.jellysquid.mods.sodium.client.gl.tessellation.TessellationBinding;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class GLRenderDevice implements RenderDevice {
    private final GlStateTracker stateTracker = new GlStateTracker();
    private final CommandList commandList = new ImmediateCommandList(this.stateTracker);
    private final DrawCommandList drawCommandList = new ImmediateDrawCommandList();

    private boolean isActive;
    private GlTessellation activeTessellation;

    @Override
    public CommandList createCommandList() {
        GLRenderDevice.this.checkDeviceActive();

        return this.commandList;
    }

    @Override
    public void makeActive() {
        if (this.isActive) {
            return;
        }

        this.stateTracker.clearRestoreState();
        this.isActive = true;
    }

    @Override
    public void makeInactive() {
        if (!this.isActive) {
            return;
        }

        this.stateTracker.applyRestoreState();
        this.isActive = false;
    }

    private void checkDeviceActive() {
        if (!this.isActive) {
            throw new IllegalStateException("Tried to access device from unmanaged context");
        }
    }

    private class ImmediateCommandList implements CommandList {
        private final GlStateTracker stateTracker;

        private ImmediateCommandList(GlStateTracker stateTracker) {
            this.stateTracker = stateTracker;
        }

        @Override
        public void bindVertexArray(GlVertexArray array) {
            if (this.stateTracker.makeVertexArrayActive(array)) {
                GL30C.glBindVertexArray(array.handle());
            }
        }

        @Override
        public void uploadData(GlMutableBuffer glBuffer, ByteBuffer byteBuffer, GlBufferUsage usage) {
            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, glBuffer);

            if (byteBuffer == null) {
                GL20C.glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), 0L, usage.getId());
                glBuffer.setSize(0L);
            } else {
                GL20C.glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), byteBuffer, usage.getId());
                glBuffer.setSize(byteBuffer.remaining());
            }
        }

        @Override
        public void uploadData(GlMutableBuffer glBuffer, ShortBuffer shortBuffer, GlBufferUsage usage) {
            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, glBuffer);

            if (shortBuffer == null) {
                GL20C.glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), 0L, usage.getId());
                glBuffer.setSize(0L);
            } else {
                GL20C.glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), shortBuffer, usage.getId());
                glBuffer.setSize((long) shortBuffer.remaining() << 1);
            }
        }

        @Override
        public void uploadDataBase(
                GlBufferTarget target, int index, GlMutableBuffer glBuffer, ByteBuffer byteBuffer, GlBufferUsage usage) {

            this.bindBufferBase(target, index, glBuffer);

            if (byteBuffer == null) {
                GL20C.glBufferData(target.getTargetParameter(), 0L, usage.getId());
                glBuffer.setSize(0L);
            } else {
                GL20C.glBufferData(target.getTargetParameter(), byteBuffer, usage.getId());
                glBuffer.setSize(byteBuffer.remaining());
            }
        }

        @Override
        public void copyBufferSubData(GlBuffer src, GlMutableBuffer dst, long readOffset, long writeOffset, long bytes) {
            if (writeOffset + bytes > dst.getSize()) {
                throw new IllegalArgumentException("Not enough space in destination buffer (writeOffset + bytes > bufferSize)");
            }

            this.bindBuffer(GlBufferTarget.COPY_READ_BUFFER, src);
            this.bindBuffer(GlBufferTarget.COPY_WRITE_BUFFER, dst);

            GL31C.glCopyBufferSubData(GL31C.GL_COPY_READ_BUFFER, GL31C.GL_COPY_WRITE_BUFFER, readOffset, writeOffset, bytes);
        }

        @Override
        public void bindBuffer(GlBufferTarget target, GlBuffer buffer) {
            if (this.stateTracker.makeBufferActive(target, buffer)) {
                GL20C.glBindBuffer(target.getTargetParameter(), buffer.handle());
            }
        }

        @Override
        public void bindBufferBase(GlBufferTarget target, int index, GlBuffer buffer) {
            this.stateTracker.makeBufferActive(target, buffer);

            // TODO: use state tracker
            GL32C.glBindBufferBase(target.getTargetParameter(), index, buffer.handle());
        }

        @Override
        public void bindBufferRange(GlBufferTarget target, int index, GlBuffer buffer, long offset, long size) {
            this.stateTracker.makeBufferActive(target, buffer);

            // TODO: use state tracker
            GL32C.glBindBufferRange(target.getTargetParameter(), index, buffer.handle(), offset, size);
        }

        @Override
        public void createData(GlBufferTarget target, GlMutableBuffer buffer, long size, GlBufferUsage usage) {
            this.bindBuffer(target, buffer);

            GL20C.glBufferData(target.getTargetParameter(), size, usage.getId());

            buffer.setSize(size);
        }

        @Override
        public void createDataBase(GlBufferTarget target, int index, GlMutableBuffer buffer, long size, GlBufferUsage usage) {
            this.bindBufferBase(target, index, buffer);

            GL20C.glBufferData(target.getTargetParameter(), size, usage.getId());

            buffer.setSize(size);
        }

        @Override
        public void unbindVertexArray() {
            if (this.stateTracker.makeVertexArrayActive(null)) {
                GL30C.glBindVertexArray(GlVertexArray.NULL_ARRAY_ID);
            }
        }

        @Override
        public void allocateStorage(GlMutableBuffer buffer, long bufferSize, GlBufferUsage usage) {
            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);

            GL20C.glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), bufferSize, usage.getId());
            buffer.setSize(bufferSize);
        }

        @Override
        public void deleteBuffer(GlBuffer buffer) {
            this.stateTracker.notifyBufferDeleted(buffer);

            int handle = buffer.handle();
            buffer.invalidateHandle();

            GL20C.glDeleteBuffers(handle);
        }

        @Override
        public void deleteVertexArray(GlVertexArray vertexArray) {
            this.stateTracker.notifyVertexArrayDeleted(vertexArray);

            int handle = vertexArray.handle();
            vertexArray.invalidateHandle();

            GL32C.glDeleteVertexArrays(handle);
        }

        @Override
        public void dispatchCompute(int x, int y, int z) {
            GL46C.glDispatchCompute(x,y,z);
        }

        @Override
        public void flush() {
            // NO-OP
        }

        @Override
        public DrawCommandList beginTessellating(GlTessellation tessellation) {
            GLRenderDevice.this.activeTessellation = tessellation;
            GLRenderDevice.this.activeTessellation.bind(GLRenderDevice.this.commandList);

            return GLRenderDevice.this.drawCommandList;
        }

        @Override
        public void deleteTessellation(GlTessellation tessellation) {
            tessellation.delete(this);
        }

        @Override
        public GlMutableBuffer createMutableBuffer() {
            return new GlMutableBuffer();
        }

        @Override
        public GlImmutableBuffer createImmutableBuffer() {
            return new GlImmutableBuffer();
        }

        @Override
        public GlTessellation createTessellation(GlPrimitiveType primitiveType, TessellationBinding[] bindings, GlBuffer indexBuffer) {
            GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(new GlVertexArray(), primitiveType, bindings, indexBuffer);
            tessellation.init(this);

            return tessellation;
        }

        @Override
        public long createFence() {
            return GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        }

        @Override
        public int clientWaitSync(long fence, long timeout) {
            return GL32C.glClientWaitSync(fence, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, timeout);
        }
    }

    private class ImmediateDrawCommandList implements DrawCommandList {
        public ImmediateDrawCommandList() {

        }

        @Override
        public void multiDrawElementsBaseVertex(PointerBuffer pointer, IntBuffer count, IntBuffer baseVertex) {
            GlPrimitiveType primitiveType = GLRenderDevice.this.activeTessellation.getPrimitiveType();
            GL32C.glMultiDrawElementsBaseVertex(primitiveType.getId(), count, GL20C.GL_UNSIGNED_INT, pointer, baseVertex);
        }

        @Override
        public void endTessellating() {
            GLRenderDevice.this.activeTessellation.unbind(GLRenderDevice.this.commandList);
            GLRenderDevice.this.activeTessellation = null;
        }

        @Override
        public void flush() {
            if (GLRenderDevice.this.activeTessellation != null) {
                this.endTessellating();
            }
        }
    }
}
