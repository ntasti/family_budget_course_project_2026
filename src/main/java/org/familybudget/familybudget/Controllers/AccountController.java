package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
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
    private ListView<String> familyMembersList;

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        String login = SessionContext.getLogin();
        String role  = SessionContext.getRole();

        // пока отдельного имени нет — используем логин как имя и почту
        userNameLabel.setText(login != null ? login : "-");
        userEmailLabel.setText(login != null ? login : "-");

        // код семьи только для админа
        if ("ADMIN".equalsIgnoreCase(role)) {
            loadFamilyCode();
        } else {
            // прячем блок с кодом для не-админов
            familyCodeBox.setManaged(false);
            familyCodeBox.setVisible(false);
        }

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
            String resp = ServerConnection.getInstance().sendCommand("LIST_FAMILY_MEMBERS");
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера (члены семьи)");
                return;
            }

            if (resp.startsWith("OK FAMILY_MEMBERS=")) {
                String payload = resp.substring("OK FAMILY_MEMBERS=".length()).trim();
                if (payload.isEmpty()) {
                    familyMembersList.setItems(FXCollections.observableArrayList());
                    return;
                }

                String[] items = payload.split(",");
                List<String> lines = new ArrayList<>();

                for (String item : items) {
                    String part = item.trim();
                    if (part.isEmpty()) continue;

                    String[] nm = part.split("\\|", 2);
                    String name  = nm[0];
                    String email = nm.length > 1 ? nm[1] : nm[0];

                    lines.add(name + " — " + email);
                }

                familyMembersList.setItems(FXCollections.observableArrayList(lines));

            } else if (resp.startsWith("ERROR NO_FAMILY")) {
                statusLabel.setText("Вы ещё не присоединились к семье");
            } else {
                statusLabel.setText("Ошибка списка семьи: " + resp);
            }

        } catch (IOException e) {
            statusLabel.setText("Ошибка соединения (члены семьи): " + e.getMessage());
        }
    }
}
