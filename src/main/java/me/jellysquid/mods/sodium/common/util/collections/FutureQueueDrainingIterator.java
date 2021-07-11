package me.jellysquid.mods.sodium.common.util.collections;

import it.unimi.dsi.fastutil.PriorityQueue;
import me.jellysquid.mods.sodium.client.SodiumClientMod;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public class FutureQueueDrainingIterator<T> implements Iterator<T> {
    private final PriorityQueue<? extends CompletableFuture<? extends T>> queue;
    private T next = null;

    public FutureQueueDrainingIterator(PriorityQueue<? extends CompletableFuture<? extends T>> queue) {
        this.queue = queue;
    }

    @Override
    public boolean hasNext() {
        if (this.next != null) {
            return true;
        }

        this.findNext();

        return this.next != null;
    }

    private void findNext() {
        while (!this.queue.isEmpty()) {
            CompletableFuture<? extends T> future = this.queue.dequeue();

            try {
                this.next = future.join();
                return;
            } catch (CancellationException e) {
                SodiumClientMod.logger().warn("Future was cancelled: {}", future);
            }
        }
    }

    @Override
    public T next() {
        if (!this.hasNext()) {
            throw new NoSuchElementException();
        }

        T result = this.next;
        this.next = null;

        return result;
    }
}
