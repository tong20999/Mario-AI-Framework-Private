package reinforcement;

import java.util.Arrays;
import java.io.ByteArrayOutputStream;

public class ObservationGrid {
    private static int size = 16;

    public int[][] getSolid() {
        return solid;
    }

    public int[][] getBlocks() {
        return blocks;
    }

    public int[][] getCoins() {
        return coins;
    }

    public int[][] getGoomba() {
        return goomba;
    }

    public int[][] getGoombaWing() {
        return goombaWing;
    }

    public int[][] getGreenKoompa() {
        return greenKoompa;
    }

    public int[][] getGreenKoompaWing() {
        return greenKoompaWing;
    }

    public int[][] getRedKoompa() {
        return redKoompa;
    }

    public int[][] getRedKoompaWing() {
        return redKoompaWing;
    }

    public int[][] getSpiky() {
        return spiky;
    }

    public int[][] getSpikyWing() {
        return spikyWing;
    }

    public int[][] getEnemyFlower() {
        return enemyFlower;
    }

    public int[][] getShell() {
        return shell;
    }

    public int[][] getBulletBill() {
        return bulletBill;
    }

    public int[][] getMushroom() {
        return mushroom;
    }

    public int[][] getFirepower() {
        return firepower;
    }

    public int[][] getLifeMushroom() {
        return lifeMushroom;
    }

    public int[][] getBrick() {
        return brick;
    }

    public int[][] getSemiSolid() {
        return semiSolid;
    }

    public int[][] getFlags() {
        return flags;
    }

    public int[][] getFireball() {
        return fireball;
    }

    int[][] solid = new int[size][size];
    int[][] blocks = new int[size][size];
    int[][] coins = new int[size][size];
    int[][] goomba = new int[size][size];
    int[][] goombaWing = new int[size][size];
    int[][] greenKoompa = new int[size][size];
    int[][] greenKoompaWing = new int[size][size];
    int[][] redKoompa = new int[size][size];
    int[][] redKoompaWing = new int[size][size];
    int[][] spiky = new int[size][size];
    int[][] spikyWing = new int[size][size];
    int[][] enemyFlower = new int[size][size];
    int[][] shell = new int[size][size];
    int[][] bulletBill = new int[size][size];
    int[][] mushroom = new int[size][size];
    int[][] firepower = new int[size][size];
    int[][] lifeMushroom = new int[size][size];
    int[][] brick = new int[size][size];
    int[][] semiSolid = new int[size][size];
    int[][] flags = new int[size][size];
    int[][] fireball = new int[size][size];


    private static byte[] intArrayToBytes(int[] input) throws Exception {
        byte[] byteArray = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            byteArray[i] = (byte) input[i];
        }
        return byteArray;
    }

    public ObservationGrid(){
    }

    public byte[] getPayload() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.write(intArrayToBytes(Arrays.stream(solid).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(blocks).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(coins).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(goomba).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(goombaWing).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(greenKoompa).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(greenKoompaWing).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(redKoompa).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(redKoompaWing).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(spiky).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(spikyWing).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(enemyFlower).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(shell).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(bulletBill).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(mushroom).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(firepower).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(lifeMushroom).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(brick).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(semiSolid).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(flags).flatMapToInt(Arrays::stream).toArray()));
        outputStream.write(intArrayToBytes(Arrays.stream(fireball).flatMapToInt(Arrays::stream).toArray()));

        return outputStream.toByteArray();
    }
}