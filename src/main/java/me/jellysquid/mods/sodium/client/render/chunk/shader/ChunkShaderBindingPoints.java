package me.jellysquid.mods.sodium.client.render.chunk.shader;

import me.jellysquid.mods.sodium.client.gl.shader.ShaderBindingPoint;

public class ChunkShaderBindingPoints {
    // Vertex in
    public static final ShaderBindingPoint POSITION = new ShaderBindingPoint(0);
    public static final ShaderBindingPoint COLOR = new ShaderBindingPoint(1);
    public static final ShaderBindingPoint TEX_COORD = new ShaderBindingPoint(2);
    public static final ShaderBindingPoint LIGHT_COORD = new ShaderBindingPoint(3);

    public static final ShaderBindingPoint MODEL_OFFSET = new ShaderBindingPoint(4);

    // Fragment out
    public static final ShaderBindingPoint FRAG_COLOR = new ShaderBindingPoint(0);

    // GPU culler
    public static final ShaderBindingPoint INPUT_DATA = new ShaderBindingPoint(0);
    public static final ShaderBindingPoint FIRST_COMMAND_LIST = new ShaderBindingPoint(1);
    public static final ShaderBindingPoint SECOND_COMMAND_LIST = new ShaderBindingPoint(2);

    public static final ShaderBindingPoint ATOMIC_COUNTERS = new ShaderBindingPoint(0);
}
