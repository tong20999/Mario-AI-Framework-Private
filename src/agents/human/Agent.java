package agents.human;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import engine.core.MarioAgent;
import engine.core.MarioForwardModel;
import engine.core.MarioTimer;
import engine.core.MarioWorld;
import engine.helper.MarioActions;
import reinforment.State;

public class Agent extends KeyAdapter implements MarioAgent {
    private boolean[] actions = null;

    @Override
    public void initialize(MarioForwardModel model, MarioTimer timer) {
        actions = new boolean[MarioActions.numberOfActions()];
    }

    private MarioForwardModel model;

    @Override
    public boolean[] getActions(MarioForwardModel model, MarioTimer timer) {
        this.model = model;
        return actions;
    }

    @Override
    public String getAgentName() {
        return "HumanAgent";
    }

    @Override
    public void keyPressed(KeyEvent e) {
        try {
            toggleKey(e.getKeyCode(), true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        try {
            toggleKey(e.getKeyCode(), false);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void toggleKey(int keyCode, boolean isPressed) throws Exception {
        if (this.actions == null) {
            return;
        }
        switch (keyCode) {
            case KeyEvent.VK_LEFT:
                this.actions[MarioActions.LEFT.getValue()] = isPressed;
                break;
            case KeyEvent.VK_RIGHT:
                this.actions[MarioActions.RIGHT.getValue()] = isPressed;
                break;
            case KeyEvent.VK_DOWN:
                this.actions[MarioActions.DOWN.getValue()] = isPressed;
                break;
            case KeyEvent.VK_S:
                this.actions[MarioActions.JUMP.getValue()] = isPressed;
                break;
            case KeyEvent.VK_A:
                this.actions[MarioActions.SPEED.getValue()] = isPressed;
                break;
            case KeyEvent.VK_M:
                if(isPressed){
                    var sceneObservation = State.toByte(model);
                    var height = model.a();
                    var a = calculateReward(height);
                    System.out.println(a);
                }

                break;
        }
    }

    public static double calculateReward(double height) {
        if (height >= 13) {
            return 0.1; // Base reward for heights 13, 14, 15
        }

        if (height <= 5) {
            return 0.5; // Max reward for heights 0, 1, 2, 3, 4
        }

        double rangeHeight = 12.0 - 4.0; // The linear range is from height 4 to 12
        double rangeReward = 0.5 - 0.1;
        double adjustedHeight = 12.0 - height;
        double reward = 0.1 + (adjustedHeight / rangeHeight) * rangeReward;
        return reward;
    }

}
