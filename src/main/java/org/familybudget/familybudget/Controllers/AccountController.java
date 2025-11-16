package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import org.familybudget.familybudget.Server.ServerConnection;
import org.familybudget.familybudget.SessionContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    private void initialize() {
        String login = SessionContext.getLogin();
        String role  = SessionContext.getRole();
        String name  = SessionContext.getUserName();


        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);

        if (name == null || name.isBlank()) {
            name = login;
        }

        userNameLabel.setText(name);
        userEmailLabel.setText(login);
        // пока отдельного имени нет — используем логин как имя и почту
        if (removeMemberButton != null) {
            removeMemberButton.setVisible(isAdmin);
            removeMemberButton.setManaged(isAdmin);
        }

        loadFamilyCode();
        loadFamilyMembers();
    }

    private void loadFamilyCode() {
        try {
            String resp = ServerConnection.getInstance().sendCommand("GET_FAMILY_CODE");
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера (код семьи)");
                return;
            }

            if (resp.startsWith("OK FAMILY_CODE=")) {
                // формат: OK FAMILY_CODE=XX-XXXX NAME=SomeName
                String tail = resp.substring("OK FAMILY_CODE=".length()).trim();
                String code = tail;
                int nameIdx = tail.indexOf(" NAME=");
                if (nameIdx >= 0) {
                    code = tail.substring(0, nameIdx).trim();
                }
                familyCodeLabel.setText(code);
            } else if (resp.startsWith("ERROR NO_FAMILY")) {
                familyCodeLabel.setText("Семья ещё не создана");
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

    private void loadFamilyMembers() {
        try {
            String resp = ServerConnection.getInstance()
                    .sendCommand("LIST_FAMILY_MEMBERS");

            if (resp == null) {
                statusLabel.setText("Ошибка: нет ответа от сервера");
                return;
            }

            if (!resp.startsWith("OK FAMILY_MEMBERS=")) {
                statusLabel.setText("Ошибка списка семьи: " + resp);
                return;
            }

            String payload = resp.substring("OK FAMILY_MEMBERS=".length()).trim();
            if (payload.isEmpty()) {
                familyMembersList.getItems().clear();
                return;
            }

            List<FamilyMemberItem> items = new ArrayList<>();
            String[] parts = payload.split(",");
            for (String p : parts) {
                String line = p.trim();
                if (line.isEmpty()) continue;

                String[] nameEmail = line.split("\\|", 2);
                String name = nameEmail[0];
                String email = nameEmail.length > 1 ? nameEmail[1] : "";

                items.add(new FamilyMemberItem(name, email));
            }

            familyMembersList.setItems(FXCollections.observableArrayList(items));
            statusLabel.setText("");

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка списка семьи: " + e.getMessage());
        }
    }

    @FXML
    private void onRemoveMemberClick() {
        FamilyMemberItem selected = familyMembersList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Выберите члена семьи для удаления");
            return;
        }

        String loginToRemove = selected.getEmail();

        // на всякий случай не даём удалить себя на клиенте тоже
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
                loadFamilyMembers();   // обновляем список
            } else {
                statusLabel.setText("Ошибка удаления: " + resp);
            }

        } catch (IOException e) {
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    public static class FamilyMemberItem {
        private final String name;
        private final String email;

        public FamilyMemberItem(String name, String email) {
            this.name = name;
            this.email = email;
        }

        public String getEmail() {
            return email;
        }

        @Override
        public String toString() {
            // то, что увидим в ListView
            return name + " — " + email;
        }
    }

}
