package com.alundra.randomizer.iso;

/**
 * Helpers to build and inspect raw 2352-byte CD-ROM XA sectors (Mode 2), including EDC and ECC generation.
 * <p>
 * Layout of a Mode 2 Form 1 sector:
 * <pre>
 *   [0..11]      sync pattern (00 FF FF FF FF FF FF FF FF FF FF 00)
 *   [12..15]     header: minutes, seconds, frames (BCD), mode (2)
 *   [16..23]     subheader (4 bytes, repeated twice)
 *   [24..2071]   2048 bytes of user data
 *   [2072..2075] EDC (CRC32 variant over [16..2071])
 *   [2076..2351] ECC (Reed-Solomon P and Q parity, computed with the header zeroed)
 * </pre>
 */
public final class CdSector {
    public static final int SIZE = 2352;
    public static final int DATA_SIZE = 2048;
    public static final int DATA_OFFSET = 24;
    public static final int FORM2_DATA_SIZE = 2324;
    /** Number of sectors in the lead-in pregap, added to LBAs to get MSF addresses */
    public static final int PREGAP_SECTORS = 150;

    public static final int SUBMODE_EOR = 0x01;
    public static final int SUBMODE_DATA = 0x08;
    public static final int SUBMODE_FORM2 = 0x20;
    public static final int SUBMODE_EOF = 0x80;

    private static final int[] ECC_F_LUT = new int[256];
    private static final int[] ECC_B_LUT = new int[256];
    private static final int[] EDC_LUT = new int[256];

    static {
        for (int i = 0; i < 256; ++i) {
            int j = (i << 1) ^ ((i & 0x80) != 0 ? 0x11D : 0);
            ECC_F_LUT[i] = j & 0xFF;
            ECC_B_LUT[i ^ (j & 0xFF)] = i;
            int edc = i;
            for (int k = 0; k < 8; ++k)
                edc = (edc >>> 1) ^ ((edc & 1) != 0 ? 0xD8018001 : 0);
            EDC_LUT[i] = edc;
        }
    }

    private CdSector() {
    }

    public static boolean hasSyncPattern(byte[] sector) {
        if (sector[0] != 0 || sector[11] != 0)
            return false;
        for (int i = 1; i < 11; ++i)
            if (sector[i] != (byte) 0xFF)
                return false;
        return true;
    }

    public static int mode(byte[] sector) {
        return sector[15] & 0xFF;
    }

    public static int submode(byte[] sector) {
        return sector[18] & 0xFF;
    }

    public static boolean isForm2(byte[] sector) {
        return (submode(sector) & SUBMODE_FORM2) != 0;
    }

    private static int toBcd(int value) {
        return ((value / 10) << 4) | (value % 10);
    }

    private static int fromBcd(int value) {
        return ((value >> 4) & 0xF) * 10 + (value & 0xF);
    }

    /** Writes the sync pattern and the Mode 2 header (MSF address computed from the given LBA). */
    public static void writeHeader(byte[] sector, int lba) {
        sector[0] = 0;
        for (int i = 1; i < 11; ++i)
            sector[i] = (byte) 0xFF;
        sector[11] = 0;
        setAddress(sector, lba);
        sector[15] = 2;
    }

    /** Updates the MSF address inside the header of an existing sector. */
    public static void setAddress(byte[] sector, int lba) {
        int address = lba + PREGAP_SECTORS;
        sector[12] = (byte) toBcd(address / (60 * 75));
        sector[13] = (byte) toBcd((address / 75) % 60);
        sector[14] = (byte) toBcd(address % 75);
    }

    /** @return the LBA stored inside the header of the given sector */
    public static int getAddress(byte[] sector) {
        int minutes = fromBcd(sector[12] & 0xFF);
        int seconds = fromBcd(sector[13] & 0xFF);
        int frames = fromBcd(sector[14] & 0xFF);
        return (minutes * 60 + seconds) * 75 + frames - PREGAP_SECTORS;
    }

