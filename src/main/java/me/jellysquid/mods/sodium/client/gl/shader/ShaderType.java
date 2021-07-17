package me.jellysquid.mods.sodium.client.gl.shader;

import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL46C;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
    VERTEX(GL20C.GL_VERTEX_SHADER, ".vsh"),
    FRAGMENT(GL20C.GL_FRAGMENT_SHADER, ".fsh"),
    COMPUTE(GL46C.GL_COMPUTE_SHADER, ".comp");

    public final int id;
    public final String defaultExtension;

    ShaderType(int id, String defaultExtension) {
        this.id = id;
        this.defaultExtension = defaultExtension;
    }
}
