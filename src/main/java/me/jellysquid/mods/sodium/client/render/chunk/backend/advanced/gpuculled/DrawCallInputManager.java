package me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.gpuculled;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferTarget;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferUsage;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.OcclusionTracker;
import me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.struct.Structured;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import net.minecraft.util.math.ChunkSectionPos;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static me.jellysquid.mods.sodium.client.render.chunk.backend.advanced.struct.Structs.*;


public class DrawCallInputManager {
    // you need approx 4 MB for all chunks in a 32 renderdistance (if none are empty)
    static final MemoryStack memoryStack = MemoryStack.create(64 * 1024 * 1024);


    private final RenderDevice device;

    private final OcclusionTracker occlusionTracker;


    private final Object2IntMap<ChunkSectionPos> lastChunkId;


//    private GlTessellation tessellation;


    public DrawCallInputManager(RenderDevice device, OcclusionTracker occlusionTracker) {
        this.device = device;
        this.occlusionTracker = occlusionTracker;

        this.lastChunkId = new Object2IntOpenHashMap<>();
    }

    public int generateInputBuffer(
            CommandList commandList,
            @Structured(PreComputeInputsStruct) GlMutableBuffer computeInputsBuffer,
            @Structured(Vec3Struct) GlMutableBuffer attributesBuffer,
            @Structured(Int) GlMutableBuffer fragmentInputsBuffer,
            ChunkCameraContext cameraContext,
            ChunkVertexType vertexType) {

        this.lastChunkId.clear();

        var occlusionData = this.occlusionTracker.getOcclusionData();

        // using our own stack to avoid out of memory caused by memory leak
        try (MemoryStack stack = memoryStack.push()) {
            @Structured(PreComputeInputsStruct) ByteBuffer preComputeInputs = stack.malloc(occlusionData.size() * PreComputeInputsStruct.size);
            @Structured(Vec3Struct) ByteBuffer chunkPositions = stack.malloc(occlusionData.size() * Vec3Struct.size);
            @Structured(Int) ByteBuffer chunkStatus = stack.malloc(occlusionData.size() * Int.size);

            /*fragmentInputs.putInt(0); // initialise index with 0*/

            int index = 0;
            for (Long2IntMap.Entry entry : Long2IntMaps.fastIterable(occlusionData)) {
                /*
                struct Inputs {
                    uint count;
                    uint firstIndex;
                    uint baseInstance;
                    vec3 pos;
                };
                 */

                int occlusionState = entry.getIntValue();
                // TODO: remove this allocation
                ChunkSectionPos chunkSectionPos = ChunkSectionPos.from(entry.getLongKey());

                preComputeInputs.putInt(ChunkCubeLookup.lookupCount(occlusionState));
                preComputeInputs.putInt(ChunkCubeLookup.lookupStart(occlusionState));
                preComputeInputs.putInt(0); // drawn state
                preComputeInputs.putInt(0); // align to 16


                preComputeInputs.putFloat(cameraContext.getCameraXTranslation(chunkSectionPos.getMinX() + 8));
                preComputeInputs.putFloat(cameraContext.getCameraYTranslation(chunkSectionPos.getMinY() + 8));
                preComputeInputs.putFloat(cameraContext.getCameraZTranslation(chunkSectionPos.getMinZ() + 8));
                preComputeInputs.putInt(0); // vec3 uses 4 floats

                chunkPositions.putFloat(cameraContext.getCameraXTranslation(chunkSectionPos.getMinX()));
                chunkPositions.putFloat(cameraContext.getCameraYTranslation(chunkSectionPos.getMinY()));
                chunkPositions.putFloat(cameraContext.getCameraZTranslation(chunkSectionPos.getMinZ()));
                chunkPositions.putInt(0); // vec3 uses 4 floats


                chunkStatus.putInt(0);


                this.lastChunkId.put(chunkSectionPos, index++);
            }

            preComputeInputs.rewind();
            chunkPositions.rewind();
            chunkStatus.rewind();

            commandList.uploadDataBase(GlBufferTarget.SHADER_STORAGE_BUFFERS,
                    0,
                    computeInputsBuffer,
                    preComputeInputs,
                    GlBufferUsage.STREAM_DRAW);


            commandList.uploadData(attributesBuffer, chunkPositions, GlBufferUsage.STREAM_DRAW);

            return occlusionData.size();
        }
    }

    public int uploadPass(
            BlockRenderPass pass,
            GlVertexFormat<?> vertexFormat,
            @Structured(ChunkMeshPassStruct) ByteBuffer targetBuffer,
            ChunkCameraContext cameraContext
    ) {
        int meshCount = 0;


        var meshes = this.occlusionTracker.getNonEmptySections();

        if (targetBuffer.remaining() < meshes.size() * ChunkMeshPassStruct.size) {
            throw new IllegalStateException("triple buffer is too small ?!?");
        }


        for (var entry : Long2ReferenceMaps.fastIterable(meshes)) {
            RenderSection renderSection = entry.getValue();
            ChunkGraphicsState data = renderSection.getGraphicsState(pass);

            if (data == null) {
                continue;
            }


            ChunkSectionPos chunkSectionPos = ChunkSectionPos.from(entry.getLongKey());
            int chunkId = this.lastChunkId.getInt(chunkSectionPos);


            targetBuffer.putInt(chunkId);

            int indexOffset = (int) data.getIndexSegment().getOffset();
            int baseVertex = (int) data.getVertexSegment().getOffset();

            for (var dir : ModelQuadFacing.VALUES) {
                var bufferSlice = data.getModelPart(dir);
                if (bufferSlice == null) {
                    targetBuffer.putInt(0);
                    targetBuffer.putInt(0);
                    targetBuffer.putInt(0);
                } else {
                    meshCount++;

                    int firstIndex = indexOffset + bufferSlice.elementOffset;
                    int count = bufferSlice.elementCount;

                    targetBuffer.putInt(count);
                    targetBuffer.putInt(firstIndex);
                    targetBuffer.putInt(baseVertex);
                }
            }

            targetBuffer.putInt(0); // vec3 needs to be aligned to 16 bytes;
            targetBuffer.putInt(0);

            // default bounds atm
            // TODO: get smaller bounds
            targetBuffer.putFloat(cameraContext.getCameraXTranslation(chunkSectionPos.getMinX()) - .5f);
            targetBuffer.putFloat(cameraContext.getCameraYTranslation(chunkSectionPos.getMinY()) - .5f);
            targetBuffer.putFloat(cameraContext.getCameraZTranslation(chunkSectionPos.getMinZ()) - .5f);
            targetBuffer.putInt(0); // vec3 uses 4 floats

            targetBuffer.putFloat(cameraContext.getCameraXTranslation(chunkSectionPos.getMaxX()) + .5f);
            targetBuffer.putFloat(cameraContext.getCameraYTranslation(chunkSectionPos.getMaxY()) + .5f);
            targetBuffer.putFloat(cameraContext.getCameraZTranslation(chunkSectionPos.getMaxZ()) + .5f);
            targetBuffer.putInt(0); // vec3 uses 4 floats

        }

        return meshCount;
    }


    /*
    private GlTessellation getTessellation() {
        return this.tessellation;
    }

    private void setTessellation(GlTessellation tessellation) {
        this.tessellation = tessellation;
    }*/
}
