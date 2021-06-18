package me.jellysquid.mods.sodium.client.gl.shader;

import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GL43C;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
    VERTEX(GL20C.GL_VERTEX_SHADER),
    FRAGMENT(GL20C.GL_FRAGMENT_SHADER),

    GE(GL32C.GL_GEOMETRY_SHADER),

    COMPUTE(GL43C.GL_COMPUTE_SHADER),
    TESS_CONTROL_SHADER(GL40C.GL_TESS_CONTROL_SHADER),
    TESS_EVALUATION_SHADER(GL40C.GL_TESS_EVALUATION_SHADER),
    ;

    public final int id;

    ShaderType(int id) {
        this.id = id;
    }
}
