package me.jellysquid.mods.sodium.client.render.chunk.backends.gpuculled;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.buffer.*;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.util.BufferSlice;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw.ChunkDrawCallBatcher;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.struct.Structured;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.util.math.ChunkSectionPos;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static me.jellysquid.mods.sodium.client.struct.Structs.*;


public class GpuCullingChunkManager {
    // you need approx 4 MB for all chunks in a 32 renderdistance (if none are empty)
    static final MemoryStack memoryStack = MemoryStack.create(64 * 1024 * 1024);

    private static final int EXPECTED_CHUNK_SIZE = 4 * 1024;

    private final RenderDevice device;

    private final GlBufferArena arena;

    private final ObjectArrayList<ChunkBuildResult<GpuCulledChunkGraphicsState>> uploadQueue;
    private final Object2IntMap<ChunkSectionPos> occlusionData;
    private final Object2IntMap<ChunkSectionPos> lastChunkId;
    private final EnumMap<
            BlockRenderPass,
            Map<
                    ChunkSectionPos,
                    PassChunkDataHolder>> meshDataLocation;

    public GlBuffer getArena() {
        return this.arena.getBuffer();
    }


//    private GlTessellation tessellation;


    private static record PassChunkDataHolder(
            GlBufferSegment segment,
            ChunkMeshData chunkMeshData
    ) {
    }

    private final GlMutableBuffer uploadBuffer;

    public GpuCullingChunkManager(RenderDevice device, int size) {
        this.device = device;
        size *= 10;
        int arenaSize = size * EXPECTED_CHUNK_SIZE;

        this.arena = new GlBufferArena(device, arenaSize, arenaSize);
        this.uploadQueue = new ObjectArrayList<>();
        this.occlusionData = new Object2IntOpenHashMap<>(size);
        this.lastChunkId = new Object2IntOpenHashMap<>(size);
        this.meshDataLocation = new EnumMap<>(BlockRenderPass.class);

        for (var pass : BlockRenderPass.VALUES) {
            this.meshDataLocation.put(pass, new HashMap<>(size));
        }

        try (CommandList commands = device.createCommandList()) {
            this.uploadBuffer = commands.createMutableBuffer(GlBufferUsage.GL_DYNAMIC_DRAW);
        }
    }


    private int getUploadQueuePayloadSize() {
        int size = 0;

        for (ChunkBuildResult<GpuCulledChunkGraphicsState> result : this.uploadQueue) {
            size += result.data.getMeshSize();
        }

        return size;
    }


    void deleteResources() {
        /*if (this.tessellation != null) {
            try (CommandList commands = this.device.createCommandList()) {
                commands.deleteTessellation(this.tessellation);
            }

            this.tessellation = null;
        }*/

        this.arena.delete();
    }

    private ObjectArrayList<ChunkBuildResult<GpuCulledChunkGraphicsState>> getUploadQueue() {
        return this.uploadQueue;
    }


    void scheduleUpload(ChunkBuildResult<GpuCulledChunkGraphicsState> next) {
        this.uploadQueue.add(next);
        this.setVisibilityData(next.render.getChunkPos(), next.data.getOcclusionData());
    }

    void scheduleUpload(Iterator<? extends ChunkBuildResult<GpuCulledChunkGraphicsState>> queue) {
        while (queue.hasNext()) {
            var next = queue.next();

            if (next.data.getMeshSize() <= 0) {
                this.remove(next);
            } else {
                this.scheduleUpload(next);
            }
        }
    }

