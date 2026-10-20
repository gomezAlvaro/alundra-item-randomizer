package com.alundra.randomizer.iso;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Builds a tiny raw Mode 2 ISO 9660 image by hand, then checks the rebuilder output: file contents, shifted extents,
 * directory records, path tables, volume size, sector addresses and error correction codes.
 */
class IsoRebuilderTest {
    private static final int ROOT_LBA = 20;
    private static final int DATA_DIR_LBA = 21;
    private static final int FILE_A_LBA = 22;   // A.EXE, 3 sectors
    private static final int FILE_B_LBA = 25;   // DATA/B.BIN, 2 sectors (Form 2 stream, like video files)
    private static final int FILE_C_LBA = 27;   // DATA/C.BIN, 1 sector
    private static final int TOTAL_SECTORS = 30;

    private static byte[] record(String name, int lba, int size, boolean directory) {
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        int length = 33 + nameBytes.length;
        if (length % 2 == 1)
            length++;
        byte[] rec = new byte[length];
        rec[0] = (byte) length;
        Iso9660.writeIntBoth(rec, 2, lba);
        Iso9660.writeIntBoth(rec, 10, size);
        rec[25] = (byte) (directory ? 0x02 : 0x00);
        rec[28] = 1;
        rec[31] = 1;
        rec[32] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, rec, 33, nameBytes.length);
        return rec;
    }

    private static void put(byte[] sector, int offset, byte[] data) {
        System.arraycopy(data, 0, sector, offset, data.length);
    }

    private static Path buildImage(Path dir, byte[] fileA, byte[] fileB, byte[] fileC) throws IOException {
        byte[][] userData = new byte[TOTAL_SECTORS][CdSector.DATA_SIZE];

        // Primary volume descriptor
        byte[] pvd = userData[16];
        pvd[0] = 1;
        put(pvd, 1, "CD001".getBytes(StandardCharsets.US_ASCII));
        pvd[6] = 1;
        Iso9660.writeIntBoth(pvd, 80, TOTAL_SECTORS);
        Iso9660.writeIntBoth(pvd, 132, 22);
        Iso9660.writeIntLE(pvd, 140, 18);
        Iso9660.writeIntBE(pvd, 148, 19);
        put(pvd, 156, record("\0", ROOT_LBA, 2048, true));
        // Terminator
        userData[17][0] = (byte) 0xFF;
        put(userData[17], 1, "CD001".getBytes(StandardCharsets.US_ASCII));

        // Path tables: root (dir 1) and DATA (parent 1)
        for (int t = 0; t < 2; ++t) {
            byte[] table = userData[18 + t];
            boolean be = t == 1;
            table[0] = 1;
            if (be) Iso9660.writeIntBE(table, 2, ROOT_LBA); else Iso9660.writeIntLE(table, 2, ROOT_LBA);
            table[6] = be ? 0 : (byte) 1; table[7] = be ? (byte) 1 : 0;
            table[10] = 4;
            if (be) Iso9660.writeIntBE(table, 12, DATA_DIR_LBA); else Iso9660.writeIntLE(table, 12, DATA_DIR_LBA);
            table[16] = be ? 0 : (byte) 1; table[17] = be ? (byte) 1 : 0;
            put(table, 18, "DATA".getBytes(StandardCharsets.US_ASCII));
        }

        // Root directory
        int offset = 0;
        for (byte[] rec : new byte[][]{record("\0", ROOT_LBA, 2048, true), record("\1", ROOT_LBA, 2048, true),
                record("A.EXE;1", FILE_A_LBA, fileA.length, false), record("DATA", DATA_DIR_LBA, 2048, true)}) {
            put(userData[ROOT_LBA], offset, rec);
            offset += rec.length;
        }
        // DATA directory
        offset = 0;
        for (byte[] rec : new byte[][]{record("\0", DATA_DIR_LBA, 2048, true), record("\1", ROOT_LBA, 2048, true),
                record("B.BIN;1", FILE_B_LBA, fileB.length, false), record("C.BIN;1", FILE_C_LBA, fileC.length, false)}) {
            put(userData[DATA_DIR_LBA], offset, rec);
            offset += rec.length;
        }

        byte[] image = new byte[TOTAL_SECTORS * CdSector.SIZE];
        for (int lba = 0; lba < TOTAL_SECTORS; ++lba) {
            byte[] sector;
            if (lba == FILE_B_LBA || lba == FILE_B_LBA + 1) {
                // Form 2 sectors, as found in XA audio / STR video streams
                sector = new byte[CdSector.SIZE];
                CdSector.writeHeader(sector, lba);
                sector[18] = sector[22] = (byte) (CdSector.SUBMODE_FORM2 | 0x04);
                System.arraycopy(fileB, (lba - FILE_B_LBA) * 2048, sector, 24, 2048);
                CdSector.finalizeForm2(sector);
            } else {
                byte[] content = userData[lba];
                if (lba >= FILE_A_LBA && lba < FILE_A_LBA + 3)
                    content = Arrays.copyOfRange(fileA, (lba - FILE_A_LBA) * 2048, (lba - FILE_A_LBA + 1) * 2048);
                else if (lba == FILE_C_LBA)
                    content = Arrays.copyOf(fileC, 2048);
                sector = CdSector.buildForm1(lba, content, 0, 2048, CdSector.SUBMODE_DATA);
            }
            System.arraycopy(sector, 0, image, lba * CdSector.SIZE, CdSector.SIZE);
        }

        Path path = dir.resolve("test.bin");
        Files.write(path, image);
        return path;
    }

    @Test
    void sectorAddressesAndEccRoundTrip() {
        byte[] data = new byte[2048];
        new Random(1).nextBytes(data);
        byte[] sector = CdSector.buildForm1(123456, data, 0, data.length, CdSector.SUBMODE_DATA);
        assertTrue(CdSector.hasSyncPattern(sector));
        assertEquals(123456, CdSector.getAddress(sector));
        assertTrue(CdSector.checkForm1(sector));

        // Changing the address of a Mode 2 sector doesn't invalidate its error codes (they don't cover the header)
        CdSector.setAddress(sector, 42);
        assertEquals(42, CdSector.getAddress(sector));
        assertTrue(CdSector.checkForm1(sector));

        sector[100] ^= 1;
        assertFalse(CdSector.checkForm1(sector));
    }

    @Test
    void edcMatchesReferenceValue() {
        // EDC of the 2056 bytes [subheader + user data] of an all-zero Mode 2 Form 1 sector is 0
        byte[] zeros = new byte[0x808];
        assertEquals(0, CdSector.computeEdc(zeros, 0, zeros.length));
        // "123456789" check value of this CRC variant (reflected, poly 0x8001801B)
        byte[] check = "123456789".getBytes(StandardCharsets.US_ASCII);
        assertEquals(0x6EC2EDC4, CdSector.computeEdc(check, 0, check.length));
    }

    @Test
    void replacingFilesShiftsFollowingExtents() throws IOException {
        Path dir = Files.createTempDirectory("isotest");
        Random random = new Random(3);
        byte[] fileA = new byte[3 * 2048 - 10];
        byte[] fileB = new byte[2 * 2048];
        byte[] fileC = new byte[700];
        random.nextBytes(fileA);
        random.nextBytes(fileB);
        random.nextBytes(fileC);
        Path input = buildImage(dir, fileA, fileB, fileC);

        byte[] newA = new byte[5 * 2048 + 1]; // 6 sectors instead of 3
        random.nextBytes(newA);
        byte[] newC = new byte[100];          // smaller: stays in place
        random.nextBytes(newC);

        Path output = dir.resolve("out.bin");
        try (CdImage image = new CdImage(input)) {
            Iso9660 iso = new Iso9660(image);
            assertArrayEquals(fileA, iso.readFile("A.EXE"));
            assertArrayEquals(fileC, iso.readFile("/data/c.bin;1"));

            IsoRebuilder rebuilder = new IsoRebuilder(iso);
            rebuilder.replaceFile("A.EXE", newA);
            rebuilder.replaceFile("DATA/C.BIN", newC);
            assertThrows(RuntimeException.class, () -> rebuilder.replaceFile("DATA/B.BIN", new byte[10]));
            Path cue = rebuilder.write(output);
            assertTrue(Files.readString(cue).contains("MODE2/2352"));
        }

        try (CdImage image = new CdImage(output)) {
            assertEquals(TOTAL_SECTORS + 3, image.sectorCount());
            Iso9660 iso = new Iso9660(image);
            assertEquals(TOTAL_SECTORS + 3, iso.volumeSpaceSize());

            assertEquals(FILE_A_LBA, iso.entry("A.EXE").lba());
            assertEquals(newA.length, iso.entry("A.EXE").size());
            assertEquals(FILE_B_LBA + 3, iso.entry("DATA/B.BIN").lba());
            assertEquals(FILE_C_LBA + 3, iso.entry("DATA/C.BIN").lba());
            assertEquals(newC.length, iso.entry("DATA/C.BIN").size());

            assertArrayEquals(newA, iso.readFile("A.EXE"));
            assertArrayEquals(newC, iso.readFile("DATA/C.BIN"));

            // The Form 2 stream was moved untouched (apart from its header address)
            for (int i = 0; i < 2; ++i) {
                byte[] sector = image.readSector(FILE_B_LBA + 3 + i);
                assertTrue(CdSector.isForm2(sector));
                assertArrayEquals(Arrays.copyOfRange(fileB, i * 2048, (i + 1) * 2048), Arrays.copyOfRange(sector, 24, 24 + 2048));
            }

            // Every sector holds its own address, and every Form 1 sector has valid error codes
            for (int lba = 0; lba < image.sectorCount(); ++lba) {
                byte[] sector = image.readSector(lba);
                assertEquals(lba, CdSector.getAddress(sector), "address of sector " + lba);
                if (!CdSector.isForm2(sector))
                    assertTrue(CdSector.checkForm1(sector), "error codes of sector " + lba);
            }

            // Last sector of a replaced file is flagged as end of file
            int lastSectorOfA = FILE_A_LBA + 5;
            assertEquals(CdSector.SUBMODE_DATA | CdSector.SUBMODE_EOF | CdSector.SUBMODE_EOR,
                    CdSector.submode(image.readSector(lastSectorOfA)));
        }
    }
}
