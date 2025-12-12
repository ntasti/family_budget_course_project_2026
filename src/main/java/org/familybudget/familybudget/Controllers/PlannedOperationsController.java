package org.familybudget.familybudget.Controllers;

import javafx.beans.property.*;
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

//плановая операция
//planned-operation-view.fxml
public class PlannedOperationsController {

    @FXML
    private TableView<PlannedItem> plannedTable;

    @FXML
    private TableColumn<PlannedItem, String> colType;

    @FXML
    private TableColumn<PlannedItem, String> colCategoryId;

    @FXML
    private TableColumn<PlannedItem, String> colAmount;

    @FXML
    private TableColumn<PlannedItem, String> colDateStart;

    @FXML
    private TableColumn<PlannedItem, String> colTime;

    @FXML
    private TableColumn<PlannedItem, String> colPeriod;

    @FXML
    private TableColumn<PlannedItem, String> colImportant;

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        colType.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getType()));
        colCategoryId.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getCategoryId())
        ));
        colAmount.setCellValueFactory(data -> new SimpleStringProperty(
                String.format("%.2f", data.getValue().getAmount())
        ));
        colDateStart.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDateStart()));
        colTime.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTime()));
        colPeriod.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPeriod()));
        colImportant.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().isImportant() ? "Да" : "Нет")
        );

        loadPlanned();
    }

    private void loadPlanned() {
        try {
            String resp = ServerConnection.getInstance()
                    .sendCommand("LIST_PLANNED");
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }
            if (!resp.startsWith("OK PLANNED=")) {
                statusLabel.setText("Ошибка: " + resp);
                return;
            }

            String payload = resp.substring("OK PLANNED=".length()).trim();
            if (payload.isEmpty()) {
                plannedTable.setItems(FXCollections.observableArrayList());
                statusLabel.setText("Запланированных списаний нет");
                return;
            }

            var items = FXCollections.<PlannedItem>observableArrayList();

            // одна строка формата:
            // id:type:catId:amount:dateStart:HH:MM:PERIOD:endDate:isImportant:comment...
            String[] rows = payload.split(",");
            for (String row : rows) {
                row = row.trim();
                if (row.isEmpty()) continue;

                String[] p = row.split(":", 11);
                if (p.length < 10) {
                    continue;
                }

                long id      = Long.parseLong(p[0]);
                String type  = p[1];
                long catId   = Long.parseLong(p[2]);
                double amount = Double.parseDouble(p[3]);

                // дата и время храним сразу строками
                String dateStartStr = p[4];                 // "2025-11-17"
                String timeStr      = p[5] + ":" + p[6];    // "22:04"

                String period      = p[7];                  // DAILY / WEEKLY / MONTHLY / ONCE
                String endDateStr  = p[8];                  // может быть "null" или пусто
                boolean important  = "1".equals(p[9]);
                String comment     = (p.length >= 11) ? p[10] : "";

                items.add(new PlannedItem(
                        id,
                        type,
                        catId,
                        amount,
                        dateStartStr,
                        timeStr,
                        period,
                        endDateStr,
                        important,
                        comment
                ));
            }

            plannedTable.setItems(items);
            statusLabel.setText("");

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка загрузки: " + e.getMessage());
        }
    }


    @FXML
    private void onRefreshClick() {
        loadPlanned();
    }

    //удаление
    @FXML
    private void onDeleteClick() {
        PlannedItem selected = plannedTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Выберите запись для удаления");
            return;
        }

        long id = selected.getId();

        try {
            String resp = ServerConnection.getInstance()
                    .sendCommand("DELETE_PLANNED " + id);

            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("OK PLANNED_DELETED")) {
                statusLabel.setText("Запланированная операция удалена");
                loadPlanned();
            } else {
                statusLabel.setText("Ошибка удаления: " + resp);
            }

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    //закрыть окно
    @FXML
    private void onCloseClick() {
        Stage stage = (Stage) plannedTable.getScene().getWindow();
        stage.close();
    }

    // открыть окно добавления запланированной операции
    @FXML
    private void onAddNewClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/familybudget/familybudget/add-time-operation-view.fxml")
            );
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Запланировать списание");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(plannedTable.getScene().getWindow());
            stage.setResizable(false);
            stage.showAndWait();

            // после закрытия окна обновляем список
            loadPlanned();

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия окна: " + e.getMessage());
        }
    }

    //модель строки для таблицы
    public static class PlannedItem {
        private final long id;
        private final String type;
        private final long categoryId;
        private final double amount;
        private final String dateStart;
        private final String time;
        private final String period;
        private final String endDate;
        private final boolean important;
        private final String comment;

        public PlannedItem(long id, String type, long categoryId, double amount,
                           String dateStart, String time, String period,
                           String endDate, boolean important, String comment) {
            this.id = id;
            this.type = type;
            this.categoryId = categoryId;
            this.amount = amount;
            this.dateStart = dateStart;
            this.time = time;
            this.period = period;
            this.endDate = endDate;
            this.important = important;
            this.comment = comment;
        }

        public long getId() { return id; }
        public String getType() { return type; }
        public long getCategoryId() { return categoryId; }
        public double getAmount() { return amount; }
        public String getDateStart() { return dateStart; }
        public String getTime() { return time; }
        public String getPeriod() { return period; }
        public boolean isImportant() { return important; }
        public String getComment() { return comment; }
    }
}
