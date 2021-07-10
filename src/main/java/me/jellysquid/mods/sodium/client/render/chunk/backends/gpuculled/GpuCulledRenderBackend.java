package me.jellysquid.mods.sodium.client.render.chunk.backends.gpuculled;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.gl.array.GlVertexArray;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttribute;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeFormat;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;
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
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.gl.tessellation.TessellationBinding;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterator;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkFogMode;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkProgram;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkRenderShaderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import me.jellysquid.mods.sodium.client.struct.Structured;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.profiler.Profiler;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static me.jellysquid.mods.sodium.client.struct.Structs.*;


public class GpuCulledRenderBackend implements ChunkRenderBackend<GpuCulledChunkGraphicsState> {

    // Preprocess programs
    private final Program generatePreCommandsProgram;
    private final Program firstPassProgram;
    private final Program secondPassProgram;
    private final ChunkProgram chunkProgram;

    // draw programs
    private final Program generatePassCommandsProgram;


    private final @Structured(PreComputeInputs)
    GlMutableBuffer computeInputsBuffer;
    private final @Structured(PreComputeInputs)
    GlMutableBuffer fragmentInputsBuffer;
    private final @Structured(PreComputeInputs)
    GlMutableBuffer attributesBuffer;

    private final GlMutableBuffer lameCube;
    private final GlMutableBuffer cubeIndexBuffer;

    private final @Structured(DrawElementsIndirectCommand)
    GlMutableBuffer firstPassCommandBuffer;
    private final @Structured(DrawElementsIndirectCommand)
    GlMutableBuffer secondPassCommandBuffer;


    private final @Structured(ChunkMeshPass)
    GlMutableBuffer meshInfoCommandBuffer;

    private final GpuCullingChunkManager chunks;

    private final GlVertexArray drawChunkBoxPassVAO;
    private final GlTessellation chunkTesselation;

    private final AtomicSystem atomicSystem;
    private final GlMutableBuffer atomicCounters;
    private final GlVertexFormat<ChunkMeshAttribute> vertexFormat;
    private final ChunkVertexType vertexType;

