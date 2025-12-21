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

import java.time.temporal.ChronoUnit;

import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.geometry.Insets;


import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.*;

//раздел аналитики
//analytics-view.fxml
public class AnalyticsController {

    @FXML
    private PieChart importancePieChart;

    @FXML
    private DatePicker fromDatePicker;

    @FXML
    private DatePicker toDatePicker;

    @FXML
    private PieChart categoryPieChart;

    // гистограмма План / Факт
    @FXML
    private BarChart<String, Number> planFactChart;

    // выбор режима аналитики
    @FXML
    private ComboBox<String> viewTypeCombo;

    @FXML
    private Label statusLabel;

    @FXML
    private Label summaryLabel;

    // контейнер для аналитики по месяцам
    @FXML
    private VBox monthlyOverviewBox;        // весь блок
    @FXML
    private VBox monthlyCardsContainer;     // только список карточек

    @FXML
    private void initialize() {
        statusLabel.setText("");
        summaryLabel.setText("Выберите период и нажмите «Показать».");

        // период по умолчанию последний месяц
        LocalDate today = LocalDate.now();
        LocalDate monthAgo = today.minusMonths(1);

        fromDatePicker.setValue(monthAgo);
        toDatePicker.setValue(today);

        // режимы аналитики
        viewTypeCombo.setItems(FXCollections.observableArrayList("Расходы по категориям", "План / факт по категориям", "Приоритеты (важно/неважно)", "Обзор по месяцам"));
        // по умолчанию круговая по расходам
        viewTypeCombo.getSelectionModel().select("Расходы по категориям");

        viewTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateVisibleChart();

            if ("Обзор по месяцам".equals(newVal)) {
                // сразу грузим аналитику по месяцам, не глядя на datePicker'ы
                loadMonthlyOverview();
            } else {
                summaryLabel.setText("Выберите период и нажмите «Показать».");
            }
        });


        // Рамка где перечислены месяцы / категории в обзоре по месяцам:
        if (monthlyOverviewBox != null) {
            monthlyOverviewBox.setSpacing(12);                     // больше расстояние внутри блока
            monthlyOverviewBox.setPadding(new Insets(6, 6, 20, 6)); // внешний отступ блока обзора
        }

        if (monthlyCardsContainer != null) {
            monthlyCardsContainer.setSpacing(10);// расстояние между карточками месяцев
            monthlyCardsContainer.setPadding(new Insets(10, 8, 18, 8)); // внутренний отступ "рамки" списка
        }

        if (summaryLabel != null) {
            VBox.setMargin(summaryLabel, new Insets(18, 0, 0, 0));
        }

        updateVisibleChart();
        loadPlanFactChart();
        loadImportanceAnalytics();
        onCalculateClick();
        loadMonthlyOverview();
    }

    //Показывает только нужный блок аналитики и прячет остальные
    private void updateVisibleChart() {
        String mode = viewTypeCombo.getValue();

        boolean isCategories = "Расходы по категориям".equals(mode);
        boolean isPlanFact = "План / факт по категориям".equals(mode);
        boolean isImportance = "Приоритеты (важно/неважно)".equals(mode);
        boolean isMonthly = "Обзор по месяцам".equals(mode);

        categoryPieChart.setVisible(isCategories);
        categoryPieChart.setManaged(isCategories);

        planFactChart.setVisible(isPlanFact);
        planFactChart.setManaged(isPlanFact);

        importancePieChart.setVisible(isImportance);
        importancePieChart.setManaged(isImportance);

        monthlyOverviewBox.setVisible(isMonthly);
        monthlyOverviewBox.setManaged(isMonthly);
    }

    //расчет по выбранной дате при нажатии
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

    //режим расходы по категориям  ANALYTICS_CATEGORIES
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

            // текущий период по категориям
            Map<String, Double> currentByCat = parseCategoryMapFromPayload(payload);
            if (currentByCat.isEmpty()) {
                categoryPieChart.setData(FXCollections.observableArrayList());
                statusLabel.setText("За выбранный период расходов нет.");
                summaryLabel.setText("");
                return;
            }

            double total = currentByCat.values().stream().mapToDouble(Double::doubleValue).sum();

            // данные для круговой диаграммы
            List<PieChart.Data> data = new ArrayList<>();
            for (Map.Entry<String, Double> e : currentByCat.entrySet()) {
                data.add(new PieChart.Data(e.getKey(), e.getValue()));
            }

            // подписи с BYN и %
            for (PieChart.Data d : data) {
                double percent = d.getPieValue() / total * 100.0;
                String label = String.format("%s (%.0f BYN, %.1f%%)", d.getName(), d.getPieValue(), percent);
                d.setName(label);
            }

            categoryPieChart.setData(FXCollections.observableArrayList(data));
            statusLabel.setText("");

            // базовое резюме топ-категория
            Map.Entry<String, Double> topEntry = currentByCat.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);

            if (topEntry != null) {
                summaryLabel.setText(String.format("Топ категория по расходам: %s (%.0f BYN, %.1f%% от всех расходов)", topEntry.getKey(), topEntry.getValue(), topEntry.getValue() / total * 100.0));
            } else {
                summaryLabel.setText("");
            }

            // добавляем персональную рекомендацию по росту категории
            addCategoryTrendAdvice(from, to, currentByCat);

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }


    //режим план / факт по категориям GET_CATEGORY_PLANS
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

            // выводим по названию категории
            Map<String, Double> plannedByCat = new LinkedHashMap<>();
            Map<String, Double> actualByCat = new LinkedHashMap<>();

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
                    actual = Double.parseDouble(p[6]);
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

            // единый набор категорий
            Set<String> allCats = new LinkedHashSet<>();
            allCats.addAll(plannedByCat.keySet());
            allCats.addAll(actualByCat.keySet());

            for (String cat : allCats) {
                double planned = plannedByCat.getOrDefault(cat, 0.0);
                double actual = actualByCat.getOrDefault(cat, 0.0);

                plannedSeries.getData().add(new XYChart.Data<>(cat, planned));
                actualSeries.getData().add(new XYChart.Data<>(cat, actual));
            }

            planFactChart.getData().setAll(plannedSeries, actualSeries);
            statusLabel.setText("");

            // краткие данные внизу окна
            double totalPlanned = plannedByCat.values().stream().mapToDouble(Double::doubleValue).sum();
            double totalActual = actualByCat.values().stream().mapToDouble(Double::doubleValue).sum();

            summaryLabel.setText(String.format("Всего категорий: %d. План: %.2f BYN, факт: %.2f BYN.", allCats.size(), totalPlanned, totalActual));

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    //режим приоритеты ANALYTICS_IMPORTANCE
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

        //высчет по всем счетам
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

            //если нет расходов
            if (total <= 0) {
                importancePieChart.setData(FXCollections.observableArrayList());
                statusLabel.setText("За выбранный период расходов нет.");
                summaryLabel.setText("");
                return;
            }

            // подписи с суммой
            List<PieChart.Data> list = new ArrayList<>();

            if (important > 0) {
                list.add(new PieChart.Data(String.format("Важные (%.0f BYN)", important), important));
            }

            if (notImportant > 0) {
                list.add(new PieChart.Data(String.format("Неважные (%.0f BYN)", notImportant), notImportant));
            }

            importancePieChart.setData(FXCollections.observableArrayList(list));

            summaryLabel.setText(String.format("Важные: %.0f BYN (%.1f%%), неважные: %.0f BYN (%.1f%%)", important, important / total * 100.0, notImportant, notImportant / total * 100.0));
            statusLabel.setText("");

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    //Режим обзор по месяцам ANALYTICS_MONTHLY
    // Режим обзор по месяцам ANALYTICS_MONTHLY
