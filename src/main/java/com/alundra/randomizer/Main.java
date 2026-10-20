package com.alundra.randomizer;

import com.alundra.randomizer.io.GraphvizWriter;
import com.alundra.randomizer.io.ModelWriter;
import com.alundra.randomizer.tools.ArgumentDictionary;
import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.RandomizerException;
import com.alundra.randomizer.tools.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

/**
 * Command line entry point.
 */
public final class Main {
    public static final String VERSION = "1.2.0";

    private static final String USAGE = """
            Usage: java -jar alundra-randomizer.jar [options]

              --preset=<name>        Preset to use (file name inside the 'presets' folder, or a path to a JSON file).
                                     Asked interactively if omitted and a console is available, else 'default'.
              --permalink[=<link>]   Generate the exact seed described by a permalink (asked if no value is given).
              --seed=<number>        Use a specific seed (0 to 4294967295) instead of a random one.
              --input=<path>         Original Alundra (USA) v1.1 disc image (.bin). Default: ./input.bin
              --outputrom=<path>     Output image path (.bin) or folder. Default: current folder, named after the seed.
              --outputlog=<path>     Spoiler log path (.json) or folder. Default: next to the output image.
              --only-logic           Only randomize and write the spoiler log, don't build a disc image.
              --seedcount=<n>        Generate several seeds in a row.
              --debuglog=<path>      Write the shuffler's step-by-step debug log (requires spoiler logs to be allowed).
              --graph                Write the logic graph as 'logic.dot' (Graphviz).
              --dumpmodel            Dump the logic model (as altered by options) inside './json_data/'.
              --noimagecheck         Don't check the input image size (for modified dumps of the same version).
              --pause                Wait for Enter before exiting.
              --help                 Show this help.
            """;

    private Main() {
    }

    public static void main(String[] argv) {
        ArgumentDictionary args = new ArgumentDictionary(argv);
        int returnCode = run(args);

        if (args.getBoolean("pause", false)) {
            System.out.print("\nPress Enter to exit.");
            readLine();
        }
        System.exit(returnCode);
    }

    static int run(ArgumentDictionary args) {
        System.out.println("======== Alundra Randomizer v" + VERSION + " (Java) ========\n");

        if (args.contains("help")) {
            System.out.println(USAGE);
            return 0;
        }

        if (args.contains("permalink") && args.getString("permalink").isBlank()) {
            System.out.print("Please specify a permalink: ");
            args.setString("permalink", readLine());
        }

        try {
            int seedCount = Math.max(1, args.getInteger("seedcount", 1));
            for (int i = 0; i < seedCount; ++i)
                generate(args, i);
            return 0;
        } catch (RandomizerException e) {
            System.err.println("ERROR: " + e.getMessage());
            return 1;
        }
    }

