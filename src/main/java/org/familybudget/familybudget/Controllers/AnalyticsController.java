package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.familybudget.familybudget.Server.ServerConnection;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class AnalyticsController {

    @FXML private PieChart importancePieChart;
    @FXML private ComboBox<AccountsController.AccountItem> importanceAccountCombo;
    @FXML private HBox importanceFilterBox;
    @FXML
    private DatePicker fromDatePicker;

    @FXML
    private DatePicker toDatePicker;

    @FXML
    private PieChart categoryPieChart;

    // новая гистограмма План / Факт
    @FXML
    private BarChart<String, Number> planFactChart;

    // выбор режима: "Расходы по категориям" / "План / факт по категориям"
    @FXML
    private ComboBox<String> viewTypeCombo;

    @FXML
    private Label statusLabel;

    @FXML
    private Label summaryLabel;

    @FXML
    private void initialize() {
        statusLabel.setText("");
        summaryLabel.setText("Выберите период и нажмите «Показать».");

        // 👉 период по умолчанию: последний месяц
        LocalDate today = LocalDate.now();
        LocalDate monthAgo = today.minusMonths(1);   // можно заменить на minusDays(30), если хочешь ровно 30 дней

        fromDatePicker.setValue(monthAgo);
        toDatePicker.setValue(today);

        // 👉 режимы аналитики
        viewTypeCombo.setItems(FXCollections.observableArrayList(
                "Расходы по категориям",
                "План / факт по категориям",
                "Приоритеты (важно/неважно)"
        ));
        // по умолчанию круговая по расходам
        viewTypeCombo.getSelectionModel().select("Расходы по категориям");

        viewTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateVisibleChart();
            summaryLabel.setText("Выберите период и нажмите «Показать».");
        });

        // сначала настроить, какой график виден
        updateVisibleChart();
        loadPlanFactChart();
        loadImportanceAnalytics();
        onCalculateClick();
    }


    /**
     * Показывает только нужный график и прячет второй
     */
    private void updateVisibleChart() {
        String mode = viewTypeCombo.getValue();

        boolean isCategories = "Расходы по категориям".equals(mode);
        boolean isPlanFact = "План / факт по категориям".equals(mode);
        boolean isImportance = "Приоритеты (важно/неважно)".equals(mode);

        if (categoryPieChart != null) {
            categoryPieChart.setVisible(isCategories);
            categoryPieChart.setManaged(isCategories);
        }

        if (planFactChart != null) {
            planFactChart.setVisible(isPlanFact);
            planFactChart.setManaged(isPlanFact);
        }

        if (importancePieChart != null) {
            importancePieChart.setVisible(isImportance);
            importancePieChart.setManaged(isImportance);
        }

        if (importanceFilterBox != null) {
            importanceFilterBox.setVisible(isImportance);
            importanceFilterBox.setManaged(isImportance);
        }
    }


    @FXML
    private void onCalculateClick() {
        statusLabel.setText("");

        String mode = viewTypeCombo.getValue();
        if ("План / факт по категориям".equals(mode)) {
            loadPlanFactChart();
        } else if ("Приоритеты (важно/неважно)".equals(mode)) {
            loadImportanceAnalytics();
        } else {
            loadCategoryAnalytics();
        }
    }

    // ---------- 1. Режим "Расходы по категориям" (pie + ANALYTICS_CATEGORIES) ----------

    private void loadCategoryAnalytics() {
        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();

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

            // подписи с процентами
            for (PieChart.Data d : data) {
                double percent = d.getPieValue() / total * 100.0;
                String label = String.format("%s (%.0f BYN, %.1f%%)",
                        d.getName(), d.getPieValue(), percent);
                d.setName(label);
            }

            categoryPieChart.setData(FXCollections.observableArrayList(data));
            statusLabel.setText("");

            // топ категория
            PieChart.Data top = data.stream()
                    .max(Comparator.comparingDouble(PieChart.Data::getPieValue))
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

    // ---------- 2. Режим "План / факт по категориям" (bar + GET_CATEGORY_PLANS) ----------

    private void loadPlanFactChart() {
        try {
            String resp = ServerConnection.getInstance().sendCommand("GET_CATEGORY_PLANS");
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }
            if (!resp.startsWith("OK CATEGORY_PLANS=")) {
                statusLabel.setText("Ошибка: " + resp);
                return;
            }

            String payload = resp.substring("OK CATEGORY_PLANS=".length()).trim();
            if (payload.isEmpty()) {
                planFactChart.getData().clear();
                statusLabel.setText("Планы по категориям не заданы.");
                summaryLabel.setText("");
                return;
            }

            // агрегируем по названию категории
            Map<String, Double> plannedByCat = new LinkedHashMap<>();
            Map<String, Double> actualByCat  = new LinkedHashMap<>();

            // формат элемента:
            // id:categoryId:categoryName:from:to:planned:actual
            String[] items = payload.split(",");
            for (String item : items) {
                String line = item.trim();
                if (line.isEmpty()) continue;

                String[] p = line.split(":", 7);
                if (p.length < 7) continue;

                String categoryName = p[2];
                double planned;
                double actual;
                try {
                    planned = Double.parseDouble(p[5]);
                    actual  = Double.parseDouble(p[6]);
                } catch (NumberFormatException e) {
                    continue;
                }

                plannedByCat.merge(categoryName, planned, Double::sum);
                actualByCat.merge(categoryName, actual, Double::sum);
            }

            if (plannedByCat.isEmpty() && actualByCat.isEmpty()) {
                planFactChart.getData().clear();
                statusLabel.setText("Нет данных для построения графика.");
                summaryLabel.setText("");
                return;
            }

            XYChart.Series<String, Number> plannedSeries = new XYChart.Series<>();
            plannedSeries.setName("План");

            XYChart.Series<String, Number> actualSeries = new XYChart.Series<>();
            actualSeries.setName("Факт");

            // единый набор категорий (ключи из обоих map)
            Set<String> allCats = new LinkedHashSet<>();
            allCats.addAll(plannedByCat.keySet());
            allCats.addAll(actualByCat.keySet());

            for (String cat : allCats) {
                double planned = plannedByCat.getOrDefault(cat, 0.0);
                double actual  = actualByCat.getOrDefault(cat, 0.0);

                plannedSeries.getData().add(new XYChart.Data<>(cat, planned));
                actualSeries.getData().add(new XYChart.Data<>(cat, actual));
            }

            planFactChart.getData().setAll(plannedSeries, actualSeries);
            statusLabel.setText("");

            // краткое резюме: сколько категорий и какая всего план/факт
            double totalPlanned = plannedByCat.values().stream()
                    .mapToDouble(Double::doubleValue).sum();
            double totalActual  = actualByCat.values().stream()
                    .mapToDouble(Double::doubleValue).sum();

            summaryLabel.setText(String.format(
                    "Всего категорий: %d. План: %.2f BYN, факт: %.2f BYN.",
                    allCats.size(), totalPlanned, totalActual
            ));

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    // важные неывжные ранзакции
    private void loadImportanceAnalytics() {
        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();

        if (from == null || to == null) {
            statusLabel.setText("Укажите обе даты: «с» и «по».");
            return;
        }
        if (to.isBefore(from)) {
            statusLabel.setText("Дата «по» не может быть раньше даты «с».");
            return;
        }

        AccountsController.AccountItem acc = importanceAccountCombo.getValue();

        String mode;
        if (acc == null || acc.getId() < 0) {
            mode = "ALL";
        } else {
            mode = "ACCOUNT " + acc.getId();
        }

        String cmd = "ANALYTICS_IMPORTANCE " + from + " " + to + " " + mode;

        try {
            String resp = ServerConnection.getInstance().sendCommand(cmd);

            if (resp == null || !resp.startsWith("OK ANALYTICS_IMPORTANCE=")) {
                statusLabel.setText("Ошибка: " + resp);
                return;
            }

            String payload = resp.substring("OK ANALYTICS_IMPORTANCE=".length());

            double important = 0;
            double notImportant = 0;

            for (String part : payload.split(",")) {
                String[] kv = part.split(":");
                if (kv.length != 2) continue;

                if (kv[0].equals("IMPORTANT")) important = Double.parseDouble(kv[1]);
                if (kv[0].equals("NOT_IMPORTANT")) notImportant = Double.parseDouble(kv[1]);
            }

            List<PieChart.Data> list = new ArrayList<>();

            if (important > 0)
                list.add(new PieChart.Data("Важные", important));

            if (notImportant > 0)
                list.add(new PieChart.Data("Неважные", notImportant));

            importancePieChart.setData(FXCollections.observableArrayList(list));

            double total = important + notImportant;

            summaryLabel.setText(String.format(
                    "Важные: %.0f BYN (%.1f%%), неважные: %.0f BYN (%.1f%%)",
                    important, important / total * 100,
                    notImportant, notImportant / total * 100
            ));

        } catch (Exception e) {
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

}
