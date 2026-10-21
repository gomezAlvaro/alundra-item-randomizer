package com.alundra.randomizer;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.game.Item;
import com.alundra.randomizer.model.ItemSource;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BitstreamReader;
import com.alundra.randomizer.tools.BitstreamWriter;
import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.MersenneTwister;
import com.alundra.randomizer.tools.RandomizerException;
import com.alundra.randomizer.tools.VecTools;
import com.alundra.randomizer.tools.JsonNode;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.alundra.randomizer.game.ItemCodes.*;

/**
 * All settings driving a randomization: game patching settings, randomization settings and the seed.
 * Options can be read from a preset JSON file, or decoded from a permalink.
 */
public class RandomizerOptions {
    /** Version byte embedded in permalinks (kept identical to the original randomizer v1.2 for compatibility) */
    public static final int PERMALINK_VERSION = 12;
    /** Permalink version carrying the "ignore logic" and "Magyscar requires Bomb" flags */
    public static final int PERMALINK_VERSION_EXTENDED = 13;

    // ------------- Game patching settings -------------

    /** If true, the japanese version's game balance is used instead of the US one (mostly enemy HP and damage) */
    private boolean originalGameBalance = true;
    /** If true, megaliths are activated on game start (no need to do Berue's memory minigame) */
    private boolean megalithsEnabledOnStart = true;
    /** If true, all gates in Lake Shrine are open right away, letting the player go straight to the final boss */
    private boolean skipLastDungeon = true;
    /** If true, each boots only give their own effect instead of also giving all previous tiers effects */
    private boolean splitBootsEffects = true;
    /** If true, tiered equipment is replaced by "upgrade" items giving the next tier for that category */
    private boolean progressiveItems = true;
    /** The amount of times you need to die in order to be able to get King Snow's item */
    private int kingSnowDeathCount = 20;
    private int startingHealth = 10;
    private int startingMana = 0;
    private int startingGold = 0;
    /** Casino win counts required on each minigame in order to obtain the second reward */
    private int casinoWinCountForLastReward = 4;

    // ------------- Randomization settings -------------

    /** The seed of the random number generator, determining all of the randomization */
    private long seed = 0;
    /** If false, only the settings will be written in the generation log, not the full contents of item sources */
    private boolean allowSpoilerLog = true;
    /** The number of crests that have to be found to access Lake Shrine */
    private int crestCount = 7;
    /** The amount of each item that will be placed inside ItemSources during randomization, indexed by item ID */
    private int[] itemsDistribution = new int[ITEM_COUNT];
    /** Items owned by the player on game start */
    private final List<Integer> startingInventory = new ArrayList<>();
    /** Item sources that will always contain the same item (keys are ItemSource IDs, values are Item IDs) */
    private Map<Integer, Integer> fixedItemSources = new TreeMap<>();
    /** Item sources that will never contain an item required for progression */
    private final List<Integer> itemSourcesWithoutProgression = new ArrayList<>();
    /**
     * If true, items are placed as plandoed (fixed item sources) and the rest of the pool is shuffled without any
     * logic: the seed is not required to be completable
     */
    private boolean ignoreLogic = false;
    /** If true, entering Magyscar logically requires Bombs, since it cannot be left without them (false = original v1.2 logic) */
    private boolean magyscarRequiresBomb = true;

    private RandomizerOptions() {
    }

    /** Builds options from a parsed preset JSON (a "permalink" key inside the preset is also supported). */
    public static RandomizerOptions fromPreset(JsonNode presetJson, long seed, GameData gameData, RandomizerWorld world) {
        RandomizerOptions options = new RandomizerOptions();
        options.seed = seed & 0xFFFFFFFFL;
        options.applyJson(presetJson, gameData, world);
        options.validate();
        return options;
    }

    public static RandomizerOptions fromPermalink(String permalink) {
        RandomizerOptions options = new RandomizerOptions();
        options.parsePermalink(permalink);
        options.validate();
        return options;
    }

    // ------------------------------------------------------------------------------------------------------

