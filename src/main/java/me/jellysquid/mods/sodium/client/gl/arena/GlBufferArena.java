package me.jellysquid.mods.sodium.client.gl.arena;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferTarget;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferUsage;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;

/**
 * A wrapper over a resizable buffer, in which sub regions can be allocated and freed
 */
public class GlBufferArena {
    private static final GlBufferUsage BUFFER_USAGE = GlBufferUsage.GL_DYNAMIC_DRAW;

    private final RenderDevice device;
    private final int resizeIncrement;

    private final Int2ObjectMap<GlBufferSegment> freeRegions = new Int2ObjectLinkedOpenHashMap<>();

    GlMutableBuffer internalBuffer;

    private int position;
    private int capacity;
    private int allocCount;

    public GlBufferArena(RenderDevice device, int initialSize, int resizeIncrement) {
        this.device = device;

        try (CommandList commands = device.createCommandList()) {
            this.internalBuffer = commands.createMutableBuffer(BUFFER_USAGE);
            commands.allocateBuffer(GlBufferTarget.COPY_WRITE_BUFFER, this.internalBuffer, initialSize);
        }

        this.resizeIncrement = resizeIncrement;
        this.capacity = initialSize;
    }

    private void resize(CommandList commandList, int newCapacity) {
        GlMutableBuffer src = this.internalBuffer;
        GlMutableBuffer dst = commandList.createMutableBuffer(BUFFER_USAGE);

        commandList.allocateBuffer(GlBufferTarget.COPY_WRITE_BUFFER, dst, newCapacity);
        commandList.copyBufferSubData(src, dst, 0, 0, this.position);
        commandList.deleteBuffer(src);

        this.internalBuffer = dst;
        this.capacity = newCapacity;
    }

    public void prepareBuffer(CommandList commandList, int bytes) {
        if (this.position + bytes >= this.capacity) {
            this.resize(commandList, this.getNextSize(bytes));
        }
    }

    @Deprecated
    public GlBufferSegment uploadBuffer(CommandList commandList, GlBuffer readBuffer, int readOffset, int byteCount) {
        this.prepareBuffer(commandList, byteCount);

        GlBufferSegment segment = this.alloc(byteCount);

        commandList.copyBufferSubData(readBuffer, this.internalBuffer, readOffset, segment.getStart(), byteCount);

        return segment;
    }


    private int getNextSize(int len) {
        return Math.max(this.capacity + this.resizeIncrement, this.capacity + len);
    }


    void free(GlBufferSegment segment) {
        GlBufferSegment next = this.freeRegions.remove(segment.getStart() + segment.getLength());
        if (next == null) {
            this.freeRegions.put(segment.getStart(), segment);
        } else {
            // merge regions
            this.freeRegions.put(
                    segment.getStart(),
                    new GlBufferSegment(this, segment.getStart(), segment.getLength() + next.getLength()));
        }


        this.allocCount--;
    }

    public GlBufferSegment alloc(CommandList commandList, int len) {
        GlBufferSegment segment = this.allocReuse(len);

        if (segment == null) {
            this.prepareBuffer(commandList, len);

            segment = new GlBufferSegment(this, this.position, len);

            this.position += len;
        }

        this.allocCount++;

        return segment;
    }

    private GlBufferSegment alloc(int len) {
        GlBufferSegment segment = this.allocReuse(len);

        if (segment == null) {
            segment = new GlBufferSegment(this, this.position, len);

            this.position += len;
        }

        this.allocCount++;

        return segment;
    }

    private GlBufferSegment allocReuse(int len) {
        GlBufferSegment bestSegment = null;

        for (GlBufferSegment segment : this.freeRegions.values()) {
            if (segment.getLength() < len) {
                continue;
            }

            if (bestSegment == null || bestSegment.getLength() > segment.getLength()) {
                bestSegment = segment;
            }
        }

        if (bestSegment == null) {
            return null;
        }

        this.freeRegions.remove(bestSegment.getStart());

        int excess = bestSegment.getLength() - len;

        if (excess > 0) {
            this.freeRegions.put(
                    bestSegment.getStart() + len,
                    new GlBufferSegment(this, bestSegment.getStart() + len, excess));
        }

        return new GlBufferSegment(this, bestSegment.getStart(), len);
    }

    public void delete() {
        try (CommandList commands = this.device.createCommandList()) {
            commands.deleteBuffer(this.internalBuffer);
        }
    }

    public boolean isEmpty() {
        return this.allocCount <= 0;
    }

    public GlBuffer getBuffer() {
        return this.internalBuffer;
    }
}
