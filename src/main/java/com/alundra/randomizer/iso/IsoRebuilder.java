package com.alundra.randomizer.iso;

import com.alundra.randomizer.tools.RandomizerException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a modified copy of a raw CD image where some files have been replaced by new contents.
 * <p>
 * The original layout is preserved: every sector of the source image is copied in its original order (XA audio and
 * video streams included, untouched). A replaced file is written in place of its original extent; if it needs more
 * sectors than the original one, every following sector is shifted, which is the same layout mkpsxiso would produce
 * when rebuilding the image from a dump. Directory records, path tables and the volume descriptor are updated to
 * the new locations and sizes, and sector headers / error correction codes are regenerated where needed.
 */
public class IsoRebuilder {
    private static final int BATCH_SECTORS = 512;

    private final Iso9660 iso;
    private final CdImage image;
    private final Map<String, byte[]> replacements = new LinkedHashMap<>();

    public IsoRebuilder(Iso9660 iso) {
        this.iso = iso;
        this.image = iso.image();
    }

    /** Replaces the contents of an existing file inside the image. */
    public void replaceFile(String path, byte[] contents) {
        IsoEntry entry = iso.entry(path);
        if (entry.directory())
            throw new RandomizerException("Cannot replace directory '" + path + "'");
        if (entry.sectorCount() > 0 && CdSector.isForm2(image.readSector(entry.lba())))
            throw new RandomizerException("Cannot replace '" + path + "' since it is not a regular data file");
        replacements.put(Iso9660.normalize(path), contents);
    }

    /** A replaced file extent: [start, start + oldCount[ in the source image becomes newCount sectors. */
    private record Replacement(IsoEntry entry, byte[] contents, int newCount) {
        int start() { return entry.lba(); }
        int end() { return entry.lba() + entry.sectorCount(); }
        int delta() { return newCount - entry.sectorCount(); }
    }

