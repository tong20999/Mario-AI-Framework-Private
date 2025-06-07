package engine.core;

import java.awt.image.VolatileImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.awt.*;

import javax.swing.JFrame;

import agents.myAgentMachineLearning.State;
import engine.helper.EventType;
import engine.helper.GameStatus;
import engine.helper.MarioActions;
import engine.helper.SpriteType;
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
    /**
     * Create a mario game to be played
     */
    public MarioGameTraining() {
        this.window = new JFrame("Mario AI Framework");
        this.window.setFocusableWindowState(false);
        this.render = new MarioRender(2);
        this.window.setContentPane(this.render);
        this.window.pack();
        this.window.setResizable(false);
        this.window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.render.init();
        this.window.setVisible(true);
    }

    MarioTimer agentTimer;
    int timer;

    //initialize graphics
    VolatileImage renderTarget = null;
    Graphics backBuffer = null;
    Graphics currentBuffer = null;
    boolean visual = true;
    int currentTimer;

    // idle kill
//    int interval = 10000;
//    long remaining = 0;
//    long nextTrigger = 0;
//    float expectPositionX = 0;
    int lastMilestone;
    int lastCoinCount;
    int episode = -1;

    boolean evaluation = false;
    ArrayList<MarioEvent> gameEvents;

    EndInfo episodeInfo = new EndInfo();
    EndInfo evaluationInfo = new EndInfo();

    public float evaluationReward = 0;
    public float episodeReward = 0;
    int episodeTimer = 0;
    int evaluationTimer = 0;

    float epsilon;
    public byte[] reset(Info info) throws Exception {
        this.gameEvents = new ArrayList<>();
        if(!info.isEvaluation()){
            this.episode = info.getEpisode();
        }
        this.evaluation = info.isEvaluation();
        this.world = new MarioWorld(this.killEvents);
        this.world.visuals = visual;
        this.timer = 40;
        this.lastMilestone = 0;
        this.lastCoinCount = 0;
        this.world.initializeLevel(getTrainingLevel(2), 1000 * this.timer);
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
        this.currentTimer = this.world.currentTimer;

        if(!this.evaluation){
            this.episodeReward = 0;
        } else {
            this.evaluationReward = 0;
        }
        this.epsilon = info.getEpsilon();
        this.world.epsilon = this.epsilon;
        return State.toByte(new MarioForwardModel(this.world.clone()));
    }

    public byte[] step(boolean[] action) throws Exception {
        MarioForwardModel modelBefore = new MarioForwardModel(this.world.clone());
        // for frame skip the agent will only send one action per 3 frames to make agent jump longer
        // because it needs to hold the jump button
        for (int i = 0; i < 4; i++) {
            miniStep(action);
        }

        // --- 3. Get the state of the world AFTER the action is taken ---
        MarioForwardModel modelAfter = new MarioForwardModel(this.world.clone());

        // =================================================================================
        // --- "COMPLETIONIST" REWARD LOGIC ---
        // =================================================================================
        float reward = 0.0f;
        // --- 1. Small penalty for time passing to encourage efficiency ---
        reward -= 0.015f;

        // --- 2. Reward for skillful actions (building the "score") ---
        // Milestone progress reward (fixed bonus per milestone)
        int currentMilestone = (int)((modelAfter.getCompletionPercentage()) * 10);
        if (currentMilestone > this.lastMilestone) {
            reward += (currentMilestone - this.lastMilestone) * 1.0f;

            this.lastMilestone = currentMilestone;
        }

        // Coin collection reward
        int currentCoins = modelAfter.getNumCollectedCoins();
        if (currentCoins > this.lastCoinCount) {
            reward += (currentCoins - this.lastCoinCount) * 0.5f; // +0.5 reward per coin
            this.lastCoinCount = currentCoins;
        }

        // Event-based rewards (kills, power-ups) and penalties (hurt, walls)
        for (MarioEvent e : this.world.lastFrameEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() || e.getEventType() == EventType.FIRE_KILL.getValue()) {
                reward += 2.0f; // +2 reward per kill
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                reward += 5.0f; // +5 for a power-up
            }
            if (e.getEventType() == EventType.HURT.getValue()) {
                reward -= 1.0f; // -1 for taking damage
            }
            if (e.getEventType() == EventType.HIT_WALL.getValue()) {
                reward -= 0.2f;
            }
        }

        // --- 3. Define the outcome: Keep your score or lose it all ---
        if (this.world.gameStatus == GameStatus.WIN) {
            // The reward for winning is that you get to keep the score you earned.
            // We can add a small bonus to break ties, but the bulk of the score is from the run itself.
            reward += 10.0f;
        }
        else if (this.world.gameStatus == GameStatus.LOSE || this.world.gameStatus == GameStatus.TIME_OUT) {
            // A massive penalty that ensures any failure is always worse than even the "laziest" win.
            reward -= 50.0f;
        }

        if(this.evaluation){
            this.evaluationReward += reward;
            this.evaluationTimer = this.world.currentTimer;
        } else {
            this.episodeReward += reward;
            this.episodeTimer = this.world.currentTimer;
        }

        this.world.reward += reward;

        this.world.episode = this.evaluation ? -1 : this.episode;
        printInfo();
        var nextState = State.toByte(modelAfter);
        return stepResult(nextState, reward, this.world.gameStatus != GameStatus.RUNNING);
    }

    private float timePenalty() {
        float reward = this.world.currentTimer - this.currentTimer;
        this.currentTimer = this.world.currentTimer;
        if(reward >= 0){
            return 0;
        }
        var timePenalty = reward * 0.0002f;
        return timePenalty;
    }

    private double mileStoneReward() {
        double completePercentage = this.world.mario.x / (this.world.level.exitTileX * 16.0);
        int milestone = (int)(completePercentage * 10);
        if (milestone > lastMilestone) {
            lastMilestone = milestone;
            return milestone;
        }
        return 0;
    }

    private void printInfo() {
        if(this.world.gameStatus != GameStatus.RUNNING){
            String msg = "";
            int fallKill = gameEvents.stream()
                    .anyMatch(e -> e.getEventType() == EventType.FALL_KILL.getValue()) ? 1 : 0;

            // hurt may not dead
            int hurt = (int) gameEvents.stream()
                    .filter(e -> e.getEventType() == EventType.HURT.getValue())
                    .count();

            int lose = this.world.gameStatus == GameStatus.LOSE ? 1 : 0;

            int timeout = this.world.gameStatus == GameStatus.TIME_OUT ? 1 : 0;

            int win = gameEvents.stream()
                    .anyMatch(e -> e.getEventType() == EventType.WIN.getValue()) ? 1 : 0;

            if(this.evaluation){
                evaluationInfo.win += win;
                evaluationInfo.lose += lose;
                evaluationInfo.hurt += hurt;
                evaluationInfo.fallKill += fallKill;
                evaluationInfo.timeout += timeout;

            }
            else {
                episodeInfo.win += win;
                episodeInfo.lose += lose;
                episodeInfo.hurt += hurt;
                episodeInfo.fallKill += fallKill;
                episodeInfo.timeout += timeout;

            }

            if(this.evaluation){
                var evaluationMsg = MessageFormat.format("Evaluation {0} time {1} reward {2}", evaluationInfo, this.evaluationTimer/1000, String.format("%.2f", this.evaluationReward));
                var episodeMsg = MessageFormat.format("Episode {0} {1} time {2} reward {3}", this.episode, episodeInfo, this.episodeTimer/1000, String.format("%.2f", this.episodeReward));
                System.out.println(episodeMsg + "   |   " + evaluationMsg);
            }
        }
    }

    public void miniStep(boolean[] action) throws Exception {
        this.world.update(action);
        this.gameEvents.addAll(this.world.lastFrameEvents);
        if (visual) {
            this.render.renderWorld(this.world, renderTarget, backBuffer, currentBuffer);
        }
    }

    private static byte[] stepResult(byte[] nextState, float reward, boolean is_terminate) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + nextState.length);
        buffer.put(float2ByteArray(reward));
        buffer.put((byte)(is_terminate ? 1 : 0));
        buffer.put(nextState);
        return buffer.array();
    }

    public static byte [] float2ByteArray (float value)
    {
        return ByteBuffer.allocate(4).putFloat(value).array();
    }

    private static String getFileFromLevel(String file){
        String content = "";
        try {
            content = new String(Files.readAllBytes(Paths.get(file)));
        } catch (IOException e) {
        }
        return content;
    }

    public static String getFirstLevel(){
        var level1 = "./levels/original/lvl-1.txt";
        return getFileFromLevel(level1);
    }

    public static String getTrainingLevel(int level){
        var levelLocation = MessageFormat.format("./levels/training/lvl-{0}.txt", level);
        return getFileFromLevel(levelLocation);
    }

    public static String getEvaluationLevel(int level){
        var levelLocation = MessageFormat.format("./levels/evaluation/lvl-{0}.txt", level);
        return getFileFromLevel(levelLocation);
    }
}
