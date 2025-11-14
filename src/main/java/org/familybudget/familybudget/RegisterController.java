package org.familybudget.familybudget;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class RegisterController {

    @FXML
    private TextField loginField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private RadioButton createFamilyRadio;

    @FXML
    private RadioButton joinFamilyRadio;

    @FXML
    private TextField familyNameField;

    @FXML
    private TextField familyCodeField;

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        ToggleGroup group = new ToggleGroup();
        createFamilyRadio.setToggleGroup(group);
        joinFamilyRadio.setToggleGroup(group);

        createFamilyRadio.setSelected(true);
        updateFamilyFields();

        group.selectedToggleProperty().addListener((obs, oldV, newV) -> updateFamilyFields());
    }

    private void updateFamilyFields() {
        boolean create = createFamilyRadio.isSelected();
        familyNameField.setDisable(!create);
        familyCodeField.setDisable(create);
    }

    @FXML
    private void onRegisterClick() {
        String login = loginField.getText();
        String pass = passwordField.getText();
        String pass2 = confirmPasswordField.getText();

        if (login == null || login.isBlank()
                || pass == null || pass.isBlank()
                || pass2 == null || pass2.isBlank()) {
            statusLabel.setText("Заполните все поля");
            return;
        }

        if (!pass.equals(pass2)) {
            statusLabel.setText("Пароли не совпадают");
            return;
        }

        try {
            String resp = ServerConnection.getInstance()
                    .sendCommand("REGISTER " + login + " " + pass);

            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("ERROR LOGIN_EXISTS")) {
                statusLabel.setText("Такой логин уже существует");
                return;
            }

            if (!resp.startsWith("OK")) {
                statusLabel.setText("Ошибка регистрации: " + resp);
                return;
            }

            SessionContext.setUser(login, "MEMBER");

            if (createFamilyRadio.isSelected()) {
                String famName = familyNameField.getText();
                if (famName == null || famName.isBlank()) {
                    statusLabel.setText("Введите название семьи");
                    return;
                }

                String respFam = ServerConnection.getInstance()
                        .sendCommand("CREATE_FAMILY " + famName.trim());

                if (respFam == null || !respFam.startsWith("OK FAMILY_CREATED")) {
                    statusLabel.setText("Ошибка создания семьи: " + respFam);
                    return;
                }

            } else {
                String code = familyCodeField.getText();
                if (code == null || code.isBlank()) {
                    statusLabel.setText("Введите код семьи");
                    return;
                }

                String respJoin = ServerConnection.getInstance()
                        .sendCommand("JOIN_FAMILY " + code.trim());

                if (respJoin == null || !respJoin.startsWith("OK JOINED")) {
                    statusLabel.setText("Ошибка присоединения: " + respJoin);
                    return;
                }
            }

            openMainWindow();

            Stage stage = (Stage) loginField.getScene().getWindow();
            stage.close();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка: " + e.getMessage());
        }
    }

    private void openMainWindow() throws Exception {
        FXMLLoader loader = new FXMLLoader(
                HelloApplication.class.getResource("main-view.fxml")
        );

        // --- УВЕЛИЧЕН РАЗМЕР ГЛАВНОГО ОКНА ---
        Scene scene = new Scene(loader.load(), 1100, 800);

        Stage stage = new Stage();
        stage.setTitle("Семейный бюджет");
        stage.initModality(Modality.NONE);
        stage.setScene(scene);

        stage.show();
    }

    @FXML
    private void onCancelClick() {
        Stage stage = (Stage) loginField.getScene().getWindow();
        stage.close();
    }
}
