package org.familybudget.familybudget;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML
    private TextField loginField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    @FXML
    protected void onLoginClick() {
        String login = loginField.getText();
        String password = passwordField.getText();

        if (login == null || login.isBlank() || password == null || password.isBlank()) {
            statusLabel.setText("Введите логин и пароль");
            return;
        }

        try {
            String response = ServerConnection.getInstance()
                    .sendCommand("AUTH " + login + " " + password);

            if (response == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (response.startsWith("OK ")) {
                // ответ вида: OK ROLE=ADMIN
                String role = "UNKNOWN";
                int idx = response.indexOf("ROLE=");
                if (idx >= 0) {
                    role = response.substring(idx + "ROLE=".length()).trim();
                }

                SessionContext.setUser(login, role);

                openMainWindow();

                Stage currentStage = (Stage) loginField.getScene().getWindow();
                currentStage.close();
            } else {
                statusLabel.setText("Ошибка: " + response);
            }

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    private void openMainWindow() throws Exception {
        FXMLLoader loader = new FXMLLoader(
                HelloApplication.class.getResource("main-view.fxml")
        );
        Scene scene = new Scene(loader.load(), 900, 600);
        Stage stage = new Stage();
        stage.setTitle("Семейный бюджет");
        stage.setScene(scene);
        stage.show();
    }

    //кнопкка регистрации метод онРегистрКлик
    @FXML
    protected void onRegisterClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("register-view.fxml")
            );
            Scene scene = new Scene(loader.load(), 420, 420);
            Stage stage = new Stage();
            stage.setTitle("Регистрация");
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия окна регистрации: " + e.getMessage());
        }
    }

}
