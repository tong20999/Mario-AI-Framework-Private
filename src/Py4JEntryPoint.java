import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import agents.myAgentMachineLearning.Agent;
import engine.core.MarioAgent;
import engine.core.MarioGame;
import engine.core.MarioGameTraining;
import engine.core.MarioResult;
import py4j.GatewayServer;

public class Py4JEntryPoint {

    private static final agents.myAgent.Agent agent = new agents.myAgent.Agent();

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

    public MarioGame getMarioGame(){
        return new MarioGame();
    }

    public agents.myAgent.Agent getAgent(){
        return agent;
    }

    public Agent getTraningAgent(){
        return new Agent();
    }

    public static String getLevel(){
        return getLevel("./levels/original/lvl-1.txt");
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
