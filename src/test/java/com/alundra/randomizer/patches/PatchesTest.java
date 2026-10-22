package com.alundra.randomizer.patches;

import com.alundra.randomizer.Randomizer;
import com.alundra.randomizer.game.MapCodes;
import com.alundra.randomizer.game.RoomStrings;
import com.alundra.randomizer.model.ItemSource;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.ByteArray;
import com.alundra.randomizer.tools.Json;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs every patch on synthetic game files having just enough structure (room string tables, sprite tables,
 * executable header, global strings) and checks their main effects.
 */
class PatchesTest {
    private static final int ROOM_AREA = 0x6800000;
    private static final int ROOM_BLOCK = 0x2000;
    private static final int MAP_COUNT = 490;
    private static final int STRINGS_PER_ROOM = 100;

    private static final Map<Integer, Map<Integer, String>> REQUIRED_STRINGS = Map.of(
            MapCodes.MAP_OVERWORLD_B3, Map.of(0, "talk Legendary\\NSword yes", 3, "you get the Legendary Sword! ok"),
            MapCodes.MAP_OVERWORLD_E1, Map.of(34, "receive the Topaz Crest now", 36, "and the Aqua Cape too"),
            MapCodes.MAP_OVERWORLD_E1_VARIANT, Map.of(34, "receive the Topaz Crest now", 36, "and the Aqua Cape too"),
            MapCodes.MAP_CASINO_ROULETTE, Map.of(44, "win a Life Vessel", 24, "or a Gilded Falcon"),
            MapCodes.MAP_CASINO_SHOOTING_RANGE, Map.of(20, "win a Life Vessel", 24, "or a Gilded Falcon"),
            MapCodes.MAP_CASINO_SLIME_ARENA, Map.of(80, "win a Life Vessel", 81, "a fantastic Gilded\\NFalcon! wow", 24, "or a Gilded Falcon"),
            MapCodes.MAP_432, Map.of(70, "behold the Diamond Crest falls"));

    private static byte[] randomBytes(int size, long seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    private static byte[] fakeDatas() {
        ByteArray datas = ByteArray.wrap(randomBytes(0x6C00000, 42));
        for (int m = 0; m <= MAP_COUNT; ++m)
            datas.setLongLE(0x28 + m * 4L, ROOM_AREA + (long) m * ROOM_BLOCK);
        for (int m = 0; m < MAP_COUNT; ++m) {
            long room = ROOM_AREA + (long) m * ROOM_BLOCK;
            datas.setLongLE(room + 0x18, 0x40);
            long table = room + 0x40;
            int offset = (STRINGS_PER_ROOM + 1) * 2;
            for (int i = 0; i < STRINGS_PER_ROOM; ++i) {
                String str = REQUIRED_STRINGS.getOrDefault(m, Map.of()).getOrDefault(i, "R" + m + " S" + i);
                datas.setWordLE(table + i * 2L, offset);
                datas.setBytes(table + offset, str.getBytes(StandardCharsets.ISO_8859_1));
                datas.setByte(table + offset + str.length(), 0);
                offset += str.length() + 1;
            }
            datas.setWordLE(table + STRINGS_PER_ROOM * 2L, 0xFFFF);
        }
        for (int i = 0; i < 0x100; ++i)
            datas.setLongLE(0x830 + i * 4L, 0x1000 + i * 0x10L);
        return datas.toByteArray();
    }

    private static byte[] fakeGlobalStrings() {
        ByteArray file = new ByteArray();
        ByteArray strings = new ByteArray();
        for (int i = 0; i < 0x400; ++i) {
            byte[] str = ("String" + i).getBytes(StandardCharsets.US_ASCII);
            file.addWordLE(0x800 + strings.size());
            strings.addBytes(str);
            strings.addByte(0);
        }
        file.addBytes(strings);
        return file.toByteArray();
    }

    static Map<String, byte[]> fakeGameFiles() {
        return fakeGameFiles(0x80010000L);
    }

    static Map<String, byte[]> fakeGameFiles(long exeTextAddress) {
        Map<String, byte[]> files = new HashMap<>();
        files.put(GameFiles.DATAS_BIN, fakeDatas());
        byte[] exe = randomBytes(0xB8000, 7);
        ByteArray exeArray = ByteArray.wrap(exe);
        exeArray.setLongLE(0x18, exeTextAddress);
        exeArray.setLongLE(0x1C, exe.length - 0x800);
        files.put(GameFiles.MAIN_EXE, exe);
        files.put(GameFiles.BOOT_EXE, randomBytes(0x7000, 8));
        files.put(GameFiles.END_EXE, randomBytes(0x2000, 9));
        files.put(GameFiles.CLOSING_EXE, randomBytes(0x10800, 10));
        files.put(GameFiles.GLOBAL_STRINGS, fakeGlobalStrings());
        files.put(GameFiles.BALANCE_BIN, randomBytes(0x3000, 12));
        return files;
    }

    @Test
    void allPatchesApplyAndHaveTheirMainEffects() {
        Randomizer randomizer = Randomizer.fromPreset(Json.parseResource("/presets/default.json"), 2024)
                .output(new PrintStream(OutputStream.nullOutputStream()));
        randomizer.randomize();

        Map<String, byte[]> originals = fakeGameFiles();
        GameFiles files = randomizer.patchFiles(originals::get);

        // Item sources contents are written at their addresses
        BinaryFile datas = files.datas();
        for (ItemSource source : randomizer.world().itemSources())
            for (long address : source.addresses())
                assertEquals(source.item().id(), datas.getByte(address), source.name());

        // Hints are written in characters' dialogues
        String jessHint = randomizer.world().hintSource("jess").text();
        String jessDialogue = new RoomStrings(MapCodes.MAP_JESS_HOUSE_RANDOMIZER, datas).string(0).replace("\\N", " ").replace("\\A", " ");
        assertTrue(jessDialogue.contains(jessHint.split(" ")[0]), jessDialogue);

        // King Snow's dialogue names the item he really gives
        String kingSnowItem = randomizer.world().itemSource(
                "Overworld B3: King Snow reward for dying a specified amount of times (Legend Sword)").item().name();
        assertTrue(new RoomStrings(MapCodes.MAP_OVERWORLD_B3, datas).string(3).contains(kingSnowItem));

        // The executable grew by 0x1000 bytes (0x800 reserved + injected code) and its header follows
        byte[] exe = files.exe().contents();
        assertEquals(0xB8000 + 0x1000, exe.length);
        ByteArray exeArray = ByteArray.wrap(exe);
        assertEquals(0x80010000L - 0x1000, exeArray.getLongLE(0x18));
        assertEquals(exe.length - 0x800, exeArray.getLongLE(0x1C));
        // Starting HP (addiu $a0, $zero, 10) at its original offset, shifted by the expansion
        assertEquals(0x2404000AL, exeArray.getLongLE(0x12590 + 0x1000));

        // Global strings: chapter names and randomizer item names
        BinaryFile strings = files.file(GameFiles.GLOBAL_STRINGS);
        assertEquals("Randomizer", strings.getCString(strings.getWordLE(0)));
        assertEquals("Sword upgrade", strings.getCString(strings.getWordLE((512 + 0x0B) * 2)));

        // Balance file is replaced by the original japanese balance
        assertEquals(11171, files.file(GameFiles.BALANCE_BIN).size());

        // Videos are disabled in boot executable
        assertEquals(0, files.file(GameFiles.BOOT_EXE).getLong(0x5DDC));
    }
}
