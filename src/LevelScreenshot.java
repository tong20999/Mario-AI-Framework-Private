import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import engine.core.MarioLevel;
import engine.helper.Assets;
import engine.helper.SpriteType;
import reinforcement.PCGLevelDto;
import reinforcement.ProceduralContentGenerationLevel;

/**
 * Renders a Mario level string to a PNG image without any of the in-game HUD.
 * Unlike {@link engine.core.MarioRender}, the output width follows the level's
 * own width (no fixed 256px camera viewport), so the whole level fits in one
 * image.
 */
public class LevelScreenshot {
    private static final String OUTPUT_FOLDER = "level-screenshot";
    private static final String TRAINED_LEVELS_FOLDER = "trained_level_parameters";

    private static final int SKY_TILE = 42;
    private static final int FLAG_TILE = 41;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            String levelPath = args[0];
            capture(getLevel(levelPath), levelPath);
            System.out.println("Saved screenshot for " + levelPath);
            return;
        }

        var pcgLevel = ProceduralContentGenerationLevel
                .parseLevel(new PCGLevelDto(
                        20,
                        21,
                        999,
                        1000,
                        1,
                        10,
                        11,
                        true,
                        true,
                        false,
                        1,
                        5,
                        11,
                        0,
                        5,
                        14,
                        0,
                        2,
                        5,
                        0,
                        2,
                        5,
                        1,
                        30,
                        null,
                        false));
        var levelPath = "gen";
        pcgLevel.generate();
        capture(pcgLevel.getContent(), levelPath);
        System.out.println("Saved screenshot for " + levelPath);

        // Gson gson = new GsonBuilder().create();
        // for (SelectedLevel selected : createLevelFromConfig()) {
        // File configFile = new File(TRAINED_LEVELS_FOLDER + "/" + selected.folder(),
        // "pcgLevel.json");
        // PCGLevelDto dto = gson.fromJson(getLevel(configFile.getPath()),
        // PCGLevelDto.class);

        // String levelPath = TRAINED_LEVELS_FOLDER + "/" + selected.name() + ".txt";
        // String content = generateWithTimeout(dto);
        // if (content == null) {
        // // some configs (e.g. width range too narrow relative to the fixed
        // // obstacle offsets) make ProceduralContentGenerationLevel#doParseLevel
        // // retry forever with the same unmodified dto instead of ever adjusting
        // // it, so generation can spin for an unpredictable amount of time before
        // // succeeding by chance or overflowing the stack
        // System.out.println("Skipping " + levelPath + " (folder " + selected.folder()
        // + "): generator could not fit this config in time");
        // continue;
        // }
        // capture(content, levelPath);
        // System.out.println("Saved screenshot for " + levelPath);
        // }
    }

    private static String generateWithTimeout(PCGLevelDto dto) throws InterruptedException {
        String[] result = new String[1];
        Thread worker = new Thread(() -> {
            try {
                ProceduralContentGenerationLevel pcg = ProceduralContentGenerationLevel.parseLevel(dto);
                pcg.generate();
                result[0] = pcg.getContent();
            } catch (StackOverflowError | IOException | java.sql.SQLException e) {
                // leave result[0] null
            }
        });
        worker.setDaemon(true);
        worker.start();
        worker.join(5000);
        return result[0];
    }

    public static String getLevel(String filepath) {
        String content = "";
        try {
            content = new String(Files.readAllBytes(Paths.get(filepath)));
        } catch (IOException e) {
        }
        return content;
    }

    /**
     * Renders the level and writes it to level-screenshot/&lt;levelPath with '/'
     * -&gt; '-'&gt;.png
     */
    public static void capture(String level, String levelPath) throws IOException {
        BufferedImage image = render(level);

        File outDir = new File(OUTPUT_FOLDER);
        outDir.mkdirs();
        ImageIO.write(image, "png", new File(outDir, toFileName(levelPath)));
    }

    private static String toFileName(String levelPath) {
        String path = levelPath.replace('\\', '/');
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.toLowerCase().endsWith(".txt")) {
            path = path.substring(0, path.length() - 4);
        }
        return path.replace('/', '-') + ".png";
    }

    public static BufferedImage render(String level) {
        if (Assets.level == null) {
            Assets.init(GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration());
        }

        MarioLevel marioLevel = new MarioLevel(level, false);
        int width = Math.max(marioLevel.width, 1);
        int height = Math.max(marioLevel.height, 1);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            drawBackground(g, width, height);
            drawTiles(g, marioLevel);
            drawFlag(g, marioLevel);
            drawSprites(g, marioLevel);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static Image tileImage(Image[][] sheet, int index) {
        return sheet[index % sheet.length][index / sheet.length];
    }

    private static void drawBackground(Graphics2D g, int width, int height) {
        Image sky = tileImage(Assets.level, SKY_TILE);
        for (int x = 0; x * 16 < width; x++) {
            for (int y = 0; y * 16 < height; y++) {
                g.drawImage(sky, x * 16, y * 16, null);
            }
        }
    }

    private static void drawTiles(Graphics2D g, MarioLevel level) {
        for (int x = 0; x < level.tileWidth; x++) {
            for (int y = 0; y < level.tileHeight; y++) {
                int index = level.getBlock(x, y);
                if (index == 999) {
                    index = 2;
                }
                if (index == 0) {
                    continue;
                }
                g.drawImage(tileImage(Assets.level, index), x * 16, y * 16, null);
            }
        }
    }

    private static void drawFlag(Graphics2D g, MarioLevel level) {
        int flagTopTile = Math.max(1, level.exitTileY - 11);
        g.drawImage(tileImage(Assets.level, FLAG_TILE), level.exitTileX * 16 - 8, flagTopTile * 16 + 16, null);
    }

    // pixel placement mirrors SpriteType#spawnSprite + the origin offsets
    // Enemy/Mario use when rendering
    private static void drawSprites(Graphics2D g, MarioLevel level) {
        for (int x = 0; x < level.tileWidth; x++) {
            for (int y = 0; y < level.tileHeight; y++) {
                SpriteType type = level.getSpriteType(x, y);
                if (type == SpriteType.NONE) {
                    continue;
                }
                Image img = tileImage(Assets.enemies, type.getStartIndex());
                if (type == SpriteType.ENEMY_FLOWER) {
                    g.drawImage(img, x * 16 + 17 - 8, y * 16 + 18 - 24, 16, 32, null);
                } else {
                    g.drawImage(img, x * 16 + 8 - 8, y * 16 + 15 - 31, 16, 32, null);
                }
            }
        }

        Image mario = tileImage(Assets.smallMario, 0);
        g.drawImage(mario, level.marioTileX * 16, level.marioTileY * 16, 16, 16, null);
    }

    private record SelectedLevel(String folder, String name) {
    }

    private static List<SelectedLevel> createLevelFromConfig() {
        // read all folder under trained_level_parameters
        // select some not all by looking at the config file for each I want to make
        // diff level as mush as possible for example
        // if level 5 and 13 has same configuration of elements, then only one is enough
        // to be used for screenshot.

        // so that mean you need to look through all config and make a list of exclude
        // String[] exclude = new String[]{"level-5.txt", "level-13.txt"};

        // return only not in exclude list

        File root = new File(TRAINED_LEVELS_FOLDER);
        File[] folders = root.listFiles(File::isDirectory);
        if (folders == null) {
            return new ArrayList<>();
        }
        Arrays.sort(folders, Comparator.comparingInt(f -> Integer.parseInt(f.getName())));

        Gson gson = new GsonBuilder().create();
        Set<String> seenSignatures = new HashSet<>();
        List<SelectedLevel> kept = new ArrayList<>();

        for (File folder : folders) {
            File configFile = new File(folder, "pcgLevel.json");
            if (!configFile.exists()) {
                continue;
            }
            PCGLevelDto dto = gson.fromJson(getLevel(configFile.getPath()), PCGLevelDto.class);

            // the level's obstacle/entity counts are what actually change its visual
            // makeup; width, timer and height-range fields only reshuffle the same kind
            // of content, so folders that only differ in those are treated as duplicates
            String signature = dto.getBlocks() + "-" + dto.getCoins() + "-" + dto.getEnemies() + "-"
                    + dto.getPits() + "-" + dto.getPipes() + "-" + dto.getRamps();

            if (seenSignatures.add(signature)) {
                kept.add(new SelectedLevel(folder.getName(), describeConfig(dto)));
            }
        }

        return kept;
    }

    // builds a name like "coin_1_ramp_2" from the non-zero counts in the config,
    // so the screenshot file name tells you what the level is meant to exercise
    private static String describeConfig(PCGLevelDto dto) {
        StringBuilder name = new StringBuilder();
        appendCount(name, "block", dto.getBlocks());
        appendCount(name, "coin", dto.getCoins());
        appendCount(name, "enemy", dto.getEnemies());
        appendCount(name, "pit", dto.getPits());
        appendCount(name, "pipe", dto.getPipes());
        appendCount(name, "ramp", dto.getRamps());
        return name.length() == 0 ? "empty" : name.toString();
    }

    private static void appendCount(StringBuilder name, String label, int count) {
        if (count <= 0) {
            return;
        }
        if (name.length() > 0) {
            name.append('_');
        }
        name.append(label).append('_').append(count);
    }
}
