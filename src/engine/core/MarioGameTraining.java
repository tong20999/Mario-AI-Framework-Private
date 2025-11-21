package engine.core;

import java.awt.image.VolatileImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.awt.*;
import java.util.Base64;
import java.util.Random;

import javax.swing.JFrame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import engine.helper.EventType;
import reinforcement.*;
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

    private static final Random rand = new Random();

    // visualization
    private JFrame window = null;
    private MarioRender render = null;
    private MarioWorld world = null;
    MarioTimer agentTimer;
    int timer;

    // initialize graphics
    VolatileImage renderTarget = null;
    Graphics backBuffer = null;
    Graphics currentBuffer = null;
    boolean visual = true;
    int currentTimer;
    int lastMilestone;
    int lastCoinCount;

    boolean evaluation = false;
    ArrayList<RewardEvent> rewardEvents;

    EndInfo episodeInfo = new EndInfo();
    EndInfo evaluationInfo = new EndInfo();

    public float evaluationReward = 0;
    public float episodeReward = 0;
    int episodeTimer = 0;
    int evaluationTimer = 0;
    int episode = -1;
    int minTimer = 20;
    int maxTimer = 30;
    ArrayList<MarioEvent> miniStepEvents = new ArrayList<>();

    private int fps = 0;
    private ProceduralContentGenerationLevel pcg = null;
    private final int frameSkip = 2;
    private final int MAX_STEP = 1000;
    private int stepCount = 0;
    private boolean testMode = false;
    private String level;
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
        if (this.window != null) {
            return;
        }

        this.window = new JFrame(this.evaluation ? "EVALUATION" : "TRAIN");
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

        // 2. Define the grid dimensions
        final int NUM_COLS = 4; // We want 4 windows per row

        // 3. Calculate the row and column for this worker
        // Integer division gives the row number (0 for top row, 1 for bottom row)
        int row = episode / NUM_COLS;
        // Modulo operator gives the column number (0, 1, 2, or 3)
        int col = episode % NUM_COLS;

        // 4. Get the size of one game window
        int windowWidth = this.window.getWidth();
        int windowHeight = this.window.getHeight();

        // 5. Calculate the exact X and Y coordinates on the screen
        int xPosition = col * windowWidth;
        int yPosition = row * windowHeight;

        // 6. Set the window's location on the screen
        this.window.setLocation(xPosition, yPosition);

        // This makes the window visible at its new position
        this.window.setVisible(true);
    }

    public byte[] reset(Info info) throws Exception {
        this.visual = info.isVisual();
        this.episode = info.getEpisode();
        this.evaluation = info.isEvaluation();
        if(this.visual){
            setupWindow(info.getEpisode());
        }
        stepCount = 0;
        var b64Level = info.getPayload();
        byte[] decodedBytes = Base64.getDecoder().decode(b64Level);
        String jsonString = new String(decodedBytes, StandardCharsets.UTF_8);
        Gson gson = new GsonBuilder().create();
        PCGLevelDto pcgLevel = gson.fromJson(jsonString, PCGLevelDto.class);
        this.testMode = pcgLevel.getFile() != null;
        this.rewardEvents = new ArrayList<>();
        this.world = new MarioWorld(this.killEvents);
        this.world.visuals = visual;
        this.minTimer = pcgLevel.getTimerMin();
        this.maxTimer = pcgLevel.getTimerMax();
        this.timer = rand.nextInt(this.minTimer, this.maxTimer);
        this.lastMilestone = 0;
        this.lastCoinCount = 0;

        if(pcgLevel.getFile() != null){
            level = Helper.getLevelFromFile(pcgLevel.getFile());
            this.fps = pcgLevel.getFps();
        } else{
            this.fps = pcgLevel.getFps();
            this.pcg = ProceduralContentGenerationLevel.parseLevel(pcgLevel);
            if(!pcgLevel.isTrainFailedLevel()){
                this.pcg.generate();
            }
            level = this.pcg.getContent();
        }

        if(pcgLevel.getLevelId() > 0){
            level = Helper.getLevelFromDatabase(pcgLevel.getLevelId());
        }

        this.world.initializeLevel(level, 1000 * this.timer);
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

        if (!this.evaluation) {
            this.episodeReward = 0;
        } else {
            this.evaluationReward = 0;
        }
        return State.toByte(new MarioForwardModel(this.world.clone()));
    }

    public byte[] step(boolean[] action) throws Exception {
        miniStepEvents.clear();
        stepCount++;
        for (int i = 0; i < this.frameSkip; i++) {
            var events = miniStep(action);
            miniStepEvents.addAll(events);
        }
        if(stepCount > MAX_STEP && this.evaluation && !this.testMode && this.world.gameStatus != GameStatus.WIN){
            world.timeout();
            miniStepEvents.add(new MarioEvent(EventType.TIME_OUT, 0));
        }
        var nextWorldState = this.world.clone();
        var nextState = new MarioForwardModel(nextWorldState, miniStepEvents);
        float reward = RewardSystem.getReward(this.world, miniStepEvents);
        if (this.evaluation) {
            ArrayList<RewardEvent> miniStepRewardEvents = RewardSystem.logRewardEvent(this.world, miniStepEvents);
            rewardEvents.addAll(miniStepRewardEvents);

            this.evaluationReward += reward;
            this.evaluationTimer = this.world.currentTimer;
        } else {
            this.episodeReward += reward;
            this.episodeTimer = this.world.currentTimer;
        }

        if (this.world.gameStatus != GameStatus.RUNNING && this.evaluation && !this.testMode) {
            Helper.logEvaluationResultToDataBase(this.episode, this.world.gameStatus.toString(),
                    level, this.world, this.rewardEvents, this.evaluationReward, this.minTimer, this.maxTimer);
        }

        var isSuccess = isObjectiveSuccess();

        return State.stepResult(State.toByte(nextState), reward,
                this.world.gameStatus != GameStatus.RUNNING, false, isSuccess);
    }

    public ArrayList<MarioEvent> miniStep(boolean[] action) throws Exception {
        long currentTime = System.currentTimeMillis();
        this.world.update(action);
        if (visual) {
            int v = renderTarget.validate(render.getGraphicsConfiguration());
            if (v != VolatileImage.IMAGE_OK && v != VolatileImage.IMAGE_RESTORED) {
                renderTarget = this.render.createVolatileImage(MarioGame.width, MarioGame.height);
                backBuffer = this.render.getGraphics();
                currentBuffer = renderTarget.getGraphics();
            }
            this.render.renderWorld(this.world, renderTarget, backBuffer, currentBuffer);
        }

        if (this.fps > 0) {
            if (this.getDelay(this.fps) > 0) {
                try {
                    currentTime += this.getDelay(this.fps);
                    Thread.sleep(Math.max(0, currentTime - System.currentTimeMillis()));
                } catch (InterruptedException e) {

                }
            }
        }
        return this.world.lastFrameEvents;
    }

    public boolean isObjectiveSuccess() {
        return this.world.gameStatus == GameStatus.WIN;
    }

    private MarioEvent createEvent(MarioWorld world, EventType eventType, int eventParam) {
        int marioState = 0;
        if (world.mario.isLarge) {
            marioState = 1;
        }
        if (world.mario.isFire) {
            marioState = 2;
        }
        return new MarioEvent(eventType, eventParam, world.mario.x, world.mario.y, marioState, 0);
    }

    private int getDelay(int fps) {
        if (fps <= 0) {
            return 0;
        }
        return 1000 / fps;
    }
}