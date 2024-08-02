package engine.core;

import engine.helper.GameStatus;

public class MarioPlayStepResult {
    int reward;
    boolean done;
    int score;
    public MarioPlayStepResult(int reward, boolean done, int score){
        this.reward = reward;
        this.done = done;
        this.score = score;
    }

    public int getScore() {
        return score;
    }

    public boolean isDone() {
        return done;
    }

    public int getReward() {
        return this.reward;
    }
}
