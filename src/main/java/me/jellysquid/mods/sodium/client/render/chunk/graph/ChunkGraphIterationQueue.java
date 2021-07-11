package me.jellysquid.mods.sodium.client.render.chunk.graph;

import net.minecraft.util.math.Direction;

import java.util.Arrays;

public class ChunkGraphIterationQueue {
    private ChunkGraphInfo[] sections;
    private Direction[] directions;

    private int pos;
    private int capacity;

    public ChunkGraphIterationQueue() {
        this(4096);
    }

    public ChunkGraphIterationQueue(int capacity) {
        this.sections = new ChunkGraphInfo[capacity];
        this.directions = new Direction[capacity];

        this.capacity = capacity;
    }

    public void add(ChunkGraphInfo section, Direction direction) {
        int i = this.pos++;

        if (i == this.capacity) {
            this.resize();
        }

        this.sections[i] = section;
        this.directions[i] = direction;
    }

    private void resize() {
        this.capacity *= 2;

        this.sections = Arrays.copyOf(this.sections, this.capacity);
        this.directions = Arrays.copyOf(this.directions, this.capacity);
    }

    public ChunkGraphInfo getSection(int i) {
        return this.sections[i];
    }

    public Direction getDirection(int i) {
        return this.directions[i];
    }

    public void clear() {
        this.pos = 0;
    }

    public int size() {
        return this.pos;
    }
}
