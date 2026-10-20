package com.alundra.randomizer.io;

import com.alundra.randomizer.model.ItemSource;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.model.WorldNode;
import com.alundra.randomizer.model.WorldPath;
import com.alundra.randomizer.model.WorldRegion;
import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.RandomizerException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dumps the logic model (as altered by options) back to JSON files, for debugging purposes.
 */
public final class ModelWriter {
    private ModelWriter() {
    }

    public static void writeLogicModel(RandomizerWorld world, Path outputDir) {
        Map<String, Object> nodesJson = Json.object();
        for (Map.Entry<String, WorldNode> entry : world.nodes().entrySet())
            nodesJson.put(entry.getKey(), entry.getValue().toJson());

        List<Object> pathsJson = new ArrayList<>();
        for (WorldPath path : world.paths())
            pathsJson.add(path.toJson());

        List<Object> itemSourcesJson = new ArrayList<>();
        for (ItemSource source : world.itemSources())
            itemSourcesJson.add(source.toJson());

        List<Object> regionsJson = new ArrayList<>();
        for (WorldRegion region : world.regions())
            regionsJson.add(region.toJson());

        try {
            Files.createDirectories(outputDir);
            write(outputDir.resolve("world_node.json"), nodesJson);
            write(outputDir.resolve("world_path.json"), pathsJson);
            write(outputDir.resolve("item_source.json"), itemSourcesJson);
            write(outputDir.resolve("world_region.json"), regionsJson);
        } catch (IOException e) {
            throw new RandomizerException("Could not write model files to '" + outputDir + "'", e);
        }
    }

    private static void write(Path path, Object json) throws IOException {
        Files.writeString(path, Json.dump(json), StandardCharsets.UTF_8);
    }
}
