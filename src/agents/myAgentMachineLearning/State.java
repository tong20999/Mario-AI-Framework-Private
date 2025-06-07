package agents.myAgentMachineLearning;

import engine.core.MarioForwardModel;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;

import static engine.core.MarioForwardModel.*;

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

        int[][] slices = new int[11][14]; // 11 columns (5..15), 14 rows (2..15)

        for (int col = 5; col <= 15; col++) {
            System.arraycopy(detail[col], 2, slices[col - 5], 0, 14);
        }

        int[] flatState = Arrays.stream(slices)
            .flatMapToInt(Arrays::stream)
            .toArray();

        int timeRemaingInSec = model.getRemainingTime() / 1000;
        byte[] state = intArrayToBytes(flatState);
        byte[] timeRemain = int2ByteArray(timeRemaingInSec);
        byte[] velocityX = float2ByteArray(model.getMarioFloatVelocity()[0]);
        byte[] velocityY = float2ByteArray(model.getMarioFloatVelocity()[1]);
        byte gameStatus = (byte)(model.getGameStatus().ordinal());

        ByteBuffer buffer = ByteBuffer.allocate(
                state.length
                + timeRemain.length
                + 8
                + 1
        );

        buffer.put(state);
        buffer.put(timeRemain);
        buffer.put(velocityX);
        buffer.put(velocityY);
        buffer.put(gameStatus);
        return buffer.array();
    }

