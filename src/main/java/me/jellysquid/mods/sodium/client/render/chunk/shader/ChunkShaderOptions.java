package me.jellysquid.mods.sodium.client.render.chunk.shader;

import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;

import java.util.ArrayList;
import java.util.List;

public record ChunkShaderOptions(ChunkFogMode fog, boolean use46) {
    public ChunkShaderOptions(ChunkFogMode fog) {
        this(fog, false);
    }

    public ShaderConstants constants() {
        List<String> defines = new ArrayList<>();
        if(this.use46){
            defines.add("sodium_460_renderer");
        }
        defines.addAll(this.fog.getDefines());

        return ShaderConstants.fromStringList(defines);
    }
}
