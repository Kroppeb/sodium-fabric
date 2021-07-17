package me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.gpuculled;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.gl.array.GlVertexArray;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttribute;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeFormat;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferTarget;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferUsage;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.gl.tessellation.TessellationBinding;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.CombinedRenderSectionContainer;
import me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.OcclusionTracker;
import me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.struct.Structured;
import me.jellysquid.mods.sodium.client.render.chunk.base.BiBufferArenas;
import me.jellysquid.mods.sodium.client.render.chunk.base.VisibilityTracker;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderManager;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.struct.Structs.*;


public class TornadoChunkRender extends ShaderChunkRenderer {

    // Preprocess programs
    private final Program generatePreCommandsProgram;
    private final Program firstPassProgram;
    private final Program secondPassProgram;


    private final GlVertexAttributeBinding[] vertexAttributeBindings;
    private final GlVertexAttributeBinding[] chunkAttributeBindings;

    // draw programs
    private final Program generatePassCommandsProgram;


    private final @Structured(PreComputeInputsStruct)
    GlMutableBuffer computeInputsBuffer;
    private final @Structured(PreComputeInputsStruct)
    GlMutableBuffer fragmentInputsBuffer;
    private final @Structured(PreComputeInputsStruct)
    GlMutableBuffer attributesBuffer;

    private final GlMutableBuffer lameCube;
    private final GlMutableBuffer cubeIndexBuffer;

    private final @Structured(DrawElementsIndirectCommandStruct)
    GlMutableBuffer firstPassCommandBuffer;
    private final @Structured(DrawElementsIndirectCommandStruct)
    GlMutableBuffer secondPassCommandBuffer;


    private final @Structured(ChunkMeshPassStruct)
    GlMutableBuffer meshInfoCommandBuffer;

    // TODO, don't like that this is lateinit
    private DrawCallInputManager drawCallInputManager;

    private final GlVertexArray drawChunkBoxPassVAO;

    private final GlMutableBuffer atomicCounters;
    private OcclusionTracker occlusionTracker;