//    public static byte[] toByte(MarioForwardModel model) throws Exception {
//        var detail = model.getMarioCompleteObservation(0,0);
//        byte isPipe2Ahead = isPipeTallAhead(detail, 2);
//        byte isPipe2Below = isPipeTallBelow(detail, 2);
//        byte isPipe2Behind = isPipeTallBehind(detail, 2);
//
//        byte isPipe3Ahead = isPipeTallAhead(detail, 3);
//        byte isPipe3Below = isPipeTallBelow(detail, 3);
//        byte isPipe3Behind = isPipeTallBehind(detail, 3);
//
//        byte isPipe4Ahead = isPipeTallAhead(detail, 4);
//        byte isPipe4Below = isPipeTallBelow(detail, 4);
//        byte isPipe4Behind = isPipeTallBehind(detail, 4);
//
//        byte isPitAhead = isPitAhead(detail);
//        byte isPitBehind = isPitBehind(detail);
//        byte isPitBelow = isPitBelow(detail);
//        byte pitDistance = pitColumn(detail);
//
//        byte isEnemyAhead = isSomeThingAhead(detail, OBS_ENEMY);
//        byte isEnemyBehind = isSomeThingBehind(detail, OBS_ENEMY);
//        byte isEnemyBelow = isSomeThingBelow(detail, OBS_ENEMY);
//
//        byte isFlagAhead = isSomeThingAhead(detail, 56);
//        byte flagColumn = flagColumn(detail);
//
//        byte facing = (byte) model.getMarioFacing();
//        byte isMarioOnGround = (byte) (model.isMarioOnGround() ? 1: 0);
//        byte mayMarioJump = (byte) (model.mayMarioJump() ? 1: 0);
//        byte getMarioCanJumpHigher = (byte) (model.getMarioCanJumpHigher() ? 1: 0);
//
//
//        var velocityX = model.getMarioFloatVelocity()[0];
//        var velocityY = model.getMarioFloatVelocity()[1];
//
//        int timeRemaingInSec = model.getRemainingTime() / 1000;
//        byte[] timeRemain = int2ByteArray(timeRemaingInSec);
//        byte gameStatus = (byte)(model.getGameStatus().ordinal());
//        ByteBuffer buffer = ByteBuffer.allocate(
//                9
//                + 4
//                + 3
//                + 2
//                + 4
//                + 8
//                + 4
//                + 1
//        );
//
//        buffer.put(isPipe2Ahead);
//        buffer.put(isPipe2Behind);
//        buffer.put(isPipe2Below);
//
//        buffer.put(isPipe3Ahead);
//        buffer.put(isPipe3Behind);
//        buffer.put(isPipe3Below);
//
//        buffer.put(isPipe4Ahead);
//        buffer.put(isPipe4Behind);
//        buffer.put(isPipe4Below);
//
//        buffer.put(isPitAhead);
//        buffer.put(isPitBehind);
//        buffer.put(isPitBelow);
//        buffer.put(pitDistance);
//
//        buffer.put(isEnemyAhead);
//        buffer.put(isEnemyBehind);
//        buffer.put(isEnemyBelow);
//
//        buffer.put(isFlagAhead);
//        buffer.put(flagColumn);
//
//        buffer.put(facing);
//        buffer.put(isMarioOnGround);
//        buffer.put(mayMarioJump);
//        buffer.put(getMarioCanJumpHigher);
//
//        buffer.put(float2ByteArray(velocityX));
//        buffer.put(float2ByteArray(velocityY));
//
//        buffer.put(timeRemain);
//        buffer.put(gameStatus);
//        return buffer.array();
//    }

    private static byte flagColumn(int[][] detail) {
        var result = IntStream.rangeClosed(10, 15)
                .filter(i -> Arrays.stream(detail[i])
                        .anyMatch(m -> m == 56))
                .findFirst()
                .orElse(0);
        return (byte) result;
    }

    public static boolean hasExactConsecutive(int[] row, int value, int exactCount) {
        int count = 0;
        for (int i = 0; i < row.length; i++) {
            if (row[i] == value) {
                count++;
            } else {
                if (count == exactCount) {
                    return true; // found exactly the required count
                }
                count = 0; // reset streak
            }
        }
        // Check in case it ends on a streak
        return count == exactCount;
    }

    public static byte isPipeTallAhead(int[][] completeObservation, int tall){
        var isPipeTallAhead = IntStream.rangeClosed(10, 15).anyMatch(i -> hasExactConsecutive(completeObservation[i], OBS_PIPE, tall));
        return (byte) (isPipeTallAhead ? 1 : 0);
    }

    public static byte isPipeTallBelow(int[][] completeObservation, int tall){
        var isPipeTallBelow = hasExactConsecutive(completeObservation[8], OBS_PIPE, tall);
        return (byte) (isPipeTallBelow ? 1 : 0);
    }

    public static byte isPipeTallBehind(int[][] completeObservation, int tall){
        var isPipeTallBehind = IntStream.rangeClosed(0, 6).anyMatch(i -> hasExactConsecutive(completeObservation[i], OBS_PIPE, tall));
        return (byte) (isPipeTallBehind ? 1 : 0);
    }

    public static byte isSomeThingAhead(int[][] completeObservation, int OBS){
        var isSomethingAhead = IntStream.rangeClosed(10, 15).anyMatch(i -> Arrays.stream(completeObservation[i]).anyMatch(m -> m == OBS));
        return (byte) (isSomethingAhead ? 1 : 0);
    }

    public static byte isSomeThingBehind(int[][] completeObservation, int OBS) {
        var isSomethingBehind = IntStream.rangeClosed(0, 6).anyMatch(i -> Arrays.stream(completeObservation[i]).anyMatch(m -> m == OBS));
        return (byte) (isSomethingBehind ? 1 : 0);
    }

    public static byte isSomeThingBelow(int[][] completeObservation, int OBS) {
        var isSomeThingBelow = Arrays.stream(completeObservation[8]).anyMatch(m -> m == OBS);
        return (byte) (isSomeThingBelow ? 1 : 0);
    }

    public static byte isPitAhead(int[][] completeObservation){
        var isPitAhead = IntStream.rangeClosed(10, 15).anyMatch(i -> Arrays.stream(completeObservation[i]).allMatch(m -> m == OBS_NONE));
        return (byte) (isPitAhead ? 1 : 0);
    }

    public static byte pitColumn(int[][] completeObservation){
        var result = IntStream.rangeClosed(10, 15)
                .filter(i -> Arrays.stream(completeObservation[i])
                        .allMatch(m -> m == OBS_NONE))
                .findFirst()
                .orElse(0);
        return (byte) result;
    }

    public static byte isPitBehind(int[][] completeObservation) {
        var isSomethingBehind = IntStream.rangeClosed(0, 6).anyMatch(i -> Arrays.stream(completeObservation[i]).allMatch(m -> m == OBS_NONE));
        return (byte) (isSomethingBehind ? 1 : 0);
    }

    public static byte isPitBelow(int[][] completeObservation) {
        var isSomeThingBelow = Arrays.stream(completeObservation[8]).allMatch(m -> m == OBS_NONE);
        return (byte) (isSomeThingBelow ? 1 : 0);
    }
}
