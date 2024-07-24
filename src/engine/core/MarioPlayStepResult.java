package engine.core;

import engine.helper.GameStatus;

public class MarioPlayStepResult {
    GameStatus gameStatus;
    int reward;
    public MarioPlayStepResult(int reward, GameStatus gameStatus){
        this.gameStatus = gameStatus;
        this.reward = reward;
    }

    public int getScore() {
        return score;
    }

    public boolean isDone() {
        return this.gameStatus == GameStatus.LOSE;
    }

    public int getReward() {
        return this.reward;
    }

    boolean done;
    int score;
}
