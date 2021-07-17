package me.jellysquid.mods.sodium.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public final class ListUtil {
    public static <T> void updateList(Collection<T> collection, Collection<T> before, Collection<T> after) {
        if (!before.isEmpty()) {
            collection.removeAll(before);
        }

        if (!after.isEmpty()) {
            collection.addAll(after);
        }
    }

    public static <T> List<T> fromIterator(Iterator<? extends T> iterator) {
        List<T> list = new ArrayList<>();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return list;
    }
}
