package com.alundra.randomizer;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.io.SpoilerWriter;
import com.alundra.randomizer.iso.CdImage;
import com.alundra.randomizer.iso.Iso9660;
import com.alundra.randomizer.iso.IsoRebuilder;
import com.alundra.randomizer.logic.WorldShuffler;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.patches.GameFiles;
import com.alundra.randomizer.patches.PatchArchipelago;
import com.alundra.randomizer.patches.Patches;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.RandomizerException;
import com.alundra.randomizer.tools.JsonNode;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * High-level API of the randomizer: builds a randomized world from options, and patches a disc image accordingly.
 * <p>
 * Typical use:
 * <pre>
 *   Randomizer randomizer = Randomizer.fromPreset(presetJson, seed);
 *   randomizer.randomize();
 *   randomizer.patchImage(inputBin, outputBin);
 *   String spoiler = Json.dump(randomizer.spoilerJson());
 * </pre>
 */
public class Randomizer {
    /** Expected size of an Alundra (USA) v1.1 disc image */
    public static final long EXPECTED_IMAGE_SIZE = 600359760L;

    private final GameData gameData;
    private final RandomizerWorld world;
    private final RandomizerOptions options;
    private PrintStream out = System.out;
    private WorldShuffler shuffler;
    private Map<String, Object> spoilerJson;
    /** Not null when the world's items come from an Archipelago multiworld instead of from the shuffler */
    private PatchArchipelago archipelago = null;

    private Randomizer(GameData gameData, RandomizerWorld world, RandomizerOptions options) {
        this.gameData = gameData;
        this.world = world;
        this.options = options;
        gameData.applyOptions(options);
        world.applyOptions(options, gameData);
    }

    public static Randomizer fromPreset(JsonNode presetJson, long seed) {
        GameData gameData = new GameData();
        RandomizerWorld world = new RandomizerWorld(gameData);
        RandomizerOptions options = RandomizerOptions.fromPreset(presetJson, seed, gameData, world);
        return new Randomizer(gameData, world, options);
    }

    /**
     * Builds the game for an Archipelago multiworld. The patch data lists the items of the item sources that belong
     * to the Archipelago world: {@code {"locations": [{"name": <item source name>, "item": <item name or null>}]}}.
     * A null item means the location holds an item of another world, shown with the sprite of the optional "sprite" item
     * (an Alundra item of another player). {@code "exe_hooks": false} only reserves the mailbox in the
     * executable, leaving the rest of the Archipelago code to the world. Item sources that are not listed keep their vanilla item.
     */
    public static Randomizer fromArchipelago(JsonNode patchJson) {
        GameData gameData = new GameData();
        RandomizerWorld world = new RandomizerWorld(gameData);
        RandomizerOptions options = RandomizerOptions.fromPreset(Json.parseResource("/presets/archipelago.json"), 0, gameData, world);
        Randomizer randomizer = new Randomizer(gameData, world, options);
        randomizer.archipelago = new PatchArchipelago(patchJson.get("player_name").asText(), patchJson.get("seed_name").asText(),
                patchJson.get("exe_hooks") == null || patchJson.get("exe_hooks").asBoolean());

        for (var source : world.itemSources())
            source.item(source.vanillaItem());
        for (JsonNode location : patchJson.get("locations")) {
            var source = world.itemSourceByName(location.get("name").asText());
            JsonNode item = location.get("item");
            source.item(item == null || item.isNull()
                    ? gameData.item(PatchArchipelago.ITEM_AP_PLACEHOLDER)
                    : gameData.item(item.asText()));
            // An item of another player's Alundra world keeps its own sprite
            JsonNode sprite = location.get("sprite");
            if (sprite != null && !sprite.isNull())
                source.spriteItem(gameData.item(sprite.asText()));
        }
        return randomizer;
    }

    public static Randomizer fromPermalink(String permalink) {
        GameData gameData = new GameData();
        RandomizerWorld world = new RandomizerWorld(gameData);
        RandomizerOptions options = RandomizerOptions.fromPermalink(permalink);
        return new Randomizer(gameData, world, options);
    }

