package me.jellysquid.mods.sodium.client.render.chunk.backends.gpuculled;

import me.jellysquid.mods.sodium.client.gl.array.GlVertexArray;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferTarget;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferUsage;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.func.GlFunctions;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterator;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkRenderShaderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;


public class GpuCulledRenderBackend extends ChunkRenderShaderBackend<GpuCulledChunkGraphicsState> {
    private final Program generateCommandsProgram;
    private final Program firstPassProgram;
    private final Program secondPassProgram;


    private final GlMutableBuffer computeInputsBuffer;
    private final GlMutableBuffer fragmentInputsBuffer;
    private final GlMutableBuffer attributesBuffer;

    private final GlMutableBuffer lameCube;
    private final GlMutableBuffer cubeIndexBuffer;

    private final GlMutableBuffer firstPassCommandBuffer;
    private final GlMutableBuffer secondPassCommandBuffer;
    private final GlMutableBuffer thirdPassCommandBuffer;

    private final GpuCullingChunkManager chunks;

    private final GlVertexArray drawChunkBoxPassVAO;

    public GpuCulledRenderBackend(RenderDevice device, ChunkVertexType vertexType) {
        super(vertexType);

        try (CommandList commandList = device.createCommandList()) {
            this.computeInputsBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STREAM_DRAW);
            this.fragmentInputsBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STREAM_DRAW);
            this.attributesBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STREAM_DRAW);
            this.lameCube = commandList.createMutableBuffer(GlBufferUsage.GL_STATIC_DRAW);
            this.cubeIndexBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STATIC_DRAW);
            this.firstPassCommandBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STREAM_COPY);
            this.secondPassCommandBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STREAM_COPY);
            this.thirdPassCommandBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STREAM_COPY);

            this.drawChunkBoxPassVAO = commandList.createVertexArray();

            this.createLameCube(commandList);

            commandList.bindVertexArray(this.drawChunkBoxPassVAO);

            // cube vertices
            commandList.bindBuffer(GlBufferTarget.ARRAY_BUFFER, this.lameCube);
            // todo: I think we have the tesselation system for this?
            GL32C.glVertexAttribPointer(0, 3, GL32C.GL_FLOAT, false, 3 * 4, 0);
            GL32C.glEnableVertexAttribArray(0);

            // cube offsets
            commandList.bindBuffer(GlBufferTarget.ARRAY_BUFFER, this.attributesBuffer);
            GL32C.glVertexAttribPointer(1, 3, GL32C.GL_FLOAT, false, 16, 0);
            GL42C.glVertexAttribDivisor(1, 1);
            GL32C.glEnableVertexAttribArray(1);

            GL32C.glVertexAttribPointer(2, 1, GL32C.GL_INT, false,  16, 12);
            GL42C.glVertexAttribDivisor(2, 1);
            GL32C.glEnableVertexAttribArray(2);

            commandList.unbindVertexArray();
        }

        this.generateCommandsProgram = this.createComputeProgram(device);
        this.firstPassProgram = this.createFirstPassProgram(device);
        this.secondPassProgram = this.createSecondPassProgram(device);
        int renderDistance = 2 * MinecraftClient.getInstance().options.viewDistance + 1;
        int height = 16; // can't get the exact value here I think. This is only for the initial size anyway

        this.chunks = new GpuCullingChunkManager(device, renderDistance * renderDistance * height);

    }

    private void createLameCube(CommandList commandList) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buffer = stack.malloc(6 * 4 * 4 * 3);
            for (Direction value : Direction.values()) {
                int baseX = value.getOffsetX();
                int baseY = value.getOffsetY();
                int baseZ = value.getOffsetZ();

                buffer.putFloat((+baseX + baseY + baseZ) * 8);
                buffer.putFloat((+baseX + baseY + baseZ) * 8);
                buffer.putFloat((+baseX + baseY + baseZ) * 8);

                if (baseX + baseY + baseZ > 0) {
                    buffer.putFloat((+baseX + baseY - baseZ) * 8);
                    buffer.putFloat((-baseX + baseY + baseZ) * 8);
                    buffer.putFloat((+baseX - baseY + baseZ) * 8);

                    buffer.putFloat((+baseX - baseY + baseZ) * 8);
                    buffer.putFloat((+baseX + baseY - baseZ) * 8);
                    buffer.putFloat((-baseX + baseY + baseZ) * 8);
                } else {
                    buffer.putFloat((+baseX - baseY + baseZ) * 8);
                    buffer.putFloat((+baseX + baseY - baseZ) * 8);
                    buffer.putFloat((-baseX + baseY + baseZ) * 8);

                    buffer.putFloat((+baseX + baseY - baseZ) * 8);
                    buffer.putFloat((-baseX + baseY + baseZ) * 8);
                    buffer.putFloat((+baseX - baseY + baseZ) * 8);

                }

                buffer.putFloat((+baseX - baseY - baseZ) * 8);
                buffer.putFloat((-baseX + baseY - baseZ) * 8);
                buffer.putFloat((-baseX - baseY + baseZ) * 8);

            }
            buffer.rewind();

            commandList.uploadData(this.lameCube, buffer);

            // upload index data
            commandList.uploadData(this.cubeIndexBuffer, stack.shorts(ChunkCubeLookup.OCCLUSION_DATA));
        }


    }

    private Program createComputeProgram(RenderDevice device) {
        var computeShader = ShaderLoader.loadShader(device, ShaderType.COMPUTE,
                new Identifier("sodium", "gpu_side_culling/generate_commands.c.glsl"), ShaderConstants.builder()
                        // .define("useTriangleStrip")
                        // .define("useAtomicCounter")
                        // .define("useMultiAtomicCounter")
                        // .define("useNoBranchFrustumCheck")
                        .build());
        System.out.println(computeShader.handle());
        try {
            return GlProgram.builder(new Identifier("sodium", "gpu_side_culling/generate_commands.c"))
                    .attachShader(computeShader)
                    .build((program, name) -> new Program(device, program, name));
        } finally {
            computeShader.delete();
        }
    }

    private Program createFirstPassProgram(RenderDevice device) {
        var vertexShader = ShaderLoader.loadShader(device, ShaderType.VERTEX,
                new Identifier("sodium", "gpu_side_culling/draw.v.glsl"), ShaderConstants.builder()
                        //.define("useColor")
                        .build());
        var fragmentShader = ShaderLoader.loadShader(device, ShaderType.FRAGMENT,
                new Identifier("sodium", "gpu_side_culling/draw.f.glsl"), ShaderConstants.builder()
                        //.define("useColor")
                        .build());
        try {
            return GlProgram.builder(new Identifier("sodium", "gpu_side_culling/draw.v"))
                    .attachShader(vertexShader)
                    .attachShader(fragmentShader)
                    .bindFragData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .build((program, name) -> new Program(device, program, name));
        } finally {
            vertexShader.delete();
        }
    }

    private Program createSecondPassProgram(RenderDevice device) {
        var vertexShader = ShaderLoader.loadShader(device, ShaderType.VERTEX,
                new Identifier("sodium", "gpu_side_culling/draw.v.glsl"), ShaderConstants.builder()
                        .define("useColor")
                        .define("secondPass")
                        .build());
        var fragmentShader = ShaderLoader.loadShader(device, ShaderType.FRAGMENT,
                new Identifier("sodium", "gpu_side_culling/draw.f.glsl"), ShaderConstants.builder()
                        //.define("supportsEarlyFragmentTests")
                        .define("secondPass")
                        .define("secondPass2")
                        .define("useColor")
                        .define("inlineAtomicCounter")
                        .build());
        try {
            return GlProgram.builder(new Identifier("sodium", "gpu_side_culling/draw.v"))
                    .attachShader(vertexShader)
                    .attachShader(fragmentShader)
                    .bindFragData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .build((program, name) -> new Program(device, program, name));
        } finally {
            vertexShader.delete();
        }
    }

    private static class Program extends GlProgram {

        private final int uFrustum;
        private final int uCamaraPos;
        private final int uModelViewProjectionMatrix;

        Program(RenderDevice owner, Identifier name, int program) {
            super(owner, name, program);

            this.uFrustum = this.getUniformLocation("frustum");
            this.uCamaraPos = this.getUniformLocation("camaraPos");
            this.uModelViewProjectionMatrix = this.getUniformLocation("u_ModelViewProjectionMatrix");
        }
    }


    @Override
    public void upload(CommandList commandList, Iterator<ChunkBuildResult<GpuCulledChunkGraphicsState>> queue) {
        this.chunks.scheduleUpload(queue);
        // upload all chunks
        this.chunks.upload(commandList, this.vertexFormat);
    }

    @Override
    public void setVisibilityData(ChunkSectionPos sectionPos, ChunkOcclusionData occlusionData) {
        this.chunks.setVisibilityData(sectionPos, occlusionData);
    }

    @Override
    public void setVisibilityData(ChunkSectionPos sectionPos, int occlusionData) {
        this.chunks.setVisibilityData(sectionPos, occlusionData);
    }

    @Override
    public void beforeRender(MatrixStack matrixStack, double x, double y, double z, SodiumWorldRenderer sodiumWorldRenderer, ChunkRenderManager<GpuCulledChunkGraphicsState> gpuCulledChunkGraphicsStateChunkRenderManager, Matrix4f projectionMatrix) {
        /*
         *  1. create compute inputs
         *  2. run the compute shader to generate the command buffers
         *      // TODO is this actually better than generating them on the cpu?
         *  3. run first draw commands with just a depth buffer
         *  4. run second draw commands with without drawing
         *      4.1 create/update a bitmap to mark which chunks are visible
         *  5. create actual draw commands by running over the previous draw commands/third buffer
         *      * by setting invisible chunks to visibility 0
         *      * by creating a new buffer where only the visible, non-empty ones are added
         *  6. draw using third command buffer
         *  7. on cpu: dispatch build tasks based on the bitmap
         */


        // TODO get frustum

        try (var commandList = RenderDevice.INSTANCE.createCommandList()) {

            // region step 1
            int i = this.chunks.generateInputBuffer(commandList, this.computeInputsBuffer, this.attributesBuffer, this.fragmentInputsBuffer);

            commandList.createDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 1, this.firstPassCommandBuffer, i * 20);
            commandList.createDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 2, this.secondPassCommandBuffer, i * 20);
            commandList.createDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 3, this.thirdPassCommandBuffer, i * 20);

            this.generateCommandsProgram.bind();

            // update frustum

            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer floatBuffer = stack.mallocFloat(4 * 6);

                FrustumExtended frustum = (FrustumExtended) sodiumWorldRenderer.getFrustum();
                frustum.writeToBuffer(floatBuffer);
                floatBuffer.rewind();

                GL32C.glUniform4fv(this.generateCommandsProgram.uFrustum, floatBuffer);
                GL32C.glUniform3f(this.generateCommandsProgram.uCamaraPos, (float) x, (float) y, (float) z);
            }


            commandList.dispatchCompute(i, 1, 1);

            // endregion part 1

            // region part 2

            // enable depth testing and clear depth values
            GL32C.glEnable(GL32C.GL_DEPTH_TEST);
            GL32C.glClear(GL32C.GL_DEPTH_BUFFER_BIT);
            GL32C.glDepthFunc(GL32C.GL_LEQUAL); // default is LESS i think


            commandList.bindVertexArray(this.drawChunkBoxPassVAO);

            this.firstPassProgram.bind();
            commandList.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, this.firstPassCommandBuffer);
            commandList.bindBuffer(GlBufferTarget.ELEMENT_ARRAY_BUFFER, this.cubeIndexBuffer);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                Matrix4f modelViewMat = matrixStack.peek().getModel();
                Matrix4f projMat = projectionMatrix.copy();


                FloatBuffer projectionBuffer = stack.mallocFloat(16);
                projMat.multiply(modelViewMat);
                projMat.writeColumnMajor(projectionBuffer);

                GL20C.glUniformMatrix4fv(this.firstPassProgram.uModelViewProjectionMatrix, false, projectionBuffer);
                GL32C.glUniform3f(this.firstPassProgram.uCamaraPos, (float) x, (float) y, (float) z);
            }

            GlFunctions.INDIRECT_DRAW.glMultiDrawElementArraysIndirect(
                    GlPrimitiveType.TRIANGLES.getId(),
                    GL32C.GL_UNSIGNED_SHORT,
                    0,
                    i,
                    0
            );

            // endregion step 2

            // region part 3


            // disable drawing to the depth buffer
            GL32C.glDepthMask(false);

            // switch to the full cubes
            commandList.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, this.secondPassCommandBuffer);

            this.secondPassProgram.bind();

            // update uniforms
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Matrix4f modelViewMat = matrixStack.peek().getModel();
                Matrix4f projMat = projectionMatrix.copy();


                FloatBuffer projectionBuffer = stack.mallocFloat(16);
                projMat.multiply(modelViewMat);
                projMat.writeColumnMajor(projectionBuffer);

                GL20C.glUniformMatrix4fv(this.secondPassProgram.uModelViewProjectionMatrix, false, projectionBuffer);
                GL32C.glUniform3f(this.secondPassProgram.uCamaraPos, (float) x, (float) y, (float) z);
            }

            // dispatch call
            GlFunctions.INDIRECT_DRAW.glMultiDrawElementArraysIndirect(
                    GlPrimitiveType.TRIANGLES.getId(),
                    GL32C.GL_UNSIGNED_SHORT,
                    0,
                    i,
                    0
            );

            // endregion step 3

            // disable depth test
            GL32C.glDisable(GL32C.GL_DEPTH_TEST);
        }
    }

    @Override
    public void render(CommandList commandList, ChunkRenderListIterator<GpuCulledChunkGraphicsState> renders, ChunkCameraContext camera) {


    }


    @Override
    public void delete() {
        super.delete();

        this.chunks.deleteResources();
        this.generateCommandsProgram.delete();
    }

    @Override
    public Class<GpuCulledChunkGraphicsState> getGraphicsStateType() {
        return GpuCulledChunkGraphicsState.class;
    }

    public static boolean isSupported(boolean disableDriverBlacklist) {
        if (!disableDriverBlacklist && isKnownBrokenIntelDriver()) {
            return false;
        }

        return GlFunctions.isVertexArraySupported() &&
                GlFunctions.isBufferCopySupported() &&
                GlFunctions.isIndirectMultiDrawSupported() &&
                GlFunctions.isInstancedArraySupported();
    }

    // https://www.intel.com/content/www/us/en/support/articles/000005654/graphics.html
    private static final Pattern INTEL_BUILD_MATCHER = Pattern.compile("(\\d.\\d.\\d) - Build (\\d+).(\\d+).(\\d+).(\\d+)");

    private static final String INTEL_VENDOR_NAME = "Intel";

    /**
     * Determines whether or not the current OpenGL renderer is an integrated Intel GPU on Windows.
     * These drivers on Windows are known to fail when using command buffers.
     */
    private static boolean isWindowsIntelDriver() {
        // We only care about Windows
        // The open-source drivers on Linux are not known to have driver bugs with indirect command buffers
        if (Util.getOperatingSystem() != Util.OperatingSystem.WINDOWS) {
            return false;
        }

        // Check to see if the GPU vendor is Intel
        return Objects.equals(GL20C.glGetString(GL20C.GL_VENDOR), INTEL_VENDOR_NAME);
    }

    /**
     * Determines whether or not the current OpenGL renderer is an old integrated Intel GPU (prior to Skylake/Gen8) on
     * Windows. These drivers on Windows are unsupported and known to create significant trouble with the multi-draw
     * renderer.
     */
    private static boolean isKnownBrokenIntelDriver() {
        return isWindowsIntelDriver();
        /*if (!isWindowsIntelDriver()) {
            return false;
        }

        String version = GL20C.glGetString(GL20C.GL_VERSION);

        // The returned version string may be null in the case of an error
        if (version == null) {
            return false;
        }

        Matcher matcher = INTEL_BUILD_MATCHER.matcher(version);

        // If the version pattern doesn't match, assume we're dealing with something special
        if (!matcher.matches()) {
            return false;
        }

        // Anything with a major build of >=100 is GPU Gen8 or newer
        // The fourth group is the major build number
        return Integer.parseInt(matcher.group(4)) < 100;*/
    }

    @Override
    public String getRendererName() {
        return "GpuCulled";
    }

    @Override
    public List<String> getDebugStrings() {
        List<String> list = new ArrayList<>();
        // list.add(String.format("Active Buffers: %s", this.bufferManager.getAllocatedRegionCount()));
        // not needed at this moment
        /*
        list.add(String.format("Submission Mode: %s", this.commandBuffer != null ?
                Formatting.AQUA + "Buffer" : Formatting.LIGHT_PURPLE + "Client Memory"));
         */

        return list;
    }


}
