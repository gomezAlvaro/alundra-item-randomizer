package com.alundra.randomizer.iso;

/**
 * A file or directory found inside an ISO 9660 filesystem.
 *
 * @param path          full path without version suffix and without leading slash (e.g. "DATA/DATAS.BIN"),
 *                      empty for the root directory
 * @param lba           first sector of the extent
 * @param size          size in bytes, as recorded in the directory record
 * @param directory     true if this entry is a directory
 * @param recordLba     sector holding the directory record describing this entry (-1 for the root directory, which
 *                      is described inside the Primary Volume Descriptor)
 * @param recordOffset  offset of the directory record inside the user data of {@code recordLba}
 */
public record IsoEntry(String path, int lba, int size, boolean directory, int recordLba, int recordOffset) {
    /** @return the number of 2048-byte sectors used by this entry's extent */
    public int sectorCount() {
        return (int) ((size + (long) CdSector.DATA_SIZE - 1) / CdSector.DATA_SIZE);
    }
}
