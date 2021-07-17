package me.jellysquid.mods.sodium.client.render.chunk.backend.advanced;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.buffer.IndexedVertexData;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultRenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.base.BiBufferArenas;
import me.jellysquid.mods.sodium.client.render.chunk.base.PendingSectionUpload;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSectionContainer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.common.util.ListUtil;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class CombinedRenderSectionContainer implements RenderSectionContainer {
    private BiBufferArenas arenas;
    private final Long2ReferenceOpenHashMap<RenderSection> sections = new Long2ReferenceOpenHashMap<>();
    private final ChunkVertexType vertexType;
    private final int renderDistance;

    private static CombinedRenderSectionContainer IM_SORRY_I_JUST_WANT_STUFF_TO_RENDER_ATM;

    public CombinedRenderSectionContainer(ChunkVertexType vertexType, int renderDistance) {
        this.vertexType = vertexType;
        this.renderDistance = renderDistance;
        IM_SORRY_I_JUST_WANT_STUFF_TO_RENDER_ATM = this;
    }

    public static BiBufferArenas getTheFArenas(CommandList commandList){
        return IM_SORRY_I_JUST_WANT_STUFF_TO_RENDER_ATM.getOrCreateArenas(commandList);
    }

    public BiBufferArenas getOrCreateArenas(CommandList commandList) {
        if (this.arenas == null) {
            // TODO: make arena bigger based on
            this.arenas = new BiBufferArenas(commandList, this.vertexType, 2048L * this.renderDistance * this.renderDistance);
        }

        return this.arenas;
    }

    @Override
    public RenderSection createSection(DefaultRenderSectionManager renderSectionManager, int x, int y, int z) {
        RenderSection renderSection = new RenderSection(renderSectionManager, x, y, z);

        // TODO: throw if not null?
        this.sections.put(ChunkSectionPos.asLong(x, y, z), renderSection);

        return renderSection;
    }

    @Override
    public RenderSection remove(int x, int y, int z) {
        RenderSection renderSection = this.sections.remove(ChunkSectionPos.asLong(x, y, z));

        if (renderSection != null) {
            renderSection.delete();
        }

        return renderSection;
    }

    @Override
    public RenderSection get(int x, int y, int z) {
        return this.sections.get(ChunkSectionPos.asLong(x, y, z));
    }

    @Override
    public void upload(CommandList commandList, Iterator<? extends ChunkBuildResult> chunkBuildResults) {
        List<ChunkBuildResult> uploadQueue = ListUtil.fromIterator(chunkBuildResults);

        for (BlockRenderPass pass : BlockRenderPass.VALUES) {
            this.upload(commandList, pass, uploadQueue);
        }

        for (ChunkBuildResult result : uploadQueue) {
            result.render.onBuildFinished(result);

            result.delete();
        }
    }

    // TODO remove a bunch of duplication
    private void upload(CommandList commandList, BlockRenderPass pass, List<ChunkBuildResult> results) {
        List<PendingSectionUpload> sectionUploads = new ArrayList<>();

        for (ChunkBuildResult result : results) {
            ChunkGraphicsState graphics = result.render.setGraphicsState(pass, null);

            // De-allocate all storage for data we're about to replace
            // This will allow it to be cheaply re-allocated just below
            if (graphics != null) {
                graphics.delete();
            }

            ChunkMeshData meshData = result.getMesh(pass);

            if (meshData != null) {
                IndexedVertexData vertexData = meshData.getVertexData();

                sectionUploads.add(new PendingSectionUpload(result.render, meshData,
                        new GlBufferArena.PendingUpload(vertexData.vertexBuffer()),
                        new GlBufferArena.PendingUpload(vertexData.indexBuffer())));
            }
        }

        // If we have nothing to upload, abort!
        if (sectionUploads.isEmpty()) {
            return;
        }

        BiBufferArenas arenas = this.getOrCreateArenas(commandList);

        boolean bufferChanged = arenas.vertexBuffers.upload(commandList, sectionUploads.stream().map(PendingSectionUpload::vertexUpload));
        bufferChanged |= arenas.indexBuffers.upload(commandList, sectionUploads.stream().map(PendingSectionUpload::indicesUpload));

        // If any of the buffers changed, the tessellation will need to be updated
        // Once invalidated the tessellation will be re-created on the next attempted use
        if (bufferChanged) {
            arenas.invalidateTessellation(commandList);
        }

        // Collect the upload results
        for (PendingSectionUpload upload : sectionUploads) {
            upload.section().setGraphicsState(pass, new ChunkGraphicsState(upload.vertexUpload().getResult(), upload.indicesUpload().getResult(), upload.meshData()));
        }
    }

    @Override
    public void cleanup() {

    }

    @Override
    public void delete(CommandList commandList) {
        this.sections.values().forEach(RenderSection::delete);
    }

    @Override
    public int getTotalSectionCount() {
        return this.sections.size();
    }

    @Override
    public Collection<? extends String> getMemoryDebugStrings() {
        List<String> list = new ArrayList<>();


        if(this.arenas == null){
            list.add("Chunk Arenas: Awaiting creation");
        } else {
            list.add(String.format(
                    "Chunk Arenas: %d/%d MiB",
                    toMib(this.arenas.getUsedMemory()),
                    toMib(this.arenas.getAllocatedMemory())));
        }


        return list;

    }

    // TODO make util function
    private static long toMib(long x) {
        return x / 1024L / 1024L;
    }
}
