package com.alundra.randomizer.patches;

import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.PsxExeFile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The set of game files that patches can read and edit, loaded lazily from the disc image (or from a folder).
 * Every file accessed through this class is considered modified and will be written back into the output image.
 */
public class GameFiles {
    public static final String DATAS_BIN = "DATA/DATAS.BIN";
    public static final String MAIN_EXE = "ALUN_CD.EXE";
    public static final String BOOT_EXE = "SLUS_005.53";
    public static final String END_EXE = "END.EXE";
    public static final String CLOSING_EXE = "CLOSING.EXE";
    public static final String GLOBAL_STRINGS = "DATA/ETC_USA.R";
    public static final String BALANCE_BIN = "DATA/BALANCE.BIN";

    private final Function<String, byte[]> loader;
    private final Map<String, BinaryFile> files = new LinkedHashMap<>();

    /** @param loader a function returning the original contents of a file given its path inside the disc */
    public GameFiles(Function<String, byte[]> loader) {
        this.loader = loader;
    }

    public BinaryFile datas() {
        return file(DATAS_BIN);
    }

    public PsxExeFile exe() {
        return (PsxExeFile) files.computeIfAbsent(MAIN_EXE, path -> new PsxExeFile(path, loader.apply(path)));
    }

    /** @return the given file, loading it from the disc on first access */
    public BinaryFile file(String path) {
        if (path.equals(MAIN_EXE))
            return exe();
        return files.computeIfAbsent(path, p -> new BinaryFile(p, loader.apply(p)));
    }

    /** Replaces a file's contents entirely. */
    public void replace(String path, byte[] contents) {
        files.put(path, new BinaryFile(path, contents));
    }

    /** @return all files that were accessed or replaced, keyed by path */
    public Map<String, BinaryFile> modifiedFiles() {
        return Collections.unmodifiableMap(files);
    }
}