    public Map<String, Object> toJson(GameData gameData, RandomizerWorld world) {
        Map<String, Object> json = Json.object();

        Map<String, Object> game = Json.child(json, "gameSettings");
        game.put("originalGameBalance", originalGameBalance);
        game.put("megalithsEnabledOnStart", megalithsEnabledOnStart);
        game.put("skipLastDungeon", skipLastDungeon);
        game.put("kingSnowDeathCount", kingSnowDeathCount);
        game.put("splitBootsEffects", splitBootsEffects);
        game.put("progressiveItems", progressiveItems);
        game.put("startingHealth", startingHealth);
        game.put("startingMana", startingMana);
        game.put("startingGold", startingGold);
        game.put("casinoWinCountForLastReward", casinoWinCountForLastReward);

        Map<String, Object> rando = Json.child(json, "randomizerSettings");
        rando.put("allowSpoilerLog", allowSpoilerLog);
        if (ignoreLogic)
            rando.put("ignoreLogic", true);
        if (magyscarRequiresBomb)
            rando.put("magyscarRequiresBomb", true);

        List<Object> startingInventoryNames = new ArrayList<>();
        for (int itemId : startingInventory)
            startingInventoryNames.add(gameData.item(itemId).name());
        rando.put("startingInventory", startingInventoryNames);

        rando.put("crestCount", crestCount);

        Map<String, Object> distribution = new TreeMap<>();
        for (int i = 0; i < itemsDistribution.length; ++i)
            if (itemsDistribution[i] > 0)
                distribution.put(gameData.item(i).name(), itemsDistribution[i]);
        rando.put("itemsDistribution", distribution);

        Map<String, Object> fixed = Json.child(rando, "fixedItemSources");
        for (Map.Entry<Integer, Integer> entry : fixedItemSources.entrySet())
            fixed.put(world.itemSource(entry.getKey()).prettyName(), gameData.item(entry.getValue()).name());

        List<Object> withoutProgression = Json.childArray(rando, "itemSourcesWithoutProgression");
        for (int sourceId : itemSourcesWithoutProgression)
            withoutProgression.add(world.itemSource(sourceId).prettyName());

        return json;
    }

    private void applyGameSettingsJson(JsonNode json) {
        for (Map.Entry<String, JsonNode> entry : Json.fields(json)) {
            JsonNode value = entry.getValue();
            switch (entry.getKey()) {
                case "originalGameBalance" -> originalGameBalance = value.asBoolean();
                case "megalithsEnabledOnStart" -> megalithsEnabledOnStart = value.asBoolean();
                case "skipLastDungeon" -> skipLastDungeon = value.asBoolean();
                case "kingSnowDeathCount" -> kingSnowDeathCount = value.asInt() & 0xFF;
                case "splitBootsEffects" -> splitBootsEffects = value.asBoolean();
                case "progressiveItems" -> progressiveItems = value.asBoolean();
                case "startingHealth" -> startingHealth = value.asInt() & 0xFF;
                case "startingMana" -> startingMana = value.asInt() & 0xFF;
                case "startingGold" -> startingGold = value.asInt() & 0xFFFF;
                case "casinoWinCountForLastReward" -> casinoWinCountForLastReward = value.asInt() & 0xFF;
                default -> throw new RandomizerException("Unknown key '" + entry.getKey() + "' in preset game settings JSON");
            }
        }
    }

    private void applyRandomizerSettingsJson(JsonNode json, GameData gameData, RandomizerWorld world) {
        for (Map.Entry<String, JsonNode> entry : Json.fields(json)) {
            JsonNode value = entry.getValue();
            switch (entry.getKey()) {
                case "allowSpoilerLog" -> allowSpoilerLog = value.asBoolean();
                case "ignoreLogic" -> ignoreLogic = value.asBoolean();
                case "magyscarRequiresBomb" -> magyscarRequiresBomb = value.asBoolean();
                case "crestCount" -> crestCount = value.asInt() & 0xFF;
                // "itemsDistributions" is the (misspelled) key written by the original randomizer's spoiler logs
                case "itemsDistribution", "itemsDistributions" -> {
                    for (Map.Entry<String, JsonNode> item : Json.fields(value))
                        itemsDistribution[gameData.item(item.getKey()).id()] = item.getValue().asInt() & 0xFF;
                }
                case "startingInventory" -> {
                    for (JsonNode itemName : value)
                        startingInventory.add(gameData.item(itemName.asText()).id());
                }
                case "fixedItemSources" -> {
                    for (Map.Entry<String, JsonNode> fixed : Json.fields(value)) {
                        ItemSource source = world.itemSource(fixed.getKey());
                        Item item = gameData.item(fixed.getValue().asText());
                        fixedItemSources.put(world.itemSourceId(source), item.id());
                    }
                }
                case "itemSourcesWithoutProgression" -> {
                    for (JsonNode sourceName : value)
                        itemSourcesWithoutProgression.add(world.itemSourceId(world.itemSource(sourceName.asText())));
                }
                default -> throw new RandomizerException("Unknown key '" + entry.getKey() + "' in preset randomizer settings JSON");
            }
        }
    }

