package me.jellysquid.mods.sodium.client.render.chunk;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.chunk.base.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderBuildTask;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class PrioritizableBuilder {
    private final Map<ChunkUpdateType, PriorityQueue<RenderSection>> rebuildQueues = new EnumMap<>(ChunkUpdateType.class);
    private final ChunkBuilder builder;

    private final Function<? super RenderSection, ? extends ChunkRenderBuildTask> createRebuildTask;

    public PrioritizableBuilder(ChunkBuilder builder, Function<? super RenderSection, ? extends ChunkRenderBuildTask> createRebuildTask) {
        this.builder = builder;
        this.createRebuildTask = createRebuildTask;

        for (ChunkUpdateType type : ChunkUpdateType.values()) {
            this.rebuildQueues.put(type, new ObjectArrayFIFOQueue<>());
        }
    }

    public void schedulePendingUpdates(RenderSection section) {
        PriorityQueue<RenderSection> queue = this.rebuildQueues.get(section.getPendingUpdate());

        if (queue.size() >= 32) {
            return;
        }

        queue.enqueue(section);
    }

    public void clear() {
        for (PriorityQueue<RenderSection> queue : this.rebuildQueues.values()) {
            queue.clear();
        }
    }

    PriorityQueue<CompletableFuture<? extends ChunkBuildResult>> submitRebuildTasks(ChunkUpdateType filterType) {
        int budget = filterType.isImportant() ? Integer.MAX_VALUE : this.builder.getSchedulingBudget();

        PriorityQueue<CompletableFuture<? extends ChunkBuildResult>> immediateFutures = new ObjectArrayFIFOQueue<>();
        PriorityQueue<RenderSection> queue = this.rebuildQueues.get(filterType);

        while (budget > 0 && !queue.isEmpty()) {
            RenderSection section = queue.dequeue();

            if (section.isDisposed()) {
                continue;
            }

            if (section.getPendingUpdate() != filterType) {
                SodiumClientMod.logger().warn("{} changed update type to {} while in queue for {}, skipping",
                        section, section.getPendingUpdate(), filterType);

                continue;
            }

            ChunkRenderBuildTask task = this.createRebuildTask.apply(section);
            CompletableFuture<?> future;

            if (filterType.isImportant()) {
                CompletableFuture<? extends ChunkBuildResult> immediateFuture = this.builder.schedule(task);
                immediateFutures.enqueue(immediateFuture);

                future = immediateFuture;
            } else {
                future = this.builder.scheduleDeferred(task);
            }

            section.onBuildSubmitted(future);

            budget--;
        }

        return immediateFutures;
    }
}
