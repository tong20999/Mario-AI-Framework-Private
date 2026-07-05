import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import engine.core.MarioLevel;
import engine.helper.Assets;
import engine.helper.SpriteType;

/**
 * Renders a Mario level string to a PNG image without any of the in-game HUD.
 * Unlike {@link engine.core.MarioRender}, the output width follows the level's
 * own width (no fixed 256px camera viewport), so the whole level fits in one
 * image.
 */
public class LevelScreenshot {
    private static final String OUTPUT_FOLDER = "level-screenshot";

    private static final int SKY_TILE = 42;
    private static final int FLAG_TILE = 41;

    public static void main(String[] args) throws IOException {
        String levelPath = args.length > 0 ? args[0] : "./levels/benchmark/isolate-coin/lvl-3.txt";
        String level = getLevel(levelPath);
        capture(level, levelPath);
        System.out.println("Saved screenshot for " + levelPath);
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
}
