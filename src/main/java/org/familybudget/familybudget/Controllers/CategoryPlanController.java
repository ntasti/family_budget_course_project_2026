package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.familybudget.familybudget.Server.ServerConnection;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryPlanController {

    @FXML
    private ComboBox<CategoryItem> categoryComboBox;

    @FXML
    private DatePicker fromDatePicker;

    @FXML
    private DatePicker toDatePicker;

    @FXML
    private TextField amountField;

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        loadCategories();
        // по умолчанию – текущий месяц
        LocalDate today = LocalDate.now();
        fromDatePicker.setValue(today.withDayOfMonth(1));
        toDatePicker.setValue(today.withDayOfMonth(today.lengthOfMonth()));
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
                statusLabel.setText("Категорий нет");
                return;
            }

            List<CategoryItem> items = new ArrayList<>();
            String[] parts = payload.split(",");
            for (String p : parts) {
                p = p.trim();
                if (p.isEmpty()) continue;

                String[] pair = p.split(":", 2);
                if (pair.length < 2) continue;

                long id = Long.parseLong(pair[0]);
                String name = pair[1];
                items.add(new CategoryItem(id, name));
            }

            categoryComboBox.setItems(FXCollections.observableArrayList(items));
            statusLabel.setText("");

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка: " + e.getMessage());
        }
    }

    @FXML
    private void onSaveClick() {
        statusLabel.setText("");

        CategoryItem cat = categoryComboBox.getValue();
        if (cat == null) {
            statusLabel.setText("Выберите категорию");
            return;
        }

        LocalDate from = fromDatePicker.getValue();
        LocalDate to   = toDatePicker.getValue();
        if (from == null || to == null) {
            statusLabel.setText("Укажите период");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountField.getText().trim().replace(",", "."));
        } catch (Exception e) {
            statusLabel.setText("Некорректная сумма");
            return;
        }

        // форматируем сумму с точкой
        String amountStr = String.format(Locale.US, "%.2f", amount);

        try {
            String cmd = String.format(
                    "SET_CATEGORY_PLAN %d %s %s %s",
                    cat.id,
                    from,
                    to,
                    amountStr
            );

            String resp = ServerConnection.getInstance().sendCommand(cmd);
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("OK PLAN_SET")) {
                statusLabel.setStyle("-fx-text-fill: #388E3C; -fx-font-size: 11;");
                statusLabel.setText("План успешно сохранён");
            } else {
                statusLabel.setStyle("-fx-text-fill: #D32F2F; -fx-font-size: 11;");
                statusLabel.setText("Ошибка: " + resp);
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

    // маленькая модель для ComboBox
    public static class CategoryItem {
        final long id;
        final String name;

        public CategoryItem(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
