import engine.core.MarioGameTraining;
import info.Info;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class GameServer {
    private final ExecutorService clientPool = Executors.newFixedThreadPool(16);
    private int totalWindows = 0;
    public void start() throws Exception {
        // Start server thread
        Thread serverThread = new Thread(this::startSocketServer);
        serverThread.start();
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
        int columns = 4;
        int col = totalWindows % columns;
        int row = totalWindows / columns;
        totalWindows++;
        MarioGameTraining clientGame = null;
        if(totalWindows == 9){
            clientGame = new MarioGameTraining(null, null);
        }
        else {
            clientGame = new MarioGameTraining(col, row);
        }
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
                            payload = Arrays.copyOfRange(buffer, 2, 11);
                            Info info = getEpisode(payload);
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

        int episode = buffer.getInt();
        float epsilon = buffer.getFloat();
        byte boolByte = buffer.get();       // 5th byte
        boolean evaluation = boolByte != 0;
        return new Info(episode, evaluation, epsilon);
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
