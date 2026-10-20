package com.alundra.randomizer.logic;

import com.alundra.randomizer.Randomizer;
import com.alundra.randomizer.RandomizerOptions;
import com.alundra.randomizer.game.Item;
import com.alundra.randomizer.model.HintSource;
import com.alundra.randomizer.model.ItemSource;
import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.JsonNode;
import com.alundra.randomizer.tools.RandomizerException;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;

import static com.alundra.randomizer.game.ItemCodes.*;
import static org.junit.jupiter.api.Assertions.*;

class RandomizationTest {
    private static final PrintStream QUIET = new PrintStream(OutputStream.nullOutputStream());

    private static JsonNode defaultPreset() {
        return Json.parseResource("/presets/default.json");
    }

    private static Randomizer generate(JsonNode preset, long seed) {
        Randomizer randomizer = Randomizer.fromPreset(preset, seed).output(QUIET);
        randomizer.randomize();
        return randomizer;
    }

    @Test
    void generatedWorldsAreCompletableAndFullyFilled() {
        for (long seed = 1; seed <= 30; ++seed) {
            Randomizer randomizer = generate(defaultPreset(), seed);

            for (ItemSource source : randomizer.world().itemSources())
                assertNotNull(source.item(), "empty item source " + source.name() + " for seed " + seed);

            WorldSolver solver = new WorldSolver(randomizer.world());
            assertTrue(solver.tryToSolve(randomizer.world().spawnNode(), randomizer.world().endNode(),
                    randomizer.world().startingInventory()), "seed " + seed + " is not completable");

            for (HintSource hint : randomizer.world().hintSources().values())
                assertFalse(hint.text().isEmpty(), "hint " + hint.name() + " is empty");

            @SuppressWarnings("unchecked")
            Map<String, Object> playthrough = (Map<String, Object>) randomizer.spoilerJson().get("playthrough");
            assertFalse(playthrough.isEmpty());
        }
    }

    @Test
    void sameSeedGivesSameWorld() {
        String first = Json.dump(generate(defaultPreset(), 123456789L).spoilerJson());
        String second = Json.dump(generate(defaultPreset(), 123456789L).spoilerJson());
        String other = Json.dump(generate(defaultPreset(), 987654321L).spoilerJson());
        assertEquals(first, second);
        assertNotEquals(first, other);
    }

    @Test
    void permalinkRoundTrip() {
        Randomizer original = generate(defaultPreset(), 42);
        RandomizerOptions options = original.options();

        Randomizer fromPermalink = Randomizer.fromPermalink(options.permalink()).output(QUIET);
        fromPermalink.randomize();

        assertEquals(options.seed(), fromPermalink.options().seed());
        assertEquals(options.permalink(), fromPermalink.options().permalink());
        assertEquals(Json.dump(original.spoilerJson()), Json.dump(fromPermalink.spoilerJson()));
    }

    @Test
    void knownPermalinkFromOriginalRandomizerIsDecoded() {
        RandomizerOptions options = RandomizerOptions.fromPermalink(
                "aGiMhIuX8QL1pJJJLqqS+ktJUD0UKEIfSSSSVJVX/YkJBlJVBmXBT+gCCiDA5B2D0AgYBBCIJBoA=/");
        assertEquals("Sluice Earth Crypt Spirit", options.hashSentence());
        assertEquals(7, options.crestCount());
        assertTrue(options.progressiveItems());
        assertEquals(1, options.casinoWinCountForLastReward());
    }

    @Test
    void malformedPermalinksAreRejected() {
        assertThrows(RandomizerException.class, () -> RandomizerOptions.fromPermalink("hello"));
        assertThrows(RandomizerException.class, () -> RandomizerOptions.fromPermalink("a!!!/"));
        assertThrows(RandomizerException.class, () -> RandomizerOptions.fromPermalink("aAA==/"));
    }

    @Test
    void startingInventoryItemsAreUsableByLogic() {
        // With a Bomb in the starting inventory and no Bomb in the item pool, the world must still be completable
        // (the original C++ solver ignored the starting inventory and could not generate such a seed).
        String preset = """
                {
                    "randomizerSettings": {
                        "startingInventory": ["Dagger", "Cloth Armor", "Short Boots", "Bomb"],
                        "itemsDistribution": %s
                    }
                }
                """.formatted(distributionWithoutBomb());
        Randomizer randomizer = generate(Json.parse(preset), 5);
        for (ItemSource source : randomizer.world().itemSources())
            assertNotEquals(ITEM_BOMB, source.item().id());
        assertNotNull(randomizer.spoilerJson().get("playthrough"));
    }

    private static String distributionWithoutBomb() {
        JsonNode distribution = defaultPreset().get("randomizerSettings").get("itemsDistribution");
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, JsonNode> entry : Json.fields(distribution)) {
            if (entry.getKey().equals("Bomb"))
                continue;
            if (sb.length() > 1)
                sb.append(',');
            sb.append('"').append(entry.getKey()).append("\": ").append(entry.getValue().asInt());
        }
        return sb.append('}').toString();
    }

    @Test
    void crestCountControlsCrestsInPool() {
        String preset = """
                {
                    "randomizerSettings": {
                        "crestCount": 2,
                        "startingInventory": ["Dagger", "Cloth Armor", "Short Boots"],
                        "itemsDistribution": %s
                    }
                }
                """.formatted(defaultDistribution());
        Randomizer randomizer = generate(Json.parse(preset), 77);
        int crests = 0;
        for (ItemSource source : randomizer.world().itemSources()) {
            int id = source.item().id();
            if (id >= ITEM_RUBY_CREST && id <= ITEM_DIAMOND_CREST)
                crests++;
        }
        assertEquals(2, crests);
        assertEquals(2, randomizer.gameData().usedCrests().size());
    }

    private static String defaultDistribution() {
        JsonNode distribution = defaultPreset().get("randomizerSettings").get("itemsDistribution");
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, JsonNode> entry : Json.fields(distribution)) {
            if (sb.length() > 1)
                sb.append(',');
            sb.append('"').append(entry.getKey()).append("\": ").append(entry.getValue().asInt());
        }
        return sb.append('}').toString();
    }

    @Test
    void invalidPresetsAreRejected() {
        assertThrows(RandomizerException.class, () -> Randomizer.fromPreset(Json.parse("""
                { "randomizerSettings": { "startingInventory": ["Cloth Armor", "Short Boots"] } }"""), 1));
        assertThrows(RandomizerException.class, () -> Randomizer.fromPreset(Json.parse("""
                { "gameSettings": { "unknownSetting": true } }"""), 1));
        assertThrows(RandomizerException.class, () -> Randomizer.fromPreset(Json.parse("""
                { "randomizerSettings": { "startingInventory": ["Dagger", "Cloth Armor", "Short Boots", "Excalibur"] } }"""), 1));
    }

    @Test
    void progressiveItemsReplaceTieredEquipment() {
        Randomizer randomizer = generate(defaultPreset(), 9);
        for (ItemSource source : randomizer.world().itemSources()) {
            Item item = source.item();
            assertNotEquals(ITEM_SWORD, item.id());
            assertNotEquals(ITEM_IRON_FLAIL, item.id());
            assertNotEquals(ITEM_HUNTERS_BOW, item.id());
        }
    }
}
