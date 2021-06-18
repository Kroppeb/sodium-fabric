package me.jellysquid.mods.sodium.client.render.chunk.backends.gpuculled;

import it.unimi.dsi.fastutil.objects.*;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferTarget;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferUsage;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.VertexData;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw.ChunkDrawCallBatcher;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.util.math.ChunkSectionPos;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Iterator;


public class GpuCullingChunkManager {
    // you need approx 4 MB for all chunks in a 32 renderdistance (if none are empty)
    private static final MemoryStack memoryStack = MemoryStack.create(64 * 1024 * 1024);

    private static final int EXPECTED_CHUNK_SIZE = 4 * 1024;

    private final RenderDevice device;

    private final GlBufferArena arena;
    private final ChunkDrawCallBatcher batch;

    private final ObjectArrayList<ChunkBuildResult<GpuCulledChunkGraphicsState>> uploadQueue;
    private final Object2IntMap<ChunkSectionPos> occlusionData;
    private final Object2ObjectMap<ChunkSectionPos, GlBufferSegment> meshDataLocation;

//    private GlTessellation tessellation;


    private final GlMutableBuffer uploadBuffer;

    public GpuCullingChunkManager(RenderDevice device, int size) {
        this.device = device;
        int arenaSize = size * EXPECTED_CHUNK_SIZE;

        this.arena = new GlBufferArena(device, arenaSize, arenaSize);
        this.uploadQueue = new ObjectArrayList<>();
        this.occlusionData = new Object2IntOpenHashMap<>(size);
        this.meshDataLocation = new Object2ObjectOpenHashMap<>(size);

        this.batch = ChunkDrawCallBatcher.create(size * ModelQuadFacing.COUNT);

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
        this.batch.delete();
    }

    private ObjectArrayList<ChunkBuildResult<GpuCulledChunkGraphicsState>> getUploadQueue() {
        return this.uploadQueue;
    }

    private ChunkDrawCallBatcher getDrawBatcher() {
        return this.batch;
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


                    state = new GpuCulledChunkGraphicsState(
                            renderContainer,
                            segment,
                            meshData,
                            vertexFormat,
                            GpuCulledChunkGraphicsState.readOcclusionData(data.getOcclusionData())
                    );
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
            GlMutableBuffer computeInputsBuffer,
            GlMutableBuffer attributesBuffer,
            GlMutableBuffer fragmentInputsBuffer) {
        System.out.println("occlusion data: " + this.occlusionData.size());
        System.out.println("gpu buffer size: " + ((GlMutableBuffer) this.arena.getBuffer()).getSize());

        // using our own stack to avoid out of memory caused by memory leak
        try (MemoryStack stack = memoryStack.push()) {
            ByteBuffer computeInputs = stack.malloc(this.occlusionData.size() * 32);
            ByteBuffer attributes = stack.malloc(this.occlusionData.size() * 4 * 4);
            ByteBuffer fragmentInputs = stack.malloc(4 + this.occlusionData.size() * 4 * 4);

            fragmentInputs.putInt(0); // initialise index with 0

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

                computeInputs.putInt(ChunkCubeLookup.lookupCount(occlusionData));
                computeInputs.putInt(ChunkCubeLookup.lookupStart(occlusionData));
                computeInputs.putInt(index);

                computeInputs.putInt(0);// vec3 needs 4 "floats" in ssbo
                computeInputs.putFloat(chunkSectionPos.getX() * 16.0f + 8.0f);
                computeInputs.putFloat(chunkSectionPos.getY() * 16.0f + 8.0f);
                computeInputs.putFloat(chunkSectionPos.getZ() * 16.0f + 8.0f);

                computeInputs.putInt(0); // idk the stride is 32

                attributes.putFloat(chunkSectionPos.getX() * 16.0f + 8.0f);
                attributes.putFloat(chunkSectionPos.getY() * 16.0f + 8.0f);
                attributes.putFloat(chunkSectionPos.getZ() * 16.0f + 8.0f);
                attributes.putInt(index);

                GlBufferSegment glBufferSegment = this.meshDataLocation.get(chunkSectionPos);
                if(glBufferSegment != null){
                    fragmentInputs.putInt(1);
                    fragmentInputs.putInt(glBufferSegment.getLength() / ); // TODO get count
                    fragmentInputs.putInt(chunkSectionPos.getY() * 16 + 8); // TODO get firstIndex
                    fragmentInputs.putInt(chunkSectionPos.getZ() * 16 + 8); // TODO get baseInstance
                } else {
                    fragmentInputs.putInt(0);
                    fragmentInputs.putInt(0);
                    fragmentInputs.putInt(0);
                    fragmentInputs.putInt(0);
                }


                index++;
            }

            computeInputs.rewind();
            attributes.rewind();
            fragmentInputs.rewind();

            commandList.uploadDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 0, computeInputsBuffer, computeInputs);
            commandList.uploadDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS, 4, fragmentInputsBuffer, fragmentInputs);
            // commandList.uploadDataBase(GlBufferTarget., 0, computeInputsBuffer, inputs);


            commandList.uploadData(GlBufferTarget.ARRAY_BUFFER, attributesBuffer, attributes);
            commandList.unbindBuffer(GlBufferTarget.ARRAY_BUFFER);
            return index;
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
