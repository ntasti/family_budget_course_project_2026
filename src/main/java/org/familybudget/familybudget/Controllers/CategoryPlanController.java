package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.familybudget.familybudget.Server.ServerConnection;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


//план по категориям
//category-plan-view.fxml
public class CategoryPlanController {

    @FXML
    private ComboBox<AddOperationController.CategoryItem> categoryComboBox;
    @FXML
    private DatePicker fromDatePicker;
    @FXML
    private DatePicker toDatePicker;
    @FXML
    private TextField amountField;
    @FXML
    private Label statusLabel;
    private String  initialCategoryName;
    private LocalDate initialFrom;
    private LocalDate initialTo;
    private Double initialAmount;
    private Long planId;
    private Long categoryId;

    @FXML
    private void initialize() {
        loadCategories();

        LocalDate today = LocalDate.now();
        fromDatePicker.setValue(today.withDayOfMonth(1));
        toDatePicker.setValue(today.withDayOfMonth(today.lengthOfMonth()));

        // если данные для редактирования уже проставлены то отображаем
        if (initialCategoryName != null) {
            selectCategoryByName(initialCategoryName);
            fromDatePicker.setValue(initialFrom);
            toDatePicker.setValue(initialTo);
            amountField.setText(String.format(Locale.US, "%.2f", initialAmount));
        }
    }

    //загрузка списка категорий LIST_CATEGORIES
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

            List<AddOperationController.CategoryItem> items = new ArrayList<>();
            String[] parts = payload.split(",");
            for (String p : parts) {
                p = p.trim();
                if (p.isEmpty()) continue;

                String[] pair = p.split(":", 2);
                if (pair.length < 2) continue;

                long id = Long.parseLong(pair[0]);
                String name = pair[1];
                items.add(new AddOperationController.CategoryItem(id, name));
            }

            categoryComboBox.setItems(FXCollections.observableArrayList(items));
            statusLabel.setText("");

            if (initialCategoryName != null) {
                selectCategoryByName(initialCategoryName);
            }

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка: " + e.getMessage());
        }
    }

    //Выбор категории по имени
    private void selectCategoryByName(String name) {
        if (categoryComboBox == null) return;
        for (AddOperationController.CategoryItem item : categoryComboBox.getItems()) {
            if (item.name.equals(name)) {
                categoryComboBox.setValue(item);
                break;
            }
        }
    }

    //сохранить
    @FXML
    private void onSaveClick() {
        statusLabel.setText("");

        AddOperationController.CategoryItem cat = categoryComboBox.getValue();
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
            amount = Double.parseDouble(
                    amountField.getText().trim().replace(",", ".")
            );
        } catch (Exception e) {
            statusLabel.setText("Некорректная сумма");
            return;
        }

        try {
            String amountStr = String.format(Locale.US, "%.2f", amount);
            long selectedCategoryId = cat.getId();

            String cmd;

            if (planId == null) {
                // создаём новый план
                cmd = String.format("SET_CATEGORY_PLAN %d %s %s %s", selectedCategoryId, from, to, amountStr);
            } else {

                cmd = String.format("UPDATE_CATEGORY_PLAN %d %d %s %s %s", planId, selectedCategoryId, from, to, amountStr);
            }

            String resp = ServerConnection.getInstance().sendCommand(cmd);
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("OK PLAN_SET") || resp.startsWith("OK PLAN_UPDATED")) {
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

    //закрытие окна
    @FXML
    private void onCancelClick() {
        Stage stage = (Stage) amountField.getScene().getWindow();
        stage.close();
    }

    //для редактирования
    public void setInitialData(long planId, long categoryId, String categoryName, LocalDate from, LocalDate to, double amount) {
        this.planId = planId;
        this.categoryId = categoryId;
        this.initialCategoryName = categoryName;
        this.initialFrom = from;
        this.initialTo = to;
        this.initialAmount = amount;
        if (fromDatePicker != null) fromDatePicker.setValue(from);
        if (toDatePicker != null) toDatePicker.setValue(to);
        if (amountField != null)
            amountField.setText(String.format(Locale.US, "%.2f", amount));
        if (categoryComboBox != null && !categoryComboBox.getItems().isEmpty()) {
            selectCategoryByName(categoryName);
        }
    }
}
