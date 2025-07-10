package engine.core;

import java.awt.image.VolatileImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.awt.*;

import javax.swing.JFrame;

import reinforment.*;
import engine.helper.GameStatus;
import engine.helper.MarioActions;
import info.EndInfo;
import info.Info;

public class MarioGameTraining {
    /**
     * the maximum time that agent takes for each step
     */
    public static final long maxTime = 40;
    /**
     * extra time before reporting that the agent is taking more time that it should
     */
    public static final long graceTime = 10;
    /**
     * Screen width
     */
    public static final int width = 256;
    /**
     * Screen height
     */
    public static final int height = 256;
    /**
     * Screen width in tiles
     */
    public static final int tileWidth = width / 16;
    /**
     * Screen height in tiles
     */
    public static final int tileHeight = height / 16;
    /**
     * print debug details
     */
    public static final boolean verbose = false;

    /**
     * pauses the whole game at any moment
     */
    public boolean pause = false;

    /**
     * events that kills the player when it happens only care about type and param
     */
    private MarioEvent[] killEvents;

    //visualization
    private JFrame window = null;
    private MarioRender render = null;
    private MarioWorld world = null;
    MarioTimer agentTimer;
    int timer;

    //initialize graphics
    VolatileImage renderTarget = null;
    Graphics backBuffer = null;
    Graphics currentBuffer = null;
    boolean visual = true;
    int currentTimer;
    int lastMilestone;
    int lastCoinCount;

    boolean evaluation = false;
    ArrayList<MarioEvent> gameEvents;

    EndInfo episodeInfo = new EndInfo();
    EndInfo evaluationInfo = new EndInfo();

    public float evaluationReward = 0;
    public float episodeReward = 0;
    int episodeTimer = 0;
    int evaluationTimer = 0;
    int frameSkip = 4;
    int episode = -1;
    boolean isNormalSpeed = false;
    Objective objective;
    ArrayList<MarioEvent> miniStepEvents = new ArrayList<>();

    private int fps = 40;
    /**
     * Create a mario game to be played
     */
    public MarioGameTraining() {

    }

     public void close() {
         if (this.window != null) {
             this.window.dispose(); // This is the crucial call to close the window
         }
     }

    private void setupWindow(int episode) {
        if(this.window != null){
            return;
        }
        this.window = new JFrame("Mario AI Framework");
        this.window.setFocusableWindowState(false);
        this.render = new MarioRender(2);
        this.window.setContentPane(this.render);
        this.window.pack();
        this.window.setResizable(false);
        this.window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.render.init();
        // === NEW LOGIC FOR WINDOW PLACEMENT STARTS HERE ===

        // 1. Get a unique ID for this worker (from 0 to 7)
        // We assume getEpisode() returns the unique worker number.
        int workerId = episode;

        // 2. Define the grid dimensions
        final int NUM_COLS = 4; // We want 4 windows per row

        // 3. Calculate the row and column for this worker
        // Integer division gives the row number (0 for top row, 1 for bottom row)
        int row = workerId / NUM_COLS;
        // Modulo operator gives the column number (0, 1, 2, or 3)
        int col = workerId % NUM_COLS;

        // 4. Get the size of one game window
        int windowWidth = this.window.getWidth();
        int windowHeight = this.window.getHeight();

        // 5. Calculate the exact X and Y coordinates on the screen
        int xPosition = col * windowWidth;
        int yPosition = row * windowHeight;

        // 6. Set the window's location on the screen
        this.window.setLocation(xPosition, yPosition);

        // This makes the window visible at its new position
        this.window.setVisible(this.visual);
    }

    public byte[] reset(Info info) throws Exception {
        this.visual = info.isVisual();
        if(this.visual){
            setupWindow(info.getEpisode());
        }
        if(!info.isEvaluation()){
            this.episode = info.getEpisode();
        }
        if(this.episode == 0){
            RewardSystem.printRewardsInformation();
        }

        var levelFileName = info.getLevel();
        var levelName = levelFileName.substring(levelFileName.lastIndexOf("/") + 1);



        this.gameEvents = new ArrayList<>();
        this.evaluation = info.isEvaluation();
        this.world = new MarioWorld(this.killEvents);
        this.world.levelFileName = levelFileName;
        this.world.levelName = levelName;
        this.world.visuals = visual;
        // timer by level width
        this.timer = ((new MarioLevel(Helper.getLevel(levelFileName), false).exitTileX)/2) + 10;
        this.timer = isNormalSpeed ? 30 : 15;
        this.lastMilestone = 0;
        this.lastCoinCount = 0;
        String level = Helper.getLevel(levelFileName);
        level = modLevel(levelFileName,  level);
        this.world.initializeLevel(level, 1000 * this.timer);
        this.objective = Helper.setObjective(this.world, levelFileName);
        if (visual) {
            this.world.initializeVisuals(this.render.getGraphicsConfiguration());
        }
        this.world.mario.isLarge = false;
        this.world.mario.isFire = false;
        this.world.update(new boolean[MarioActions.numberOfActions()]);

        if (visual) {
            renderTarget = this.render.createVolatileImage(MarioGameTraining.width, MarioGameTraining.height);
            backBuffer = this.render.getGraphics();
            currentBuffer = renderTarget.getGraphics();
            this.render.addFocusListener(this.render);
        }

        this.agentTimer = new MarioTimer(MarioGameTraining.maxTime);

        if(!this.evaluation){
            this.episodeReward = 0;
        } else {
            this.evaluationReward = 0;
        }
        return State.toByte(new MarioForwardModel(this.world.clone()));
    }

