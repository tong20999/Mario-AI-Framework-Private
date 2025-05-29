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

    public static byte[] toByte(MarioForwardModel model) throws Exception {
        var detail = model.getMarioCompleteObservation(0,0);
        int[] flatState = Arrays.stream(detail)
            .flatMapToInt(Arrays::stream)
            .toArray();


        byte[] state = intArrayToBytes(flatState);

        ByteBuffer buffer = ByteBuffer.allocate(
                state.length
        );

        buffer.put(state);
        return buffer.array();
    }
}
