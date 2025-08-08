import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import engine.core.MarioGame;
import engine.core.MarioResult;
import reinforment.EnumBlockType;
import reinforment.EnumEnemy;
import reinforment.Helper;
import reinforment.ProceduralContentGenerationLevel;

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
        var level = getLevel("./levels/training/100-basic/104-basic-block-enemy-pit/lvl-1a.txt");
        var original = getLevel("./levels/evaluation/lvl-1.txt");
        String levelFileName = "blocks=3,enemies=0,pits=0,pipes=0,ramps=0,width_min=50,width_max=50";
        //testPcg(levelFileName);

        while (true){
            ProceduralContentGenerationLevel pcgLevel = ProceduralContentGenerationLevel
                    .parseLevel(levelFileName);
            //pcgLevel.addPit(4,2, 2, 5, 1);
            //pcgLevel.addPipe(1,2,1);
            //pcgLevel.addBlock(1,2,1);
            //pcgLevel.addEnemy(1,2, 2, EnumEnemy.GOOMBA);
            //pcgLevel.addBlock(2, 2, 1);
            pcgLevel.generate(false);
            String mod = pcgLevel.getContent();
            printResults(game.runGame(new agents.human.Agent(), mod, 1000, 0, true));
        }

    }

    private static void testPcg(String level) throws Exception {
        for (int i = 0; i < 100000; i++) {
            ProceduralContentGenerationLevel pcgLevel = ProceduralContentGenerationLevel
                    .parseLevel(level);
            pcgLevel.generate(false);
            String mod = pcgLevel.getContent();
        }
        System.out.println("Test Done");
    }
}
