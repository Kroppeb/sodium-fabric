package me.jellysquid.mods.sodium.client.render.chunk.shader;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.*;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class ChunkShaderManager {
    private List<GlProgram> programs = new ArrayList<>();

    public ChunkProgram createShader(RenderDevice device, String path, ChunkShaderOptions options) {
        ShaderConstants constants = options.constants();

        GlShader vertShader = ShaderLoader.loadShader(ShaderType.VERTEX,
                new Identifier("sodium", path + ".vsh"), constants);

        GlShader fragShader = ShaderLoader.loadShader(ShaderType.FRAGMENT,
                new Identifier("sodium", path + ".fsh"), constants);


        // TODO: shaders with different options get the same id
        try {
            ChunkProgram build = GlProgram.builder(new Identifier("sodium", path + "/chunk_shader"))
                    .attachShader(vertShader)
                    .attachShader(fragShader)
                    .bindAttribute("a_Pos", ChunkShaderBindingPoints.ATTRIBUTE_POSITION_ID)
                    .bindAttribute("a_Color", ChunkShaderBindingPoints.ATTRIBUTE_COLOR)
                    .bindAttribute("a_TexCoord", ChunkShaderBindingPoints.ATTRIBUTE_BLOCK_TEXTURE)
                    .bindAttribute("a_LightCoord", ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_TEXTURE)
                    .bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .build((name) -> new ChunkProgram(device, name, options));
            this.programs.add(build);
            return build;
        } finally {
            vertShader.delete();
            fragShader.delete();
        }
    }

    public void delete() {
        this.programs.forEach(GlProgram::delete);
        this.programs.clear();
    }
}
