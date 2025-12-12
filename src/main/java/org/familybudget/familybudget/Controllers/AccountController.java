package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.familybudget.familybudget.Server.ServerConnection;
import org.familybudget.familybudget.SessionContext;

import java.io.IOException;
import java.util.*;

//личный кабинет
//account-view.fxml
public class AccountController {

    @FXML
    private Label userNameLabel;

    @FXML
    private Label userEmailLabel;

    @FXML
    private Label familyCodeLabel;

    @FXML
    private VBox familyCodeBox;

    @FXML
    private ListView<FamilyMemberItem> familyMembersList;

    @FXML
    private Label statusLabel;

    @FXML
    private Button removeMemberButton;

    @FXML
    private VBox joinFamilyBox;

    @FXML
    private TextField joinCodeField;

    @FXML
    private Button joinFamilyButton;

    @FXML
    private void initialize() {
        String login = SessionContext.getLogin();
        String role = SessionContext.getRole();
        String name = SessionContext.getUserName();

        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);

        if (name == null || name.isBlank()) {
            name = login;
        }

        // В шапке:
        userNameLabel.setText(name);   // Имя
        userEmailLabel.setText(login); // Логин / email

        // Кнопка удаления только для админа
        if (removeMemberButton != null) {
            removeMemberButton.setVisible(isAdmin);
            removeMemberButton.setManaged(isAdmin);
        }

        loadFamilyCode();
        loadFamilyMembers();
    }

    // блок присоединиться к семье по коду
    private void showJoinFamilyBox() {
        if (joinFamilyBox != null) {
            joinFamilyBox.setVisible(true);
            joinFamilyBox.setManaged(true);
        }
        // можно дополнительно скрыть блок с кодом семьи
        if (familyCodeBox != null) {
            familyCodeBox.setVisible(false);
            familyCodeBox.setManaged(false);
        }
    }

    //код для присоединения к семье
    private void loadFamilyCode() {
        try {
            String resp = ServerConnection.getInstance().sendCommand("GET_FAMILY_CODE");
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера (код семьи)");
                return;
            }

            if (resp.startsWith("OK FAMILY_CODE=")) {
                String tail = resp.substring("OK FAMILY_CODE=".length()).trim();
                String code = tail;
                int nameIdx = tail.indexOf(" NAME=");
                if (nameIdx >= 0) {
                    code = tail.substring(0, nameIdx).trim();
                }
                familyCodeLabel.setText(code);
            } else if (resp.startsWith("ERROR NO_FAMILY")) {
                familyCodeLabel.setText("Семья ещё не создана");
                showJoinFamilyBox();
            } else if (resp.startsWith("ERROR ACCESS_DENIED")) {
                familyCodeBox.setManaged(false);
                familyCodeBox.setVisible(false);
            } else {
                statusLabel.setText("Ошибка кода семьи: " + resp);
            }

        } catch (IOException e) {
            statusLabel.setText("Ошибка соединения (код семьи): " + e.getMessage());
        }
    }

    //список членов семьи
    private void loadFamilyMembers() {
        try {
            String resp = ServerConnection.getInstance()
                    .sendCommand("LIST_FAMILY_MEMBERS");

            if (resp == null) {
                statusLabel.setText("Ошибка: нет ответа от сервера");
                return;
            }
            // если пользователя удалили из семьи
            if (resp.startsWith("ERROR NO_FAMILY")) {
                familyMembersList.getItems().clear();
                statusLabel.setText("Вы не состоите ни в одной семье");
                showJoinFamilyBox();
                return;
            }
            if (!resp.startsWith("OK FAMILY_MEMBERS=")) {
                statusLabel.setText("Ошибка списка семьи: " + resp);
                return;
            }

            String payload = resp.substring("OK FAMILY_MEMBERS=".length()).trim();
            familyMembersList.getItems().clear();

            if (payload.isEmpty()) {
                return;
            }

            List<FamilyMemberItem> items = new ArrayList<>();

            String[] parts = payload.split(",");
            for (String p : parts) {
                String line = p.trim();
                if (line.isEmpty()) continue;

                String[] loginName = line.split("\\|", 2);
                String login = loginName[0];
                String name = loginName.length > 1 ? loginName[1] : "";

                if (name == null || name.isBlank()) {
                    name = "(без имени)";
                }

                items.add(new FamilyMemberItem(login, name));
            }

            familyMembersList.setItems(FXCollections.observableArrayList(items));
            statusLabel.setText("");

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка списка семьи: " + e.getMessage());
        }
    }

    // обработчик кнопки присоединиться по коду
    @FXML
    private void onJoinFamilyClick() {
        String code = joinCodeField.getText().trim();
        if (code.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Введите код семьи").showAndWait();
            return;
        }

        try {
            String resp = ServerConnection.getInstance().sendCommand("JOIN_FAMILY " + code);

            if (resp != null && resp.startsWith("OK JOINED")) {

                MainController main = MainController.getInstance();
                if (main != null) {
                    main.refreshAfterJoinFamily();
                }

                new Alert(Alert.AlertType.INFORMATION, "Вы успешно присоединились к семье").showAndWait();

                // можно закрыть окно кабинета
                ((Stage) joinCodeField.getScene().getWindow()).close();

            } else {
                new Alert(Alert.AlertType.ERROR, "Ошибка: " + resp).showAndWait();
            }
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Ошибка соединения: " + e.getMessage()).showAndWait();
        }
    }

    //обработчик кнопки удаления членов семьи
    @FXML
    private void onRemoveMemberClick() {
        FamilyMemberItem selected = familyMembersList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Выберите члена семьи для удаления");
            return;
        }

        String loginToRemove = selected.getLogin();

        if (loginToRemove.equals(SessionContext.getLogin())) {
            statusLabel.setText("Нельзя удалить самого себя");
            return;
        }

        try {
            String resp = ServerConnection.getInstance()
                    .sendCommand("REMOVE_FAMILY_MEMBER " + loginToRemove);

            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("OK MEMBER_REMOVED")) {
                statusLabel.setText("Участник удалён");
                loadFamilyMembers();
            } else {
                statusLabel.setText("Ошибка удаления: " + resp);
            }

        } catch (IOException e) {
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    // элемент списка логин - имя
    public static class FamilyMemberItem {
        private final String login;
        private final String name;

        public FamilyMemberItem(String login, String name) {
            this.login = login;
            this.name = name;
        }

        public String getLogin() {
            return login;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return login + " — " + name;
        }
    }
}