// НЕ зависит от значений сверху (fromDatePicker / toDatePicker)
    private void loadMonthlyOverview() {
        // Берём большой диапазон "за всё время" – условно последние 10 лет
        LocalDate to = LocalDate.now().plusMonths(1);
        LocalDate from = to.minusYears(10);

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
            double totalIncome = 0.0;

            List<MonthSummary> stats = new ArrayList<>();

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
                    income = Double.parseDouble(p[2]);
                } catch (NumberFormatException e) {
                    continue;
                }

                YearMonth ym = YearMonth.parse(ymStr);
                HBox card = buildMonthCard(ym, expense, income);
                monthlyCardsContainer.getChildren().add(card);

                totalExpense += expense;
                totalIncome += income;

                stats.add(new MonthSummary(ym, expense, income));
            }

            if (monthlyCardsContainer.getChildren().isEmpty()) {
                summaryLabel.setText("За выбранный период операций нет.");
            } else {
                summaryLabel.setText(String.format(
                        "Итого за период — доходы: %.2f BYN, расходы: %.2f BYN.",
                        totalIncome, totalExpense
                ));
                // добавляем тренд между последними двумя месяцами
                appendMonthlyTrendAdvice(stats);
            }

            statusLabel.setText("");

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }


    //карточка для каждого месяца
    private HBox buildMonthCard(YearMonth ym, double expense, double income) {
        HBox card = new HBox(16);
        card.setAlignment(Pos.CENTER_LEFT);

        // стили карточки
        card.setStyle("-fx-background-color: linear-gradient(to bottom, #EEF2FF, #E5E7EB);" + "-fx-background-radius: 18;" + "-fx-border-radius: 18;" + "-fx-border-color: #D1D5DB;" + "-fx-border-width: 1;" + "-fx-padding: 16 20;" + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 16, 0.2, 0, 3);");

        // левая часть доходы
        VBox leftBox = new VBox(2);
        Label incomeValue = new Label(String.format("%.0f BYN", income));
        incomeValue.setStyle("-fx-text-fill: #2E7D32; -fx-font-weight: bold; -fx-font-size: 14;");
        Label incomeLabel = new Label("Доходы");
        incomeLabel.setStyle("-fx-text-fill: #555555; -fx-font-size: 11;");
        leftBox.getChildren().addAll(incomeValue, incomeLabel);

        // центральная часть месяц, даты, прогресс-бар + кнопка рекомендаций
        VBox centerBox = new VBox(6);

        String monthName = ym.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru-RU"));
        Label monthLabel = new Label(monthName + " " + ym.getYear());
        monthLabel.setStyle("-fx-text-fill: #333333; -fx-font-weight: bold; -fx-font-size: 14;");

        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        String dateRange = String.format("%02d.%02d.%d — %02d.%02d.%d", start.getDayOfMonth(), start.getMonthValue(), start.getYear(), end.getDayOfMonth(), end.getMonthValue(), end.getYear());
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

        // кнопка внутри блока месяца для рекомендаций
        Button adviceButton = new Button("Рекомендации");
        adviceButton.setStyle("-fx-background-color: #FFFFFF;" + "-fx-background-radius: 999;" + "-fx-border-color: #4C6FFF;" + "-fx-border-radius: 999;" + "-fx-padding: 2 10;" + "-fx-text-fill: #4C6FFF;" + "-fx-font-size: 11;");
        adviceButton.setOnAction(e -> showMonthDetails(ym));

        centerBox.getChildren().addAll(monthLabel, dateLabel, bar, adviceButton);

        // правая часть: расходы
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

    //Режим с персональными рекомендациями по выбранному месяцу ANALYTICS_CATEGORIES
    private void showMonthDetails(YearMonth ym) {
        // текущий месяц
        LocalDate currentFrom = ym.atDay(1);
        LocalDate currentTo = ym.atEndOfMonth();

        // предыдущий месяц
        YearMonth prevYm = ym.minusMonths(1);
        LocalDate prevFrom = prevYm.atDay(1);
        LocalDate prevTo = prevYm.atEndOfMonth();

        try {
            // текущий месяц по категориям
            String respCurrent = ServerConnection.getInstance().sendCommand("ANALYTICS_CATEGORIES " + currentFrom + " " + currentTo);
            if (respCurrent == null || !respCurrent.startsWith("OK ANALYTICS_CATEGORIES=")) {
                showSimpleError("Не удалось получить данные по категориям за выбранный месяц.");
                return;
            }
            String payloadCurrent = respCurrent.substring("OK ANALYTICS_CATEGORIES=".length()).trim();
            Map<String, Double> currentByCat = parseCategoryMapFromPayload(payloadCurrent);
            if (currentByCat.isEmpty()) {
                showSimpleInfo("За этот месяц расходов по категориям нет.");
                return;
            }

            // предыдущий месяц по категориям
            String respPrev = ServerConnection.getInstance().sendCommand("ANALYTICS_CATEGORIES " + prevFrom + " " + prevTo);
            if (respPrev == null || !respPrev.startsWith("OK ANALYTICS_CATEGORIES=")) {
                showSimpleInfo("Нет данных по категориям за предыдущий месяц — сравнивать не с чем.");
                return;
            }
            String payloadPrev = respPrev.substring("OK ANALYTICS_CATEGORIES=".length()).trim();
            Map<String, Double> prevByCat = parseCategoryMapFromPayload(payloadPrev);
            if (prevByCat.isEmpty()) {
                showSimpleInfo("Нет данных по категориям за предыдущий месяц — сравнивать не с чем.");
                return;
            }

            String monthName = ym.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));

            List<String> lines = new ArrayList<>();
            double thresholdPct = 10.0;   // порог роста в процентах
            double minAmount = 20.0;   // минимальная сумма чтобы не ловить копейки

            // ищем категории, где текущий месяц больше по тратам чем предидущий
            for (Map.Entry<String, Double> e : currentByCat.entrySet()) {
                String category = e.getKey();
                double currentVal = e.getValue();
                double prevVal = prevByCat.getOrDefault(category, 0.0);

                if (prevVal <= 0 || currentVal <= prevVal) {
                    continue; // либо не было расходов, либо не выросли
                }

                double deltaPct = (currentVal - prevVal) / prevVal * 100.0;

                if (deltaPct >= thresholdPct && currentVal >= minAmount) {
                    lines.add(String.format("В месяце %s расходы на категорию «%s» выросли на %.1f%% (с %.0f до %.0f BYN)." + "Пересмотрите и постарайтесь сократить расходы на категорию «%s»", monthName, category, deltaPct, prevVal, currentVal, category));
                }
            }

            if (lines.isEmpty()) {
                lines.add(String.format("В месяце %s нет категорий, где расходы заметно выросли по сравнению с предыдущим месяцем.", monthName));
            }

            // окно с рекомендациями
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Рекомендации по месяцу");
            alert.setHeaderText("Персональные рекомендации для " + monthName + " " + ym.getYear());

            // VBox в котором будут лежать все строки рекомендаций
            VBox contentBox = new VBox(8);          // расстояние между элементами
            contentBox.setPadding(new Insets(10));  // внутренние отступы

            boolean first = true;
            for (String line : lines) {

                //  разделительная линия
                if (!first) {
                    contentBox.getChildren().add(new Separator());
                }
                first = false;

                // каждая рекомендация выводится как Label
                Label lbl = new Label(line);
                lbl.setWrapText(true);              // перенос строк, чтобы текст не обрезался
                contentBox.getChildren().add(lbl);
            }

            // ScrollPane для прокрутки списка
            ScrollPane scrollPane = new ScrollPane(contentBox);
            scrollPane.setFitToWidth(true);                                 // растягивание по ширине
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);     // отключение горизонтального скрола
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // для прокрутки списка если список большой

            //  ScrollPane внутри окна Alert
            alert.getDialogPane().setContent(scrollPane);

            alert.getDialogPane().setPrefSize(650, 450);

            alert.showAndWait();

        } catch (IOException e) {
            showSimpleError("Ошибка соединения: " + e.getMessage());
        }
    }

    private void showSimpleError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSimpleInfo(String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Информация");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // разбор payload в Map
    private Map<String, Double> parseCategoryMapFromPayload(String payload) {
        Map<String, Double> res = new LinkedHashMap<>();
        if (payload == null || payload.isBlank()) return res;

        String[] items = payload.split(",");
        for (String item : items) {
            String line = item.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split(":", 2);
            if (parts.length < 2) continue;

            try {
                double sum = Double.parseDouble(parts[1]);
                if (sum > 0) {
                    res.put(parts[0], sum);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return res;
    }

    //добавление рек к summaryLabel и сравнение текущ и предыдущего периода
    private void addCategoryTrendAdvice(LocalDate from, LocalDate to, Map<String, Double> currentByCat) {
        if (currentByCat.isEmpty()) return;

        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 0) return;

        // предыдущий период той же длины
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);

        String cmdPrev = "ANALYTICS_CATEGORIES " + prevFrom + " " + prevTo;

        try {
            String respPrev = ServerConnection.getInstance().sendCommand(cmdPrev);
            if (respPrev == null || !respPrev.startsWith("OK ANALYTICS_CATEGORIES=")) {
                // нет данных для сравнения
                String base = summaryLabel.getText();
                String advice = String.format("Это первый выбранный период для анализа расходов по категориям. " + "Продолжайте фиксировать операции — тогда появятся рекомендации по динамике.");
                summaryLabel.setText((base == null || base.isBlank()) ? advice : base + "\n\n" + advice);
                return;
            }

            String payloadPrev = respPrev.substring("OK ANALYTICS_CATEGORIES=".length()).trim();

            Map<String, Double> prevByCat = parseCategoryMapFromPayload(payloadPrev);
            if (prevByCat.isEmpty()) {
                String base = summaryLabel.getText();
                String advice = "Данных за предыдущий аналогичный период нет — пока сравнивать не с чем.";
                summaryLabel.setText((base == null || base.isBlank()) ? advice : base + "\n\n" + advice);
                return;
            }

            String growCat = null;
            double growDeltaPct = 0.0;
            double growPrev = 0.0;
            double growCurr = 0.0;

            String dropCat = null;
            double dropDeltaPct = 0.0;
            double dropPrev = 0.0;
            double dropCurr = 0.0;

            for (Map.Entry<String, Double> e : currentByCat.entrySet()) {
                String cat = e.getKey();
                double curr = e.getValue();
                double prev = prevByCat.getOrDefault(cat, 0.0);

                if (prev <= 0) continue;

                double deltaPct = (curr - prev) / prev * 100.0;

                // рост
                if (deltaPct > 0 && deltaPct > growDeltaPct) {
                    growDeltaPct = deltaPct;
                    growPrev = prev;
                    growCurr = curr;
                    growCat = cat;
                }

                // падение (экономия)
                if (deltaPct < 0 && Math.abs(deltaPct) > Math.abs(dropDeltaPct)) {
                    dropDeltaPct = deltaPct;
                    dropPrev = prev;
                    dropCurr = curr;
                    dropCat = cat;
                }
            }

            String base = summaryLabel.getText();
            String advice;

            // приоритет: сначала предупреждение о росте, потом похвала за снижение
            if (growCat != null && growDeltaPct >= 10.0) {
                advice = String.format("Расходы в категории «%s» выросли на %.1f%% по сравнению с предыдущим периодом " + "(с %.0f до %.0f BYN). Попробуйте планировать покупки заранее, " + "сравнивать цены и избегать импульсивных трат.", growCat, growDeltaPct, growPrev, growCurr);
            } else if (dropCat != null && Math.abs(dropDeltaPct) >= 10.0) {
                advice = String.format("Расходы в категории «%s» снизились на %.1f%% " + "(с %.0f до %.0f BYN). Это хороший результат — постарайтесь закрепить " + "текущие привычки и не возвращаться к прежнему уровню.", dropCat, Math.abs(dropDeltaPct), dropPrev, dropCurr);
            } else {
                advice = "Структура расходов по категориям в целом близка к предыдущему периоду. " + "Можно задать цели по сокращению 1–2 самых крупных статей расходов.";
            }

            summaryLabel.setText((base == null || base.isBlank()) ? advice : base + "\n\n" + advice);

        } catch (IOException ex) {
//            ex.getMessage();
        }
    }

    //для описания месяца
    private static class MonthSummary {
        final YearMonth ym;
        final double expense;
        final double income;

        MonthSummary(YearMonth ym, double expense, double income) {
            this.ym = ym;
            this.expense = expense;
            this.income = income;
        }
    }

    //Рекомендация по расходам между последними двумя месяцами в окне пай аналитики
    private void appendMonthlyTrendAdvice(List<MonthSummary> stats) {
        if (stats.size() < 2) return;

        // сортируем по месяцу, если вдруг пришло не по порядку
        stats.sort(Comparator.comparing(ms -> ms.ym));

        MonthSummary prev = stats.get(stats.size() - 2);
        MonthSummary last = stats.get(stats.size() - 1);

        String lastName = last.ym.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));
        String prevName = prev.ym.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));

        if (prev.expense > 0) {
            double deltaPct = (last.expense - prev.expense) / prev.expense * 100.0;

            String advice;
            if (deltaPct >= 10.0) {
                // рост расходов
                advice = String.format("В %s расходы были выше на %.1f%% по сравнению с %s " + "(с %.0f до %.0f BYN). Попробуйте заранее распределить бюджет " + "по категориям и задать лимиты на необязательные траты.", lastName, deltaPct, prevName, prev.expense, last.expense);
            } else if (deltaPct <= -10.0) {
                // заметное снижение
                advice = String.format("В %s вам удалось снизить расходы на %.1f%% по сравнению с %s " + "(с %.0f до %.0f BYN). Хорошая динамика — можно зафиксировать " + "удачные решения и перенести их на следующие месяцы.", lastName, Math.abs(deltaPct), prevName, prev.expense, last.expense);
            } else {
                // примерно тот же уровень
                advice = String.format("Расходы в %s были примерно на уровне %s (%.0f и %.0f BYN). " + "Если вы хотите уменьшить траты, попробуйте выделить 1–2 крупные " + "категории и задать для них целевой лимит на следующий месяц.", lastName, prevName, last.expense, prev.expense);
            }

            String base = summaryLabel.getText();
            summaryLabel.setText((base == null || base.isBlank()) ? advice : base + "\n\n" + advice);
        } else if (last.expense > 0) {
            // за прошлый месяц почти не было расходов, а в этом появились
            String advice = String.format("В %s появились существенные расходы (%.0f BYN), тогда как в %s расходов почти не было. " + "Убедитесь, что это разовые траты, а не новая регулярная статья бюджета.", lastName, last.expense, prevName);
            String base = summaryLabel.getText();
            summaryLabel.setText((base == null || base.isBlank()) ? advice : base + "\n\n" + advice);
        }
    }


}
