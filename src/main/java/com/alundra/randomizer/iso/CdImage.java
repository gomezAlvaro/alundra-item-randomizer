package com.alundra.randomizer.iso;

import com.alundra.randomizer.tools.RandomizerException;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Read access to a raw (2352 bytes per sector) single-track CD image, such as a ".bin" file.
 */
public class CdImage implements Closeable {
    private final Path path;
    private final FileChannel channel;
    private final int sectorCount;

    public CdImage(Path path) {
        this.path = path;
        try {
            this.channel = FileChannel.open(path, StandardOpenOption.READ);
            long size = channel.size();
            if (size % CdSector.SIZE != 0)
                throw new RandomizerException("'" + path + "' is not a raw CD image (size is not a multiple of 2352 bytes)");
            this.sectorCount = (int) (size / CdSector.SIZE);
        } catch (IOException e) {
            throw new RandomizerException("Could not open disc image '" + path + "'", e);
        }
    }

    public Path path() {
        return path;
    }

    public int sectorCount() {
        return sectorCount;
    }

    /** Reads a full raw sector. */
    public byte[] readSector(int lba) {
        byte[] sector = new byte[CdSector.SIZE];
        readSectors(lba, 1, sector);
        return sector;
    }

    /** Reads several consecutive raw sectors into the given buffer. */
    public void readSectors(int lba, int count, byte[] buffer) {
        if (lba < 0 || lba + count > sectorCount)
            throw new RandomizerException("Tried to read sectors " + lba + " to " + (lba + count) + " outside of the disc image");
        ByteBuffer bb = ByteBuffer.wrap(buffer, 0, count * CdSector.SIZE);
        long position = (long) lba * CdSector.SIZE;
        try {
            while (bb.hasRemaining()) {
                int read = channel.read(bb, position + bb.position());
                if (read < 0)
                    throw new RandomizerException("Unexpected end of disc image");
            }
        } catch (IOException e) {
            throw new RandomizerException("Could not read disc image '" + path + "'", e);
        }
    }

    /** Reads the 2048 bytes of user data of a Mode 2 Form 1 sector. */
    public byte[] readUserData(int lba) {
        byte[] data = new byte[CdSector.DATA_SIZE];
        System.arraycopy(readSector(lba), CdSector.DATA_OFFSET, data, 0, CdSector.DATA_SIZE);
        return data;
    }

    /** Reads {@code size} bytes of user data stored in consecutive Mode 2 Form 1 sectors starting at {@code lba}. */
    public byte[] readFileData(int lba, int size) {
        byte[] result = new byte[size];
        int sectorsToRead = (size + CdSector.DATA_SIZE - 1) / CdSector.DATA_SIZE;
        final int batch = 256;
        byte[] buffer = new byte[batch * CdSector.SIZE];
        int written = 0;
        for (int i = 0; i < sectorsToRead; i += batch) {
            int count = Math.min(batch, sectorsToRead - i);
            readSectors(lba + i, count, buffer);
            for (int s = 0; s < count && written < size; ++s) {
                int length = Math.min(CdSector.DATA_SIZE, size - written);
                System.arraycopy(buffer, s * CdSector.SIZE + CdSector.DATA_OFFSET, result, written, length);
                written += length;
            }
        }
        return result;
    }

    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }
}