    public GpuCulledRenderBackend(
            RenderDevice device,
            ChunkVertexType vertexType,
            AtomicSystem atomicSystem) {

        this.vertexFormat = vertexType.getCustomVertexFormat();
        this.vertexType = vertexType;

        ByteBuffer starting_the_renderer = MemoryUtil.memUTF8("starting the renderer");
        GL46C.glDebugMessageInsert(
                GL46C.GL_DEBUG_SOURCE_APPLICATION,
                GL46C.GL_DEBUG_TYPE_MARKER,
                0,
                GL46C.GL_DEBUG_SEVERITY_NOTIFICATION,
                starting_the_renderer
        );
        MemoryUtil.memFree(starting_the_renderer);

        this.atomicSystem = atomicSystem;


        int renderDistance = 2 * MinecraftClient.getInstance().options.viewDistance + 1;
        int height = 16; // can't get the exact value here I think. This is only for the initial size anyway

        this.chunks = new GpuCullingChunkManager(device, renderDistance * renderDistance * height);

        try (CommandList commandList = device.createCommandList()) {

            // create all the buffers
            // TODO: switch to buffer storage
            this.computeInputsBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STREAM_DRAW);
            this.fragmentInputsBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STREAM_DRAW);
            this.attributesBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STREAM_DRAW);
            this.lameCube = commandList.createMutableBuffer(GlBufferUsage.GL_STATIC_DRAW);
            this.cubeIndexBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STATIC_DRAW);
            this.firstPassCommandBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STREAM_COPY);
            this.secondPassCommandBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STREAM_COPY);
            this.meshInfoCommandBuffer = commandList.createMutableBuffer(GlBufferUsage.GL_STREAM_DRAW);
            this.atomicCounters = commandList.createMutableBuffer(GlBufferUsage.GL_DYNAMIC_DRAW);

            this.drawChunkBoxPassVAO = commandList.createVertexArray();

            this.createLameCube(commandList);

            // region init drawChunkBoxPassVAO
            commandList.bindVertexArray(this.drawChunkBoxPassVAO);

            commandList.bindBuffer(GlBufferTarget.ELEMENT_ARRAY_BUFFER, this.cubeIndexBuffer);

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

            GL32C.glVertexAttribPointer(2, 1, GL32C.GL_INT, false, 16, 12);
            GL42C.glVertexAttribDivisor(2, 1);
            GL32C.glEnableVertexAttribArray(2);

            // endregion

            this.chunkTesselation = this.createChunkTessellation(commandList, this.chunks.getArena());

            this.chunkProgram = ChunkRenderShaderBackend.createShader(
                    device, ChunkFogMode.SMOOTH, this.vertexFormat
            );
        }

        // create pre render programs
        this.generatePreCommandsProgram = this.createPreComputeProgram(device);
        this.firstPassProgram = this.createFirstPassProgram(device);
        this.secondPassProgram = this.createSecondPassProgram(device);

        // create pass programs
        this.generatePassCommandsProgram = this.createPassComputeProgram(device);


    }

    private GlTessellation createChunkTessellation(CommandList commandList, GlBuffer buffer) {
        return commandList.createTessellation(GlPrimitiveType.QUADS, new TessellationBinding[] {
                new TessellationBinding(buffer, new GlVertexAttributeBinding[] {
                        new GlVertexAttributeBinding(ChunkShaderBindingPoints.POSITION,
                                this.vertexFormat.getAttribute(ChunkMeshAttribute.POSITION)),
                        new GlVertexAttributeBinding(ChunkShaderBindingPoints.COLOR,
                                this.vertexFormat.getAttribute(ChunkMeshAttribute.COLOR)),
                        new GlVertexAttributeBinding(ChunkShaderBindingPoints.TEX_COORD,
                                this.vertexFormat.getAttribute(ChunkMeshAttribute.TEXTURE)),
                        new GlVertexAttributeBinding(ChunkShaderBindingPoints.LIGHT_COORD,
                                this.vertexFormat.getAttribute(ChunkMeshAttribute.LIGHT))
                }, false),
                new TessellationBinding(this.attributesBuffer, new GlVertexAttributeBinding[] {
                        new GlVertexAttributeBinding(ChunkShaderBindingPoints.MODEL_OFFSET,
                                new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, 4, false, 0, 0))
                }, true)
        });
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

    private ShaderConstants.Builder getConstantBuilder() {
        ShaderConstants.Builder builder = ShaderConstants.builder();

        // apply default defines
        //this.atomicSystem.getDefines().forEach(builder::define);

        return builder;
    }

    private Program createPreComputeProgram(RenderDevice device) {
        var computeShader = ShaderLoader.loadShader(device, ShaderType.COMPUTE,
                new Identifier("sodium", "gpu_side_culling/generate_pre_commands.c.glsl"),
                this.getConstantBuilder()
                        // .define("useTriangleStrip")
                        // .define("useMultiAtomicCounter")
                        // .define("useNoBranchFrustumCheck")
                        .build());
        System.out.println(computeShader.handle());
        try {
            return GlProgram.builder(new Identifier("sodium", "gpu_side_culling/generate_pre_commands.c"))
                    .attachShader(computeShader)
                    .build((program, name) -> new Program(device, program, name));
        } finally {
            computeShader.delete();
        }
    }

    private Program createPassComputeProgram(RenderDevice device) {
        var computeShader = ShaderLoader.loadShader(device, ShaderType.COMPUTE,
                new Identifier("sodium", "gpu_side_culling/generate_pass_commands.c.glsl"),
                this.getConstantBuilder().build());
        System.out.println(computeShader.handle());
        try {
            return GlProgram.builder(new Identifier("sodium", "gpu_side_culling/generate_pass_commands.c"))
                    .attachShader(computeShader)
                    .build((program, name) -> new Program(device, program, name));
        } finally {
            computeShader.delete();
        }
    }

    private Program createFirstPassProgram(RenderDevice device) {
        var vertexShader = ShaderLoader.loadShader(device, ShaderType.VERTEX,
                new Identifier("sodium", "gpu_side_culling/draw.v.glsl"),
                this.getConstantBuilder()
                      //  .define("useColor")
                        .build());
        var fragmentShader = ShaderLoader.loadShader(device, ShaderType.FRAGMENT,
                new Identifier("sodium", "gpu_side_culling/draw.f.glsl"),
                this.getConstantBuilder()
                       //  .define("useColor")
                        .build());
        try {
            return GlProgram.builder(new Identifier("sodium", "gpu_side_culling/draw.v"))
                    .attachShader(vertexShader)
                    .attachShader(fragmentShader)
                    //.bindFragData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .build((program, name) -> new Program(device, program, name));
        } finally {
            vertexShader.delete();
        }
    }

    private Program createSecondPassProgram(RenderDevice device) {
        var vertexShader = ShaderLoader.loadShader(device, ShaderType.VERTEX,
                new Identifier("sodium", "gpu_side_culling/draw.v.glsl"),
                this.getConstantBuilder()
                        //.define("useColor")
                        .define("secondPass")
                        .build());
        var fragmentShader = ShaderLoader.loadShader(device, ShaderType.FRAGMENT,
                new Identifier("sodium", "gpu_side_culling/draw.f.glsl"),
                this.getConstantBuilder()
                        //.define("supportsEarlyFragmentTests")
                        .define("secondPass")
                        .define("secondPass2")
                        //.define("useColor")
                        .build());
        try {
            return GlProgram.builder(new Identifier("sodium", "gpu_side_culling/draw.v"))
                    .attachShader(vertexShader)
                    .attachShader(fragmentShader)
                    //.bindFragData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
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

    private Profiler profiler;

    /*
     *  # Pre draw passes
     *
     *  0. initialize buffers
     *  1. run the compute shader to generate the command buffers
     *      // TODO is this actually better than generating them on the cpu?
     *
     *  2. run first draw commands with just a depth buffer
     *  3. run second draw commands with without drawing
     *      3.1 update a status to mark which chunks are visible
     *
     *  # For each draw pass
     *
     *  4. create actual draw commands by running over the status buffer
     *      by creating a new buffer where only the visible, non-empty ones are added
     *  5. draw using third command buffer
     *
     *  # Post draw pass?
     *  6. on cpu: dispatch build tasks based on the status map
     */
    @Override
    public void beforeRender(
            MatrixStack matrixStack, double x, double y, double z, SodiumWorldRenderer sodiumWorldRenderer,
            ChunkRenderManager<GpuCulledChunkGraphicsState> gpuCulledChunkGraphicsStateChunkRenderManager,
            Matrix4f projectionMatrix) {
        var profiler = this.profiler = sodiumWorldRenderer.getWorld().getProfiler();


        // TODO get frustum

        profiler.push("GpuCullingBackend:preRender");

        try (var commandList = RenderDevice.INSTANCE.createCommandList()) {


            profiler.push("step 0");

            // region step 0
            int i = this.chunks.generateInputBuffer(
                    commandList,
                    this.computeInputsBuffer, this.attributesBuffer, this.fragmentInputsBuffer,
                    x,y,z,
                    vertexType
                    );

            try (var stack = this.chunks.memoryStack.push()) {
                ByteBuffer zeroes = stack.calloc(20 * i);
                commandList.uploadDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 1, this.firstPassCommandBuffer, zeroes);
                commandList.uploadDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 2, this.secondPassCommandBuffer, zeroes);
            }
            // commandList.createDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 1, this.firstPassCommandBuffer, i * 20);
            // commandList.createDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 2, this.secondPassCommandBuffer, i * 20);

            commandList.bindBuffer(GlBufferTarget.PARAMETER_BUFFER, this.atomicCounters);

            try (var stack = MemoryStack.stackPush()) {
                // TODO use better way to clear atomic counter
                commandList.uploadDataBase(
                        GlBufferTarget.ATOMIC_COUNTER_BUFFERS, 0, this.atomicCounters, stack.calloc(4));
            }

            // endregion step 0
            profiler.swap("step 1");

            // region step 1
            this.generatePreCommandsProgram.bind();

            // update frustum and cameraPos uniform
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer floatBuffer = stack.mallocFloat(4 * 6);

                FrustumExtended frustum = (FrustumExtended) sodiumWorldRenderer.getFrustum();
                frustum.writeToBuffer(floatBuffer);
                floatBuffer.rewind();

                GL32C.glUniform4fv(this.generatePreCommandsProgram.uFrustum, floatBuffer);
                if(this.generatePreCommandsProgram.uCamaraPos >=0) {
                    GL32C.glUniform3f(this.generatePreCommandsProgram.uCamaraPos, (float) x, (float) y, (float) z);
                }
            }

            GL46C.glFlush();
            commandList.dispatchCompute(i, 1, 1);

            // endregion part 1
            profiler.swap("part 2");

            // region part 2

            // enable depth testing and clear depth values
            GL32C.glClear(GL32C.GL_DEPTH_BUFFER_BIT);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL32C.GL_LEQUAL); // default is GL_LESS I think



            commandList.bindVertexArray(this.drawChunkBoxPassVAO);
            GL46C.glMemoryBarrier(GL46C.GL_ALL_BARRIER_BITS);
            GL46C.glFinish();

            this.firstPassProgram.bind();
            commandList.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, this.firstPassCommandBuffer);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                Matrix4f modelViewMat = matrixStack.peek().getModel();
                Matrix4f projMat = projectionMatrix.copy();


                FloatBuffer projectionBuffer = stack.mallocFloat(16);
                projMat.multiply(modelViewMat);
                projMat.writeColumnMajor(projectionBuffer);

                GL20C.glUniformMatrix4fv(this.firstPassProgram.uModelViewProjectionMatrix, false, projectionBuffer);
                if(this.firstPassProgram.uCamaraPos >=0) {
                    GL32C.glUniform3f(this.firstPassProgram.uCamaraPos, (float) x, (float) y, (float) z);
                }
            }


            GlFunctions.INDIRECT_COUNT_DRAW.glMultiDrawElementArraysIndirectCount(
                    GlPrimitiveType.TRIANGLES.getId(),
                    GL32C.GL_UNSIGNED_SHORT,
                    0L,
                    0L,
                    i,
                    0
            );

            // endregion step 2
            profiler.swap("part 3");

            // region part 3

            // disable drawing to the depth buffer
            RenderSystem.depthMask(false);

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
                if(this.secondPassProgram.uCamaraPos >=0) {
                    GL32C.glUniform3f(this.secondPassProgram.uCamaraPos, (float) x, (float) y, (float) z);
                }
            }


            // dispatch call
            GlFunctions.INDIRECT_COUNT_DRAW.glMultiDrawElementArraysIndirectCount(
                    GlPrimitiveType.TRIANGLES.getId(),
                    GL32C.GL_UNSIGNED_SHORT,
                    0L,
                    0L,
                    i,
                    0
            );

            // endregion step 3
            profiler.pop();
            RenderSystem.depthMask(true);
            GL46C.glClear(GL46C.GL_DEPTH_BUFFER_BIT);
            RenderSystem.depthFunc(GL46C.GL_LESS);
            GL46C.glUseProgram(0);
        }
        profiler.pop();
    }

    @Override
    public void render(CommandList commandList, ChunkRenderListIterator<GpuCulledChunkGraphicsState> renders, ChunkCameraContext camera, BlockRenderPass pass) {
        // I kinda want to skip the current render system, as it's doing work that can be pushed to the gpu

        var profiler = this.profiler;
        profiler.push("GpuCullingBackend:renderStep:" + pass);

        profiler.push("step 4");

        // region step 4
        try (var stack = MemoryStack.stackPush()) {
            // TODO use better way to clear atomic counter
            commandList.uploadDataBase(
                    GlBufferTarget.ATOMIC_COUNTER_BUFFERS, 0, this.atomicCounters, stack.calloc(4));
        }


        long mi = this.chunks.uploadPass(commandList, pass, this.vertexFormat, this.meshInfoCommandBuffer, camera.cameraX, camera.cameraY, camera.cameraZ);
        int i = (int) mi; // lower 32 bits
        int m = (int) (mi >>> 32); // upper 32 bits;

        // TODO: count the valid meshes (fix i * 7)
        commandList.bindBufferBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 0, this.computeInputsBuffer);
        commandList.createDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 1, this.firstPassCommandBuffer, i * 7 * 20);
        commandList.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, this.firstPassCommandBuffer);
        commandList.bindBuffer(GlBufferTarget.PARAMETER_BUFFER, this.atomicCounters);


        this.generatePassCommandsProgram.bind();
        GL46C.glDispatchCompute(i, 1, 1);

        // endregion
        profiler.swap("step 5");

        // region step 5
        this.chunkProgram.bind();

        this.chunkTesselation.bind(commandList);

        var indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS, m);
        GL20C.glBindBuffer(GlBufferTarget.ELEMENT_ARRAY_BUFFER.getTargetParameter(), indexBuffer.getId());


        this.chunkProgram.setup(this.matrixStack, this.vertexType.getModelScale(), this.vertexType.getTextureScale());

        GlFunctions.INDIRECT_COUNT_DRAW.glMultiDrawElementArraysIndirectCount(
                GlPrimitiveType.TRIANGLES.getId(),
                indexBuffer.getElementFormat().count,
                0L,
                0L,
                i * 7,
                0
        );

        GL46C.glUseProgram(0);

        // endregion
        profiler.pop();
        profiler.pop();
    }

    @Override
    public void createShaders(RenderDevice device) {
        // pff
    }

    private MatrixStack matrixStack;

    @Override
    public void begin(MatrixStack matrixStack) {
        this.matrixStack = matrixStack;
    }

    @Override
    public void end(MatrixStack matrixStack) {

    }


    @Override
    public void delete() {
        this.chunks.deleteResources();
        this.generatePreCommandsProgram.delete();
        this.generatePassCommandsProgram.delete();
        this.firstPassProgram.delete();
        this.secondPassProgram.delete();
        this.chunkProgram.delete();
        try (var commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.chunkTesselation.delete(commandList);
        }
    }

    @Override
    public ChunkVertexType getVertexType() {
        return this.vertexType;
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