    private List<Replacement> sortedReplacements() {
        List<Replacement> list = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : replacements.entrySet()) {
            IsoEntry entry = iso.entry(e.getKey());
            int neededSectors = (e.getValue().length + CdSector.DATA_SIZE - 1) / CdSector.DATA_SIZE;
            // Never shrink extents: a smaller file is padded inside its original space to avoid moving other files
            list.add(new Replacement(entry, e.getValue(), Math.max(neededSectors, entry.sectorCount())));
        }
        list.sort(Comparator.comparingInt(Replacement::start));
        for (int i = 1; i < list.size(); ++i)
            if (list.get(i).start() < list.get(i - 1).end())
                throw new RandomizerException("Overlapping files cannot be replaced at the same time");
        return list;
    }

    /** Maps a sector address of the source image to its address in the output image. */
    private static int mapLba(List<Replacement> sorted, int oldLba) {
        int shift = 0;
        for (Replacement r : sorted)
            if (oldLba >= r.end())
                shift += r.delta();
        return oldLba + shift;
    }

    /**
     * Writes the modified image to the given path, along with a matching ".cue" file.
     *
     * @return the path of the written cue sheet
     */
    public Path write(Path outputBin) {
        List<Replacement> sorted = sortedReplacements();
        Map<Integer, Replacement> replacementsByStart = new HashMap<>();
        for (Replacement r : sorted)
            replacementsByStart.put(r.start(), r);

        Map<Integer, byte[]> metadataSectors = buildMetadataSectors(sorted);

        try (FileChannel out = FileChannel.open(outputBin, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] readBuffer = new byte[BATCH_SECTORS * CdSector.SIZE];
            ByteBuffer writeBuffer = ByteBuffer.allocate(BATCH_SECTORS * CdSector.SIZE);
            int sectorCount = image.sectorCount();

            int oldLba = 0;
            int newLba = 0;
            while (oldLba < sectorCount) {
                Replacement replacement = replacementsByStart.get(oldLba);
                if (replacement != null) {
                    if (newLba != mapLba(sorted, oldLba))
                        throw new IllegalStateException("Sector mapping mismatch");
                    byte[] contents = replacement.contents();
                    for (int i = 0; i < replacement.newCount(); ++i) {
                        int offset = i * CdSector.DATA_SIZE;
                        int length = Math.max(0, Math.min(CdSector.DATA_SIZE, contents.length - offset));
                        boolean last = (i == replacement.newCount() - 1);
                        int submode = CdSector.SUBMODE_DATA | (last ? CdSector.SUBMODE_EOF | CdSector.SUBMODE_EOR : 0);
                        byte[] sector = CdSector.buildForm1(newLba, contents, offset, length, submode);
                        put(out, writeBuffer, sector);
                        newLba++;
                    }
                    oldLba = replacement.end();
                    continue;
                }

                // Copy a batch of untouched sectors, stopping before the next replaced extent
                int batchEnd = Math.min(sectorCount, oldLba + BATCH_SECTORS);
                for (Replacement r : sorted)
                    if (r.start() > oldLba && r.start() < batchEnd)
                        batchEnd = r.start();
                int count = batchEnd - oldLba;
                image.readSectors(oldLba, count, readBuffer);

                int shift = newLba - oldLba;
                for (int i = 0; i < count; ++i) {
                    byte[] sector = new byte[CdSector.SIZE];
                    System.arraycopy(readBuffer, i * CdSector.SIZE, sector, 0, CdSector.SIZE);

                    byte[] newUserData = metadataSectors.get(oldLba + i);
                    if (newUserData != null) {
                        System.arraycopy(newUserData, 0, sector, CdSector.DATA_OFFSET, CdSector.DATA_SIZE);
                        CdSector.setAddress(sector, newLba);
                        CdSector.finalizeForm1(sector);
                    } else if (shift != 0 && CdSector.hasSyncPattern(sector)) {
                        if (CdSector.mode(sector) != 2)
                            throw new RandomizerException("Unsupported sector mode " + CdSector.mode(sector) + " at LBA " + (oldLba + i));
                        // For Mode 2 sectors, EDC and ECC don't cover the header: updating the address is enough
                        CdSector.setAddress(sector, newLba);
                    }
                    put(out, writeBuffer, sector);
                    newLba++;
                }
                oldLba = batchEnd;
            }
            flush(out, writeBuffer);
        } catch (IOException e) {
            throw new RandomizerException("Could not write output disc image at '" + outputBin + "'", e);
        }

        return writeCue(outputBin);
    }

    private static void put(FileChannel out, ByteBuffer buffer, byte[] sector) throws IOException {
        if (buffer.remaining() < sector.length)
            flush(out, buffer);
        buffer.put(sector);
    }

    private static void flush(FileChannel out, ByteBuffer buffer) throws IOException {
        buffer.flip();
        while (buffer.hasRemaining())
            out.write(buffer);
        buffer.clear();
    }

    private static Path writeCue(Path outputBin) {
        String fileName = outputBin.getFileName().toString();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        Path cuePath = outputBin.resolveSibling(baseName + ".cue");
        String cue = "FILE \"" + fileName + "\" BINARY\r\n"
                + "  TRACK 01 MODE2/2352\r\n"
                + "    INDEX 01 00:00:00\r\n";
        try {
            Files.writeString(cuePath, cue, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new RandomizerException("Could not write cue sheet at '" + cuePath + "'", e);
        }
        return cuePath;
    }

    // ------------------------------------------------------------------------------------------------------
    //  Filesystem metadata update
    // ------------------------------------------------------------------------------------------------------

    /**
     * Builds the updated user data of every sector holding filesystem metadata (volume descriptor, path tables,
     * directories), keyed by their address in the source image.
     */
    private Map<Integer, byte[]> buildMetadataSectors(List<Replacement> sorted) {
        Map<Integer, byte[]> sectors = new HashMap<>();
        Map<Integer, Integer> newSizesByLba = new HashMap<>();
        for (Replacement r : sorted)
            newSizesByLba.put(r.start(), r.contents().length);

        // Primary Volume Descriptor
        byte[] pvd = iso.primaryVolumeDescriptor();
        Iso9660.writeIntBoth(pvd, 80, mapLba(sorted, iso.volumeSpaceSize()));
        int[] pathTables = iso.pathTableLbas();
        if (pathTables[0] != 0) Iso9660.writeIntLE(pvd, 140, mapLba(sorted, pathTables[0]));
        if (pathTables[1] != 0) Iso9660.writeIntLE(pvd, 144, mapLba(sorted, pathTables[1]));
        if (pathTables[2] != 0) Iso9660.writeIntBE(pvd, 148, mapLba(sorted, pathTables[2]));
        if (pathTables[3] != 0) Iso9660.writeIntBE(pvd, 152, mapLba(sorted, pathTables[3]));
        updateRecord(pvd, 156, sorted, newSizesByLba);
        sectors.put(Iso9660.PVD_LBA, pvd);

        // Path tables (L tables store little-endian LBAs, M tables big-endian LBAs)
        int pathTableSectors = (iso.pathTableSize() + CdSector.DATA_SIZE - 1) / CdSector.DATA_SIZE;
        for (int t = 0; t < 4; ++t) {
            if (pathTables[t] == 0)
                continue;
            boolean bigEndian = t >= 2;
            byte[] table = new byte[pathTableSectors * CdSector.DATA_SIZE];
            for (int s = 0; s < pathTableSectors; ++s)
                System.arraycopy(image.readUserData(pathTables[t] + s), 0, table, s * CdSector.DATA_SIZE, CdSector.DATA_SIZE);

            int offset = 0;
            while (offset < iso.pathTableSize()) {
                int nameLength = table[offset] & 0xFF;
                if (nameLength == 0)
                    break;
                int lba = bigEndian ? Iso9660.readIntBE(table, offset + 2) : Iso9660.readIntLE(table, offset + 2);
                int newLba = mapLba(sorted, lba);
                if (bigEndian)
                    Iso9660.writeIntBE(table, offset + 2, newLba);
                else
                    Iso9660.writeIntLE(table, offset + 2, newLba);
                offset += 8 + nameLength + (nameLength % 2);
            }

            for (int s = 0; s < pathTableSectors; ++s) {
                byte[] data = new byte[CdSector.DATA_SIZE];
                System.arraycopy(table, s * CdSector.DATA_SIZE, data, 0, CdSector.DATA_SIZE);
                sectors.put(pathTables[t] + s, data);
            }
        }

        // Directory records
        for (IsoEntry dir : iso.directories()) {
            for (int s = 0; s < dir.sectorCount(); ++s) {
                int sectorLba = dir.lba() + s;
                byte[] data = image.readUserData(sectorLba);
                int offset = 0;
                while (offset < CdSector.DATA_SIZE) {
                    int length = data[offset] & 0xFF;
                    if (length == 0)
                        break;
                    updateRecord(data, offset, sorted, newSizesByLba);
                    offset += length;
                }
                sectors.put(sectorLba, data);
            }
        }

        for (Replacement r : sorted)
            for (int lba = r.start(); lba < r.end(); ++lba)
                if (sectors.containsKey(lba))
                    throw new RandomizerException("A replaced file overlaps filesystem structures");

        return sectors;
    }

    private static void updateRecord(byte[] data, int offset, List<Replacement> sorted, Map<Integer, Integer> newSizesByLba) {
        int lba = Iso9660.readIntLE(data, offset + 2);
        Integer newSize = newSizesByLba.get(lba);
        boolean isDirectory = (data[offset + 25] & 0x02) != 0;
        if (newSize != null && !isDirectory)
            Iso9660.writeIntBoth(data, offset + 10, newSize);
        Iso9660.writeIntBoth(data, offset + 2, mapLba(sorted, lba));
    }
}
