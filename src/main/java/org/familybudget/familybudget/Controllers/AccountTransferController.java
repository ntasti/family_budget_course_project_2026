package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.familybudget.familybudget.Server.ServerConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AccountTransferController {

    @FXML
    private ComboBox<AccountsController.AccountItem> fromAccountCombo;

    @FXML
    private ComboBox<AccountsController.AccountItem> toAccountCombo;


    @FXML
    private TextField amountField;

    @FXML
    private TextArea commentArea;

    @FXML
    private Label statusLabel;

    // чтобы можно было заранее подставить "С какого счёта"
    private Long initialFromAccountId = null;

    @FXML
    private void initialize() {
        loadAccounts();

    }

    // вызывается из AccountsController перед показом окна
    public void setInitialFromAccount(long accountId) {
        this.initialFromAccountId = accountId;

        // если комбобоксы уже заполнены – сразу выберем
        if (fromAccountCombo != null && fromAccountCombo.getItems() != null) {
            for (AccountsController.AccountItem ai : fromAccountCombo.getItems()) {
                if (ai.getId() == accountId) {
                    fromAccountCombo.setValue(ai);
                    break;
                }
            }
        }
    }

    // ===== Загрузка счетов =====
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
                fromAccountCombo.setItems(FXCollections.observableArrayList());
                toAccountCombo.setItems(FXCollections.observableArrayList());
                statusLabel.setText("Счетов ещё нет");
                return;
            }

            List<AccountsController.AccountItem> list = new ArrayList<>();
            String[] rows = payload.split(",");
            for (String row : rows) {
                row = row.trim();
                if (row.isEmpty()) continue;

                // формат: id:name:currency:...
                String[] p = row.split(":", 4);
                if (p.length < 3) continue;

                long id = Long.parseLong(p[0]);
                String name = p[1];
                String curr = p[2];

                list.add(new AccountsController.AccountItem(id, name, curr));
            }

            var obs = FXCollections.observableArrayList(list);
            fromAccountCombo.setItems(obs);
            toAccountCombo.setItems(obs);

            // если заранее передали счёт "с какого" — выберем его
            if (initialFromAccountId != null) {
                for (AccountsController.AccountItem ai : obs) {
                    if (ai.getId() == initialFromAccountId) {
                        fromAccountCombo.setValue(ai);
                        break;
                    }
                }
            }

            statusLabel.setText("");

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка загрузки счетов: " + e.getMessage());
        }
    }



    // ===== выполнить перевод =====
    @FXML
    private void onDoTransferClick() {
        statusLabel.setText("");

        var fromAcc = fromAccountCombo.getValue();
        var toAcc = toAccountCombo.getValue();

        if (fromAcc == null) {
            statusLabel.setText("Выберите счёт-источник");
            return;
        }
        if (toAcc == null) {
            statusLabel.setText("Выберите счёт-получатель");
            return;
        }
        if (fromAcc.getId() == toAcc.getId()) {
            statusLabel.setText("Нельзя переводить на тот же счёт");
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
        if (amount <= 0) {
            statusLabel.setText("Сумма должна быть > 0");
            return;
        }

        String comment = commentArea.getText();
        if (comment == null) comment = "";
        comment = comment.trim().replaceAll("\\s+", " ");

        try {
            // новый формат команды: без категории
            String cmd = String.format(
                    Locale.US,
                    "TRANSFER_BETWEEN_ACCOUNTS %d %d %.2f",
                    fromAcc.getId(),
                    toAcc.getId(),
                    amount
            );
            if (!comment.isEmpty()) {
                cmd += " " + comment;
            }

            String resp = ServerConnection.getInstance().sendCommand(cmd);
            if (resp != null && resp.startsWith("OK TRANSFER_DONE")) {
                statusLabel.setStyle("-fx-text-fill: #388E3C; -fx-font-size: 11;");
                statusLabel.setText("Перевод выполнен");
                onCancelClick();
            } else {
                statusLabel.setStyle("-fx-text-fill: #D32F2F; -fx-font-size: 11;");
                statusLabel.setText("Ошибка перевода: " + resp);
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
