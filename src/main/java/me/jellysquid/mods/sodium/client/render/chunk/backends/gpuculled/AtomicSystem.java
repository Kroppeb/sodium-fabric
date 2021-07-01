package me.jellysquid.mods.sodium.client.render.chunk.backends.gpuculled;

import org.lwjgl.opengl.GLCapabilities;

import java.util.EnumSet;
import java.util.List;

public enum AtomicSystem {
    SSBO("__SODIUM__ATOMICS__USE_SSBO"),
    AC("__SODIUM__ATOMICS__USE_AC");

    final private List<? extends String> defines;

    AtomicSystem(List<? extends String> defines) {
        this.defines = defines;
    }

    AtomicSystem(String... defines) {
        this(List.of(defines));
    }

    public static EnumSet<? extends AtomicSystem> getAvailable(GLCapabilities capabilities) {
        var result = EnumSet.noneOf(AtomicSystem.class);

        if (capabilities.OpenGL43 || capabilities.GL_ARB_shader_storage_buffer_object) {
            result.add(SSBO);
        }

        if (capabilities.OpenGL42 || capabilities.GL_ARB_shader_atomic_counters) {
            result.add(AC);
        }

        return result;
    }

    List<? extends String> getDefines(){
        return this.defines;
    }
}
