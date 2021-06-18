package me.jellysquid.mods.sodium.common.util;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class IterableIdTable<T> extends IdTable<T> {
    private final IntSet usedIds;

    public IterableIdTable(int capacity) {
        super(capacity);
        this.usedIds = new IntOpenHashSet(capacity);
    }

    @Override
    public int add(T element) {
        int id = super.add(element);
        this.usedIds.add(id);
        return id;
    }

    @Override
    public void remove(int id) {
        super.remove(id);
        this.usedIds.remove(id);
    }

    public IntSet getUsedIds() {
        return this.usedIds;
    }
}
