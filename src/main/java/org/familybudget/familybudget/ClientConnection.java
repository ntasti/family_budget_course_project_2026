package org.familybudget.familybudget;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Простое подключение к серверу по сокету.
 * Пока: открыли — отправили команду — прочитали ответ — закрыли.
 */
public class ClientConnection implements AutoCloseable {

    private final String host;
    private final int port;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ClientConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Открыть соединение.
     */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    /**
     * Прочитать одну строку от сервера.
     */
    public String readLine() throws IOException {
        return in.readLine();
    }

    /**
     * Отправить строку серверу.
     */
    public void sendLine(String line) {
        out.println(line);
    }

    @Override
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }
}

