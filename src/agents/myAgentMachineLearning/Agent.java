package agents.myAgentMachineLearning;

import engine.core.MarioAgentPy4j;
import engine.core.MarioForwardModel;
import engine.core.MarioTimer;
import engine.helper.MarioActions;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;

import static engine.core.MarioForwardModel.*;

/**
 * @author RobinBaumgarten
 */
public class Agent implements MarioAgentPy4j {
    AgentListener listener;
    MarioForwardModel model;

    public Agent getAgent(){
        return this;
    }

    @Override
    public void initialize(MarioForwardModel model, MarioTimer timer) {
        this.model = model;
    }

    public void registerListener(AgentListener listener) {
        this.listener = listener;
    }

    public MarioForwardModel getState(){
        return this.model;
    }

    @Override
    public boolean[] getActions(MarioForwardModel model, MarioTimer timer) {
        //byte[] data = State.make(model).toByte();
        byte[] data = State.toByte(model);
        byte[] o;
        if(model.evaluation){
            o = (byte[])listener.getEvaluateTrainingActions(data);
        } else {
            o = (byte[])listener.getTrainingActions(data);
        }
        boolean[] actions = new boolean[o.length];
        for (int i = 0; i < o.length; i++) {
            actions[i] = o[i] == 1;
        }
        return actions;
    }

    public static final byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }

    @Override
    public String getAgentName() {
        return "MyAgent";
    }

    @Override
    public void update(boolean[] actions, MarioForwardModel state, MarioForwardModel nextState, float reward, boolean isTerminate) {
        //byte[] oldStateBytes = State.make(state).toByte();
        //byte[] newStateBytes = State.make(nextState).toByte();
        byte[] oldStateBytes = State.toByte(state);
        byte[] newStateBytes = State.toByte(nextState);
        var rewardByte = State.float2ByteArray(reward);
        byte isDone = (byte) (isTerminate ? 1 : 0);

        byte[] actionsBytes = new byte[actions.length];
        for (int i = 0; i < actions.length; i++) {
            actionsBytes[i] = (byte) (actions[i] ? 1 : 0);
        }

        ByteBuffer buffer = ByteBuffer.allocate(
                rewardByte.length //reward
                + 1 //isDone
                + actionsBytes.length
                + oldStateBytes.length
                + newStateBytes.length
        );

        buffer.put(rewardByte);
        buffer.put(isDone);
        buffer.put(actionsBytes);
        buffer.put(oldStateBytes);
        buffer.put(newStateBytes);
        listener.update(buffer.array());
    }
}
