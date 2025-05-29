package agents.myAgentMachineLearning;

import engine.core.MarioForwardModel;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class State {

    private static byte[] intArrayToBytes(int[] input) throws Exception {
        byte[] byteArray = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            byteArray[i] = (byte)input[i];
        }
        return byteArray;
    }

    public static byte [] float2ByteArray (float value)
    {
        return ByteBuffer.allocate(4).putFloat(value).array();
    }

    public static byte [] int2ByteArray (int value)
    {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    public static byte[] toByte(MarioForwardModel model) throws Exception {
        var detail = model.getMarioCompleteObservation(0,0);
        int[] flatState = Arrays.stream(detail)
            .flatMapToInt(Arrays::stream)
            .toArray();

        ;

        byte[] state = intArrayToBytes(flatState);
        byte[] timeRemain = int2ByteArray(model.getRemainingTime());
        byte[] velocityX = float2ByteArray(model.getMarioFloatVelocity()[0]);
        byte[] velocityY = float2ByteArray(model.getMarioFloatVelocity()[1]);
        byte gameStatus = (byte)(model.getGameStatus().ordinal());

        ByteBuffer buffer = ByteBuffer.allocate(
                state.length
                + timeRemain.length
                + velocityX.length
                + velocityY.length
                + 1
        );

        buffer.put(state);
        buffer.put(timeRemain);
        buffer.put(velocityX);
        buffer.put(velocityY);
        buffer.put(gameStatus);
        return buffer.array();
    }
}