    public TornadoChunkRender(
            RenderDevice device,
            ChunkVertexType vertexType) {
        super(device, vertexType);


        ByteBuffer starting_the_renderer = MemoryUtil.memUTF8("starting the renderer");
        GL46C.glEnable(GL46C.GL_DEBUG_OUTPUT_SYNCHRONOUS);

        GL46C.glDebugMessageInsert(
                GL46C.GL_DEBUG_SOURCE_APPLICATION,
                GL46C.GL_DEBUG_TYPE_MARKER,
                0,
                GL46C.GL_DEBUG_SEVERITY_NOTIFICATION,
                starting_the_renderer
        );
        MemoryUtil.memFree(starting_the_renderer);


        int renderDistance = 2 * MinecraftClient.getInstance().options.viewDistance + 1;
        int height = 16; // can't get the exact value here I think. This is only for the initial size anyway

        this.vertexAttributeBindings = new GlVertexAttributeBinding[] {
                new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_POSITION_ID,
                        this.vertexFormat.getAttribute(ChunkMeshAttribute.POSITION_ID)),
                new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_COLOR,
                        this.vertexFormat.getAttribute(ChunkMeshAttribute.COLOR)),
                new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_BLOCK_TEXTURE,
                        this.vertexFormat.getAttribute(ChunkMeshAttribute.BLOCK_TEXTURE)),
                new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_TEXTURE,
                        this.vertexFormat.getAttribute(ChunkMeshAttribute.LIGHT_TEXTURE))
        };

        this.chunkAttributeBindings = new GlVertexAttributeBinding[] {
                new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_CHUNK_OFFSET,
                        new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, 3, false, 0, 16, 1)
                )
        };

        try (CommandList commandList = device.createCommandList()) {

            // create all the buffers
            // TODO: switch to buffer storage
            this.computeInputsBuffer = commandList.createMutableBuffer();
            this.fragmentInputsBuffer = commandList.createMutableBuffer();
            this.attributesBuffer = commandList.createMutableBuffer();
            this.lameCube = commandList.createMutableBuffer();
            this.cubeIndexBuffer = commandList.createMutableBuffer();
            this.firstPassCommandBuffer = commandList.createMutableBuffer();
            this.secondPassCommandBuffer = commandList.createMutableBuffer();
            this.meshInfoCommandBuffer = commandList.createMutableBuffer();
            this.atomicCounters = commandList.createMutableBuffer();

            this.drawChunkBoxPassVAO = new GlVertexArray();

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
            commandList.unbindVertexArray();
        }

        // create pre render programs
        this.generatePreCommandsProgram = this.createPreComputeProgram();
        this.firstPassProgram = this.createFirstPassProgram();
        this.secondPassProgram = this.createSecondPassProgram();

        // create pass programs
        this.generatePassCommandsProgram = this.createPassComputeProgram();


    }

    @Override
    public VisibilityTracker createVisibilityTracker(ClientWorld world, int renderDistance) {

        this.occlusionTracker = new OcclusionTracker(this);
        this.drawCallInputManager = new DrawCallInputManager(this.device, this.occlusionTracker);

        return this.occlusionTracker;
    }

    private void createLameCube(CommandList commandList) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buffer = stack.malloc(6 * 4 * 4 * 3);
            for (Direction value : Direction.values()) {
                int baseX = value.getOffsetX();
                int baseY = value.getOffsetY();
                int baseZ = value.getOffsetZ();

                buffer.putFloat((1 + baseX + baseY + baseZ) * 8);
                buffer.putFloat((1 + baseX + baseY + baseZ) * 8);
                buffer.putFloat((1 + baseX + baseY + baseZ) * 8);

                if (baseX + baseY + baseZ > 0) {
                    buffer.putFloat((1 + baseX + baseY - baseZ) * 8);
                    buffer.putFloat((1 - baseX + baseY + baseZ) * 8);
                    buffer.putFloat((1 + baseX - baseY + baseZ) * 8);

                    buffer.putFloat((1 + baseX - baseY + baseZ) * 8);
                    buffer.putFloat((1 + baseX + baseY - baseZ) * 8);
                    buffer.putFloat((1 - baseX + baseY + baseZ) * 8);
                } else {
                    buffer.putFloat((1 + baseX - baseY + baseZ) * 8);
                    buffer.putFloat((1 + baseX + baseY - baseZ) * 8);
                    buffer.putFloat((1 - baseX + baseY + baseZ) * 8);

                    buffer.putFloat((1 + baseX + baseY - baseZ) * 8);
                    buffer.putFloat((1 - baseX + baseY + baseZ) * 8);
                    buffer.putFloat((1 + baseX - baseY + baseZ) * 8);

                }

                buffer.putFloat((1 + baseX - baseY - baseZ) * 8);
                buffer.putFloat((1 - baseX + baseY - baseZ) * 8);
                buffer.putFloat((1 - baseX - baseY + baseZ) * 8);

            }
            buffer.rewind();

            commandList.uploadData(this.lameCube, buffer, GlBufferUsage.STATIC_DRAW);

            // upload index data
            commandList.uploadData(this.cubeIndexBuffer, stack.shorts(ChunkCubeLookup.OCCLUSION_DATA), GlBufferUsage.STATIC_DRAW);
        }


    }

    private GlTessellation createRegionTessellation(CommandList commandList, BiBufferArenas arenas) {
        return commandList.createTessellation(GlPrimitiveType.TRIANGLES, new TessellationBinding[] {
                new TessellationBinding(arenas.vertexBuffers.getBufferObject(), this.vertexAttributeBindings),
                new TessellationBinding(this.attributesBuffer, this.chunkAttributeBindings)
        }, arenas.indexBuffers.getBufferObject());
    }

    private GlTessellation createTessellation(CommandList commandList, BiBufferArenas arenas) {
        GlTessellation tessellation = arenas.getTessellation();

        if (tessellation == null) {
            arenas.setTessellation(tessellation = this.createRegionTessellation(commandList, arenas));
        }

        return tessellation;
    }

    private ShaderConstants.Builder getConstantBuilder() {
        ShaderConstants.Builder builder = ShaderConstants.builder();

        // apply default defines
        //this.atomicSystem.getDefines().forEach(builder::define);

        return builder;
    }

    private Program createPreComputeProgram() {
        var computeShader = ChunkShaderManager.getGlShader(
                "gpu_side_culling/generate_pre_commands",
                this.getConstantBuilder()
                        // .define("useTriangleStrip")
                        // .define("useMultiAtomicCounter")
                        // .define("useNoBranchFrustumCheck")
                        .build(),
                ShaderType.COMPUTE);

        try {
            return GlProgram.builder(new Identifier("sodium", "gpu_side_culling/generate_pre_commands.c"))
                    .attachShader(computeShader)
                    .build(Program::new);
        } finally {
            computeShader.delete();
        }
    }

    private Program createPassComputeProgram() {
        var computeShader = ChunkShaderManager.getGlShader(
                "gpu_side_culling/generate_pass_commands",
                this.getConstantBuilder().build(),
                ShaderType.COMPUTE);
        System.out.println(computeShader.handle());
        try {
            return GlProgram.builder(new Identifier("sodium", "gpu_side_culling/generate_pass_commands.c"))
                    .attachShader(computeShader)
                    .build(Program::new);
        } finally {
            computeShader.delete();
        }
    }

    private Program createFirstPassProgram() {
        var vertexShader = ChunkShaderManager.getGlShader(
                "gpu_side_culling/draw",
                this.getConstantBuilder()
                        //.define("useColor")
                        .build(),
                ShaderType.VERTEX);
        var fragmentShader = ChunkShaderManager.getGlShader(
                "gpu_side_culling/draw",
                this.getConstantBuilder()
                        //.define("useColor")
                        .build(),
                ShaderType.FRAGMENT);
        try {
            return GlProgram.builder(new Identifier("sodium", "gpu_side_culling/draw1"))
                    .attachShader(vertexShader)
                    .attachShader(fragmentShader)
                    //.bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .build(Program::new);
        } finally {
            vertexShader.delete();
        }
    }

    private Program createSecondPassProgram() {
        var vertexShader = ChunkShaderManager.getGlShader(
                "gpu_side_culling/draw",
                this.getConstantBuilder()
                        //.define("useColor")
                        .define("secondPass")
                        .build(),
                ShaderType.VERTEX
        );
        var fragmentShader = ChunkShaderManager.getGlShader(
                "gpu_side_culling/draw",
                this.getConstantBuilder()
                        //.define("supportsEarlyFragmentTests")
                        .define("secondPass")
                        .define("secondPass2")
                        //.define("useColor")
                        .build(),
                ShaderType.FRAGMENT);
        try {
            return GlProgram.builder(new Identifier("sodium", "gpu_side_culling/draw2"))
                    .attachShader(vertexShader)
                    .attachShader(fragmentShader)
                    //.bindFragData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .build(Program::new);
        } finally {
            vertexShader.delete();
        }
    }

    private static class Program extends GlProgram {

        private final int uFrustum;
        private final int uCamaraPos;
        private final int uModelViewProjectionMatrix;

        Program(int program) {
            super(program);

            this.uFrustum = this.getUniformLocation("frustum");
            this.uCamaraPos = this.getUniformLocation("camaraPos");
            this.uModelViewProjectionMatrix = this.getUniformLocation("u_ModelViewProjectionMatrix");
        }
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
    public void update(
            Camera camera, FrustumExtended frustum, int frame, boolean spectator
    ) {
        Vec3d pos = camera.getPos();
        ChunkCameraContext chunkCameraContext = new ChunkCameraContext(pos.x, pos.y, pos.z);
        var profiler = this.profiler = SodiumWorldRenderer.getInstance().getWorld().getProfiler();


        // TODO get frustum

        profiler.push("GpuCullingBackend:preRender");

        try (var commandList = RenderDevice.INSTANCE.createCommandList()) {


            profiler.push("step 0");

            // region step 0

            int i = this.drawCallInputManager.generateInputBuffer(
                    commandList,
                    this.computeInputsBuffer, this.attributesBuffer, this.fragmentInputsBuffer,
                    chunkCameraContext,
                    this.vertexType
            );

            if (i == 0) {
                profiler.pop();
                profiler.pop();
                return;
            }

            try (var stack = this.drawCallInputManager.memoryStack.push()) {
                ByteBuffer zeroes = stack.calloc(20 * i);
                commandList.uploadDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 1, this.firstPassCommandBuffer, zeroes, GlBufferUsage.STREAM_COPY);
                commandList.uploadDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 2, this.secondPassCommandBuffer, zeroes, GlBufferUsage.STREAM_COPY);
            }
            // commandList.createDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 1, this.firstPassCommandBuffer, i * 20);
            // commandList.createDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 2, this.secondPassCommandBuffer, i * 20);

            commandList.bindBuffer(GlBufferTarget.PARAMETER_BUFFER, this.atomicCounters);

            try (var stack = MemoryStack.stackPush()) {
                // TODO use better way to clear atomic counter
                commandList.uploadDataBase(
                        GlBufferTarget.ATOMIC_COUNTER_BUFFERS,
                        0,
                        this.atomicCounters,
                        stack.calloc(4),
                        GlBufferUsage.DYNAMIC_DRAW);
            }

            // endregion step 0
            profiler.swap("step 1");

            // region step 1
            this.generatePreCommandsProgram.bind();

            // update frustum and cameraPos uniform
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer floatBuffer = stack.mallocFloat(4 * 6);

                frustum.writeToBuffer(floatBuffer);
                floatBuffer.rewind();

                GL32C.glUniform4fv(this.generatePreCommandsProgram.uFrustum, floatBuffer);
                if (this.generatePreCommandsProgram.uCamaraPos >= 0) {
                    // only used for debug rendering (I think)
                    GL32C.glUniform3f(this.generatePreCommandsProgram.uCamaraPos, (float) pos.x, (float) pos.y, (float) pos.z);
                }
            }

            commandList.dispatchCompute(i, 1, 1);

            // endregion part 1
            profiler.swap("part 2");

            // region part 2

            // enable depth testing and clear depth values
            GL32C.glClear(GL32C.GL_DEPTH_BUFFER_BIT);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL32C.GL_LEQUAL); // default is GL_LESS I think


            commandList.bindVertexArray(this.drawChunkBoxPassVAO);

            // TODO: test if/which bits are needed
            GL46C.glMemoryBarrier(GL46C.GL_ALL_BARRIER_BITS);

            this.firstPassProgram.bind();
            commandList.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, this.firstPassCommandBuffer);

            try (MemoryStack stack = MemoryStack.stackPush()) {


                FloatBuffer projectionBuffer = stack.mallocFloat(16);
                frustum.getProjectionModelViewMatrix().writeColumnMajor(projectionBuffer);

                GL20C.glUniformMatrix4fv(this.firstPassProgram.uModelViewProjectionMatrix, false, projectionBuffer);
                if (this.firstPassProgram.uCamaraPos >= 0) {
                    GL32C.glUniform3f(this.firstPassProgram.uCamaraPos, (float) pos.x, (float) pos.y, (float) pos.z);
                }
            }

            // TODO: figure out why we need this
            commandList.bindBuffer(GlBufferTarget.ELEMENT_ARRAY_BUFFER, this.cubeIndexBuffer);

            ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(
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


                FloatBuffer projectionBuffer = stack.mallocFloat(16);
                frustum.getProjectionModelViewMatrix().writeColumnMajor(projectionBuffer);

                GL20C.glUniformMatrix4fv(this.secondPassProgram.uModelViewProjectionMatrix, false, projectionBuffer);
                if (this.secondPassProgram.uCamaraPos >= 0) {
                    GL32C.glUniform3f(this.secondPassProgram.uCamaraPos, (float) pos.x, (float) pos.y, (float) pos.z);
                }
            }


            // dispatch call
            ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(
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
    public void render(MatrixStack matrixStack, CommandList commandList, BlockRenderPass pass, ChunkCameraContext camera) {
        var profiler = this.profiler;
        profiler.push("GpuCullingBackend:renderStep:" + pass);

        profiler.push("step 4");

        // region step 4
        try (var stack = MemoryStack.stackPush()) {
            // TODO use better way to clear atomic counter
            commandList.uploadDataBase(
                    GlBufferTarget.ATOMIC_COUNTER_BUFFERS,
                    0,
                    this.atomicCounters,
                    stack.calloc(4),
                    GlBufferUsage.DYNAMIC_DRAW
            );
        }


        long mi = this.drawCallInputManager.uploadPass(commandList, pass, this.vertexFormat, this.meshInfoCommandBuffer, camera);
        int i = (int) mi; // lower 32 bits
        int m = (int) (mi >>> 32); // upper 32 bits;

        if (i == 0) {
            profiler.pop();
            profiler.pop();
            return;
        }

        commandList.bindBufferBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 0, this.computeInputsBuffer);
        commandList.createDataBase(
                GlBufferTarget.SHADER_STORAGE_BUFFERS, 1, this.firstPassCommandBuffer,
                (long) i * DrawElementsIndirectCommandStruct.size, GlBufferUsage.STREAM_COPY);
        commandList.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, this.firstPassCommandBuffer);
        commandList.bindBuffer(GlBufferTarget.PARAMETER_BUFFER, this.atomicCounters);


        this.generatePassCommandsProgram.bind();
        GL46C.glDispatchCompute(i, 1, 1);

        // endregion
        profiler.swap("step 5");

        // region step 5
        super.begin(pass, matrixStack);

        GlTessellation tessellation = this.createTessellation(commandList, CombinedRenderSectionContainer.getTheFArenas(commandList));

        try (var drawCommandList = commandList.beginTessellating(tessellation)) {

            // todo add to DrawCommandList?

            ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(
                    GlPrimitiveType.TRIANGLES.getId(),
                    GL46C.GL_UNSIGNED_INT,
                    0L,
                    0L,
                    i,
                    0
            );

        }

        GL46C.glUseProgram(0);

        // endregion
        profiler.pop();
        profiler.pop();
    }

    @Override
    public void delete() {
        super.delete();
        this.generatePreCommandsProgram.delete();
        this.generatePassCommandsProgram.delete();
        this.firstPassProgram.delete();
        this.secondPassProgram.delete();
    }
}
