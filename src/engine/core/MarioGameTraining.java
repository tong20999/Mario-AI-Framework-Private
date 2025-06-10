package engine.core;

import java.awt.image.VolatileImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.awt.*;
import java.util.stream.IntStream;

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
    boolean wasOverPit = false;
    boolean enableJumpReward = true;
    int jumpRewardTimer = 0;
    float epsilon;

    float winReward = 50;
    float mileStoneReward = 0.1f;
    float jumpOverPitReward = 0f;
    float killReward = 2;
    float coinReward = 2;
    float fireworkReward = 5;
    float mushroomReward = 2;
    float lifeMushroomReward = 5;
    float loseReward = -15f;
    float loseTimeoutReward = -25f;
    float hurtReward = -1f;
    float hitWallReward = -0.25f;
    float timePenaltyRewardCoefficient = 0.00005f;
    public byte[] reset(Info info) throws Exception {
        this.gameEvents = new ArrayList<>();
        if(!info.isEvaluation()){
            this.episode = info.getEpisode();
        }
        this.evaluation = info.isEvaluation();
        this.world = new MarioWorld(this.killEvents);
        this.world.visuals = visual;
        this.timer = 100;
        this.lastMilestone = 0;
        this.lastCoinCount = 0;
        this.wasOverPit = false;
        this.world.initializeLevel(getTrainingLevel("9"), 1000 * this.timer);
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
        this.currentTimer = this.jumpRewardTimer = this.world.currentTimer;


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
        var state = new MarioForwardModel(this.world.clone());
        // for frame skip the agent will only send one action per 3 frames to make agent jump longer
        // because it needs to hold the jump button
        for (int i = 0; i < 4; i++) {
            miniStep(action);
        }

        resetJumpReward(action);

        // --- 3. Get the state of the world AFTER the action is taken ---
        MarioForwardModel nextState = new MarioForwardModel(this.world.clone());

        // =================================================================================
        // --- "COMPLETIONIST" REWARD LOGIC ---
        // =================================================================================
        float reward = 0.0f;
        // --- 1. Small penalty for time passing to encourage efficiency ---
        reward += timePenalty();
        reward += mileStoneReward();
        reward += jumpOverPit(nextState);

        // Coin collection reward
        int currentCoins = nextState.getNumCollectedCoins();
        if (currentCoins > this.lastCoinCount) {
            reward += coinReward; // +0.5 reward per coin
            this.lastCoinCount = currentCoins;
        }

        // Event-based rewards (kills, power-ups) and penalties (hurt, walls)
        for (MarioEvent e : this.world.lastFrameEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                reward += killReward; // +2 reward per kill
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                reward += fireworkReward; // +5 for a power-up
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                reward += mushroomReward; // +2 for a mushroom
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.LIFE_MUSHROOM.getValue()) {
                reward += lifeMushroomReward; // +5 for a life
            }
            if (e.getEventType() == EventType.HURT.getValue()) {
                reward += hurtReward; // -1 for taking damage
            }
            if (e.getEventType() == EventType.HIT_WALL.getValue()) {
                reward += hitWallReward;
            }
        }

        // --- 3. Define the outcome: Keep your score or lose it all ---
        if (this.world.gameStatus == GameStatus.WIN) {
            // The reward for winning is that you get to keep the score you earned.
            // We can add a small bonus to break ties, but the bulk of the score is from the run itself.
            reward += winReward;
        } else if (this.world.gameStatus == GameStatus.TIME_OUT) {
            // A massive penalty that ensures any failure is always worse than even the "laziest" win.
            reward += loseTimeoutReward;
        } else if (this.world.gameStatus == GameStatus.LOSE) {
            // A massive penalty that ensures any failure is always worse than even the "laziest" win.
            reward += loseReward;
            reward += distanceToFlag(nextState);
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
        return stepResult(State.toByte(nextState), reward, this.world.gameStatus != GameStatus.RUNNING);
    }

    private float distanceToFlag(MarioForwardModel model) {
        var complete = model.getCompletionPercentage() * 10;
        return complete;
    }

    private void resetJumpReward(boolean[] action){
        // Prevent jump exploit
        var timePassSecond = (this.jumpRewardTimer - this.world.currentTimer) * 0.001;

        if(timePassSecond > 0.5 && action[4]){
            this.enableJumpReward = true;
        }
    }

    private float jumpOverPit(MarioForwardModel model) {
        var isOnGround = model.isMarioOnGround();
        float reward = 0;
        if (isOnGround && this.wasOverPit && this.enableJumpReward) {
            this.wasOverPit = false;
            this.enableJumpReward = false;
            this.jumpRewardTimer = this.world.currentTimer;
            reward = jumpOverPitReward;
        }

        int[][] observation = model.getMarioCompleteObservation(0, 0);
        var tileUnderMario = IntStream.range(10, 15)
                .allMatch(row -> observation[8][row] == 0);

        if (tileUnderMario) {
            // If the tile below is a pit, set the flag for the next time step.
            this.wasOverPit = true;
        }

        return reward;
    }

    private float timePenalty() {
        float reward = this.world.currentTimer - this.currentTimer;
        this.currentTimer = this.world.currentTimer;
        if(reward >= 0){
            return 0;
        }
        var timePenalty = reward * timePenaltyRewardCoefficient;
        return timePenalty;
    }

    private double mileStoneReward() {
        double completePercentage = this.world.mario.x / (this.world.level.exitTileX * 16.0);
        int milestone = (int)(completePercentage * 100);
        if (milestone > lastMilestone) {
            lastMilestone = milestone;
            return mileStoneReward;
        }
        return 0;
    }

    private void printInfo() {
        if(this.world.gameStatus != GameStatus.RUNNING){
            long fallKill = gameEvents.stream()
                    .filter(e -> e.getEventType() == EventType.FALL_KILL.getValue())
                    .count();

            long shellKill = gameEvents.stream()
                    .filter(e -> e.getEventType() == EventType.SHELL_KILL.getValue())
                    .count();

            long stompKill = gameEvents.stream()
                    .filter(e -> e.getEventType() == EventType.STOMP_KILL.getValue())
                    .count();

            long fireKill = gameEvents.stream()
                    .filter(e -> e.getEventType() == EventType.FIRE_KILL.getValue())
                    .count();

            // hurt may not dead
            int hurt = (int) gameEvents.stream()
                    .filter(e -> e.getEventType() == EventType.HURT.getValue())
                    .count();

            int collect = (int) gameEvents.stream()
                    .filter(e -> e.getEventType() == EventType.COLLECT.getValue())
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
                evaluationInfo.stompKill += stompKill;
                evaluationInfo.shellKill += shellKill;
                evaluationInfo.fireKill += fireKill;
                evaluationInfo.collect += collect;
                evaluationInfo.timeout += timeout;
            }
            else {
                episodeInfo.win += win;
                episodeInfo.lose += lose;
                episodeInfo.hurt += hurt;
                episodeInfo.fallKill += fallKill;
                episodeInfo.stompKill += stompKill;
                episodeInfo.shellKill += shellKill;
                episodeInfo.fireKill += fireKill;
                episodeInfo.collect += collect;
                episodeInfo.timeout += timeout;
            }

            if(this.evaluation){
                var evaluationMsg = MessageFormat.format("Evaluation {0} time {1} reward {2}", evaluationInfo, this.evaluationTimer/1000, String.format("%.2f", this.evaluationReward));
                var episodeMsg = MessageFormat.format("Episode {0} {1} time {2} reward {3}", this.episode, episodeInfo, this.episodeTimer/1000, String.format("%.2f", this.episodeReward));
                System.out.println(episodeMsg + "   |   " + evaluationMsg);
                var rewardTable = getRewardsInformation();
                if(this.episode == 500 || this.episode == 1000){
                    try (PrintWriter out = new PrintWriter("C:\\thesis_data\\result.txt")) {
                        out.println(episodeMsg);
                        out.println(evaluationMsg);
                        out.println(rewardTable);
                    } catch (IOException e) {
                        System.err.println("Error writing to file: " + e.getMessage());
                    }
                }
            }
        }
    }

    private String getRewardsInformation() {
        return MessageFormat.format("""
                        REWARDS
                        WIN {0}
                        LOSE {1}
                        TIME_OUT {2}
                        MILESTONE {3}
                        JumpOverPit {4}
                        KILL {5}
                        COLLECT_COIN {6}
                        COLLECT_FIREWORK {7}
                        COLLECT_MUSHROOM {8}
                        COLLECT_LIFE_MUSHROOM {9}
                        HURT {10}
                        HIT_WALL {11}
                        TIME_PENALTY_COEFFICIENT {12}
                        """,
                this.winReward,
                this.loseReward,
                this.loseTimeoutReward,
                this.mileStoneReward,
                this.jumpOverPitReward,
                this.killReward,
                this.coinReward,
                this.fireworkReward,
                this.mushroomReward,
                this.lifeMushroomReward,
                this.hurtReward,
                this.hitWallReward,
                this.timePenaltyRewardCoefficient);
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

    public static String getTrainingLevel(String level){
        var levelLocation = MessageFormat.format("./levels/training/lvl-{0}.txt", level);
        return getFileFromLevel(levelLocation);
    }

    public static String getEvaluationLevel(int level){
        var levelLocation = MessageFormat.format("./levels/evaluation/lvl-{0}.txt", level);
        return getFileFromLevel(levelLocation);
    }


    private String getOriginalLevel(int level) {
        var levelLocation = MessageFormat.format("./levels/original/lvl-{0}.txt", level);
        return getFileFromLevel(levelLocation);
    }
}
