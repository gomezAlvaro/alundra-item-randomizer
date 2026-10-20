package com.alundra.randomizer.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An in-memory binary file, identified by a name (usually its path inside the disc image).
 */
public class BinaryFile extends ByteArray {
    private final String name;

    public BinaryFile(String name, byte[] bytes) {
        super(1);
        this.name = name;
        this.data = bytes;
        this.size = bytes.length;
    }

    public static BinaryFile load(Path path) {
        try {
            return new BinaryFile(path.getFileName().toString(), Files.readAllBytes(path));
        } catch (IOException e) {
            throw new RandomizerException("Could not open binary file '" + path + "'", e);
        }
    }

    public String name() {
        return name;
    }

    /** @return the file contents as they should be written to disc */
    public byte[] contents() {
        return toByteArray();
    }

    public void saveAs(Path outputPath) {
        try {
            Files.write(outputPath, contents());
        } catch (IOException e) {
            throw new RandomizerException("Could not output binary file at '" + outputPath + "'", e);
        }
    }

    public long checksum() {
        long checksum = 0;
        for (int i = 0; i < size; ++i)
            checksum += data[i] & 0xFF;
        return checksum;
    }
}
