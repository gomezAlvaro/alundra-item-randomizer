package com.alundra.randomizer.iso;

import com.alundra.randomizer.tools.RandomizerException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A minimal read-only ISO 9660 filesystem parser working on top of a raw {@link CdImage}.
 */
public class Iso9660 {
    public static final int PVD_LBA = 16;

    private final CdImage image;
    private final Map<String, IsoEntry> entries = new LinkedHashMap<>();
    private final byte[] pvd;

    public Iso9660(CdImage image) {
        this.image = image;
        this.pvd = image.readUserData(PVD_LBA);

        String identifier = new String(pvd, 1, 5, StandardCharsets.US_ASCII);
        if ((pvd[0] & 0xFF) != 1 || !identifier.equals("CD001"))
            throw new RandomizerException("No ISO 9660 primary volume descriptor found inside the disc image");

        IsoEntry root = parseRecord(pvd, 156, "", -1);
        entries.put("", root);
        parseDirectories(root);
    }

    public CdImage image() {
        return image;
    }

    /** @return a copy of the Primary Volume Descriptor's 2048 bytes */
    public byte[] primaryVolumeDescriptor() {
        return pvd.clone();
    }

    public int volumeSpaceSize() {
        return readIntLE(pvd, 80);
    }

    public int pathTableSize() {
        return readIntLE(pvd, 132);
    }

    /** @return the LBAs of the (up to 4) path tables: L, optional L, M, optional M (0 when absent) */
    public int[] pathTableLbas() {
        return new int[]{readIntLE(pvd, 140), readIntLE(pvd, 144), readIntBE(pvd, 148), readIntBE(pvd, 152)};
    }

    /** @return all entries (root directory first), keyed by their uppercase path */
    public Map<String, IsoEntry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    public List<IsoEntry> directories() {
        List<IsoEntry> dirs = new ArrayList<>();
        for (IsoEntry entry : entries.values())
            if (entry.directory())
                dirs.add(entry);
        return dirs;
    }

    public IsoEntry entry(String path) {
        IsoEntry entry = entries.get(normalize(path));
        if (entry == null)
            throw new RandomizerException("File '" + path + "' could not be found inside the disc image");
        return entry;
    }

    public boolean exists(String path) {
        return entries.containsKey(normalize(path));
    }

    public byte[] readFile(String path) {
        IsoEntry entry = entry(path);
        if (entry.directory())
            throw new RandomizerException("'" + path + "' is a directory");
        return image.readFileData(entry.lba(), entry.size());
    }

    public static String normalize(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/"))
            normalized = normalized.substring(1);
        int versionIndex = normalized.indexOf(';');
        if (versionIndex >= 0)
            normalized = normalized.substring(0, versionIndex);
        return normalized.toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------------------------------------------

    private void parseDirectories(IsoEntry root) {
        Deque<IsoEntry> toVisit = new ArrayDeque<>();
        toVisit.add(root);
        while (!toVisit.isEmpty()) {
            IsoEntry dir = toVisit.poll();
            for (int s = 0; s < dir.sectorCount(); ++s) {
                int sectorLba = dir.lba() + s;
                byte[] data = image.readUserData(sectorLba);
                int offset = 0;
                while (offset < CdSector.DATA_SIZE) {
                    int length = data[offset] & 0xFF;
                    if (length == 0)
                        break; // Records never cross sector boundaries, the rest of this sector is padding
                    if (offset + length > CdSector.DATA_SIZE || length < 34)
                        throw new RandomizerException("Malformed directory record in sector " + sectorLba);

                    int nameLength = data[offset + 32] & 0xFF;
                    boolean isSelfOrParent = nameLength == 1 && (data[offset + 33] == 0 || data[offset + 33] == 1);
                    if (!isSelfOrParent) {
                        String name = recordName(data, offset);
                        String path = dir.path().isEmpty() ? name : dir.path() + "/" + name;
                        IsoEntry entry = parseRecord(data, offset, path, sectorLba);
                        entries.put(path, entry);
                        if (entry.directory())
                            toVisit.add(entry);
                    }
                    offset += length;
                }
            }
        }
    }

    static String recordName(byte[] data, int recordOffset) {
        int nameLength = data[recordOffset + 32] & 0xFF;
        String name = new String(data, recordOffset + 33, nameLength, StandardCharsets.ISO_8859_1);
        return normalize(name);
    }

    private static IsoEntry parseRecord(byte[] data, int offset, String path, int recordLba) {
        int lba = readIntLE(data, offset + 2);
        int size = readIntLE(data, offset + 10);
        boolean directory = (data[offset + 25] & 0x02) != 0;
        return new IsoEntry(path, lba, size, directory, recordLba, offset);
    }

    static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    static int readIntBE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    static void writeIntLE(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }

    static void writeIntBE(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    /** Writes a "both-endian" 32-bit value (little-endian copy followed by big-endian copy) as used by ISO 9660. */
    static void writeIntBoth(byte[] data, int offset, int value) {
        writeIntLE(data, offset, value);
        writeIntBE(data, offset + 4, value);
    }
}
