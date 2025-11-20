package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.familybudget.familybudget.Server.ServerConnection;

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
    @FXML
    private CheckBox importantCheckBox;

    @FXML
    private ComboBox<AccountsController.AccountItem> accountComboBox;
    private AccountsController.AccountItem initialAccount;

    // маленькая модель категории
    public static class CategoryItem {
        private final long id;
        protected final String name;

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

        loadAccounts();
        loadCategories();

        // если уже передали счёт до открытия окна – выбрать его
        if (initialAccount != null) {
            selectAccount(initialAccount);
        }
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

        // ... вся валидация как у тебя ...

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
            AccountsController.AccountItem acc = accountComboBox.getValue();
            if (acc == null) {
                statusLabel.setText("Выберите счёт");
                return;
            }

            boolean important = importantCheckBox != null && importantCheckBox.isSelected();

            String cmd;
            if ("INCOME".equalsIgnoreCase(type)) {
                // Для доходов приоритет у тебя на сервере вообще не предусмотрен.
                cmd = "ADD_INCOME_ACCOUNT " + acc.getId() + " " + category.getId() + " " + amount;
                if (!comment.isEmpty()) {
                    cmd += " " + comment;
                }
            } else { // EXPENSE
                cmd = "ADD_EXPENSE_ACCOUNT " + acc.getId() + " " + category.getId() + " " + amount;

                // Добавляем флаг важности и комментарий в формате, который ждёт сервер
                if (important || !comment.isEmpty()) {
                    cmd += " " + (important ? "1" : "0");
                    if (!comment.isEmpty()) {
                        cmd += " " + comment;
                    }
                }
            }

            String resp = ServerConnection.getInstance().sendCommand(cmd);
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("OK")) {
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

    public void setCurrentAccount(AccountsController.AccountItem acc) {
        if (acc == null) return;

        // запоминаем, какой счёт должен быть выбран
        this.initialAccount = acc;

        // если ComboBox уже инициализирован и в нём есть элементы – сразу выберем
        if (accountComboBox != null && accountComboBox.getItems() != null && !accountComboBox.getItems().isEmpty()) {
            selectAccount(acc);
        }
    }


    private void loadAccounts() {
        try {
            String resp = ServerConnection.getInstance().sendCommand("LIST_ACCOUNTS");
            if (resp == null || !resp.startsWith("OK ACCOUNTS=")) {
                return;
            }
            String payload = resp.substring("OK ACCOUNTS=".length()).trim();
            if (payload.isEmpty()) {
                return;
            }
            List<AccountsController.AccountItem> items = new ArrayList<>();
            for (String row : payload.split(",")) {
                row = row.trim();
                if (row.isEmpty()) continue;
                String[] p = row.split(":", 4);
                if (p.length < 3) continue;
                long id = Long.parseLong(p[0]);
                String name = p[1];
                String curr = p[2];
                items.add(new AccountsController.AccountItem(id, name, curr));
            }
            accountComboBox.setItems(FXCollections.observableArrayList(items));
            if (!items.isEmpty()) {
                accountComboBox.setValue(items.get(0)); // по умолчанию первый
            }
        } catch (Exception ignored) {
        }
    }

    private void selectAccount(AccountsController.AccountItem acc) {
        if (accountComboBox == null || acc == null) return;

        for (AccountsController.AccountItem item : accountComboBox.getItems()) {
            if (item.getId() == acc.getId()) {
                accountComboBox.setValue(item);
                break;
            }
        }

    }

}
