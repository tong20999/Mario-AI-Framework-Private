import engine.core.MarioGameTraining;
import info.Info;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class GameServer {
    private final ExecutorService clientPool = Executors.newFixedThreadPool(Config.getThreadPoolSize());
    public void start() throws Exception {
        // Start server thread
        System.out.println(MessageFormat.format("start server on port {0} with {1} thread(s)",
                Config.getPort(), Config.getThreadPoolSize()
        ));
        Thread serverThread = new Thread(this::startSocketServer);
        serverThread.start();
        serverThread.join();
    }

    private void startSocketServer() {
        try (ServerSocket serverSocket = new ServerSocket(Config.getPort())) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientPool.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        MarioGameTraining clientGame = new MarioGameTraining();
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

                    byte[] payload;
                    switch (opCode) {
                        case "01": // reset
                            //payload = Arrays.copyOfRange(buffer, 2, 11);
                            Info info = getEpisode(buffer);
                            var state = clientGame.reset(info);
                            sendResponse(output, "01", state);
                            break;
                        case "02": // step
                            payload = Arrays.copyOfRange(buffer, 2, 7);
                            boolean[] actions = getActionFromPayload(payload); // fix: use payload[0], not [2]
                            var result = clientGame.step(actions);
                            sendResponse(output, "02", result);
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
            System.err.println("I/O error with client " + socket.getInetAddress() + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("An unexpected error occurred while handling client " + socket.getInetAddress());
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
            System.out.println("Client disconnected: " + socket.getInetAddress());

            // *** ADDED: Clean up the game instance when the client disconnects. ***
            clientGame.close(); // You must implement this method in MarioGameTraining
            System.out.println("Cleaned up game resources for client " + socket.getInetAddress());
        }
    }

    private Info getEpisode(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
        buffer.position(2);

        int episode = buffer.getInt();
        byte boolEvaluationByte = buffer.get();
        boolean evaluation = boolEvaluationByte != 0;

        byte boolVisualByte = buffer.get();
        boolean visual = boolVisualByte != 0;

        // Read the length of the level string (int)
        int levelLength = buffer.getInt();

        // Create a byte array to hold the level string bytes
        byte[] levelBytes = new byte[levelLength];
        // Read the level string bytes into the array
        buffer.get(levelBytes);

        // Convert the level bytes to a String using UTF-8 encoding
        String level = new String(levelBytes, StandardCharsets.UTF_8);

        return new Info(episode, evaluation, visual, level);
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
