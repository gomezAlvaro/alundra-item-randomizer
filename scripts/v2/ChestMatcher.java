import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Finds chest sprites in the vgmaps Alundra atlas images by colour-tolerant template matching.
 * The template is a 24x28 chest taken from World.png; only its inner 18x22 area is compared, because the edges
 * are often covered by tree tops or railings.
 *
 * Usage: java scripts/v2/ChestMatcher.java docs/v2/maps 0.65 docs/v2/candidates.json
 */
public class ChestMatcher {
    private static final int TEMPLATE_X = 4278, TEMPLATE_Y = 35, TEMPLATE_W = 24, TEMPLATE_H = 28, MARGIN = 3;
    private static final int MAX_COLOR_DISTANCE_SQUARED = 1800;
    private static final int MIN_SPACING = 14;

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        double threshold = Double.parseDouble(args[1]);

        int[][] world = pixels(ImageIO.read(new File(dir, "World.png")));
        List<int[]> mask = new ArrayList<>();
        for (int y = MARGIN; y < TEMPLATE_H - MARGIN; y++) {
            for (int x = MARGIN; x < TEMPLATE_W - MARGIN; x++) {
                mask.add(new int[]{x, y, world[TEMPLATE_Y + y][TEMPLATE_X + x]});
            }
        }
        Collections.shuffle(mask, new Random(1)); // random order makes the early exit on mismatches much faster
        int maxMisses = mask.size() - (int) Math.ceil(mask.size() * threshold);

        File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));
        Arrays.sort(files);
        StringBuilder json = new StringBuilder("{\n");
        for (File file : files) {
            List<int[]> found = findChests(pixels(ImageIO.read(file)), mask, maxMisses);
            System.out.println(file.getName() + ": " + found.size());
            json.append("  \"").append(file.getName()).append("\": [");
            for (int i = 0; i < found.size(); i++) {
                json.append(i > 0 ? "," : "").append('[').append(found.get(i)[0]).append(',').append(found.get(i)[1]).append(']');
            }
            json.append("],\n");
        }
        json.setLength(json.length() - 2);
        json.append("\n}\n");
        try (PrintWriter out = new PrintWriter(args[2])) {
            out.print(json);
        }
    }

    private static List<int[]> findChests(int[][] image, List<int[]> mask, int maxMisses) {
        int height = image.length, width = image[0].length;
        List<int[]> hits = new ArrayList<>();
        for (int y = 0; y + TEMPLATE_H <= height; y++) {
            for (int x = 0; x + TEMPLATE_W <= width; x++) {
                int misses = 0;
                for (int[] m : mask) {
                    if (!isClose(image[y + m[1]][x + m[0]], m[2]) && ++misses > maxMisses) {
                        break;
                    }
                }
                if (misses <= maxMisses) {
                    hits.add(new int[]{x, y, mask.size() - misses});
                }
            }
        }
        hits.sort((a, b) -> b[2] - a[2]);
        List<int[]> kept = new ArrayList<>();
        for (int[] hit : hits) {
            boolean overlaps = false;
            for (int[] k : kept) {
                if (Math.abs(k[0] - hit[0]) < MIN_SPACING && Math.abs(k[1] - hit[1]) < MIN_SPACING) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                kept.add(hit);
            }
        }
        List<int[]> centers = new ArrayList<>();
        for (int[] k : kept) {
            centers.add(new int[]{k[0] + TEMPLATE_W / 2, k[1] + TEMPLATE_H / 2});
        }
        centers.sort((a, b) -> a[1] != b[1] ? a[1] - b[1] : a[0] - b[0]);
        return centers;
    }

    private static boolean isClose(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255);
        int dg = ((a >> 8) & 255) - ((b >> 8) & 255);
        int db = (a & 255) - (b & 255);
        return dr * dr + dg * dg + db * db < MAX_COLOR_DISTANCE_SQUARED;
    }

    private static int[][] pixels(BufferedImage image) {
        int[][] pixels = new int[image.getHeight()][image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                pixels[y][x] = image.getRGB(x, y) & 0xFFFFFF;
            }
        }
        return pixels;
    }
}
