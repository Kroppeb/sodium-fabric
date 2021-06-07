package me.jellysquid.mods.sodium.client.model.vertex.formats.line.writer;

import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterNio;
import me.jellysquid.mods.sodium.client.model.vertex.formats.line.LineVertexSink;

import java.nio.ByteBuffer;

public class LineVertexBufferWriterNio extends VertexBufferWriterNio implements LineVertexSink {
    public LineVertexBufferWriterNio(VertexBufferView backingBuffer) {
        super(backingBuffer, VanillaVertexTypes.LINES);
    }

    @Override
    public void vertexLine(float x, float y, float z, int color) {
        int i = this.writeOffset;

        ByteBuffer buffer = this.byteBuffer;
        buffer.putFloat(i, x);
        buffer.putFloat(i + 4, y);
        buffer.putFloat(i + 8, z);
        buffer.putInt(i + 12, color);

        this.advance();
    }

    @Override
    public void vertexLine(float x, float y, float z, int color, float nx, float ny, float nz) {
        int i = this.writeOffset;

        ByteBuffer buffer = this.byteBuffer;
        buffer.putFloat(i, x);
        buffer.putFloat(i + 4, y);
        buffer.putFloat(i + 8, z);
        buffer.putInt(i + 12, color);
        buffer.putFloat(i + 16, nx);
        buffer.putFloat(i + 20, ny);
        buffer.putFloat(i + 24, nz);

        this.advance();
    }
}