    /**
     * @param index index of the seed being generated (when generating several seeds in a row, a fixed --seed is
     *              incremented for each of them)
     */
    private static void generate(ArgumentDictionary args, int index) {
        Randomizer randomizer;
        String permalink = args.getString("permalink").trim();
        if (!permalink.isEmpty()) {
            randomizer = Randomizer.fromPermalink(permalink);
        } else {
            long seed = args.contains("seed")
                    ? (parseSeed(args.getString("seed")) + index) & 0xFFFFFFFFL
                    : (new SecureRandom().nextInt() & 0xFFFFFFFFL);
            randomizer = Randomizer.fromPreset(loadPreset(args), seed);
        }
        RandomizerOptions options = randomizer.options();

        System.out.println("\nRandomizing world...");
        randomizer.randomize();

        if (options.allowSpoilerLog()) {
            String debugLogPath = args.getString("debuglog");
            if (!debugLogPath.isEmpty())
                writeText(Paths.get(debugLogPath), Json.dump(randomizer.debugLogJson()));

            if (args.getBoolean("dumpmodel", false)) {
                ModelWriter.writeLogicModel(randomizer.world(), Paths.get("json_data"));
                System.out.println("Model dumped to './json_data/'");
            }
        }

        // Compute output paths
        String hashSentence = options.hashSentence();
        Path outputRomPath = Paths.get(args.getString("outputrom", "."));
        Path spoilerLogPath = args.getString("outputlog").isEmpty() ? null : Paths.get(args.getString("outputlog"));
        if (spoilerLogPath == null)
            spoilerLogPath = hasExtension(outputRomPath) ? replaceExtension(outputRomPath, ".json") : outputRomPath;
        if (!outputRomPath.getFileName().toString().toLowerCase().endsWith(".bin"))
            outputRomPath = outputRomPath.resolve(hashSentence + ".bin");
        if (!spoilerLogPath.getFileName().toString().toLowerCase().endsWith(".json"))
            spoilerLogPath = spoilerLogPath.resolve(hashSentence + ".json");

        if (!args.contains("only-logic")) {
            Path inputPath = Paths.get(args.getString("input", "./input.bin"));
            System.out.println("Checking input image...");
            Path cuePath = randomizer.patchImage(inputPath, outputRomPath, !args.contains("noimagecheck"));
            System.out.println("Randomized game written to '" + outputRomPath + "' (cue sheet: '" + cuePath.getFileName() + "').");
        }

        randomizer.writeSpoilerLog(spoilerLogPath);
        System.out.println((options.allowSpoilerLog() ? "Spoiler log" : "Generation log") + " written into '" + spoilerLogPath + "'.");

        if (args.contains("graph"))
            GraphvizWriter.writeLogicAsDot(randomizer.world(), Paths.get("logic.dot"));

        System.out.println("\nHash sentence: " + hashSentence);
        System.out.println("\nPermalink: " + options.permalink());
        System.out.println("\nShare the permalink above with other people to enable them building the exact same seed.\n");
    }

    private static long parseSeed(String value) {
        try {
            long seed = Long.parseLong(value.trim());
            if (seed < 0 || seed > 0xFFFFFFFFL)
                throw new NumberFormatException();
            return seed;
        } catch (NumberFormatException e) {
            throw new RandomizerException("Invalid seed '" + value + "' (must be a number between 0 and 4294967295)");
        }
    }

    private static JsonNode loadPreset(ArgumentDictionary args) {
        String presetName = args.getString("preset").trim();
        if (presetName.isEmpty() && System.console() != null) {
            System.out.print("Please specify a preset name (name of a file inside the 'presets' folder, leave empty for default): ");
            presetName = readLine().trim();
        }
        if (presetName.isEmpty())
            presetName = "default";
        args.setString("preset", presetName); // Don't ask again when generating several seeds

        // A direct path to an existing file
        Path direct = Paths.get(presetName);
        if (Files.isRegularFile(direct))
            return readPreset(direct);

        // A name inside the presets folder
        String fileName = Paths.get(presetName).getFileName().toString();
        if (!fileName.endsWith(".json"))
            fileName += ".json";
        Path presetPath = Paths.get("presets", fileName);
        if (Files.isRegularFile(presetPath))
            return readPreset(presetPath);

        // The default preset is bundled inside the program
        if (fileName.equals("default.json")) {
            System.out.println("Preset: built-in default");
            return Json.parseResource("/presets/default.json");
        }

        throw new RandomizerException("Could not open preset file at given path '" + presetPath + "'");
    }

    private static JsonNode readPreset(Path path) {
        System.out.println("Preset: '" + path + "'");
        try {
            return Json.parse(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RandomizerException("Could not read preset file '" + path + "'", e);
        }
    }

    private static boolean hasExtension(Path path) {
        String name = path.getFileName().toString();
        return name.lastIndexOf('.') > 0;
    }

    private static Path replaceExtension(Path path, String extension) {
        String name = path.getFileName().toString();
        return path.resolveSibling(name.substring(0, name.lastIndexOf('.')) + extension);
    }

    private static void writeText(Path path, String text) {
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RandomizerException("Could not write file '" + path + "'", e);
        }
    }

    private static String readLine() {
        try {
            String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
            return line == null ? "" : line;
        } catch (IOException e) {
            return "";
        }
    }
}
