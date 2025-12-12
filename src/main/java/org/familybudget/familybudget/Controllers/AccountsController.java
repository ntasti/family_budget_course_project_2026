package org.familybudget.familybudget.Controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.familybudget.familybudget.Server.ServerConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

//счета
//accounts-view.fxml
public class AccountsController {

    @FXML
    private TableView<AccountItem> accountsTable;
    @FXML
    private TableColumn<AccountItem, String> colName;
    @FXML
    private TableColumn<AccountItem, String> colCurrency;
    @FXML
    private Label statusLabel;


    @FXML
    private void initialize() {
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colCurrency.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCurrency()));
        loadAccounts();
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
                statusLabel.setText("Ошибка: " + resp);
                accountsTable.setItems(FXCollections.observableArrayList());
                return;
            }

            String payload = resp.substring("OK ACCOUNTS=".length()).trim();
            if (payload.isEmpty()) {
                accountsTable.setItems(FXCollections.observableArrayList());
                statusLabel.setText("Счета ещё не созданы");
                return;
            }

            List<AccountItem> list = new ArrayList<>();
            String[] rows = payload.split(",");
            for (String row : rows) {
                row = row.trim();
                if (row.isEmpty()) continue;

                String[] p = row.split(":", 4);
                if (p.length < 3) continue;

                long id = Long.parseLong(p[0]);
                String name = p[1];
                String curr = p[2];

                list.add(new AccountItem(id, name, curr));
            }

            accountsTable.setItems(FXCollections.observableArrayList(list));
            statusLabel.setText("");

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка загрузки: " + e.getMessage());
        }
    }

    //обновление счетов
    @FXML
    private void onRefreshClick() {
        loadAccounts();
    }

    //добавление нового счета
    @FXML
    private void onAddClick() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Новый счёт");
        dlg.setHeaderText("Введите название счёта");
        dlg.setContentText("Название:");

        dlg.showAndWait().ifPresent(name -> {
            name = name.trim();
            if (name.isEmpty()) return;

            try {
                String resp = ServerConnection.getInstance()
                        .sendCommand("ADD_ACCOUNT " + name);
                if (resp != null && resp.startsWith("OK ACCOUNT_ADDED")) {

                    loadAccounts();
                    statusLabel.setText("");

                    // обновление главного окна
                    MainController main = MainController.getInstance();
                    if (main != null) {
                        main.refreshAfterJoinFamily();
                    }

                } else {
                    statusLabel.setText("Ошибка добавления: " + resp);
                }
            } catch (IOException e) {
                e.printStackTrace();
                statusLabel.setText("Ошибка соединения: " + e.getMessage());
            }
        });
    }

    //редактирование информации о счете
    @FXML
    private void onEditClick() {
        AccountItem item = accountsTable.getSelectionModel().getSelectedItem();
        if (item == null) {
            statusLabel.setText("Выберите счёт");
            return;
        }

        TextInputDialog dlg = new TextInputDialog(item.getName());
        dlg.setTitle("Переименовать счёт");
        dlg.setHeaderText(null);
        dlg.setContentText("Новое название:");

        dlg.showAndWait().ifPresent(newName -> {
            newName = newName.trim();
            if (newName.isEmpty()) return;

            try {
                String cmd = "UPDATE_ACCOUNT " + item.getId() + " " + newName;
                String resp = ServerConnection.getInstance().sendCommand(cmd);
                if (resp != null && resp.startsWith("OK ACCOUNT_UPDATED")) {

                    loadAccounts();
                    statusLabel.setText("");

                    //обновление главного окна
                    MainController main = MainController.getInstance();
                    if (main != null) {
                        main.refreshAfterJoinFamily();
                    }

                } else {
                    statusLabel.setText("Ошибка обновления: " + resp);
                }
            } catch (IOException e) {
                e.printStackTrace();
                statusLabel.setText("Ошибка соединения: " + e.getMessage());
            }
        });
    }

    //удаление
    @FXML
    private void onDeleteClick() {
        AccountItem item = accountsTable.getSelectionModel().getSelectedItem();
        if (item == null) {
            statusLabel.setText("Выберите счёт");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удаление счёта");
        confirm.setHeaderText("Удалить счёт \"" + item.getName() + "\"?");
        confirm.setContentText("Транзакции пока не трогаем.");

        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            try {
                String resp = ServerConnection.getInstance()
                        .sendCommand("DELETE_ACCOUNT " + item.getId());
                if (resp != null && resp.startsWith("OK ACCOUNT_DELETED")) {

                    loadAccounts();
                    statusLabel.setText("");

                    // обновление главного окна
                    MainController main = MainController.getInstance();
                    if (main != null) {
                        main.refreshAfterJoinFamily();
                    }

                } else {
                    statusLabel.setText("Ошибка удаления: " + resp);
                }
            } catch (IOException e) {
                e.printStackTrace();
                statusLabel.setText("Ошибка соединения: " + e.getMessage());
            }
        });
    }

    //закрытие
    @FXML
    private void onCloseClick() {
        Stage stage = (Stage) accountsTable.getScene().getWindow();
        stage.close();
    }

    //откртие окна перевод между счетами
    @FXML
    private void onTransferClick() {
        AccountItem selected = accountsTable.getSelectionModel().getSelectedItem();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/familybudget/familybudget/account-transfer-view.fxml")
            );
            Parent root = loader.load();

            // контроллер окна перевода
            AccountTransferController controller = loader.getController();
            if (selected != null) {
                controller.setInitialFromAccount(selected.getId());
            }

            Stage stage = new Stage();
            stage.setTitle("Перевод между счетами");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();

            // обновляем список счетов
            loadAccounts();

            // и главное окно (балансы/операции)
            MainController main = MainController.getInstance();
            if (main != null) {
                main.refreshAfterJoinFamily();
            }

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия окна перевода: " + e.getMessage());
        }
    }

    //для вывода информации о счетах
    public static class AccountItem {
        final long id;
        final String name;
        final String currency;

        public AccountItem(long id, String name, String currency) {
            this.id = id;
            this.name = name;
            this.currency = currency;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getCurrency() {
            return currency;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
