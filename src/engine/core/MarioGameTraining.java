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
import engine.helper.SpriteType;
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
    int frameSkip = 3;
    int episode = -1;
    int minTimer = 20;
    int maxTimer = 30;
    ArrayList<MarioEvent> miniStepEvents = new ArrayList<>();

    private int fps = 0;
    private ProceduralContentGenerationLevel pcg = null;
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
        if (this.visual) {
            setupWindow(info.getEpisode());
        }
        this.episode = info.getEpisode();

        // var levelFileName = info.getLevel();

        var b64Level = info.getLevel();
        byte[] decodedBytes = Base64.getDecoder().decode(b64Level);
        String jsonString = new String(decodedBytes, StandardCharsets.UTF_8);
        Gson gson = new GsonBuilder().create();
        PCGLevelDto pcgLevel = gson.fromJson(jsonString, PCGLevelDto.class);
        if (pcgLevel.getFps() > 20) {
            this.fps = pcgLevel.getFps();
        }

        this.rewardEvents = new ArrayList<>();
        this.evaluation = info.isEvaluation();
        this.world = new MarioWorld(this.killEvents);
        this.world.visuals = visual;
        this.minTimer = pcgLevel.getTimerMin();
        this.maxTimer = pcgLevel.getTimerMax();
        this.timer = rand.nextInt(this.minTimer,this.maxTimer);
        this.lastMilestone = 0;
        this.lastCoinCount = 0;
        String level;
        String file = pcgLevel.getFile();
        if (file == null) {
            this.pcg = ProceduralContentGenerationLevel.parseLevel(pcgLevel);
            this.pcg.generate(false);
            level = this.pcg.getContent();
        } else {
            level = Helper.getFileFromLevel(file);
            this.timer = 100;
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
        for (int i = 0; i < this.frameSkip; i++) {
            var events = miniStep(action);
            miniStepEvents.addAll(events);
        }
        var nextWorldState = this.world.clone();
        var nextState = new MarioForwardModel(nextWorldState, miniStepEvents);
        float reward = RewardSystem.getReward(this.world, miniStepEvents);

        if (this.evaluation) {
            logRewardEvent(miniStepEvents, this.world.gameStatus);
            this.evaluationReward += reward;
            this.evaluationTimer = this.world.currentTimer;
        } else {
            this.episodeReward += reward;
            this.episodeTimer = this.world.currentTimer;
        }

        if(this.world.gameStatus != GameStatus.RUNNING && this.evaluation && this.fps < 30){

            Helper.logEvaluationResult(this.episode, this.world.gameStatus.toString(),
                    this.pcg, this.world, this.rewardEvents, this.evaluationReward, this.minTimer, this.maxTimer);
        }

        return State.stepResult(State.toByte(nextState), reward,
                this.world.gameStatus != GameStatus.RUNNING,
                false);
    }

    private void logRewardEvent(ArrayList<MarioEvent> miniStepEvents, GameStatus gameStatus) {
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.BUMP_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                rewardEvents.add(new RewardEvent(RewardSystem.KILL_REWARD, e));
            } else if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                rewardEvents.add(new RewardEvent(RewardSystem.POWER_UP_REWARD, e));
            } else if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                rewardEvents.add(new RewardEvent(RewardSystem.POWER_UP_REWARD, e));
            } else if (e.getEventType() == EventType.BUMP.getValue() && e.getEventParam() == MarioForwardModel.OBS_QUESTION_BLOCK) {
                rewardEvents.add(new RewardEvent(RewardSystem.BUMP_REWARD, e));
            } else if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) { // COIN
                rewardEvents.add(new RewardEvent(RewardSystem.COIN_REWARD, e));
            } else if(e.getEventType() == EventType.EXPLORER.getValue()){
                var tileY = (int)(e.getMarioY()/16f);
                var grade = tileY < 6 ? 2f : tileY < 10 ? 1.5f : 1f;
                rewardEvents.add(new RewardEvent(RewardSystem.EXPLORATION_REWARD * grade, e));
            } else if(e.getEventType() == EventType.WIN.getValue()){
                var remainTask = world.getUnbumpBlocks().size() +
                        world.getUnCollectCoin().size() +
                        world.getAliveEnemies().size();

                int totalTasksInThisLevel = world.level.getBumpableBlocks().size()
                        + world.level.getCoins().size() + world.level.getEnemies().size();
                float taskBonusForThisLevel = 0;
                if (totalTasksInThisLevel > 0) {
                    taskBonusForThisLevel = (RewardSystem.TARGET_PERFECT_REWARD - RewardSystem.WIN_REWARD) / totalTasksInThisLevel;
                }
                int tasksCompleted = totalTasksInThisLevel - remainTask;
                float winReward = RewardSystem.WIN_REWARD + (tasksCompleted * taskBonusForThisLevel);
                rewardEvents.add(new RewardEvent(winReward, e));
            } else {
                rewardEvents.add(new RewardEvent(0, e));
            }
        }

        if (gameStatus.equals(GameStatus.TIME_OUT)) {
            float timeoutReward = RewardSystem.TIMEOUT_PENALTY;
            int marioState = 0;
            if (this.world.mario.isLarge) {
                marioState = 1;
            }
            if (this.world.mario.isFire) {
                marioState = 2;
            }
            rewardEvents.add(new RewardEvent(timeoutReward,
                    new MarioEvent(EventType.TIME_OUT, 0, this.world.mario.x,
                            this.world.mario.y, marioState, this.world.currentTick)));
        }
    }

//    private float getTimeoutReward() {
//        float reward = 0;
//        var debt = world.getUnbumpBlocks().size() +
//                world.getUnCollectCoin().size() +
//                world.getAliveEnemies().size();
//        float penalty = Math.min(RewardSystem.TIMEOUT_PENALTY, RewardSystem.DEBT_PENALTY_FACTOR * debt);
//        reward += penalty;
//        return reward;
//    }

    public ArrayList<MarioEvent> miniStep(boolean[] action) throws Exception {
        long currentTime = System.currentTimeMillis();
        this.world.update(action);
        if (visual) {
            int v = renderTarget.validate(render.getGraphicsConfiguration());
            if(v != VolatileImage.IMAGE_OK && v != VolatileImage.IMAGE_RESTORED){
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

    private int getDelay(int fps) {
        if (fps <= 0) {
            return 0;
        }
        return 1000 / fps;
    }
}