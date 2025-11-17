package org.familybudget.familybudget.Controllers;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.familybudget.familybudget.Server.ServerConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        statusLabel.setText("");

        try {
            String resp = ServerConnection.getInstance().sendCommand("LIST_PLANNED");
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (!resp.startsWith("OK PLANNED=")) {
                statusLabel.setText("Ошибка получения списка: " + resp);
                return;
            }

            String payload = resp.substring("OK PLANNED=".length()).trim();
            List<PlannedItem> items = new ArrayList<>();

            if (!payload.isEmpty()) {
                String[] rows = payload.split(",");
                for (String row : rows) {
                    String line = row.trim();
                    if (line.isEmpty()) continue;

                    // id:type:categoryId:amount:dateStart:time:period:endDate:isImportant:comment
                    String[] parts = line.split(":", 10);
                    if (parts.length < 9) continue;

                    try {
                        long id = Long.parseLong(parts[0]);
                        String type = parts[1];
                        long categoryId = Long.parseLong(parts[2]);
                        double amount = Double.parseDouble(parts[3]);
                        String dateStart = parts[4];
                        String time = parts[5];
                        String period = parts[6];
                        String endDate = parts[7]; // можем пока не показывать
                        boolean important = "1".equals(parts[8]);
                        String comment = (parts.length == 10) ? parts[9] : "";

                        items.add(new PlannedItem(
                                id, type, categoryId, amount,
                                dateStart, time, period, endDate,
                                important, comment
                        ));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            plannedTable.setItems(FXCollections.observableArrayList(items));
            statusLabel.setText("");

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    @FXML
    private void onRefreshClick() {
        loadPlanned();
    }

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

    @FXML
    private void onCloseClick() {
        Stage stage = (Stage) plannedTable.getScene().getWindow();
        stage.close();
    }

    // --- Модель строки для таблицы ---
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
