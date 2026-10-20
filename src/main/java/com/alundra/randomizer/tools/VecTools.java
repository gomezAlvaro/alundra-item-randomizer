package com.alundra.randomizer.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * List helpers, most notably a deterministic shuffle that reproduces the original randomizer's algorithm
 * (repeatedly picking a uniformly random remaining element) on top of {@link MersenneTwister}.
 */
public final class VecTools {
    private VecTools() {
    }

    private static int uniformInteger(long number, int rangeLow, int rangeHigh) {
        double proportion = (double) number / (1.0 + 4294967295.0);
        long range = (long) rangeHigh - rangeLow + 1;
        return (int) ((long) (proportion * range) + rangeLow);
    }

    public static <T> void shuffle(List<T> list, MersenneTwister rng) {
        if (list.isEmpty())
            return;

        List<T> elems = new ArrayList<>(list.size());
        while (list.size() > 1) {
            int randomPos = uniformInteger(rng.next(), 0, list.size() - 1);
            elems.add(list.remove(randomPos));
        }
        elems.add(list.get(0));

        list.clear();
        list.addAll(elems);
    }

    /** Removes the first element equal to the given element. */
    public static <T> void eraseFirst(List<T> list, T elem) {
        for (int i = 0; i < list.size(); ++i) {
            if (Objects.equals(list.get(i), elem)) {
                list.remove(i);
                return;
            }
        }
        throw new IllegalStateException("Element to erase was not found in list");
    }

    public static <T> int count(List<T> list, T elem) {
        int count = 0;
        for (T t : list)
            if (Objects.equals(t, elem))
                count++;
        return count;
    }
}
