import engine.core.MarioGameTraining;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class GameServer {
    private final ExecutorService clientPool = Executors.newFixedThreadPool(4);
    private MarioGameTraining game;
    public void start() throws Exception {
        // Start server thread
        Thread serverThread = new Thread(this::startSocketServer);
        serverThread.start();

        // Start game loop
        game = new MarioGameTraining();

        serverThread.join();
    }

    private void startSocketServer() {
        try (ServerSocket serverSocket = new ServerSocket(4455)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientPool.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try (
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream()
        ) {
            byte[] buffer = new byte[1024];
            int bytesRead;

            // Loop until the client disconnects
            while ((bytesRead = input.read(buffer)) != -1) {
                if (bytesRead == 1024) {
                    String opCode = new String(buffer, 0, 2, StandardCharsets.UTF_8);
                    byte[] payload = Arrays.copyOfRange(buffer, 2, 7);

                    switch (opCode) {
                        case "01": // reset
                            var state = game.reset();
                            sendResponse(output, "01", state);
                            break;
                        case "02": // step
                            boolean[] actions = getActionFromPayload(payload); // fix: use payload[0], not [2]
                            var result = game.step(actions);
                            sendResponse(output, "02", result);
                            break;
                        case "03": // get observation
                            sendResponse(output, "03", null);
                            break;
                        default:
                            System.out.println("Unknown op: " + opCode);
                    }
                } else {
                    System.out.println("Unexpected message length: " + bytesRead);
                }
            }

            System.out.println("Client disconnected.");
        } catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean[] getActionFromPayload(byte[] payload) {
        if (payload.length != 5) {
            throw new IllegalArgumentException("Payload must be exactly 5 bytes.");
        }

        boolean[] action = new boolean[5];
        for (int i = 0; i < 5; i++) {
            action[i] = payload[i] == 1;
        }

        return action;
    }

    private void sendResponse(OutputStream output, String opCode, byte[] states) throws IOException {
        byte[] response = new byte[1024];
        byte[] opBytes = opCode.getBytes(StandardCharsets.UTF_8);

        System.arraycopy(opBytes, 0, response, 0, 2);
        System.arraycopy(states, 0, response, 2, states.length);

        output.write(response);
        output.flush();
    }
}
