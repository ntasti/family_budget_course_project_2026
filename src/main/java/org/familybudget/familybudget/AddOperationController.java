package org.familybudget.familybudget;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AddOperationController {

    @FXML
    private ComboBox<String> typeComboBox;

    @FXML
    private ComboBox<CategoryItem> categoryComboBox;

    @FXML
    private TextField amountField;

    @FXML
    private TextField commentField;

    @FXML
    private Label statusLabel;

    // маленькая модель категории для ComboBox
    public static class CategoryItem {
        public final long id;
        public final String name;

        public CategoryItem(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name; // показываем только название
        }
    }

    @FXML
    private void initialize() {
        // Заполняем типы операции
        typeComboBox.setItems(FXCollections.observableArrayList("INCOME", "EXPENSE"));
        typeComboBox.getSelectionModel().selectFirst();

        // Загружаем категории с сервера
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
                statusLabel.setText("Ошибка категорий: " + resp);
                return;
            }

            String payload = resp.substring("OK CATEGORIES=".length()).trim();
            List<CategoryItem> items = new ArrayList<>();

            if (!payload.isEmpty()) {
                // формат: 1:Еда,3:Жильё,5:Одежда...
                String[] parts = payload.split(",");
                for (String p : parts) {
                    String[] kv = p.trim().split(":", 2);
                    if (kv.length == 2) {
                        try {
                            long id = Long.parseLong(kv[0]);
                            String name = kv[1];
                            items.add(new CategoryItem(id, name));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            categoryComboBox.setItems(FXCollections.observableArrayList(items));
            if (!items.isEmpty()) {
                categoryComboBox.getSelectionModel().selectFirst();
            }

            statusLabel.setText("");

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    @FXML
    private void onSaveClick() {
        String type = typeComboBox.getSelectionModel().getSelectedItem();
        CategoryItem category = categoryComboBox.getSelectionModel().getSelectedItem();
        String amountStr = amountField.getText();
        String comment = commentField.getText() == null ? "" : commentField.getText().trim();

        if (type == null || category == null) {
            statusLabel.setText("Выберите тип и категорию");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr.replace(',', '.'));
        } catch (NumberFormatException e) {
            statusLabel.setText("Некорректная сумма");
            return;
        }
        if (amount <= 0) {
            statusLabel.setText("Сумма должна быть > 0");
            return;
        }

        String cmd;
        if ("INCOME".equalsIgnoreCase(type)) {
            cmd = "ADD_INCOME " + category.id + " " + amount;
        } else {
            cmd = "ADD_EXPENSE " + category.id + " " + amount;
        }

        if (!comment.isEmpty()) {
            cmd += " " + comment;
        }

        try {
            String resp = ServerConnection.getInstance().sendCommand(cmd);
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }
            if (resp.startsWith("OK ")) {
                // всё ок — закрываем окно
                closeWindow();
            } else {
                statusLabel.setText("Ошибка: " + resp);
            }
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    @FXML
    private void onCancelClick() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) amountField.getScene().getWindow();
        stage.close();
    }
}
