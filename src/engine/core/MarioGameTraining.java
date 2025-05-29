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

    /**
     * Create a mario game with a different forward model where the player on certain event
     *
     * @param killEvents events that will kill the player
     */
    public MarioGameTraining(MarioEvent[] killEvents) {
        this.killEvents = killEvents;
    }

    private int getDelay(int fps) {
        if (fps <= 0) {
            return 0;
        }
        return 1000 / fps;
    }

//    private void setAgent(MarioAgentPy4j agent) {
//        this.agent = agent;
//        if (agent instanceof KeyAdapter) {
//            this.render.addKeyListener((KeyAdapter) this.agent);
//        }
//    }

    /**
     * Run a certain mario level with a certain agent
     *
     * @param agent      the current AI agent used to play the game
     * @param level      a string that constitutes the mario level, it uses the same representation as the VGLC but with more details. for more details about each symbol check the json file in the levels folder.
     * @param timer      number of ticks for that level to be played. Setting timer to anything &lt;=0 will make the time infinite
     * @param marioState the initial state that mario appears in. 0 small mario, 1 large mario, and 2 fire mario.
     * @param visuals    show the game visuals if it is true and false otherwise
     * @param fps        the number of frames per second that the update function is following
     * @param scale      the screen scale, that scale value is multiplied by the actual width and height
     * @return statistics about the current game
     */
    public MarioTrainingResult runGame(MarioAgentPy4j agent,
                                       String level,
                                       int episode,
                                       int timer,
                                       int marioState,
                                       boolean visuals,
                                       int fps,
                                       float scale,
                                       boolean evaluation) {
        if (visuals) {
            if(this.window == null){
                this.window = new JFrame("Mario AI Framework");
                this.window.setFocusableWindowState(false);
            }
            this.render = new MarioRender(scale);
            this.window.setContentPane(this.render);
            this.window.pack();
            this.window.setResizable(false);
            this.window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            this.render.init();
            this.window.setVisible(true);
        }
        //this.setAgent(agent);
        return this.gameLoop(level, episode, timer, marioState, visuals, fps, evaluation);
    }

    int minReward = -25;
    int maxReward = 25;

    private MarioTrainingResult gameLoop(String level, int episode, int timer, int marioState, boolean visual, int fps, boolean evaluation) {
        this.world = new MarioWorld(this.killEvents);
        this.world.visuals = visual;
        this.world.initializeLevel(level, 1000 * timer);
        if (visual) {
            this.world.initializeVisuals(this.render.getGraphicsConfiguration());
        }
        this.world.mario.isLarge = marioState > 0;
        this.world.mario.isFire = marioState > 1;
        this.world.update(new boolean[MarioActions.numberOfActions()]);
        long currentTime = System.currentTimeMillis();

        //initialize graphics
        VolatileImage renderTarget = null;
        Graphics backBuffer = null;
        Graphics currentBuffer = null;
        if (visual) {
            renderTarget = this.render.createVolatileImage(MarioGameTraining.width, MarioGameTraining.height);
            backBuffer = this.render.getGraphics();
            currentBuffer = renderTarget.getGraphics();
            this.render.addFocusListener(this.render);
        }

//        MarioTimer agentTimer = new MarioTimer(MarioGameTraining.maxTime);
//        this.agent.initialize(new MarioForwardModel(this.world.clone()), agentTimer);
        int lastProgress = 0;
        int lastMilestone = 0;
        var start = timer * 1000L;
        var remaining = start;
        long interval = 20000; // 5 seconds in milliseconds
        long nextTrigger = start - interval;
        float expectPositionX = 0;
        int distanceNeedToAdvance = 1;
        int frame = 0;
        int frameSkip = 6;
        float currentPosition = this.world.mario.x;
        int t = timer;
        ArrayList<MarioEvent> gameEvents = new ArrayList<>();
        var actions = new boolean[MarioActions.numberOfActions()];

        while (this.world.gameStatus == GameStatus.RUNNING) {
            float reward = 0;
            if (!this.pause) {
                //get actions
                agentTimer = new MarioTimer(MarioGameTraining.maxTime);
                MarioForwardModel state = new MarioForwardModel(this.world.clone());
                state.evaluation = evaluation;
                if (MarioGameTraining.verbose) {
                    if (agentTimer.getRemainingTime() < 0 && Math.abs(agentTimer.getRemainingTime()) > MarioGameTraining.graceTime) {
                        System.out.println("The Agent is slowing down the game by: "
                                + Math.abs(agentTimer.getRemainingTime()) + " msec.");
                    }
                }

                remaining -= 30;
                if (remaining <= nextTrigger) {
                    if(expectPositionX > state.getMarioFloatPos()[0]){
                        this.world.timeout();
                    } else {
                        expectPositionX = state.getMarioFloatPos()[0] + distanceNeedToAdvance;
                    }
                    nextTrigger -= interval;
                }

//                if(t > this.world.currentTimer/1000){
//                    reward -= 1;
//                    t = this.world.currentTimer;
//                }

                if(frame % frameSkip == 0){
                    //actions = this.agent.getActions(state, agentTimer);
                }

                // update world
                this.world.update(actions);
                gameEvents.addAll(this.world.lastFrameEvents);

                double completePercentage = this.world.mario.x / (this.world.level.exitTileX * 16);
//                int progress = (int)(completePercentage * 100);       // 0..10
//                if (progress > lastProgress) {
//                    reward = 0.01f;
//                    lastProgress = progress;
//                }

                int milestone = (int)(completePercentage * 10);       // 0..10
                if (milestone > lastMilestone && (milestone == 3 || milestone == 4 || milestone == 6
                || milestone == 8)) {
                    //reward += 1f;
                    lastMilestone = milestone;
                    if(milestone == 3){
                        this.world.win();
                    }
                }

                if (this.world.gameStatus == GameStatus.LOSE || this.world.gameStatus == GameStatus.TIME_OUT) {
                    reward -= 1f;
                } else if (this.world.gameStatus == GameStatus.WIN) {
                    reward += 1f;
                }

                this.world.reward += reward;

                if(!evaluation){
                    MarioForwardModel nextState = new MarioForwardModel(this.world.clone());
                    //this.agent.update(actions, state, nextState, reward, this.world.gameStatus != GameStatus.RUNNING);
                }
                frame += 1;
            }

            //render world
            if (visual) {
                this.render.renderWorld(this.world, renderTarget, backBuffer, currentBuffer);
            }
            //check if delay needed
            if (this.getDelay(fps) > 0) {
                try {
                    currentTime += this.getDelay(fps);
                    var sleep = Math.max(0, currentTime - System.currentTimeMillis());
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        return new MarioTrainingResult(this.world, gameEvents);
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
    int episode = -1;

    boolean evaluation = false;
    ArrayList<MarioEvent> gameEvents;

    EndInfo episodeInfo = new EndInfo();
    EndInfo evaluationInfo = new EndInfo();

    public float evaluationReward = 0;
    public float episodeReward = 0;
    int episodeTimer = 0;
    int evaluationTimer = 0;
    public byte[] reset(Info info) throws Exception {
        this.gameEvents = new ArrayList<>();
        if(!info.isEvaluation()){
            this.episode = info.getEpisode();
        }
        this.evaluation = info.isEvaluation();
        this.world = new MarioWorld(this.killEvents);
        this.world.visuals = visual;
        this.timer = 30;
        this.lastMilestone = 0;
        this.world.initializeLevel(getTrainingLevel(1), 1000 * this.timer);
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

        // timeout section
        var start = timer * 1000L;
//        this.remaining = start;
//        this.interval = 5000; // 5 seconds in milliseconds
//        this.nextTrigger = start - interval;
//        this.expectPositionX = this.world.mario.x;

        if(!this.evaluation){
            this.episodeReward = 0;
        } else {
            this.evaluationReward = 0;
        }
        return State.toByte(new MarioForwardModel(this.world.clone()));
    }

    public byte[] step(boolean[] action) throws Exception {
        float reward = 0;
        for (int i = 0; i < 7; i++) {
            miniStep(action);
        }
//        this.remaining -= 30;
//        if (this.remaining <= this.nextTrigger) {
//            if(this.expectPositionX > this.world.mario.x){
//                this.world.timeout();
//            } else {
//                this.expectPositionX = this.world.mario.x + 1;
//            }
//            this.nextTrigger -= this.interval;
//        }

        //var mileStoneReward = mileStoneReward();
        //var timePenalty = timePenalty();
        //reward += mileStoneReward;
        //reward += timePenalty;

        if (this.world.gameStatus == GameStatus.LOSE || this.world.gameStatus == GameStatus.TIME_OUT) {
            reward -= 1;
        } else if (this.world.gameStatus == GameStatus.WIN) {
            reward += 1;
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
        var nextState = State.toByte(new MarioForwardModel(this.world.clone()));
        return stepResult(nextState, reward, this.world.gameStatus != GameStatus.RUNNING);
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

    private float timePenalty() {
        float reward = this.world.currentTimer - this.currentTimer;
        this.currentTimer = this.world.currentTimer;
        if(reward >= 0){
            return 0;
        }
        return reward/1000f;
    }

    private double mileStoneReward() {
        double completePercentage = this.world.mario.x / (this.world.level.exitTileX * 16);
        int milestone = (int)(completePercentage * 10);       // 0..10
        if (milestone > lastMilestone) {
            lastMilestone = milestone;
            return 0.1f;
        }
        return 0;
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
