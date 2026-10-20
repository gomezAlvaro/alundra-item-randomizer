package com.alundra.randomizer.tools;

import java.util.List;

public final class StringTools {
    private StringTools() {
    }

    /**
     * Replaces the first occurrence of {@code from} by {@code to} inside {@code str}.
     *
     * @throws RandomizerException if {@code from} cannot be found (which means the game data is not what we expect)
     */
    public static String replaceFirst(String str, String from, String to) {
        int start = str.indexOf(from);
        if (start < 0)
            throw new RandomizerException("Could not find string '" + from + "' to replace inside '" + str + "'");
        return str.substring(0, start) + to + str.substring(start + from.length());
    }

    public static String join(List<String> words, String junction) {
        return String.join(junction, words);
    }
}
