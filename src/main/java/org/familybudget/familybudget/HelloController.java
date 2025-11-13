package org.familybudget.familybudget;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class HelloController {

    @FXML
    private TextField loginField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Label statusLabel;

    private final String host = "localhost";
    private final int port = 5555;

    @FXML
    protected void onLoginClick() {
        String login = loginField.getText();
        String password = passwordField.getText();

        if (login == null || login.isBlank() || password == null || password.isBlank()) {
            statusLabel.setText("Введите логин и пароль");
            return;
        }

        loginButton.setDisable(true);
        statusLabel.setText("Подключение...");

        new Thread(() -> doAuth(login, password)).start();
    }

    private void doAuth(String login, String password) {
        try (ClientConnection connection = new ClientConnection(host, port)) {

            connection.connect();

            // читаем две строки приветствия
            String greet1 = connection.readLine();
            String greet2 = connection.readLine();
            System.out.println("SERVER: " + greet1);
            System.out.println("SERVER: " + greet2);

            // отправляем AUTH
            connection.sendLine("AUTH " + login + " " + password);

            String response = connection.readLine();
            System.out.println("SERVER: " + response);

            Platform.runLater(() -> {
                loginButton.setDisable(false);
                if (response != null && response.startsWith("OK")) {
                    statusLabel.setText("Успешный вход: " + response);
                    // TODO: позже здесь откроем главное окно
                } else {
                    statusLabel.setText(response != null ? response : "Нет ответа от сервера");
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                loginButton.setDisable(false);
                statusLabel.setText("Ошибка: " + e.getMessage());
            });
        }
    }
}