    public Randomizer output(PrintStream out) {
        this.out = out;
        return this;
    }

    /** Places items and hints inside the world, and builds the spoiler log. */
    public void randomize() {
        spoilerJson = Json.object();
        spoilerJson.put("permalink", options.permalink());
        spoilerJson.put("hashSentence", options.hashSentence());
        spoilerJson.putAll(options.toJson(gameData, world));

        shuffler = new WorldShuffler(world, gameData, options, out);
        shuffler.randomizeItems();
        shuffler.randomizeHints();

        if (options.allowSpoilerLog()) {
            spoilerJson.putAll(SpoilerWriter.buildSpoilerJson(world));
            spoilerJson.put("playthrough", shuffler.playthroughAsJson());
        }
    }

    /**
     * Applies all randomizer patches to the game files, given a function loading original files from the disc.
     *
     * @return the patched files
     */
    public GameFiles patchFiles(java.util.function.Function<String, byte[]> originalFileLoader) {
        if (shuffler == null && archipelago == null)
            throw new IllegalStateException("randomize() must be called before patching");
        GameFiles files = new GameFiles(originalFileLoader);
        if (archipelago != null)
            Patches.applyArchipelagoPatches(files, gameData, world, options, archipelago);
        else
            Patches.applyRandomizerPatches(files, gameData, world, options);
        return files;
    }

    /**
     * Builds a randomized disc image from an original Alundra (USA) v1.1 disc image.
     *
     * @param checkImageSize if true, refuses input images that don't have the expected size
     * @return the path of the written cue sheet
     */
    public Path patchImage(Path inputImage, Path outputImage, boolean checkImageSize) {
        if (!Files.exists(inputImage))
            throw new RandomizerException("Input file '" + inputImage + "' is missing. Please provide your Alundra (USA) v1.1 "
                    + "disc image (.bin), either by naming it 'input.bin' in the randomizer folder or by using --input=<path>.");

        try {
            long size = Files.size(inputImage);
            if (checkImageSize && size != EXPECTED_IMAGE_SIZE)
                throw new RandomizerException("Invalid file size (" + size + ") on the image file. Make sure you are using "
                        + "the .bin file of an Alundra (USA) v1.1 image (not the .cue).");
        } catch (IOException e) {
            throw new RandomizerException("Could not read input file '" + inputImage + "'", e);
        }

        if (inputImage.toAbsolutePath().normalize().equals(outputImage.toAbsolutePath().normalize()))
            throw new RandomizerException("Output image cannot overwrite the input image");

        try (CdImage image = new CdImage(inputImage)) {
            Iso9660 iso = new Iso9660(image);

            out.println("Editing game files...");
            GameFiles files = patchFiles(iso::readFile);

            out.println("Building a disc image...");
            IsoRebuilder rebuilder = new IsoRebuilder(iso);
            for (Map.Entry<String, BinaryFile> entry : files.modifiedFiles().entrySet())
                rebuilder.replaceFile(entry.getKey(), entry.getValue().contents());

            Path parent = outputImage.toAbsolutePath().getParent();
            if (parent != null)
                Files.createDirectories(parent);
            return rebuilder.write(outputImage);
        } catch (IOException e) {
            throw new RandomizerException("Could not write output image '" + outputImage + "'", e);
        }
    }

    public void writeSpoilerLog(Path path) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null)
                Files.createDirectories(parent);
            Files.writeString(path, Json.dump(spoilerJson), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RandomizerException("Could not open output log file for writing at path '" + path + "'", e);
        }
    }

    public Map<String, Object> spoilerJson() { return spoilerJson; }
    public Map<String, Object> debugLogJson() { return shuffler.debugLogAsJson(); }
    public GameData gameData() { return gameData; }
    public RandomizerWorld world() { return world; }
    public RandomizerOptions options() { return options; }
}
