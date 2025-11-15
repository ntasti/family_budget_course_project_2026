package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import org.familybudget.familybudget.Server.ServerConnection;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AnalyticsController {

    @FXML
    private DatePicker fromDatePicker;

    @FXML
    private DatePicker toDatePicker;

    @FXML
    private PieChart categoryPieChart;

    @FXML
    private Label statusLabel;

    @FXML
    private Label summaryLabel;

    @FXML
    private void initialize() {
        statusLabel.setText("");
        summaryLabel.setText("Выберите период и нажмите «Показать».");
    }

    @FXML
    private void onCalculateClick() {
        LocalDate from = fromDatePicker.getValue();
        LocalDate to   = toDatePicker.getValue();

        if (from == null || to == null) {
            statusLabel.setText("Укажите обе даты: «с» и «по».");
            return;
        }
        if (to.isBefore(from)) {
            statusLabel.setText("Дата «по» не может быть раньше даты «с».");
            return;
        }

        String cmd = "ANALYTICS_CATEGORIES " + from + " " + to;

        try {
            String resp = ServerConnection.getInstance().sendCommand(cmd);
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }
            if (!resp.startsWith("OK ANALYTICS_CATEGORIES=")) {
                statusLabel.setText("Ошибка: " + resp);
                return;
            }

            String payload = resp.substring("OK ANALYTICS_CATEGORIES=".length()).trim();

            List<PieChart.Data> data = new ArrayList<>();
            double total = 0.0;

            if (!payload.isEmpty()) {
                String[] items = payload.split(",");
                for (String item : items) {
                    String line = item.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split(":", 2); // name:sum
                    if (parts.length < 2) continue;

                    String name = parts[0];
                    double sum;
                    try {
                        sum = Double.parseDouble(parts[1]);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    if (sum <= 0) continue;

                    total += sum;
                    data.add(new PieChart.Data(name, sum));
                }
            }

            if (data.isEmpty()) {
                categoryPieChart.setData(FXCollections.observableArrayList());
                statusLabel.setText("За выбранный период расходов нет.");
                summaryLabel.setText("");
                return;
            }

            // Подписи с процентами
            for (PieChart.Data d : data) {
                double percent = d.getPieValue() / total * 100.0;
                String label = String.format("%s (%.0f BYN, %.1f%%)",
                        d.getName(), d.getPieValue(), percent);
                d.setName(label);
            }

            categoryPieChart.setData(FXCollections.observableArrayList(data));
            statusLabel.setText("");

            // Топ категория
            PieChart.Data top = data.stream()
                    .max((a, b) -> Double.compare(a.getPieValue(), b.getPieValue()))
                    .orElse(null);

            if (top != null) {
                summaryLabel.setText(String.format(
                        "Топ категория по расходам: %s (%.0f BYN, %.1f%% от всех расходов)",
                        top.getName(),
                        top.getPieValue(),
                        top.getPieValue() / total * 100.0
                ));
            }

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }
}
