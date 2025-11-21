package org.familybudget.familybudget.Server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ServerConnection {

    private static ServerConnection instance;

    private String host;
    private int port;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private ServerConnection() throws IOException {
        loadConfig();
        connect();
    }

    /**
     * Singleton — потокобезопасный
     */
    public static synchronized ServerConnection getInstance() throws IOException {
        if (instance == null) {
            instance = new ServerConnection();
        }
        return instance;
    }

    /**
     * Загружаем client.properties
     */
    private void loadConfig() throws IOException {
        Properties props = new Properties();

        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("client.properties")) {

            if (is == null) {
                throw new FileNotFoundException("client.properties not found!");
            }

            props.load(is);
        }

        host = props.getProperty("server.host", "localhost");
        port = Integer.parseInt(props.getProperty("server.port", "5555"));
    }

    /**
     * Подключение к серверу
     */
    private void connect() throws IOException {
        socket = new Socket(host, port);

        in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );

        out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true
        );

        // читаем приветствие от сервера
        String g1 = in.readLine();
        String g2 = in.readLine();
        System.out.println("SERVER: " + g1);
        System.out.println("SERVER: " + g2);
    }

    /**
     * Отправка команды на сервер (с авто-переподключением)
     */
    public synchronized String sendCommand(String command) throws IOException {
        if (socket == null || socket.isClosed()) {
            connect();
        }

        out.println(command);
        return in.readLine();
    }

    /**
     * Внутреннее закрытие
     */
    private void internalClose() {
        try {
            if (out != null) out.close();
        } catch (Exception ignored) {}

        try {
            if (in != null) in.close();
        } catch (Exception ignored) {}

        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        socket = null;
        in     = null;
        out    = null;
    }

    /**
     * Публичное закрытие
     */
    public synchronized void close() {
        internalClose();
        instance = null;
    }

    /**
     * Удобный статический метод для logout
     */
    public static synchronized void disconnect() {
        if (instance != null) {
            instance.internalClose();
            instance = null;
        }
    }
}
