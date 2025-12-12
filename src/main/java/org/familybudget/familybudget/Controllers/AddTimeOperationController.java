package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.familybudget.familybudget.Server.ServerConnection;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

//операция по таймеру
//add-time-operation-view.fxml
public class AddTimeOperationController {

    @FXML
    private ComboBox<CategoryItem> categoryComboBox;

    @FXML
    private TextField amountField;

    @FXML
    private CheckBox importantCheckBox;

    @FXML
    private DatePicker startDatePicker;

    @FXML
    private TextField timeField;

    @FXML
    private ComboBox<String> periodComboBox;

    @FXML
    private DatePicker endDatePicker;

    @FXML
    private TextField commentField;

    @FXML
    private Label statusLabel;
    @FXML
    private ComboBox<AccountsController.AccountItem> accountComboBox;


    @FXML
    private void initialize() {
        // только числа в поле суммы
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String newText = change.getControlNewText();
            if (newText.matches("\\d*(\\.\\d{0,2})?")) {
                return change;
            }
            return null;
        };
        amountField.setTextFormatter(new TextFormatter<>(filter));

        // периодичность
        periodComboBox.setItems(FXCollections.observableArrayList("ONCE", "DAILY", "WEEKLY", "MONTHLY"));
        periodComboBox.getSelectionModel().select("MONTHLY");

        startDatePicker.setValue(LocalDate.now());
        timeField.setText(LocalTime.now().withSecond(0).withNano(0).toString()); // HH:mm

        loadCategories();
        loadAccounts();
    }

    //список категорий
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

    //добавление операции
    @FXML
    private void onSaveClick() {

        statusLabel.setText("");
        AccountsController.AccountItem account = accountComboBox.getValue();
        if (account == null) {
            statusLabel.setText("Выберите счёт");
            return;
        }

        CategoryItem category = categoryComboBox.getValue();
        String amountStr = amountField.getText();
        boolean important = importantCheckBox.isSelected();
        LocalDate startDate = startDatePicker.getValue();
        String timeStr = timeField.getText();
        String period = periodComboBox.getValue();
        LocalDate endDate = endDatePicker.getValue();
        String comment = commentField.getText() == null ? "" : commentField.getText().trim();

        //валидация
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

        if (startDate == null) {
            statusLabel.setText("Выберите дату первого списания");
            return;
        }

        LocalTime time;
        try {
            time = LocalTime.parse(timeStr);
        } catch (DateTimeParseException e) {
            statusLabel.setText("Некорректное время (формат ЧЧ:ММ)");
            return;
        }

        if (period == null || period.isBlank()) {
            statusLabel.setText("Выберите периодичность");
            return;
        }

        if (endDate != null && endDate.isBefore(startDate)) {
            statusLabel.setText("Дата окончания раньше даты начала");
            return;
        }

        // SCHEDULE_EXPENSE_ACCOUNT
        String endDateStr = (endDate == null) ? "NULL" : endDate.toString();

        StringBuilder cmd = new StringBuilder("SCHEDULE_EXPENSE_ACCOUNT ");
        cmd.append(account.getId()).append(" ")
                .append(category.getId()).append(" ")
                .append(amount).append(" ")
                .append(important ? "1" : "0").append(" ")
                .append(startDate.toString()).append(" ")
                .append(time.toString()).append(" ")
                .append(period).append(" ")
                .append(endDateStr);

        if (!comment.isEmpty()) {
            cmd.append(" ").append(comment);
        }

        try {
            String resp = ServerConnection.getInstance().sendCommand(cmd.toString());
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("OK EXPENSE_SCHEDULED")) {
                // если успешно то закрываем окно
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

    //закрытие окна
    @FXML
    private void onCancelClick() {
        Stage stage = (Stage) amountField.getScene().getWindow();
        stage.close();
    }

    //загрузка счетов
    private void loadAccounts() {
        try {
            String resp = ServerConnection.getInstance().sendCommand("LIST_ACCOUNTS");
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }
            if (!resp.startsWith("OK ACCOUNTS=")) {
                statusLabel.setText("Ошибка загрузки счетов: " + resp);
                return;
            }

            String payload = resp.substring("OK ACCOUNTS=".length()).trim();
            if (payload.isEmpty()) {
                accountComboBox.setItems(FXCollections.observableArrayList());
                statusLabel.setText("Счетов ещё нет");
                return;
            }

            List<AccountsController.AccountItem> list = new ArrayList<>();
            for (String row : payload.split(",")) {
                row = row.trim();
                if (row.isEmpty()) continue;

                String[] p = row.split(":", 4);
                if (p.length < 3) continue;

                long id = Long.parseLong(p[0]);
                String name = p[1];
                String curr = p[2];

                list.add(new AccountsController.AccountItem(id, name, curr));
            }

            var obs = FXCollections.observableArrayList(list);
            accountComboBox.setItems(obs);

            if (!obs.isEmpty()) {
                accountComboBox.getSelectionModel().selectFirst();
            }

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка загрузки счетов: " + e.getMessage());
        }
    }

    // модель для выбора категории
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
            return name;
        }
    }
}
