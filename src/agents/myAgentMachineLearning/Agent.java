package agents.myAgentMachineLearning;

import engine.core.MarioAgentPy4j;
import engine.core.MarioForwardModel;
import engine.core.MarioPlayStepResult;
import engine.core.MarioTimer;
import engine.helper.MarioActions;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

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
        var data = getState(model);

        var o = (byte[])listener.getTrainingActions(data);
        boolean[] actions = new boolean[o.length];
        for (int i = 0; i < o.length; i++) {
            actions[i] = o[i] == 0;
        }
        actions[MarioActions.DOWN.getValue()] = false;
        return actions;
        //return new boolean[]{false,true,false,false,false};
    }

    private byte[] intArrayToBytes(int[] input){
        byte[] byteArray = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            byteArray[i] = (byte)input[i];
        }
        return byteArray;
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
    public void update(MarioPlayStepResult playstepResult, MarioForwardModel oldState, boolean[] actions, MarioForwardModel nextState, MarioTimer timer) {
        byte[] oldStateBytes = getState(oldState);
        byte[] newStateBytes = getState(nextState);

        var reward = intToByteArray(playstepResult.getReward());
        //byte score = (byte) playstepResult.getScore();
        byte isDone = (byte) (playstepResult.isDone() ? 1 : 0);

        byte[] actionsBytes = new byte[actions.length];
        for (int i = 0; i < actions.length; i++) {
            actionsBytes[i] = (byte) (actions[i] ? 1 : 0);
        }

        ByteBuffer buffer = ByteBuffer.allocate(
                reward.length //reward
                + 1 //isDone
                + actionsBytes.length
                + oldStateBytes.length
                + newStateBytes.length
        );
        buffer.put(reward);
        buffer.put(isDone);
        buffer.put(actionsBytes);
        buffer.put(oldStateBytes);
        buffer.put(newStateBytes);
        listener.update(buffer.array());
    }

    private byte[] getState(MarioForwardModel model){

        int[] flatOldState = Arrays.stream(model.getMarioCompleteObservation(1,1))
                .flatMapToInt(Arrays::stream)
                .toArray();

        byte[] oldState = intArrayToBytes(flatOldState);
        byte[] positionX = float2ByteArray(model.getMarioFloatPos()[0]);
        byte[] positionY = float2ByteArray(model.getMarioFloatPos()[1]);
        byte[] velocityX = float2ByteArray(model.getMarioFloatVelocity()[0]);
        byte[] velocityY = float2ByteArray(model.getMarioFloatVelocity()[1]);
        byte killTotal = (byte) model.getKillsTotal();
        byte canJumpHigher = (byte) (model.getMarioCanJumpHigher() ? 1 : 0);
        byte coinCollect = (byte) model.getNumCollectedCoins();
        byte flowerCollect = (byte) model.getNumCollectedFireflower();
        byte mushroomsCollect = (byte) model.getNumCollectedMushrooms();
        byte destroyBrick = (byte) model.getNumDestroyedBricks();
        byte marioMode = (byte) model.getMarioMode();

        ByteBuffer buffer = ByteBuffer.allocate(
                oldState.length
                + positionX.length
                + positionY.length
                + velocityX.length
                + velocityY.length
                + 1 //killTotal
                + 1 //canJumpHigher
                + 1 //coinCollect
                + 1 //flowerCollect
                + 1 //mushroomsCollect
                + 1 //destroyBrick
                + 1 //marioMode
        );
        buffer.put(oldState);
        buffer.put(positionX);
        buffer.put(positionY);
        buffer.put(velocityX);
        buffer.put(velocityY);
        buffer.put(killTotal);
        buffer.put(canJumpHigher);
        buffer.put(coinCollect);
        buffer.put(flowerCollect);
        buffer.put(mushroomsCollect);
        buffer.put(destroyBrick);
        buffer.put(marioMode);
        return buffer.array();
    }

    public static byte [] float2ByteArray (float value)
    {
        return ByteBuffer.allocate(4).putFloat(value).array();
    }
}
