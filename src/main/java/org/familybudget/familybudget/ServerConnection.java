package org.familybudget.familybudget;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ServerConnection {

    private static ServerConnection instance;

    private final String host = "localhost";
    private final int port  = 5555;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private ServerConnection() {}

    public static synchronized ServerConnection getInstance() throws IOException {
        if (instance == null) {
            instance = new ServerConnection();
            instance.connect();
        }
        return instance;
    }

    private void connect() throws IOException {
        socket = new Socket(host, port);

        in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );
        out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true
        );

        // читаем приветствие сервера (2 строки)
        String g1 = in.readLine();
        String g2 = in.readLine();
        System.out.println("SERVER: " + g1);
        System.out.println("SERVER: " + g2);
    }

    public synchronized String sendCommand(String command) throws IOException {
        if (socket == null || socket.isClosed()) {
            connect();
        }
        out.println(command);
        return in.readLine();
    }

    public synchronized void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }
}