    // TODO the basic upload logic could be wrapped in a different class I think
    void upload(CommandList commandList, GlVertexFormat<?> vertexFormat) {
        commandList.bindBuffer(GlBufferTarget.ARRAY_BUFFER, this.uploadBuffer);

        this.arena.prepareBuffer(commandList, this.getUploadQueuePayloadSize());

        for (var result : this.uploadQueue) {
            var renderContainer = result.render;
            ChunkRenderData data = result.data;

            for (BlockRenderPass pass : BlockRenderPass.VALUES) {
                var graphicsState = renderContainer.getGraphicsState(pass);

                var meshMap = this.meshDataLocation.get(pass);

                // De-allocate the existing buffer arena for this render
                // This will allow it to be cheaply re-allocated just below
                if (graphicsState != null) {
                    graphicsState.delete(commandList);
                }

                ChunkMeshData meshData = data.getMesh(pass);

                GpuCulledChunkGraphicsState state = null;
                if (meshData.hasVertexData()) {
                    VertexData upload = meshData.takeVertexData();

                    commandList.uploadData(this.uploadBuffer, upload.buffer);

                    GlBufferSegment segment = this.arena.uploadBuffer(commandList, this.uploadBuffer, 0, upload.buffer.capacity());


                    meshMap.put(renderContainer.getChunkPos(), new PassChunkDataHolder(
                            segment,
                            meshData
                    ));
                    state = new GpuCulledChunkGraphicsState(
                            renderContainer,
                            segment,
                            meshData,
                            vertexFormat,
                            GpuCulledChunkGraphicsState.readOcclusionData(data.getOcclusionData())
                    );
                } else {
                    // TODO better check all memory leaks
                    PassChunkDataHolder old = meshMap.remove(renderContainer.getChunkPos());
                    this.occlusionData.removeInt(renderContainer.getChunkPos());

                    if (old != null) {
                        old.segment().delete();
                    }
                }
                renderContainer.setGraphicsState(pass, state);
            }

            renderContainer.setData(data);
        }

        // Check if the tessellation needs to be updated
        // This happens whenever the backing buffer object for the arena changes, or if it hasn't already been created
        /*
        if (region.getTessellation() == null || buffer != arena.getBuffer()) {
            if (region.getTessellation() != null) {
                commandList.deleteTessellation(region.getTessellation());
            }

            region.setTessellation(this.createRegionTessellation(commandList, arena.getBuffer()));
        }*/

        this.uploadQueue.clear();


        commandList.invalidateBuffer(this.uploadBuffer);
    }

    public void remove(ChunkBuildResult<GpuCulledChunkGraphicsState> next) {
        // TODO ?
        this.occlusionData.removeInt(next.render.getChunkPos());
    }


    public void setVisibilityData(ChunkSectionPos sectionPos, ChunkOcclusionData occlusionData) {
        if (occlusionData == null) {
            this.occlusionData.removeInt(sectionPos);
        } else {
            this.setVisibilityData(
                    sectionPos,
                    GpuCulledChunkGraphicsState.readOcclusionData(occlusionData));
        }
    }

    public void setVisibilityData(ChunkSectionPos sectionPos, int occlusionData) {
        this.occlusionData.put(
                sectionPos,
                occlusionData);
    }

