package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.familybudget.familybudget.Server.ServerConnection;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

public class AnalyticsController {

    @FXML private PieChart importancePieChart;

    @FXML
    private DatePicker fromDatePicker;

    @FXML
    private DatePicker toDatePicker;

    @FXML
    private PieChart categoryPieChart;

    // гистограмма План / Факт
    @FXML
    private BarChart<String, Number> planFactChart;

    // выбор режима: "Расходы по категориям" / "План / факт по категориям"
    @FXML
    private ComboBox<String> viewTypeCombo;

    @FXML
    private Label statusLabel;

    @FXML
    private Label summaryLabel;

    // контейнер для "карточек" по месяцам
    @FXML
    private VBox monthlyOverviewBox;        // весь блок
    @FXML
    private VBox monthlyCardsContainer;     // только список карточек

    @FXML
    private void initialize() {
        statusLabel.setText("");
        summaryLabel.setText("Выберите период и нажмите «Показать».");

        // период по умолчанию: последний месяц
        LocalDate today = LocalDate.now();
        LocalDate monthAgo = today.minusMonths(1);

        fromDatePicker.setValue(monthAgo);
        toDatePicker.setValue(today);

        // режимы аналитики
        viewTypeCombo.setItems(FXCollections.observableArrayList(
                "Расходы по категориям",
                "План / факт по категориям",
                "Приоритеты (важно/неважно)",
                "Обзор по месяцам"
        ));
        // по умолчанию круговая по расходам
        viewTypeCombo.getSelectionModel().select("Расходы по категориям");

        viewTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateVisibleChart();
            summaryLabel.setText("Выберите период и нажмите «Показать».");
        });

        // сначала настроить, какой блок виден
        updateVisibleChart();
        loadPlanFactChart();
        loadImportanceAnalytics();
        onCalculateClick();
        loadMonthlyOverview();
    }

    /**
     * Показывает только нужный блок и прячет остальные
     */
    private void updateVisibleChart() {
        String mode = viewTypeCombo.getValue();

        boolean isCategories = "Расходы по категориям".equals(mode);
        boolean isPlanFact   = "План / факт по категориям".equals(mode);
        boolean isImportance = "Приоритеты (важно/неважно)".equals(mode);
        boolean isMonthly    = "Обзор по месяцам".equals(mode);

        categoryPieChart.setVisible(isCategories);
        categoryPieChart.setManaged(isCategories);

        planFactChart.setVisible(isPlanFact);
        planFactChart.setManaged(isPlanFact);

        importancePieChart.setVisible(isImportance);
        importancePieChart.setManaged(isImportance);

        monthlyOverviewBox.setVisible(isMonthly);
        monthlyOverviewBox.setManaged(isMonthly);
    }

    @FXML
    private void onCalculateClick() {
        statusLabel.setText("");

        String mode = viewTypeCombo.getValue();
        if ("План / факт по категориям".equals(mode)) {
            loadPlanFactChart();
        } else if ("Приоритеты (важно/неважно)".equals(mode)) {
            loadImportanceAnalytics();
        } else if ("Обзор по месяцам".equals(mode)) {
            loadMonthlyOverview();
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

    // ---------- 3. Режим "Приоритеты" (pie + ANALYTICS_IMPORTANCE) ----------

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

        // считаем по всем счетам
        String cmd = "ANALYTICS_IMPORTANCE " + from + " " + to + " ALL";

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

                if ("IMPORTANT".equals(kv[0])) {
                    important = Double.parseDouble(kv[1]);
                } else if ("NOT_IMPORTANT".equals(kv[0])) {
                    notImportant = Double.parseDouble(kv[1]);
                }
            }

            double total = important + notImportant;

            // если вообще нет расходов
            if (total <= 0) {
                importancePieChart.setData(FXCollections.observableArrayList());
                statusLabel.setText("За выбранный период расходов нет.");
                summaryLabel.setText("");
                return;
            }

            // подписи с суммой
            List<PieChart.Data> list = new ArrayList<>();

            if (important > 0) {
                list.add(new PieChart.Data(
                        String.format("Важные (%.0f BYN)", important),
                        important
                ));
            }

            if (notImportant > 0) {
                list.add(new PieChart.Data(
                        String.format("Неважные (%.0f BYN)", notImportant),
                        notImportant
                ));
            }

            importancePieChart.setData(FXCollections.observableArrayList(list));

            summaryLabel.setText(String.format(
                    "Важные: %.0f BYN (%.1f%%), неважные: %.0f BYN (%.1f%%)",
                    important, important / total * 100.0,
                    notImportant, notImportant / total * 100.0
            ));
            statusLabel.setText("");

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    // ---------- 4. Режим "Обзор по месяцам" (карточки + ANALYTICS_MONTHLY) ----------

    /**
     * Используем ANALYTICS_MONTHLY, где сервер возвращает:
     * OK ANALYTICS_MONTHLY=YYYY-MM:expense:income,...
     *
     * Слева показываем доходы, справа расходы, полоска — доля расходов от доходов.
     */
    private void loadMonthlyOverview() {
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

        String cmd = "ANALYTICS_MONTHLY " + from + " " + to;

        try {
            String resp = ServerConnection.getInstance().sendCommand(cmd);
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }
            if (!resp.startsWith("OK ANALYTICS_MONTHLY=")) {
                statusLabel.setText("Ошибка: " + resp);
                return;
            }

            String payload = resp.substring("OK ANALYTICS_MONTHLY=".length()).trim();
            monthlyCardsContainer.getChildren().clear();

            if (payload.isEmpty()) {
                summaryLabel.setText("За выбранный период операций нет.");
                return;
            }

            double totalExpense = 0.0;
            double totalIncome  = 0.0;

            String[] items = payload.split(",");
            for (String item : items) {
                String line = item.trim();
                if (line.isEmpty()) continue;

                String[] p = line.split(":");
                if (p.length < 3) continue;

                String ymStr = p[0]; // "2024-03"
                double expense;
                double income;
                try {
                    expense = Double.parseDouble(p[1]);
                    income  = Double.parseDouble(p[2]);
                } catch (NumberFormatException e) {
                    continue;
                }

                YearMonth ym = YearMonth.parse(ymStr);
                HBox card = buildMonthCard(ym, expense, income);
                monthlyCardsContainer.getChildren().add(card);

                totalExpense += expense;
                totalIncome  += income;
            }

            if (monthlyCardsContainer.getChildren().isEmpty()) {
                summaryLabel.setText("За выбранный период операций нет.");
            } else {
                summaryLabel.setText(String.format(
                        "Итого за период — доходы: %.2f BYN, расходы: %.2f BYN.",
                        totalIncome, totalExpense
                ));
            }

            statusLabel.setText("");

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    /**
     * Одна «карточка» месяца в стиле как на скрине.
     */
    private HBox buildMonthCard(YearMonth ym, double expense, double income) {
        HBox card = new HBox(16);
        card.setAlignment(Pos.CENTER_LEFT);

        // Новая карточка с мягким серо-лавандовым градиентом
        card.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #EEF2FF, #E5E7EB);" +
                "-fx-background-radius: 18;" +
                "-fx-border-radius: 18;" +
                "-fx-border-color: #D1D5DB;" +
                "-fx-border-width: 1;" +
                "-fx-padding: 16 20;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 16, 0.2, 0, 3);"
        );

        // ------ остальной код без изменений ------

        VBox leftBox = new VBox(2);
        Label incomeValue = new Label(String.format("%.0f BYN", income));
        incomeValue.setStyle("-fx-text-fill: #2E7D32; -fx-font-weight: bold; -fx-font-size: 14;");
        Label incomeLabel = new Label("Доходы");
        incomeLabel.setStyle("-fx-text-fill: #555555; -fx-font-size: 11;");
        leftBox.getChildren().addAll(incomeValue, incomeLabel);

        VBox centerBox = new VBox(4);
        String monthName = ym.getMonth()
                .getDisplayName(TextStyle.FULL_STANDALONE, new java.util.Locale("ru"));
        Label monthLabel = new Label(monthName);
        monthLabel.setStyle("-fx-text-fill: #333333; -fx-font-weight: bold; -fx-font-size: 14;");

        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        String dateRange = String.format("%02d.%02d.%d — %02d.%02d.%d",
                start.getDayOfMonth(), start.getMonthValue(), start.getYear(),
                end.getDayOfMonth(), end.getMonthValue(), end.getYear());
        Label dateLabel = new Label(dateRange);
        dateLabel.setStyle("-fx-text-fill: #777777; -fx-font-size: 10;");

        ProgressBar bar = new ProgressBar();
        bar.setPrefWidth(220);

        double progress = income > 0 ? Math.min(expense / income, 1.0) : 1.0;

        if (income == 0 && expense > 0) {
            bar.setProgress(1.0);
            bar.setStyle("-fx-accent: #E53935;");
        } else if (expense <= income) {
            bar.setProgress(progress);
            bar.setStyle("-fx-accent: #4CAF50;");
        } else {
            bar.setProgress(1.0);
            bar.setStyle("-fx-accent: #E53935;");
        }

        centerBox.getChildren().addAll(monthLabel, dateLabel, bar);

        VBox rightBox = new VBox(2);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        Label expenseValue = new Label(String.format("%.0f BYN", expense));
        expenseValue.setStyle("-fx-text-fill: #D32F2F; -fx-font-weight: bold; -fx-font-size: 14;");
        Label expenseLabel = new Label("Расходы");
        expenseLabel.setStyle("-fx-text-fill: #555555; -fx-font-size: 11;");
        rightBox.getChildren().addAll(expenseValue, expenseLabel);

        card.getChildren().addAll(leftBox, centerBox, rightBox);
        return card;
    }



}
