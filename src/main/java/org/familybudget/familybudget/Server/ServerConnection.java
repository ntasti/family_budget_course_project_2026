package org.familybudget.familybudget.Server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ServerConnection {

    private static ServerConnection instance;

    //адрес и порт сервера
    private String host;
    private int port;
    //tcp подключение
    private Socket socket;
    //поток для чтения и записи данных
    private BufferedReader in;
    private PrintWriter out;

    //приватный конструктор для загрузки настроек подключения к серверу
    private ServerConnection() throws IOException {
        loadConfig();
        connect();
    }


    //создание подключения к серверу
    public static synchronized ServerConnection getInstance() throws IOException {
        if (instance == null) {
            instance = new ServerConnection();
        }
        return instance;
    }


     // загрузка настроек из client.properties
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

     //Подключение к серверу через создаваемый сокет и входной и выходной поток
    private void connect() throws IOException {
        socket = new Socket(host, port);

        in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );

        out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true
        );

        // читаем сообщение от сервера после подключения
        String g1 = in.readLine();
        String g2 = in.readLine();
        System.out.println("SERVER: " + g1);
        System.out.println("SERVER: " + g2);
    }

     //Отправка команды на сервер и возврат ответа
    public synchronized String sendCommand(String command) throws IOException {
        if (socket == null || socket.isClosed()) {
            connect();
        }

        out.println(command);
        return in.readLine();
    }

    // закрытие сокета и потока
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

   //полное закрытие соединения
    public synchronized void close() {
        internalClose();
        instance = null;
    }

   //для logout закрывает соединение но не закрывает приложение
    public static synchronized void disconnect() {
        if (instance != null) {
            instance.internalClose();
            instance = null;
        }
    }
}
