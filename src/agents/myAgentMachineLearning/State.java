package agents.myAgentMachineLearning;

import engine.core.MarioForwardModel;

import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.stream.IntStream;

import static engine.core.MarioForwardModel.*;
import static engine.core.MarioForwardModel.OBS_NONE;

public class State {

    byte isPitAhead;
    byte isPitBehind;
    byte isPitBelow;

    byte isPipe2Ahead;
    byte isPipe2Behind;
    byte isPipe2Below;

    byte isPipe3Ahead;
    byte isPipe3Behind;
    byte isPipe3Below;

    byte isPipe4Ahead;
    byte isPipe4Behind;
    byte isPipe4Below;
    byte pitDistance;
    byte isEnemyAhead;
    byte isEnemyBehind;
    byte isEnemyBelow;
    byte facing;
    byte isMarioOnGround;
    byte mayMarioJump;
    byte getMarioCanJumpHigher;
    float velocityX;
    float velocityY;

    @Override
    public String toString() {
        return MessageFormat.format(
                        "Pit {0} {1} {2}\n" +
                        "Pipe 2 {3} {4} {5}\n" +
                                "Pipe 3 {6} {7} {8}\n" +
                                "Pipe 4 {9} {10} {11}\n" +
                        "Enemy {12} {13} {14}\n" +
                        "Facing {15} OnGroud {16} CanJump? {17}\n" +
                                "Velocity {18} {19}",
                isPitBehind, isPitBelow, isPitAhead,
                isPipe2Behind, isPipe2Below , isPipe2Ahead,
                isPipe3Behind, isPipe3Below, isPipe3Ahead,
                isPipe4Behind, isPipe4Below, isPipe4Ahead,
                isEnemyBehind, isEnemyBelow, isEnemyAhead,
                facing, isMarioOnGround, mayMarioJump, velocityX, velocityY
        );
    }

    private State(){

    }

    public static State make(MarioForwardModel model){
        State s = new State();
        var detail = model.getMarioCompleteObservation(1,1);
        s.isPipe2Ahead = isPipeTallAhead(detail, 2);
        s.isPipe2Below = isPipeTallBelow(detail, 2);
        s.isPipe2Behind = isPipeTallBehind(detail, 2);

        s.isPipe3Ahead = isPipeTallAhead(detail, 3);
        s.isPipe3Below = isPipeTallBelow(detail, 3);
        s.isPipe3Behind = isPipeTallBehind(detail, 3);

        s.isPipe4Ahead = isPipeTallAhead(detail, 4);
        s.isPipe4Below = isPipeTallBelow(detail, 4);
        s.isPipe4Behind = isPipeTallBehind(detail, 4);

        s.isPitAhead = isPitAhead(detail);
        s.isPitBehind = isPitBehind(detail);
        s.isPitBelow = isPitBelow(detail);
        s.pitDistance = pitColumn(detail);

        s.isEnemyAhead = isSomeThingAhead(model.getMarioCompleteObservation(1,2), OBS_ENEMY);
        s.isEnemyBehind = isSomeThingBehind(model.getMarioCompleteObservation(1,2), OBS_ENEMY);
        s.isEnemyBelow = isSomeThingBelow(model.getMarioCompleteObservation(1,2), OBS_ENEMY);
        s.facing = (byte) model.getMarioFacing();
        s.isMarioOnGround = (byte) (model.isMarioOnGround() ? 1: 0);
        s.mayMarioJump = (byte) (model.mayMarioJump() ? 1: 0);
        s.getMarioCanJumpHigher = (byte) (model.getMarioCanJumpHigher() ? 1: 0);
        s.velocityX = model.getMarioFloatVelocity()[0];
        s.velocityY = model.getMarioFloatVelocity()[1];
        return s;
    }

    private static byte[] intArrayToBytes(int[] input) throws Exception {
        byte[] byteArray = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            int mapValue = mapState(input[i]);
            byteArray[i] = (byte)mapValue;
        }
        return byteArray;
    }

    private static int mapState(int i) throws Exception {
        if(i == 0){
            return 0;
        } else if(i == 1){
            return 1;
        } else if(i == 100){
            return 100;
        }

        throw new Exception("map error");
    }

    public static byte [] float2ByteArray (float value)
    {
        return ByteBuffer.allocate(4).putFloat(value).array();
    }

    public static byte[] toByte(MarioForwardModel model) throws Exception {
        var detail = model.getMarioCompleteObservation(2,2);
        int[][] sliced = new int[9][7]; // 9 columns × 7 rows

        for (int col = 0; col < 9; col++) {
            System.arraycopy(detail[col + 7], 4, sliced[col], 0, 7);
        }
        int[] flatState = Arrays.stream(sliced)
            .flatMapToInt(Arrays::stream)
            .toArray();


        byte[] state = intArrayToBytes(flatState);
//        byte facing = (byte) model.getMarioFacing();
//        byte isMarioOnGround = (byte) (model.isMarioOnGround() ? 1: 0);
//        byte mayMarioJump = (byte) (model.mayMarioJump() ? 1: 0);
//        byte getMarioCanJumpHigher = (byte) (model.getMarioCanJumpHigher() ? 1: 0);
//        byte[] velocityX = float2ByteArray(model.getMarioFloatVelocity()[0]);
//        byte[] velocityY = float2ByteArray(model.getMarioFloatVelocity()[1]);

        ByteBuffer buffer = ByteBuffer.allocate(
                state.length
//                + velocityX.length
//                + velocityX.length
//                        + 4
        );

//        buffer.put(velocityX);
//        buffer.put(velocityY);
        buffer.put(state);
//        buffer.put(facing);
//        buffer.put(isMarioOnGround);
//        buffer.put(mayMarioJump);
//        buffer.put(getMarioCanJumpHigher);
        return buffer.array();
    }

    public byte[] toByte(){
        //var velocityXBytes = float2ByteArray(velocityX);
        //var velocityYBytes = float2ByteArray(velocityY);
        ByteBuffer buffer = ByteBuffer.allocate(
                //velocityXBytes.length // Velocity
                //+ velocityYBytes.length // Velocity
                + 9 // Pipe
                + 4 // Pit
                + 3 // isEnemyFront
                + 4
        );

        //buffer.put(velocityXBytes);
        //buffer.put(velocityYBytes);

        buffer.put(isPipe2Ahead);
        buffer.put(isPipe2Behind);
        buffer.put(isPipe2Below);

        buffer.put(isPipe3Ahead);
        buffer.put(isPipe3Behind);
        buffer.put(isPipe3Below);

        buffer.put(isPipe4Ahead);
        buffer.put(isPipe4Behind);
        buffer.put(isPipe4Below);

        buffer.put(isPitAhead);
        buffer.put(isPitBehind);
        buffer.put(isPitBelow);
        buffer.put(pitDistance);

        buffer.put(isEnemyAhead);
        buffer.put(isEnemyBehind);
        buffer.put(isEnemyBelow);

        buffer.put(facing);
        buffer.put(isMarioOnGround);
        buffer.put(mayMarioJump);
        buffer.put(getMarioCanJumpHigher);

        return buffer.array();
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
