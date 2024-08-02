package engine.core;

import engine.helper.GameStatus;
import engine.helper.MarioActions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.image.VolatileImage;
import java.util.ArrayList;

public class MarioGamePlayStep extends MarioGame {

    /**
     * events that kills the player when it happens only care about type and param
     */
    private MarioEvent[] killEvents;

    //visualization
    private JFrame window = null;
    private MarioRender render = null;
    private MarioAgent agent = null;
    private MarioWorld world = null;

    /**
     * Create a mario game to be played
     */
    public MarioGamePlayStep() {

    }

    /**
     * Create a mario game with a different forward model where the player on certain event
     *
     * @param killEvents events that will kill the player
     */
    public MarioGamePlayStep(MarioEvent[] killEvents) {
        this.killEvents = killEvents;
    }

    private int getDelay(int fps) {
        if (fps <= 0) {
            return 0;
        }
        return 1000 / fps;
    }

    private void setAgent(MarioAgent agent) {
        this.agent = agent;
        if (agent instanceof KeyAdapter) {
            this.render.addKeyListener((KeyAdapter) this.agent);
        }
    }

    MarioTimer agentTimer = new MarioTimer(MarioGamePlayStep.maxTime);
    ArrayList<MarioEvent> gameEvents = new ArrayList<>();
    ArrayList<MarioAgentEvent> agentEvents = new ArrayList<>();
    VolatileImage renderTarget = null;
    Graphics backBuffer = null;
    Graphics currentBuffer = null;
    long currentTime = System.currentTimeMillis();
    long currentTime1 = System.currentTimeMillis();
    int fps = 30;
    float lastPositionX;
    float expectPositionX = 200;

    public void reset(MarioAgent agent, String level,int timer, int marioState, boolean visual){
        if (visual) {
            if(this.window == null){
                this.window = new JFrame("Mario AI Framework");
                this.render = new MarioRender(2);
                this.window.setContentPane(this.render);
                this.window.pack();
                this.window.setResizable(false);
                this.window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                this.render.init();
            }

            this.window.setVisible(true);
        }
        this.setAgent(agent);
        this.world = new MarioWorld(this.killEvents);
        this.world.visuals = visual;
        this.world.initializeLevel(level, 1000 * timer);
        if (visual) {
            this.world.initializeVisuals(this.render.getGraphicsConfiguration());
        }
        this.world.mario.isLarge = marioState > 0;
        this.world.mario.isFire = marioState > 1;
        this.world.update(new boolean[MarioActions.numberOfActions()]);
        this.currentTime = System.currentTimeMillis();

        //initialize graphics
        this.renderTarget = null;
        this.backBuffer = null;
        this.currentBuffer = null;
        if (visual) {
            this.renderTarget = this.render.createVolatileImage(MarioGamePlayStep.width, MarioGamePlayStep.height);
            this.backBuffer = this.render.getGraphics();
            this.currentBuffer = this.renderTarget.getGraphics();
            this.render.addFocusListener(this.render);
        }

        this.agentTimer = new MarioTimer(MarioGamePlayStep.maxTime);
        this.agent.initialize(new MarioForwardModel(this.world.clone()), agentTimer);

        this.gameEvents = new ArrayList<>();
        this.agentEvents = new ArrayList<>();

        var cloneModel = new MarioForwardModel(this.world.clone());
        lastPositionX = cloneModel.getMarioFloatPos()[0];
        currentTime1 = System.currentTimeMillis();
        expectPositionX = 200;
    }

    private boolean[] arrayListToArray(ArrayList<Boolean> listActions){
        boolean[] actions = new boolean[5];
        for (int i = 0; i < listActions.size(); i++) {
            actions[i] = listActions.get(i);
        }
        return actions;
    }

    public MarioPlayStepResult playStep(boolean visual, ArrayList<Boolean> listActions){
        if(this.world.gameStatus == GameStatus.RUNNING) {
            if (!this.pause) {
                //get actions
                this.agentTimer = new MarioTimer(MarioGamePlayStep.maxTime);
                boolean[] actions = arrayListToArray(listActions);
                //boolean[] actions = this.agent.getActions(new MarioForwardModel(this.world.clone()), agentTimer);
                if (MarioGamePlayStep.verbose) {
                    if (this.agentTimer.getRemainingTime() < 0 && Math.abs(this.agentTimer.getRemainingTime()) > MarioGamePlayStep.graceTime) {
                        System.out.println("The Agent is slowing down the game by: "
                                + Math.abs(this.agentTimer.getRemainingTime()) + " msec.");
                    }
                }
                // update world
                this.world.update(actions);
                this.gameEvents.addAll(this.world.lastFrameEvents);
                this.agentEvents.add(new MarioAgentEvent(actions, this.world.mario.x,
                        this.world.mario.y, (this.world.mario.isLarge ? 1 : 0) + (this.world.mario.isFire ? 1 : 0),
                        this.world.mario.onGround, this.world.currentTick));
            }

            //render world
            if (visual) {
                this.render.renderWorld(this.world, this.renderTarget, this.backBuffer, this.currentBuffer);
            }
            //check if delay needed
            if (this.getDelay(this.fps) > 0) {
                try {
                    this.currentTime += this.getDelay(this.fps);
                    Thread.sleep(Math.max(0, this.currentTime - System.currentTimeMillis()));
                } catch (InterruptedException e) {

                }
            }
        }
        var diff = System.currentTimeMillis() - this.currentTime1;
        if(diff > 5000){
            this.currentTime1 = System.currentTimeMillis();
            if(expectPositionX > lastPositionX){
                this.world.lose();
            } else {
                expectPositionX += 200;
            }
        }
        var reward = calculateReward(new MarioForwardModel(this.world.clone()));
        return new MarioPlayStepResult(reward, this.world.gameStatus == GameStatus.LOSE, 0);
    }

    private int calculateReward(MarioForwardModel model){
        int reward = 0;
        var currentPositionX = model.getMarioFloatPos()[0];
        if(currentPositionX - lastPositionX > 0){
            reward -= 1;
        } else{
            reward += 1;
        }

        if(this.world.gameStatus == GameStatus.LOSE){
            reward -= 10;
        }
        lastPositionX = currentPositionX;
        return reward;
    }
}
