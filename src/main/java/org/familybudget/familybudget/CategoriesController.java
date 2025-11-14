package org.familybudget.familybudget;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CategoriesController {

    @FXML
    private ListView<String> categoriesList;

    @FXML
    private TextField newCategoryField;

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        loadCategories();
    }

    private void loadCategories() {
        try {
            String resp = ServerConnection.getInstance().sendCommand("LIST_CATEGORIES");
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (!resp.startsWith("OK CATEGORIES=")) {
                statusLabel.setText("Ошибка: " + resp);
                return;
            }

            String payload = resp.substring("OK CATEGORIES=".length()).trim();
            List<String> items = new ArrayList<>();

            if (!payload.isEmpty()) {
                // формат: 1:Еда,3:Жильё,...
                String[] parts = payload.split(",");
                for (String p : parts) {
                    String[] kv = p.trim().split(":", 2);
                    if (kv.length == 2) {
                        // будем показывать просто "Еда", "Жильё", ...
                        items.add(kv[1]);
                    }
                }
            }

            categoriesList.setItems(FXCollections.observableArrayList(items));
            statusLabel.setText("");

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    @FXML
    private void onAddCategoryClick() {
        String name = newCategoryField.getText();
        if (name == null || name.isBlank()) {
            statusLabel.setText("Введите название категории");
            return;
        }

        name = name.trim();

        try {
            String resp = ServerConnection.getInstance()
                    .sendCommand("ADD_CATEGORY " + name);

            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("OK CATEGORY_CREATED")) {
                newCategoryField.clear();
                loadCategories();
            } else {
                statusLabel.setText("Ошибка: " + resp);
            }

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }
}
