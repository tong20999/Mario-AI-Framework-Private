package agents.human;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import reinforment.State;
import engine.core.MarioAgent;
import engine.core.MarioForwardModel;
import engine.core.MarioTimer;
import engine.helper.MarioActions;

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
                var complate = model.getMarioCompleteObservation(0,0);
                int a =5;
                break;
        }
    }

}
