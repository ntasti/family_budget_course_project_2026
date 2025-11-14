package org.familybudget.familybudget;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public class AddOperationController {

    @FXML
    private ComboBox<String> typeComboBox;      // "INCOME" / "EXPENSE"

    @FXML
    private ComboBox<CategoryItem> categoryComboBox;

    @FXML
    private TextField amountField;

    @FXML
    private TextField commentField;

    @FXML
    private Label statusLabel;

    // маленькая модель категории
    public static class CategoryItem {
        private final long id;
        private final String name;

        public CategoryItem(long id, String name) {
            this.id = id;
            this.name = name;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name; // так будет отображаться в ComboBox
        }
    }

    @FXML
    private void initialize() {
        // тип операции
        typeComboBox.setItems(FXCollections.observableArrayList("INCOME", "EXPENSE"));
        typeComboBox.getSelectionModel().select("EXPENSE");

        // только числа в поле суммы (до двух знаков после точки)
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String newText = change.getControlNewText();
            if (newText.matches("\\d*(\\.\\d{0,2})?")) {
                return change;
            }
            return null;
        };
        amountField.setTextFormatter(new TextFormatter<>(filter));

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
                statusLabel.setText("Ошибка загрузки категорий: " + resp);
                return;
            }

            String payload = resp.substring("OK CATEGORIES=".length()).trim();
            if (payload.isEmpty()) {
                categoryComboBox.setItems(FXCollections.observableArrayList());
                statusLabel.setText("Категорий пока нет");
                return;
            }

            String[] items = payload.split(",");
            List<CategoryItem> list = new ArrayList<>();
            for (String item : items) {
                String line = item.trim();
                if (line.isEmpty()) continue;

                // формат: id:name
                String[] parts = line.split(":", 2);
                if (parts.length < 2) continue;

                try {
                    long id = Long.parseLong(parts[0]);
                    String name = parts[1];
                    list.add(new CategoryItem(id, name));
                } catch (NumberFormatException ignored) {
                }
            }

            categoryComboBox.setItems(FXCollections.observableArrayList(list));
            if (!list.isEmpty()) {
                categoryComboBox.getSelectionModel().selectFirst();
            }

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    @FXML
    private void onSaveClick() {
        statusLabel.setText("");

        String type = typeComboBox.getValue();
        CategoryItem category = categoryComboBox.getValue();
        String amountStr = amountField.getText();
        String comment = commentField.getText() == null ? "" : commentField.getText().trim();

        // --- валидация ---
        if (type == null || type.isBlank()) {
            statusLabel.setText("Выберите тип операции");
            return;
        }

        if (category == null) {
            statusLabel.setText("Выберите категорию");
            return;
        }

        if (amountStr == null || amountStr.isBlank()) {
            statusLabel.setText("Введите сумму");
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
            statusLabel.setText("Сумма должна быть больше 0");
            return;
        }

        try {
            String cmd;
            if ("INCOME".equalsIgnoreCase(type)) {
                cmd = "ADD_INCOME " + category.getId() + " " + amount + " " + comment;
            } else { // EXPENSE
                cmd = "ADD_EXPENSE " + category.getId() + " " + amount + " " + comment;
            }

            String resp = ServerConnection.getInstance().sendCommand(cmd);
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("OK")) {
                // успех — просто закрываем окно
                Stage stage = (Stage) amountField.getScene().getWindow();
                stage.close();
            } else {
                statusLabel.setText("Ошибка сохранения: " + resp);
            }

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    @FXML
    private void onCancelClick() {
        Stage stage = (Stage) amountField.getScene().getWindow();
        stage.close();
    }
}
