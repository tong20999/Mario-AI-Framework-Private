import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import engine.core.MarioGame;
import engine.core.MarioResult;
import reinforcement.*;

public class PlayLevel {
    public static void printResults(MarioResult result) {
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

    public static String getLevel(String filepath) {
        String content = "";
        try {
            content = new String(Files.readAllBytes(Paths.get(filepath)));
        } catch (IOException e) {
        }
        return content;
    }

    public static void main(String[] args) throws Exception {
        MarioGame game = new MarioGame();
        // printResults(game.playGame(getLevel("../levels/original/lvl-1-basic-move-right.txt"), 200, 0));
        var level = getLevel("./levels/original/lvl-4.txt");
        var original = getLevel("./levels/evaluation/lvl-8.txt");

        //testPcg(levelFileName);
        int a = 1;
        while (true){
            ProceduralContentGenerationLevel pcgLevel = ProceduralContentGenerationLevel
                    .parseLevel(new PCGLevelDto(40, 51, 50,60, 4,5,11,
                    false,false,false, 4 ,5 ,11 ,4
                    , 5 ,14 , 0, 2 ,5 ,5 ,2, 5, 10
                    ,100, null, false));
            pcgLevel.generate();
            printResults(game.runGame(new agents.human.Agent(), original, 100, 0, true));
        }
    }

//    private static void testPcg(String level) throws Exception {
//        for (int i = 0; i < 100000; i++) {
//            ProceduralContentGenerationLevel pcgLevel = ProceduralContentGenerationLevel
//                    .parseLevel(level);
//            pcgLevel.generate(false);
//            String mod = pcgLevel.getContent();
//        }
//        System.out.println("Test Done");
//    }
}
