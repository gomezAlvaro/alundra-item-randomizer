package com.alundra.randomizer.patches;

import com.alundra.randomizer.Randomizer;
import com.alundra.randomizer.game.ItemCodes;
import com.alundra.randomizer.model.ItemSource;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.ByteArray;
import com.alundra.randomizer.tools.Json;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArchipelagoPatchTest {
    private static final String LOCAL_SOURCE = "Inoa: Chest in Lutas's house";
    private static final String REMOTE_SOURCE = "Overworld C2: Chest in Olen's house";
    private static final String UNLISTED_SOURCE = "Merrick's Shop: Item traded for 15 Gilded Falcons";

    private static Randomizer randomizer() {
        return Randomizer.fromArchipelago(Json.parse("""
                {"game": "Alundra", "format": 1, "seed_name": "1234567890", "player": 1, "player_name": "Luzbe",
                 "locations": [
                   {"name": "%s", "item": "Sword"},
                   {"name": "%s", "item": null}]}
                """.formatted(LOCAL_SOURCE, REMOTE_SOURCE)));
    }

    @Test
    void itemsComeFromTheArchipelagoPatchData() {
        Randomizer randomizer = randomizer();
        assertEquals(ItemCodes.ITEM_SWORD, randomizer.world().itemSourceByName(LOCAL_SOURCE).item().id());
        assertEquals(PatchArchipelago.ITEM_AP_PLACEHOLDER, randomizer.world().itemSourceByName(REMOTE_SOURCE).item().id());
        // Item sources the client cannot track keep their vanilla item
        ItemSource unlisted = randomizer.world().itemSourceByName(UNLISTED_SOURCE);
        assertSame(unlisted.vanillaItem(), unlisted.item());
    }

    @Test
    void theGameIsPatchedToTalkToTheClient() {
        Randomizer randomizer = randomizer();
        Map<String, byte[]> originals = PatchesTest.fakeGameFiles(0x80020000L);
        GameFiles files = randomizer.patchFiles(originals::get);

        BinaryFile datas = files.datas();
        for (long address : randomizer.world().itemSourceByName(LOCAL_SOURCE).addresses())
            assertEquals(ItemCodes.ITEM_SWORD, datas.getByte(address));
        for (long address : randomizer.world().itemSourceByName(REMOTE_SOURCE).addresses())
            assertEquals(PatchArchipelago.ITEM_AP_PLACEHOLDER, datas.getByte(address));

        // The mailbox is where the client expects it, and contains the names needed to log in
        ByteArray exe = ByteArray.wrap(files.exe().contents());
        long textStart = exe.getLongLE(0x18);
        long mailbox = 0x800 + PatchArchipelago.MAILBOX_ADDRESS - textStart;
        assertEquals(PatchArchipelago.MAILBOX_MAGIC, exe.getLongLE(mailbox));
        assertEquals("Luzbe", exe.getCString(mailbox + 0x60));
        assertEquals("1234567890", exe.getCString(mailbox + 0x80));
        assertTrue(PatchArchipelago.MAILBOX_ADDRESS + PatchArchipelago.MAILBOX_SIZE <= 0x8001F800L,
                "the mailbox must not overlap the area where overlay executables are loaded");

        // The placeholder has a readable name in the game's strings
        BinaryFile strings = files.file(GameFiles.GLOBAL_STRINGS);
        assertEquals("Archipelago item",
                strings.getCString(strings.getWordLE((512 + PatchArchipelago.ITEM_AP_PLACEHOLDER) * 2)));
    }
}
