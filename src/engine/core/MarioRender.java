package engine.core;

import javax.swing.*;

import engine.helper.Assets;
import engine.helper.MarioActions;

import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.text.MessageFormat;


public class MarioRender extends JComponent implements FocusListener {
    private static final long serialVersionUID = 790878775993203817L;
    public static final int TICKS_PER_SECOND = 24;

    private float scale;
    private GraphicsConfiguration graphicsConfiguration;

    int frame;
    Thread animator;
    boolean focused;

    public MarioRender(float scale) {
        this.setFocusable(true);
        this.setEnabled(true);
        this.scale = scale;

        Dimension size = new Dimension((int) (256 * scale), (int) (240 * scale));

        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);

        setFocusable(true);
    }

    public void init() {
        graphicsConfiguration = getGraphicsConfiguration();
        Assets.init(graphicsConfiguration);
    }

    public void renderWorld(MarioWorld world, Image image, Graphics g, Graphics og) {
        og.fillRect(0, 0, 256, 240);
        world.render(og);
        double completePercentage = world.mario.x / (world.level.exitTileX * 16);
        //drawStringDropShadow(og, "Lives: " + world.lives, 0, 0, 7);
        drawStringDropShadow(og, "Coins: " + world.level.totalCoins, 0, 0, 7);
        //drawStringDropShadow(og, "Coins: " + world.coins, 11, 0, 7);
        drawStringDropShadow(og, "Time: " + (world.currentTimer == -1 ? "Inf" : (int) Math.ceil(world.currentTimer / 1000f)), 22, 0, 7);
        //drawStringDropShadow(og, "R: " + String.format("%.2f", world.reward), 0, 2, 7);
        drawStringDropShadow(og, "Collect: " + world.collectCoin, 0, 2, 7);
//        if(world.episode > 0){
//            drawStringDropShadow(og, "Episode: " + world.episode, 14, 2, 7);
//        }
//        else {
//            drawStringDropShadow(og, "Evaluation", 14, 2, 7);
//        }
//        if(world.levelName != null){
//            drawStringDropShadow(og, world.levelName, 14, 2, 7);
//        }

        //drawStringDropShadow(og, "Complete: " + String.format("%.2f", completePercentage), 0, 4, 7);
        var enemyCompass = world.nearestCompass(world.findNearestEnemyVector());
        var totalEnemy = MessageFormat.format("{0} {1}", world.level.getEnemies().size(), enemyCompass);
        drawStringDropShadow(og, "Enemy: " + totalEnemy, 0, 4, 7);
        drawStringDropShadow(og, "Kill: " + (world.level.getEnemies().size() - world.getAliveEnemies().size()), 0, 6, 7);

        var blockCompass = world.nearestCompass(world.findNearestBlockVector());
        var totalBlock = MessageFormat.format("{0} {1}", world.level.getBumpableBlocks().size(), blockCompass);
        drawStringDropShadow(og, "Block: " + totalBlock, 0, 8, 7);
        drawStringDropShadow(og, "Bump: " + (world.level.getBumpableBlocks().size() - world.getUnbumpBlocks().size()), 0, 10, 7);
        if (MarioGame.verbose) {
            String pressedButtons = "";
            for (int i = 0; i < world.mario.actions.length; i++) {
                if (world.mario.actions[i]) {
                    pressedButtons += MarioActions.getAction(i).getString() + " ";
                }
            }
            drawStringDropShadow(og, "Buttons: " + pressedButtons, 0, 2, 1);
        }
        if (scale > 1) {
            g.drawImage(image, 0, 0, (int) (256 * scale), (int) (240 * scale), null);
        } else {
            g.drawImage(image, 0, 0, null);
        }
    }

    public void drawStringDropShadow(Graphics g, String text, int x, int y, int c) {
        drawString(g, text, x * 8 + 5, y * 8 + 5, 0);
        drawString(g, text, x * 8 + 4, y * 8 + 4, c);
    }

    private void drawString(Graphics g, String text, int x, int y, int c) {
        char[] ch = text.toCharArray();
        for (int i = 0; i < ch.length; i++) {
            g.drawImage(Assets.font[ch[i] - 32][c], x + i * 8, y, null);
        }
    }

    public void focusGained(FocusEvent arg0) {
        focused = true;
    }

    public void focusLost(FocusEvent arg0) {
        focused = false;
    }
}