    private void applyJson(JsonNode json, GameData gameData, RandomizerWorld world) {
        if (json.has("permalink")) {
            parsePermalink(json.get("permalink").asText());
            return;
        }

        for (Map.Entry<String, JsonNode> entry : Json.fields(json)) {
            switch (entry.getKey()) {
                case "seed" -> seed = entry.getValue().asLong() & 0xFFFFFFFFL;
                case "randomizerSettings" -> applyRandomizerSettingsJson(entry.getValue(), gameData, world);
                case "gameSettings" -> applyGameSettingsJson(entry.getValue());
                // Keys written in spoiler logs, ignored so that a spoiler log can be used as a preset
                case "hashSentence", "hints", "itemSources", "playthrough" -> { }
                default -> throw new RandomizerException("Unknown key '" + entry.getKey() + "' in preset JSON");
            }
        }
    }

    private void validate() {
        if (crestCount > 7)
            throw new RandomizerException("Crest count cannot be above 7");
        if (startingHealth == 0 || startingHealth > 50)
            throw new RandomizerException("Starting health must be between 1 and 50");
        if (startingMana > 4)
            throw new RandomizerException("Starting mana cannot be above 4");
        if (startingGold > 9999)
            throw new RandomizerException("Starting gold cannot be above 9999");
        if (casinoWinCountForLastReward < 1 || casinoWinCountForLastReward > 4)
            throw new RandomizerException("Casino win count for last reward must be between 1 and 4");
        if (!startingInventory.contains(ITEM_DAGGER))
            throw new RandomizerException("Starting inventory must at least contain the Dagger");
        if (!startingInventory.contains(ITEM_CLOTH_ARMOR))
            throw new RandomizerException("Starting inventory must at least contain the Cloth Armor");
        if (!startingInventory.contains(ITEM_SHORT_BOOTS))
            throw new RandomizerException("Starting inventory must at least contain the Short Boots");
    }

    // ------------------------------------------------------------------------------------------------------

    private static final List<String> HASH_WORDS = List.of(
            "Dagger", "Sword", "Fiend", "Blade", "Holy", "Hunters", "Bow", "Willow", "Spirit", "Wand", "Legend", "Iron",
            "Steel", "Flail", "Ice", "Cloth", "Armor", "Ancient", "Silver", "Short", "Boots", "Long", "Merman",
            "Charm", "Spring", "Bean", "Sand", "Cape", "Aqua", "Broken", "Bomb", "Herbs", "Strength", "Elixyr", "Magic",
            "Wonder", "Essence", "Tonic", "Earth", "Water", "Fire", "Wind", "Scroll", "Book", "Ring", "Armlet",
            "Recovery", "Secret", "Pass", "Power", "Glove", "Elevator", "Key", "Crest", "Ruby", "Sapphire", "Chest",
            "Topaz", "Agate", "Garnet", "Emerald", "Diamond", "Gilder", "Sluice", "Bouquet", "Small", "Letter", "Tree",
            "Gem", "Zolist", "Stone", "Gilded", "Falcon", "Magic", "Seed", "Runes", "Elna", "Curious", "Life", "Vessel",
            "Dreamwalker", "Dream", "Nightmare", "Inoa", "Torla", "Murgg", "Crystal", "Megalith", "Crypt", "Manor", "Desert",
            "Despair", "Cliff", "Madness", "Shrine", "Lake", "Berue", "Casino", "Underground", "Waterway", "Gazeck",
            "Lair", "Tarn", "Coal", "Mine", "Sara", "Coastal", "Cave", "Reptile", "Magyscar", "Sanctuary", "Fairy", "Pond",
            "Golem", "Dragon", "Alundra", "Meia", "Giles", "Lutas", "Merrick", "Jess", "Olga", "Oak", "Nava", "Ronan",
            "Melzas", "Lars", "Bonaire", "Nadia", "Kline", "Wendell", "Olen", "Nirude", "Zazan", "Wilda", "Nestus",
            "Zorgia", "Elene", "Lurvy", "Kohei", "Vul", "Jeal", "Uma", "Mountain", "Bergus", "Roulette", "Cephas", "Stenia");

    /** @return four words identifying a seed (generated from the seed only) */
    public List<String> hashWords() {
        List<String> words = new ArrayList<>(HASH_WORDS);
        VecTools.shuffle(words, new MersenneTwister(seed));
        return words.subList(0, 4);
    }

    public String hashSentence() {
        return String.join(" ", hashWords());
    }

