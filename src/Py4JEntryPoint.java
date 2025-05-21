import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;

import agents.myAgentMachineLearning.Agent;
import engine.core.MarioGameTraining;
import engine.core.MarioResult;
import py4j.GatewayServer;

public class Py4JEntryPoint {
    private final MarioGameTraining marioGameTraining = new MarioGameTraining();

    public static String getLevel(String filepath) {
        String content = "";
        try {
            content = new String(Files.readAllBytes(Paths.get(filepath)));
        } catch (IOException e) {
        }
        return content;
    }

    public MarioGameTraining getMarioGameTraining(){
        return marioGameTraining;
    }

    public Agent getTraningAgent(){
        return new Agent();
    }

    private static String getFileFromLevel(String file){
        String content = "";
        try {
            content = new String(Files.readAllBytes(Paths.get(file)));
        } catch (IOException e) {
        }
        return content;
    }

    public static String getLevel(){
        var randomLevel = randomLevel();
        return getFileFromLevel(randomLevel);
    }

    public static String getFirstLevel(){
        var level1 = "./levels/original/lvl-1.txt";
        return getFileFromLevel(level1);
    }

    private static String randomLevel(){
        var min = 1;
        var max = 15;
        var random = (int) ((Math.random() * (max - min)) + min);
        var level = MessageFormat.format("./levels/original/lvl-{0}.txt",random);
        return level;
    }

    public static void main(String[] args) {
        GatewayServer gatewayServer = new GatewayServer(new Py4JEntryPoint());
        gatewayServer.start();
        System.out.println("Gateway Server Started");
    }

    private static void printResults(MarioResult result) {
        System.out.println("****************************************************************");
        System.out.println("Game Status: " + result.getGameStatus().toString() +
                " Percentage Completion: " + result.getCompletionPercentage());
        System.out.println("Lives: " + result.getCurrentLives() + " Coins: " + result.getCurrentCoins() +
                " Remaining Time: " + (int) Math.ceil(result.getRemainingTime() / 1000f));
        System.out.println("Mario State: " + result.getMarioMode() +
                " (Mushrooms: " + result.getNumCollectedMushrooms() + " Fire Flowers: " + result.getNumCollectedFireflower() + ")");
        System.out.println("Total Kills: " + result.getKillsTotal() + " (Stomps: " + result.getKillsByStomp() +
                " Fireballs: " + result.getKillsByFire() + " Shells: " + result.getKillsByShell() +
                " Falls: " + result.getKillsByFall() + ")");
        System.out.println("Bricks: " + result.getNumDestroyedBricks() + " Jumps: " + result.getNumJumps() +
                " Max X Jump: " + result.getMaxXJump() + " Max Air Time: " + result.getMaxJumpAirTime());
        System.out.println("****************************************************************");
    }
}
