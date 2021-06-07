package me.jellysquid.mods.sodium.client.model.vertex.formats.line;

import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

public interface LineVertexSink extends VertexSink {
    VertexFormat VERTEX_FORMAT = VertexFormats.POSITION_COLOR;

    /**
     * Writes a line vertex to the sink.
     *
     * @param x The x-position of the vertex
     * @param y The y-position of the vertex
     * @param z The z-position of the vertex
     * @param color The ABGR-packed color of the vertex
     * @param nx The x-normal of the vertex
     * @param ny The y-normal of the vertex
     * @param nz The z-normal of the vertex
     */
    void vertexLine(float x, float y, float z, int color, float nx, float ny, float nz);

    /**
     * Writes a line vertex to the sink using unpacked normalised colors with normals added on top.
     * This is slower than {@link LineVertexSink#vertexLine(float, float, float, int, float, float, float)}
     * as it needs to pack the colors each call.
     *
     *
     * @param x The x-position of the vertex
     * @param y The y-position of the vertex
     * @param z The z-position of the vertex
     * @param r The normalized red component of the vertex's color
     * @param g The normalized green component of the vertex's color
     * @param b The normalized blue component of the vertex's color
     * @param a The normalized alpha component of the vertex's color
     * @param nx The x-normal of the vertex
     * @param ny The y-normal of the vertex
     * @param nz The z-normal of the vertex
     * */
    default void vertexLine(float x, float y, float z, float r, float g, float b, float a, float nx, float ny, float nz) {
        this.vertexLine(x, y, z, ColorABGR.pack(r, g, b, a), nx, ny, nz);
    }
}
