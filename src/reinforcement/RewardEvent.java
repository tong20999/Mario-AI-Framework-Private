package reinforcement;

import engine.core.MarioEvent;

public class RewardEvent {
    private float reward;
    private MarioEvent event;
    private String timer;

    public MarioEvent getEvent() {
        return event;
    }

    public float getReward() {
        return reward;
    }

    public String getTimer() {
        return timer;
    }

    public RewardEvent(float reward, MarioEvent event, String timer){
        this.reward = reward;
        this.event = event;
        this.timer = timer;
    }
}