    public String permalink() {
        BitstreamWriter bitpack = new BitstreamWriter();

        // Version 13 only differs from 12 by a trailing "ignore logic" flag, so permalinks stay compatible when unused
        boolean extended = ignoreLogic || magyscarRequiresBomb;
        bitpack.packSmall(extended ? PERMALINK_VERSION_EXTENDED : PERMALINK_VERSION);
        bitpack.packUInt32(seed);
        bitpack.pack(allowSpoilerLog);

        bitpack.pack(originalGameBalance);
        bitpack.pack(megalithsEnabledOnStart);
        bitpack.pack(skipLastDungeon);
        bitpack.pack(splitBootsEffects);
        bitpack.pack(progressiveItems);

        bitpack.packSmall(kingSnowDeathCount);
        bitpack.packSmall(crestCount);
        bitpack.packSmall(startingHealth);
        bitpack.packSmall(startingMana);
        bitpack.packSmall(startingGold);
        bitpack.packSmall(casinoWinCountForLastReward);

        bitpack.packSmallArray(itemsDistribution);
        bitpack.packSmallMap(fixedItemSources);
        bitpack.packSmallVector(itemSourcesWithoutProgression);
        bitpack.packSmallVector(startingInventory);
        if (extended) {
            bitpack.pack(ignoreLogic);
            bitpack.pack(magyscarRequiresBomb);
        }

        return "a" + Base64.getEncoder().encodeToString(bitpack.bytes()) + "/";
    }

    private void parsePermalink(String permalink) {
        permalink = permalink.trim();
        if (!permalink.startsWith("a") || !permalink.endsWith("/") || permalink.length() < 3)
            throw new RandomizerException("This permalink is malformed, please make sure you copied the full permalink string.");

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(permalink.substring(1, permalink.length() - 1));
        } catch (IllegalArgumentException e) {
            throw new RandomizerException("This permalink is malformed, please make sure you copied the full permalink string.");
        }

        BitstreamReader bitpack = new BitstreamReader(bytes);

        int version = bitpack.unpackUInt8();
        if (version != PERMALINK_VERSION && version != PERMALINK_VERSION_EXTENDED)
            throw new RandomizerException("This permalink comes from an incompatible version (" + version + ").");

        seed = bitpack.unpackUInt32();
        allowSpoilerLog = bitpack.unpackBool();

        originalGameBalance = bitpack.unpackBool();
        megalithsEnabledOnStart = bitpack.unpackBool();
        skipLastDungeon = bitpack.unpackBool();
        splitBootsEffects = bitpack.unpackBool();
        progressiveItems = bitpack.unpackBool();

        kingSnowDeathCount = bitpack.unpackUInt8();
        crestCount = bitpack.unpackUInt8();
        startingHealth = bitpack.unpackUInt8();
        startingMana = bitpack.unpackUInt8();
        startingGold = bitpack.unpackUInt16();
        casinoWinCountForLastReward = bitpack.unpackUInt8();

        itemsDistribution = bitpack.unpackUInt8Array(ITEM_COUNT);
        fixedItemSources = bitpack.unpackUInt16ToUInt8Map();
        itemSourcesWithoutProgression.clear();
        itemSourcesWithoutProgression.addAll(bitpack.unpackUInt16Vector());
        startingInventory.clear();
        startingInventory.addAll(bitpack.unpackUInt8Vector());
        // Version 12 permalinks predate these flags and use the original logic
        boolean extended = version == PERMALINK_VERSION_EXTENDED;
        ignoreLogic = extended && bitpack.unpackBool();
        magyscarRequiresBomb = extended && bitpack.unpackBool();
    }

    // ------------------------------------------------------------------------------------------------------

    public long seed() { return seed; }
    public boolean allowSpoilerLog() { return allowSpoilerLog; }
    public boolean ignoreLogic() { return ignoreLogic; }
    public boolean magyscarRequiresBomb() { return magyscarRequiresBomb; }
    public int[] itemsDistribution() { return itemsDistribution; }
    public List<Integer> startingInventory() { return Collections.unmodifiableList(startingInventory); }

    public boolean originalGameBalance() { return originalGameBalance; }
    public boolean megalithsEnabledOnStart() { return megalithsEnabledOnStart; }
    public boolean skipLastDungeon() { return skipLastDungeon; }
    public boolean splitBootsEffects() { return splitBootsEffects; }
    public boolean progressiveItems() { return progressiveItems; }
    public int crestCount() { return crestCount; }
    public int kingSnowDeathCount() { return kingSnowDeathCount; }
    public int startingHealth() { return startingHealth; }
    public int startingMana() { return startingMana; }
    public int startingGold() { return startingGold; }
    public int casinoWinCountForLastReward() { return casinoWinCountForLastReward; }

    public Map<Integer, Integer> fixedItemSources() { return Collections.unmodifiableMap(fixedItemSources); }
    public List<Integer> itemSourcesWithoutProgression() { return Collections.unmodifiableList(itemSourcesWithoutProgression); }
}
