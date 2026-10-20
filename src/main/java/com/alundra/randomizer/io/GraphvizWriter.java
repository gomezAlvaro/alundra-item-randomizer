package com.alundra.randomizer.io;

import com.alundra.randomizer.game.Item;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.model.WorldNode;
import com.alundra.randomizer.model.WorldPath;
import com.alundra.randomizer.tools.RandomizerException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exports the logic graph as a Graphviz DOT file (render it with {@code neato -Tsvg logic.dot -o logic.svg}).
 */
public final class GraphvizWriter {
    private static final String[] COLORS = {
            "indianred2", "lightslateblue", "limegreen", "deeppink2", "darkorchid3", "chocolate2", "darkturquoise"
    };

    private GraphvizWriter() {
    }

    public static String logicAsDot(RandomizerWorld world) {
        StringBuilder graphviz = new StringBuilder();
        graphviz.append("digraph {\n");
        graphviz.append("\tgraph [pad=0.5, nodesep=0.7];\n");
        graphviz.append("\tnode[shape=rect];\n");
        graphviz.append("\tlayout = neato;\n\n");

        List<WorldPath> paths = world.paths();
        for (int i = 0; i < paths.size(); ++i) {
            WorldPath path = paths.get(i);
            graphviz.append('\t').append(path.origin().id()).append(" -> ").append(path.destination().id()).append(" [");
            if (path.isTwoWay())
                graphviz.append("dir=both ");

            List<String> requiredNames = new ArrayList<>();
            for (Map.Entry<Item, Integer> entry : path.requiredItemsAndQuantity().entrySet()) {
                String descriptor = entry.getKey().name();
                if (entry.getValue() > 1)
                    descriptor += " x" + entry.getValue();
                requiredNames.add(descriptor);
            }
            for (WorldNode node : path.requiredNodes())
                requiredNames.add("Access to " + node.id());

            if (!requiredNames.isEmpty()) {
                String color = COLORS[i % COLORS.length];
                graphviz.append("color=").append(color).append(' ');
                graphviz.append("fontcolor=").append(color).append(' ');
                graphviz.append("label=\"").append(String.join("\\n", requiredNames)).append("\" ");
            }
            graphviz.append("]\n");
        }

        graphviz.append('\n');

        for (Map.Entry<String, WorldNode> entry : world.nodes().entrySet()) {
            String nodeId = entry.getKey();
            WorldNode node = entry.getValue();
            graphviz.append('\t').append(nodeId).append('[');
            if (node.positionInGraph() != null)
                graphviz.append(" pos=\"").append(node.positionInGraph()[0]).append(',').append(node.positionInGraph()[1]).append("!\"");
            graphviz.append(" label=\"").append(nodeId);
            if (!node.itemSources().isEmpty()) {
                graphviz.append("\\n(").append(node.itemSources().size());
                if (node.shouldHaveUniqueItems())
                    graphviz.append(" unique");
                graphviz.append(" items)");
            }
            graphviz.append('"');
            if (node == world.spawnNode() || node == world.endNode())
                graphviz.append(" shape=circle style=filled fillcolor=\"#AAAAFF\" fixedsize=true width=\"2\"");
            graphviz.append("]\n");
        }

        graphviz.append("}\n");
        return graphviz.toString();
    }

    public static void writeLogicAsDot(RandomizerWorld world, Path outputPath) {
        try {
            Files.writeString(outputPath, logicAsDot(world), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RandomizerException("Could not write graph file at '" + outputPath + "'", e);
        }
    }
}