    public static int computeEdc(byte[] buffer, int offset, int length) {
        int edc = 0;
        for (int i = 0; i < length; ++i)
            edc = (edc >>> 8) ^ EDC_LUT[(edc ^ buffer[offset + i]) & 0xFF];
        return edc;
    }

    private static void computeEccBlock(byte[] sector, int srcOffset, int majorCount, int minorCount,
                                        int majorMult, int minorInc, int destOffset) {
        int size = majorCount * minorCount;
        for (int major = 0; major < majorCount; ++major) {
            int index = (major >> 1) * majorMult + (major & 1);
            int eccA = 0;
            int eccB = 0;
            for (int minor = 0; minor < minorCount; ++minor) {
                int temp = sector[srcOffset + index] & 0xFF;
                index += minorInc;
                if (index >= size)
                    index -= size;
                eccA ^= temp;
                eccB ^= temp;
                eccA = ECC_F_LUT[eccA];
            }
            eccA = ECC_B_LUT[ECC_F_LUT[eccA] ^ eccB];
            sector[destOffset + major] = (byte) eccA;
            sector[destOffset + major + majorCount] = (byte) (eccA ^ eccB);
        }
    }

    /** Generates the ECC (P then Q parity) of a Mode 2 Form 1 sector, computed with a zeroed header. */
    private static void generateEcc(byte[] sector) {
        byte[] savedAddress = {sector[12], sector[13], sector[14], sector[15]};
        sector[12] = sector[13] = sector[14] = sector[15] = 0;
        computeEccBlock(sector, 0xC, 86, 24, 2, 86, 0x81C);
        computeEccBlock(sector, 0xC, 52, 43, 86, 88, 0x8C8);
        System.arraycopy(savedAddress, 0, sector, 12, 4);
    }

    /** Recomputes EDC and ECC of a Mode 2 Form 1 sector (sync, header, subheader and data must already be set). */
    public static void finalizeForm1(byte[] sector) {
        int edc = computeEdc(sector, 0x10, 0x808);
        writeIntLE(sector, 0x818, edc);
        generateEcc(sector);
    }

    /** Recomputes the EDC of a Mode 2 Form 2 sector. */
    public static void finalizeForm2(byte[] sector) {
        int edc = computeEdc(sector, 0x10, 0x91C);
        writeIntLE(sector, 0x92C, edc);
    }

    /** Recomputes error detection / correction codes of a Mode 2 sector depending on its form. */
    public static void finalizeSector(byte[] sector) {
        if (isForm2(sector))
            finalizeForm2(sector);
        else
            finalizeForm1(sector);
    }

    /**
     * Builds a complete Mode 2 Form 1 sector.
     *
     * @param lba     the sector address
     * @param data    source buffer for user data (shorter data is padded with zeroes)
     * @param offset  offset of the user data inside the source buffer
     * @param length  number of bytes to take from the source buffer (at most 2048)
     * @param submode the subheader submode byte
     */
    public static byte[] buildForm1(int lba, byte[] data, int offset, int length, int submode) {
        byte[] sector = new byte[SIZE];
        writeHeader(sector, lba);
        sector[16] = sector[20] = 0;       // file number
        sector[17] = sector[21] = 0;       // channel number
        sector[18] = sector[22] = (byte) submode;
        sector[19] = sector[23] = 0;       // coding info
        if (length > 0)
            System.arraycopy(data, offset, sector, DATA_OFFSET, Math.min(length, DATA_SIZE));
        finalizeForm1(sector);
        return sector;
    }

    /** @return true if EDC and ECC of the given Mode 2 Form 1 sector are consistent with its contents */
    public static boolean checkForm1(byte[] sector) {
        byte[] copy = sector.clone();
        finalizeForm1(copy);
        return java.util.Arrays.equals(copy, sector);
    }

    private static void writeIntLE(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) value;
        buffer[offset + 1] = (byte) (value >>> 8);
        buffer[offset + 2] = (byte) (value >>> 16);
        buffer[offset + 3] = (byte) (value >>> 24);
    }
}
