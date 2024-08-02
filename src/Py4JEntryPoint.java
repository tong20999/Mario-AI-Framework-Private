import engine.core.MarioGame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import engine.core.MarioGamePy4j;
import py4j.GatewayServer;

public class Py4JEntryPoint {

    private final MarioGamePy4j game = new MarioGamePy4j();

    public static String getLevel(String filepath) {
        String content = "";
        try {
            content = new String(Files.readAllBytes(Paths.get(filepath)));
        } catch (IOException e) {
        }
        return content;
    }

    public MarioGamePy4j getMarioGame(){
        return game;
    }

    public agents.myAgent.Agent getAgent(){
        return new agents.myAgent.Agent();
    }

    public String getLevel(){
        return getLevel("./levels/original/lvl-10.txt");
    }

    public static void main(String[] args) {
        GatewayServer gatewayServer = new GatewayServer(new Py4JEntryPoint());
        gatewayServer.start();
        System.out.println("Gateway Server Started");
    }
}