    public int generateInputBuffer(
            CommandList commandList,
            @Structured(PreComputeInputs) GlMutableBuffer computeInputsBuffer,
            @Structured(Vec3) GlMutableBuffer attributesBuffer,
            @Structured(Int) GlMutableBuffer fragmentInputsBuffer,
            double cameraX, double cameraY, double cameraZ,
            ChunkVertexType vertexType) {
        System.out.println("occlusion data: " + this.occlusionData.size());
        System.out.println("gpu buffer size: " + ((GlMutableBuffer) this.arena.getBuffer()).getSize());

        this.lastChunkId.clear();


        // using our own stack to avoid out of memory caused by memory leak
        try (MemoryStack stack = memoryStack.push()) {
            @Structured(PreComputeInputs) ByteBuffer preComputeInputs = stack.malloc(this.occlusionData.size() * PreComputeInputs.size);
            @Structured(Vec3) ByteBuffer chunkPositions = stack.malloc(this.occlusionData.size() * Vec3.size);
            @Structured(Int) ByteBuffer chunkStatus = stack.malloc(this.occlusionData.size() * Int.size);

            /*fragmentInputs.putInt(0); // initialise index with 0*/

            int index = 0;
            for (Object2IntMap.Entry<ChunkSectionPos> entry : this.occlusionData.object2IntEntrySet()) {
                /*
                struct Inputs {
                    uint count;
                    uint firstIndex;
                    uint baseInstance;
                    vec3 pos;
                };
                 */

                int occlusionData = entry.getIntValue();
                ChunkSectionPos chunkSectionPos = entry.getKey();

                preComputeInputs.putInt(ChunkCubeLookup.lookupCount(occlusionData));
                preComputeInputs.putInt(ChunkCubeLookup.lookupStart(occlusionData));
                preComputeInputs.putInt(1); // drawn state
                preComputeInputs.putInt(0); // align to 16

                preComputeInputs.putFloat((float) (chunkSectionPos.getMinX() + 8.0d - cameraX));
                preComputeInputs.putFloat((float) (chunkSectionPos.getMinY() + 8.0d - cameraY));
                preComputeInputs.putFloat((float) (chunkSectionPos.getMinZ() + 8.0d - cameraZ));
                preComputeInputs.putInt(0); // vec3 needs 4 "floats"

                chunkPositions.putFloat((float) (chunkSectionPos.getMinX() - cameraX - 8.0d)); // i don't know why it's - 8
                chunkPositions.putFloat((float) (chunkSectionPos.getMinY() - cameraY - 8.0d)); // i don't know why it's - 8
                chunkPositions.putFloat((float) (chunkSectionPos.getMinZ() - cameraZ - 8.0d)); // i don't know why it's - 8
                chunkPositions.putInt(0); // vec3 needs 4 "floats"

                chunkStatus.putInt(0);


                this.lastChunkId.put(chunkSectionPos, index++);
            }

            preComputeInputs.rewind();
            chunkPositions.rewind();
            chunkStatus.rewind();

            commandList.uploadDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 0, computeInputsBuffer, preComputeInputs);
            // commandList.uploadDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 4, fragmentInputsBuffer, chunkStatus);
            // commandList.uploadDataBase(GlBufferTarget., 0, computeInputsBuffer, inputs);


            commandList.uploadData(GlBufferTarget.ARRAY_BUFFER, attributesBuffer, chunkPositions);
            commandList.unbindBuffer(GlBufferTarget.ARRAY_BUFFER);
            return this.occlusionData.size();
        }
    }

    public long uploadPass(
            CommandList commandList,
            BlockRenderPass pass,
            GlVertexFormat<?> vertexFormat,
            @Structured(ChunkMeshPass) GlMutableBuffer meshInfoCommandBuffer,
            double x, double y, double z
    ) {

        int maxSize = 0;
        try (var stack = memoryStack.push()) {
            var meshes = this.meshDataLocation.get(pass);

            @Structured(ChunkMeshPass) ByteBuffer buffer = stack.malloc(meshes.size() * ChunkMeshPass.size);

            for (var entry : meshes.entrySet()) {
                ChunkSectionPos chunkSectionPos = entry.getKey();
                int chunkId = this.lastChunkId.getInt(chunkSectionPos);

                PassChunkDataHolder values = entry.getValue();
                var sliceMap = values.chunkMeshData().getSliceMap();
                buffer.putInt(chunkId);

                for (var dir : ModelQuadFacing.VALUES) {
                    BufferSlice bufferSlice = sliceMap.get(dir);
                    if (bufferSlice == null) {
                        buffer.putInt(0);
                        buffer.putInt(0);
                        buffer.putInt(0);
                    } else {
                        int size = bufferSlice.len / vertexFormat.getStride() * 6 / 4; // size -> vertex count -> index
                        int start = (bufferSlice.start + values.segment().getStart()) / vertexFormat.getStride();
                        if (size > maxSize) {
                            maxSize = size;
                        }

                        buffer.putInt(size);
                        buffer.putInt(0);
                        buffer.putInt(start);
                    }
                }

                buffer.putInt(0); // vec3 needs to be aligned to 16 bytes;
                buffer.putInt(0);

                // default bounds atm
                buffer.putFloat((float) (chunkSectionPos.getMinX() - 0.5d - x));
                buffer.putFloat((float) (chunkSectionPos.getMinY() - 0.5d - y));
                buffer.putFloat((float) (chunkSectionPos.getMinZ() - 0.5d - z));
                buffer.putInt(0); // vec3 uses 4 floats

                buffer.putFloat((float) (chunkSectionPos.getMaxX() + 0.5f - x));
                buffer.putFloat((float) (chunkSectionPos.getMaxY() + 0.5f - y));
                buffer.putFloat((float) (chunkSectionPos.getMaxZ() + 0.5f - z));
                buffer.putInt(0); // vec3 uses 4 floats

            }

            buffer.rewind();

            commandList.uploadDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 3, meshInfoCommandBuffer, buffer);

            return meshes.size() | ((long) maxSize << 32);
        }
    }


    /*
    private GlTessellation getTessellation() {
        return this.tessellation;
    }

    private void setTessellation(GlTessellation tessellation) {
        this.tessellation = tessellation;
    }*/
}
