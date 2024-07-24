package agents.myAgent;

import engine.core.MarioForwardModel;

public class State {
    public int getRemainingTime() {
        return remainingTime;
    }

    public float getMarioFloatPosX() {
        return marioFloatPosX;
    }

    public float getMarioFloatPosY() {
        return marioFloatPosY;
    }

    public float getMarioFloatVelocityX() {
        return marioFloatVelocityX;
    }

    public float getMarioFloatVelocityY() {
        return marioFloatVelocityY;
    }

    public boolean isMarioCanJumpHigher() {
        return marioCanJumpHigher;
    }

    public int getMarioMode() {
        return marioMode;
    }

    public boolean isMarioOnGround() {
        return isMarioOnGround;
    }

    public boolean isMayMarioJump() {
        return mayMarioJump;
    }

    public int[][] getScreenCompleteObservation() {
        return screenCompleteObservation;
    }

    int remainingTime;
    float marioFloatPosX;
    float marioFloatPosY;
    float marioFloatVelocityX;
    float marioFloatVelocityY;
    boolean marioCanJumpHigher;

    int marioMode;
    boolean isMarioOnGround;
    boolean mayMarioJump;

    int[][] screenCompleteObservation;

    private State(){}

    public static State make(MarioForwardModel model){
        var state = new State();
        state.remainingTime = model.getRemainingTime();
        state.marioFloatPosX = model.getMarioFloatPos()[0];
        state.marioFloatPosY = model.getMarioFloatPos()[1];
        state.marioFloatVelocityX = model.getMarioFloatVelocity()[0];
        state.marioFloatVelocityY = model.getMarioFloatVelocity()[1];
        state.marioCanJumpHigher = model.getMarioCanJumpHigher();

        state.marioMode = model.getMarioMode();
        state.isMarioOnGround = model.isMarioOnGround();
        state.mayMarioJump = model.mayMarioJump();
        state.screenCompleteObservation = model.getScreenCompleteObservation();
        return state;
    }
}
