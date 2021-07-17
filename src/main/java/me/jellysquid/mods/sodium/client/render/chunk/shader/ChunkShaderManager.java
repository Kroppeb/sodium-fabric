package me.jellysquid.mods.sodium.client.render.chunk.shader;

import me.jellysquid.mods.sodium.client.gl.shader.*;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class ChunkShaderManager {
    private final List<GlProgram> programs = new ArrayList<>();


    public ChunkProgram createShader(String path, ChunkShaderOptions options) {
        ShaderConstants constants = options.constants();

        GlShader vertShader = getGlShader(path, constants, ShaderType.VERTEX);

        GlShader fragShader = getGlShader(path, constants, ShaderType.FRAGMENT);


        // TODO: shaders with different options get the same id
        try {
            ChunkProgram build = GlProgram.builder(new Identifier("sodium", path + "/chunk_shader"))
                    .attachShader(vertShader)
                    .attachShader(fragShader)
                    .bindAttribute("a_Pos", ChunkShaderBindingPoints.ATTRIBUTE_POSITION_ID)
                    .bindAttribute("a_Color", ChunkShaderBindingPoints.ATTRIBUTE_COLOR)
                    .bindAttribute("a_TexCoord", ChunkShaderBindingPoints.ATTRIBUTE_BLOCK_TEXTURE)
                    .bindAttribute("a_LightCoord", ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_TEXTURE)
                    .bindAttribute("a_ChunkOffset", ChunkShaderBindingPoints.ATTRIBUTE_CHUNK_OFFSET)
                    .bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .build((name) -> new ChunkProgram(name, options));
            this.programs.add(build);
            return build;
        } finally {
            vertShader.delete();
            fragShader.delete();
        }
    }

    public static GlShader getGlShader(String path, ShaderConstants constants, ShaderType type) {
        return ShaderLoader.loadShader(
                type,
                new Identifier("sodium", path + type.defaultExtension),
                constants);
    }

    public void delete() {
        this.programs.forEach(GlProgram::delete);
        this.programs.clear();
    }
}
