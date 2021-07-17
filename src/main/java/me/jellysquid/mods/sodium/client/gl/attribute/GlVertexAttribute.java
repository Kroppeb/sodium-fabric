package me.jellysquid.mods.sodium.client.gl.attribute;

public class GlVertexAttribute {
    private final int format;
    private final int count;
    private final int pointer;
    private final int size;
    private final int stride;
    private final int divisor;

    private final boolean normalized;

    /**
     * @param format The format used
     * @param count The number of components in the vertex attribute
     * @param normalized Specifies whether or not fixed-point data values should be normalized (true) or used directly
 *                   as fixed-point values (false)
     * @param pointer The offset to the first component in the attribute
     */
    public GlVertexAttribute(GlVertexAttributeFormat format, int count, boolean normalized, int pointer, int stride) {
        this(format, count, normalized, pointer, stride, 0);
    }

    public GlVertexAttribute(GlVertexAttributeFormat format, int count, boolean normalized, int pointer, int stride, int divisor) {
        this(format.typeId(), format.size() * count, count, normalized, pointer, stride, divisor);
    }

    protected GlVertexAttribute(int format, int size, int count, boolean normalized, int pointer, int stride, int divisor) {
        this.format = format;
        this.size = size;
        this.count = count;
        this.normalized = normalized;
        this.pointer = pointer;
        this.stride = stride;
        this.divisor = divisor;
    }

    public int getSize() {
        return this.size;
    }

    public int getPointer() {
        return this.pointer;
    }

    public int getCount() {
        return this.count;
    }

    public int getFormat() {
        return this.format;
    }

    public boolean isNormalized() {
        return this.normalized;
    }

    public int getStride() {
        return this.stride;
    }

    public int getDivisor() {
        return this.divisor;
    }
}
