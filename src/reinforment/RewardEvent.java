package reinforment;

import engine.core.MarioEvent;

public class RewardEvent {
    private float reward;
    private MarioEvent event;

    public MarioEvent getEvent() {
        return event;
    }

    public float getReward() {
        return reward;
    }

    public RewardEvent(float reward, MarioEvent event){
        this.reward = reward;
        this.event = event;
    }
}