    private String modLevel(String levelFileName, String level) {
        if(levelFileName.contains("training/100-basic/104-basic-block-enemy-pit/")){
            printPCGInfo();
            level = ProceduralContentGeneration.replacePit(level, 10, 5, 2,4);
            level = ProceduralContentGeneration.replaceSingleBlock(level, 1, 2);
            return ProceduralContentGeneration.replaceSingleEnemy(level, 8, 2);
        }


        if(levelFileName.contains("training/100-basic/101-basic-block/")){

            return ProceduralContentGeneration.replaceSingleBlock(level, 1, 2);
        }

        if(levelFileName.contains("training/100-basic/102-basic-enemy/")){
            return ProceduralContentGeneration.replaceSingleEnemy(level, 10, 10);
        }

        if(levelFileName.contains("training/100-basic/103-basic-block-enemy/")){

            level = ProceduralContentGeneration.replaceSingleBlock(level, 1, 2);
            return ProceduralContentGeneration.replaceSingleEnemy(level, 8, 2);
        }

        if(levelFileName.contains("training/100-basic/103-basic-jump/")){
            return ProceduralContentGeneration.replacePit(level, 10, 10, 2,4);
        }

        if(levelFileName.contains("training/100-basic/104-basic-pipe/")){
            return ProceduralContentGeneration.replacePipe(level, 10, 10);
        }

        throw new IllegalArgumentException("modLevel " + levelFileName);
    }

    private void printPCGInfo() {
        File file = new File("C:\\thesis_data\\pcg.txt");
        if(file.exists()){
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("ProceduralContentGeneration.replaceSingleBlock(level, 1, 2)");
            writer.write("ProceduralContentGeneration.replaceSingleEnemy(level, 8, 2)");
            writer.write("ProceduralContentGeneration.replacePit(level, 10, 5, 2,4)");
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    public byte[] step(boolean[] action) throws Exception {
        // for frame skip the agent will only send one action per 3 frames to make agent jump longer
        // because it needs to hold the jump button
        if(this.evaluation){
            int a = 5;
        }

        miniStepEvents.clear();

        for (int i = 0; i < this.frameSkip; i++) {
            var events = miniStep(action);
            miniStepEvents.addAll(events);
        }

        checkSubGoalMet(world, objective);
        var nextWorldState = this.world.clone();
        var nextState = new MarioForwardModel(nextWorldState);
        float reward = RewardSystem.getReward(this.world, miniStepEvents, action);

        if(this.evaluation){
            this.evaluationReward += reward;
            this.evaluationTimer = this.world.currentTimer;
        } else {
            this.episodeReward += reward;
            this.episodeTimer = this.world.currentTimer;
        }

        this.world.reward += reward;

        this.world.episode = this.evaluation ? -1 : this.episode;
        Helper.printInfo(this.world, this.gameEvents,
                this.evaluation, this.evaluationInfo, this.evaluationTimer, this.evaluationReward,
                this.episodeInfo, this.episode, this.episodeTimer, this.episodeReward);
        return State.stepResult(State.toByte(nextState), reward, this.world.gameStatus != GameStatus.RUNNING);
    }

    public ArrayList<MarioEvent> miniStep(boolean[] action) throws Exception {
        long currentTime = System.currentTimeMillis();
        this.world.update(action);
        this.gameEvents.addAll(this.world.lastFrameEvents);
        if (visual) {
            this.render.renderWorld(this.world, renderTarget, backBuffer, currentBuffer);
        }

        if(this.isNormalSpeed)
        {
            if (this.getDelay(fps) > 0) {
                try {
                    currentTime += this.getDelay(fps);
                    Thread.sleep(Math.max(0, currentTime - System.currentTimeMillis()));
                } catch (InterruptedException e) {

                }
            }
        }
        return this.world.lastFrameEvents;
    }

    private static void checkSubGoalMet(MarioWorld world, Objective objective) {
        if(objective.coin){
            if(world.level.totalCoins == world.collectCoin){
                world.isSubGoalCoinMet = true;
            }
        }

        if(objective.block){
            if(world.level.totalBumpBlock == world.bumpBlock){
                //world.win();
                world.isSubGoalBlockMet = true;
            }
        }

        if(objective.enemy){
            if(world.level.totalEnemies == world.kill){
                //world.win();
                world.isSubGoalEnemyMet = true;
            }
        }

        if(world.isSubGoalEnemyMet && world.isSubGoalBlockMet){
            //world.win();
        }
    }

    private int getDelay(int fps) {
        if (fps <= 0) {
            return 0;
        }
        return 1000 / fps;
    }
}