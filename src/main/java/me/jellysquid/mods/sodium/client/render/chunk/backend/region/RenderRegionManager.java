package me.jellysquid.mods.sodium.client.render.chunk.backend.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.buffer.IndexedVertexData;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.*;
import me.jellysquid.mods.sodium.client.render.chunk.base.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.*;

public class RenderRegionManager implements RenderSectionContainer, SectionCuller.FrustumChecker {
    private final Long2ReferenceOpenHashMap<RenderRegion> regions = new Long2ReferenceOpenHashMap<>();
    private final Long2ReferenceMap<RegionalRenderSection> sections = new Long2ReferenceOpenHashMap<>();

    private final ChunkRenderer renderer;

    public RenderRegionManager(ChunkRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void updateVisibility(FrustumExtended frustum) {
        for (RenderRegion region : this.regions.values()) {
            if (!region.isEmpty()) {
                region.updateVisibility(frustum);
            }
        }
    }

    @Override
    public boolean getVisibility(RenderSection section, FrustumExtended frustum) {
        RenderRegionVisibility parentVisibility = this.getRegionForSection(section).getVisibility();
        return parentVisibility == RenderRegionVisibility.FULLY_VISIBLE ||
                parentVisibility == RenderRegionVisibility.VISIBLE &&
                        !section.getGraphInfo().isCulledByFrustum(frustum);
    }

    private RenderRegion getRegionForSection(RenderSection section) {
        // TODO: we could use generics to have a class `RenderSection` and a subclass `RegionalRenderSection`
        //       or add a map from RenderSection to RenderRegion
        return this.getRenderRegionForChunkSection(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    }

    @Override
    public void cleanup() {
        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.cleanup(commandList);
        }
    }

    private void cleanup(CommandList commandList) {
        Iterator<RenderRegion> it = this.regions.values()
                .iterator();

        while (it.hasNext()) {
            RenderRegion region = it.next();

            if (region.isEmpty()) {
                SodiumClientMod.logger().warn("Cleaning up a render region that should have been removed. PLEASE REPORT");

                region.deleteResources(commandList);

                it.remove();
            }
        }
    }

    public void upload(CommandList commandList, Iterator<? extends ChunkBuildResult> queue) {
        for (Map.Entry<RenderRegion, List<ChunkBuildResult>> entry : this.setupUploadBatches(queue).entrySet()) {
            RenderRegion region = entry.getKey();
            List<ChunkBuildResult> uploadQueue = entry.getValue();

            for (BlockRenderPass pass : BlockRenderPass.VALUES) {
                this.upload(commandList, region, pass, uploadQueue);
            }

            for (ChunkBuildResult result : uploadQueue) {
                result.render.onBuildFinished(result);

                result.delete();
            }
        }
    }

    private void upload(CommandList commandList, RenderRegion region, BlockRenderPass pass, List<ChunkBuildResult> results) {
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

        RenderRegion.RenderRegionArenas arenas = region.getOrCreateArenas(commandList, pass);

        boolean bufferChanged = arenas.vertexBuffers.upload(commandList, sectionUploads.stream().map(i -> i.vertexUpload));
        bufferChanged |= arenas.indexBuffers.upload(commandList, sectionUploads.stream().map(i -> i.indicesUpload));

        // If any of the buffers changed, the tessellation will need to be updated
        // Once invalidated the tessellation will be re-created on the next attempted use
        if (bufferChanged) {
            arenas.invalidateTessellation(commandList);
        }

        // Collect the upload results
        for (PendingSectionUpload upload : sectionUploads) {
            upload.section.setGraphicsState(pass, new ChunkGraphicsState(upload.vertexUpload.getResult(), upload.indicesUpload.getResult(), upload.meshData));
        }
    }

    private Map<RenderRegion, List<ChunkBuildResult>> setupUploadBatches(Iterator<? extends ChunkBuildResult> renders) {
        Map<RenderRegion, List<ChunkBuildResult>> map = new Reference2ObjectLinkedOpenHashMap<>();

        while (renders.hasNext()) {
            ChunkBuildResult result = renders.next();
            RenderSection render = result.render;

            if (!render.canAcceptBuildResults(result)) {
                result.delete();

                continue;
            }

            RenderRegion region = this.regions.get(RenderRegion.getRegionKeyForChunk(render.getChunkX(), render.getChunkY(), render.getChunkZ()));

            if (region == null) {
                throw new NullPointerException("Couldn't find region for chunk: " + render);
            }

            List<ChunkBuildResult> uploadQueue = map.computeIfAbsent(region, k -> new ArrayList<>());
            uploadQueue.add(result);
        }

        return map;
    }


    private RenderRegion createRegionForChunk(int x, int y, int z) {
        long key = RenderRegion.getRegionKeyForChunk(x, y, z);
        RenderRegion region = this.regions.get(key);

        if (region == null) {
            this.regions.put(key, region = RenderRegion.createRegionForChunk(this.renderer, x, y, z));
        }

        return region;
    }

    private RenderRegion getRenderRegionForChunkSection(int x, int y, int z) {
        long key = RenderRegion.getRegionKeyForChunk(x, y, z);
        return this.regions.get(key);
    }

    public void delete(CommandList commandList) {
        for (RenderRegion region : this.regions.values()) {
            region.deleteResources(commandList);
        }

        this.regions.clear();
    }

    public Collection<RenderRegion> getLoadedRegions() {
        return this.regions.values();
    }


    private static final class PendingSectionUpload {
        private final RenderSection section;
        private final ChunkMeshData meshData;

        private final GlBufferArena.PendingUpload vertexUpload;
        private final GlBufferArena.PendingUpload indicesUpload;

        private PendingSectionUpload(RenderSection section, ChunkMeshData meshData, GlBufferArena.PendingUpload vertexUpload, GlBufferArena.PendingUpload indicesUpload) {
            this.section = section;
            this.meshData = meshData;
            this.vertexUpload = vertexUpload;
            this.indicesUpload = indicesUpload;
        }
    }

    @Override
    public RegionalRenderSection createSection(RenderSectionManager renderSectionManager, int x, int y, int z) {

        // Get region for the render section
        RenderRegion region = this.createRegionForChunk(x, y, z);

        // add new render section to the region
        RegionalRenderSection renderSection = new RegionalRenderSection(renderSectionManager, x, y, z, region);
        region.addChunk(renderSection);

        // store a direct lookup to the render section
        this.sections.put(ChunkSectionPos.asLong(x, y, z), renderSection);

        return renderSection;
    }

    @Override
    public RegionalRenderSection remove(int x, int y, int z) {
        RegionalRenderSection section = this.sections.remove(ChunkSectionPos.asLong(x, y, z));

        if (section != null) {

            // Shouldn't be null
            RenderRegion region = this.getRenderRegionForChunkSection(x, y, z);
            region.removeChunk(section);
        }

        return section;
    }

    @Override
    public RegionalRenderSection get(int x, int y, int z) {
        return this.sections.get(ChunkSectionPos.asLong(x, y, z));
    }

    @Override
    public int getTotalSectionCount() {
        int sum = 0;

        for (RenderRegion renderRegion : this.regions.values()) {
            int chunkCount = renderRegion.getChunkCount();
            sum += chunkCount;
        }

        return sum;
    }

    @Override
    public Collection<? extends String> getMemoryDebugStrings() {
        List<String> list = new ArrayList<>();

        Iterator<RenderRegion.RenderRegionArenas> it = this.regions.values()
                .stream()
                .flatMap(i -> Arrays.stream(BlockRenderPass.values())
                        .map(i::getArenas))
                .filter(Objects::nonNull)
                .iterator();

        int count = 0;

        long used = 0;
        long allocated = 0;

        while (it.hasNext()) {
            RenderRegion.RenderRegionArenas arena = it.next();
            used += arena.getUsedMemory();
            allocated += arena.getAllocatedMemory();

            count++;
        }

        String format = String.format("Chunk Arenas: %d/%d MiB (%d buffers)", toMib(used), toMib(allocated), count);
        list.add(format);

        return list;

    }

    // TODO make util function
    private static long toMib(long x) {
        return x / 1024L / 1024L;
    }
}
