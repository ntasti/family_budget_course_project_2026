package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import org.familybudget.familybudget.HelloApplication;
import org.familybudget.familybudget.DTO.OperationExportItem;
import org.familybudget.familybudget.Server.ServerConnection;
import org.familybudget.familybudget.SessionContext;
import javafx.scene.chart.PieChart;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class MainController {

    private static MainController instance;

    public MainController() {
        instance = this;
    }

    public static MainController getInstance() {
        return instance;
    }

    @FXML
    private Label familyNameLabel;

    @FXML
    private Label userInfoLabel;

    @FXML
    private Label balanceLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private ListView<OperationRow> operationsList;

    @FXML
    private Button manageCategoriesButton;

    @FXML
    private Button resetFiltersButton;

    @FXML
    private Button addOperationButton;

    @FXML
    private Button refreshOperationsButton;

    @FXML
    private Button logoutButton;

    // сериализация (dat)
    @FXML
    private Button exportButton;

    // экспорт CSV
    @FXML
    private Button exportCsvButton;

    // импорт .dat
    @FXML
    private Button importButton;

    // Фильтры
    @FXML
    private ComboBox<String> typeFilterCombo;

    @FXML
    private ComboBox<String> categoryFilterCombo;

    @FXML
    private ComboBox<String> userFilterCombo;

    @FXML
    private DatePicker fromDatePicker;

    @FXML
    private DatePicker toDatePicker;

    // analytics
    @FXML
    private Button analyticsButton;
    @FXML
    private Button accountButton;
    @FXML
    private Button openPlannedListButton;
    @FXML
    private Button accountsButton;
    // ОДНА круговая диаграмма + выбор типа
    @FXML
    private PieChart categoryPieChart;

    @FXML
    private ComboBox<String> chartTypeCombo;
    @FXML
    private Button categoryPlanButton;

    @FXML
    private ComboBox<AccountsController.AccountItem> accountSelector;
    @FXML
    private Label accountBalanceLabel;
    private AccountsController.AccountItem currentAccount;


    // агрегированные данные по категориям
    private Map<String, Double> incomeTotalsByCategory = new HashMap<>();
    private Map<String, Double> expenseTotalsByCategory = new HashMap<>();

    // полный список операций (до фильтрации)
    private final List<OperationRow> allOperations = new ArrayList<>();

    // модель строки
    public static class OperationRow {
        public long id;        // <--- НОВОЕ
        public String type;    // INCOME / EXPENSE
        public double amount;
        public String category;
        public String user;
        public String date;    // "2025-11-14"
        public String time;    // "14:35"

        public OperationRow(long id,
                            String type,
                            double amount,
                            String category,
                            String user,
                            String date,
                            String time) {
            this.id = id;
            this.type = type;
            this.amount = amount;
            this.category = category;
            this.user = user;
            this.date = date;
            this.time = time;
        }
    }


    @FXML
    private void initialize() {
        String login = SessionContext.getLogin();
        String rawRole = SessionContext.getRole();

        // определяем, админ ли пользователь
        boolean isAdmin = "ADMIN".equalsIgnoreCase(rawRole) || "1".equals(rawRole);

        // красивый текст роли для отображения
        String roleLabel = isAdmin ? "ADMIN" : rawRole;

        userInfoLabel.setText("Пользователь: " + login);

        // показать / скрыть кнопку управления категориями
        if (manageCategoriesButton != null) {
            manageCategoriesButton.setVisible(isAdmin);
            manageCategoriesButton.setManaged(isAdmin);
        }

        // крупные тулбар-кнопки
        setupToolbarButton(addOperationButton);
        setupToolbarButton(manageCategoriesButton);
        setupToolbarButton(analyticsButton);
        setupToolbarButton(accountButton);
        setupToolbarButton(openPlannedListButton);
        setupToolbarButton(importButton);
        setupToolbarButton(categoryPlanButton);
        setupToolbarButton(accountsButton);

        initAccounts();
        loadAccountsForSelector();
        loadFamilyInfo();
        setupOperationsCellFactory();
        setupFilters();
        setupChartsControls(); // <-- твой код для диаграмм

        onRefreshBalance();
        onRefreshOperations();
    }


    // -------------------- ВЫБОР СЧЕТА --------------------

    private void loadAccountsForSelector() {
        try {
            String resp = ServerConnection.getInstance().sendCommand("LIST_ACCOUNTS");
            if (resp == null || !resp.startsWith("OK ACCOUNTS=")) {
                // можно вывести ошибку при желании
                accountSelector.setItems(FXCollections.observableArrayList());
                currentAccount = null;
                accountBalanceLabel.setText("Баланс: —");
                return;
            }

            String payload = resp.substring("OK ACCOUNTS=".length()).trim();
            if (payload.isEmpty()) {
                accountSelector.setItems(FXCollections.observableArrayList());
                currentAccount = null;
                accountBalanceLabel.setText("Баланс: —");
                return;
            }

            List<AccountsController.AccountItem> list = new ArrayList<>();
            for (String row : payload.split(",")) {
                row = row.trim();
                if (row.isEmpty()) continue;

                String[] p = row.split(":", 4); // id:name:currency:isArchived
                if (p.length < 3) continue;

                long id = Long.parseLong(p[0]);
                String name = p[1];
                String curr = p[2];

                list.add(new AccountsController.AccountItem(id, name, curr));
            }

            var observable = FXCollections.observableArrayList(list);
            accountSelector.setItems(observable);

            // если уже был выбран счёт – пробуем сохранить выбор
            if (currentAccount != null) {
                for (AccountsController.AccountItem it : list) {
                    if (it.getId() == currentAccount.getId()) {
                        accountSelector.setValue(it);
                        currentAccount = it;
                        return;
                    }
                }
            }


            accountSelector.setItems(observable);

            // если уже был выбран счёт – пробуем сохранить выбор
            if (currentAccount != null) {
                for (AccountsController.AccountItem it : list) {
                    if (it.getId() == currentAccount.getId()) {
                        currentAccount = it;
                        accountSelector.setValue(it);
                        refreshAccountBalance();   // обновить баланс
                        return;
                    }
                }
            }

            // иначе берём первый как дефолт
            if (!observable.isEmpty()) {
                currentAccount = observable.get(0);
                accountSelector.setValue(currentAccount);
                refreshAccountBalance();
            }

            // вешаем слушатель (один раз, но если боишься дубликатов — можно вынести в initialize())
            accountSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                currentAccount = newVal;
                refreshAccountBalance();
                onRefreshOperations();
            });

        } catch (Exception e) {
            e.printStackTrace();
            accountSelector.setItems(FXCollections.observableArrayList());
            currentAccount = null;
            accountBalanceLabel.setText("Баланс: ошибка");
        }
    }


    @FXML
    private void onAccountSelectorChanged() {
        currentAccount = accountSelector.getValue();
        refreshAccountBalance();
        onRefreshOperations();
    }

    @FXML
    private void onRefreshBalance() {
        refreshAccountBalance();
    }

// -------------------- ПЛАН ПО ЗАТРАТ ПО КАТЕГОРИЯМ --------------------

    @FXML
    private void onOpenCategoryPlanClick() {

        try {
            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("category-plan-list-view.fxml")
            );
            Scene scene = new Scene(loader.load(), 650, 400);
            Stage stage = new Stage();
            stage.setTitle("План по категориям");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия алана: " + e.getMessage());
        }
    }

    private void refreshAccountBalance() {
        if (currentAccount == null) {
            accountBalanceLabel.setText("Баланс: —");
            return;
        }

        try {
            String resp = ServerConnection.getInstance()
                    .sendCommand("GET_ACCOUNT_BALANCE " + currentAccount.getId());

            if (resp != null && resp.startsWith("OK ACCOUNT_BALANCE=")) {
                String val = resp.substring("OK ACCOUNT_BALANCE=".length()).trim();
                accountBalanceLabel.setText("Баланс: " + val);
            } else {
                accountBalanceLabel.setText("Баланс: ошибка");
            }
        } catch (IOException e) {
            e.printStackTrace();
            accountBalanceLabel.setText("Баланс: нет связи");
        }
    }


    @FXML
    private void initAccounts() {
        try {
            String resp = ServerConnection.getInstance().sendCommand("LIST_ACCOUNTS");
            if (resp == null || !resp.startsWith("OK ACCOUNTS=")) {
                accountBalanceLabel.setText("Баланс: ошибка");
                return;
            }

            String payload = resp.substring("OK ACCOUNTS=".length()).trim();
            if (payload.isEmpty()) {
                accountSelector.setItems(FXCollections.observableArrayList());
                accountBalanceLabel.setText("Баланс: —");
                currentAccount = null;
                return;
            }

            var list = new ArrayList<AccountsController.AccountItem>();
            for (String row : payload.split(",")) {
                row = row.trim();
                if (row.isEmpty()) continue;

                String[] p = row.split(":", 4); // id:name:currency:isArchived
                if (p.length < 3) continue;

                long id = Long.parseLong(p[0]);
                String name = p[1];
                String curr = p[2];

                list.add(new AccountsController.AccountItem(id, name, curr));
            }

            var obs = FXCollections.observableArrayList(list);
            accountSelector.setItems(obs);

            if (!obs.isEmpty()) {
                currentAccount = obs.get(0);
                accountSelector.getSelectionModel().select(currentAccount);
                refreshAccountBalance();
            }

            // слушатель смены счёта
            accountSelector.getSelectionModel()
                    .selectedItemProperty()
                    .addListener((obsVal, oldVal, newVal) -> {
                        currentAccount = newVal;
                        refreshAccountBalance();
                        // тут же можно перезагружать список операций по счёту
                    });

        } catch (Exception e) {
            e.printStackTrace();
            accountBalanceLabel.setText("Баланс: ошибка подключения");
        }
    }

    @FXML
    private void onAccountsButtonClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("accounts-view.fxml")
            );
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Счета");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(balanceLabel.getScene().getWindow());
            stage.setScene(scene);
            stage.setResizable(false);
            stage.showAndWait();

            // 👇 после закрытия окна счетов:
            loadAccountsForSelector();   // вдруг добавили/удалили счёт
            refreshAccountBalance();     // и обязательно обновим баланс текущего счёта

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия окна счетов: " + e.getMessage());
        }
    }


// -------------------- СТИЛИ КНОПОК --------------------

    private void setupHoverDark(Button btn, String normal, String hover) {
        if (btn == null) return;
        String base = "-fx-background-radius: 999; " +
                      "-fx-text-fill: white; " +
                      "-fx-font-weight: bold; " +
                      "-fx-padding: 6 14;";

        btn.setStyle("-fx-background-color: " + normal + ";" + base);

        btn.setOnMouseEntered(e ->
                btn.setStyle("-fx-background-color: " + hover + ";" + base));

        btn.setOnMouseExited(e ->
                btn.setStyle("-fx-background-color: " + normal + ";" + base));
    }

    private void setupToolbarButton(Button btn) {
        if (btn == null) return;
        String base = "-fx-background-radius: 999; " +
                      "-fx-text-fill: #333333; " +
                      "-fx-font-weight: bold; " +
                      "-fx-padding: 7 14; " +
                      "-fx-font-size: 13;";

        String normal = "#FFFFFF";
        String hover = "#E0E0E0";

        btn.setStyle("-fx-background-color: " + normal + ";" + base);

        btn.setOnMouseEntered(e ->
                btn.setStyle("-fx-background-color: " + hover + ";" + base));

        btn.setOnMouseExited(e ->
                btn.setStyle("-fx-background-color: " + normal + ";" + base));
    }

// -------------------- ФИЛЬТРЫ --------------------

    private void setupFilters() {
        if (typeFilterCombo != null) {
            typeFilterCombo.setItems(FXCollections.observableArrayList(
                    "Все операции",
                    "Только доходы",
                    "Только расходы"
            ));
            typeFilterCombo.getSelectionModel().selectFirst();
            typeFilterCombo.valueProperty().addListener((obs, o, n) -> applyFilters());
        }

        if (categoryFilterCombo != null) {
            categoryFilterCombo.setItems(FXCollections.observableArrayList("Все категории"));
            categoryFilterCombo.getSelectionModel().selectFirst();
            categoryFilterCombo.valueProperty().addListener((obs, o, n) -> applyFilters());
        }

        if (userFilterCombo != null) {
            userFilterCombo.setItems(FXCollections.observableArrayList("Все пользователи"));
            userFilterCombo.getSelectionModel().selectFirst();
            userFilterCombo.valueProperty().addListener((obs, o, n) -> applyFilters());
        }

        if (fromDatePicker != null) {
            fromDatePicker.valueProperty().addListener((obs, o, n) -> applyFilters());
        }
        if (toDatePicker != null) {
            toDatePicker.valueProperty().addListener((obs, o, n) -> applyFilters());
        }
    }

    private void applyFilters() {
        List<OperationRow> filtered = new ArrayList<>(allOperations);

        if (typeFilterCombo != null) {
            String typeFilter = typeFilterCombo.getValue();
            if ("Только доходы".equals(typeFilter)) {
                filtered = filtered.stream()
                        .filter(o -> "INCOME".equalsIgnoreCase(o.type))
                        .collect(Collectors.toList());
            } else if ("Только расходы".equals(typeFilter)) {
                filtered = filtered.stream()
                        .filter(o -> "EXPENSE".equalsIgnoreCase(o.type))
                        .collect(Collectors.toList());
            }
        }

        if (categoryFilterCombo != null) {
            String catFilter = categoryFilterCombo.getValue();
            if (catFilter != null && !"Все категории".equals(catFilter)) {
                filtered = filtered.stream()
                        .filter(o -> catFilter.equals(o.category))
                        .collect(Collectors.toList());
            }
        }

        if (userFilterCombo != null) {
            String userFilter = userFilterCombo.getValue();
            if (userFilter != null && !"Все пользователи".equals(userFilter)) {
                filtered = filtered.stream()
                        .filter(o -> userFilter.equals(o.user))
                        .collect(Collectors.toList());
            }
        }

        LocalDate from = (fromDatePicker != null) ? fromDatePicker.getValue() : null;
        LocalDate to = (toDatePicker != null) ? toDatePicker.getValue() : null;

        if (from != null || to != null) {
            filtered = filtered.stream()
                    .filter(o -> {
                        try {
                            LocalDate d = LocalDate.parse(o.date); // только дата, без времени
                            if (from != null && d.isBefore(from)) return false;
                            if (to != null && d.isAfter(to)) return false;
                            return true;
                        } catch (DateTimeParseException e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
        }

        operationsList.setItems(FXCollections.observableArrayList(filtered));

        // обновляем диаграммы по отфильтрованному списку
        updateChartsFromList(filtered);
    }

    @FXML
    private void onResetFiltersClick() {
        if (typeFilterCombo != null) typeFilterCombo.getSelectionModel().selectFirst();
        if (categoryFilterCombo != null) categoryFilterCombo.getSelectionModel().selectFirst();
        if (userFilterCombo != null) userFilterCombo.getSelectionModel().selectFirst();
        if (fromDatePicker != null) fromDatePicker.setValue(null);
        if (toDatePicker != null) toDatePicker.setValue(null);
        applyFilters();
    }

// -------------------- БАЛАНС --------------------


// -------------------- ИСТОРИЯ ОПЕРАЦИЙ --------------------

    @FXML
    protected void onRefreshOperations() {
        // 1. нет выбранного счёта — нет операций
        if (currentAccount == null) {
            allOperations.clear();
            operationsList.setItems(FXCollections.observableArrayList());
            statusLabel.setText("Счёт не выбран");
            return;
        }

        try {
            String cmd = "GET_OPERATIONS_ACCOUNT " + currentAccount.getId();
            String resp = ServerConnection.getInstance().sendCommand(cmd);
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (!resp.startsWith("OK OPERATIONS=")) {
                statusLabel.setText("Ошибка: " + resp);
                return;
            }

            String payload = resp.substring("OK OPERATIONS=".length()).trim();

            allOperations.clear();

            if (!payload.isEmpty()) {
                String[] items = payload.split(",");
                for (String item : items) {
                    String line = item.trim();
                    if (line.isEmpty()) continue;

                    // формат строки с сервера:
                    // id:type:categoryName:amount:userLogin:2024-12-08 14:35
                    String[] parts = line.split(":", 6);
                    if (parts.length < 6) {
                        System.out.println("Некорректная строка: " + line);
                        continue;
                    }

                    long id;
                    try {
                        id = Long.parseLong(parts[0]);
                    } catch (NumberFormatException e) {
                        System.out.println("Некорректный id в строке: " + line);
                        continue;
                    }

                    String type = parts[1];
                    String category = parts[2];

                    double amount;
                    try {
                        amount = Double.parseDouble(parts[3]);
                    } catch (NumberFormatException e) {
                        System.out.println("Ошибка суммы в строке: " + line);
                        continue;
                    }

                    String user = parts[4];
                    String dateTime = parts[5];

                    String date = dateTime;
                    String time = "";
                    if (dateTime != null && !dateTime.isBlank()) {
                        String[] dt = dateTime.split(" ", 2);
                        date = dt[0];
                        if (dt.length > 1) {
                            time = dt[1];
                        }
                    }

                    allOperations.add(new OperationRow(
                            id,
                            type,
                            amount,
                            category,
                            user,
                            date,
                            time
                    ));
                }

                // сортируем по дате и времени (от новых к старым)
                Comparator<OperationRow> cmp =
                        Comparator.<OperationRow, String>comparing(o -> o.date)
                                .thenComparing(o -> o.time);
                allOperations.sort(cmp.reversed());
            }

            statusLabel.setText(allOperations.isEmpty() ? "Операций по этому счёту пока нет" : "");

            updateCategoryFilterItems();
            updateUserFilterItems();
            applyFilters(); // обновит ListView и диаграммы

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    private void updateCategoryFilterItems() {
        if (categoryFilterCombo == null) return;

        Set<String> cats = allOperations.stream()
                .map(o -> o.category)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));

        List<String> values = new ArrayList<>();
        values.add("Все категории");
        values.addAll(cats);

        categoryFilterCombo.setItems(FXCollections.observableArrayList(values));
        categoryFilterCombo.getSelectionModel().selectFirst();
    }

    private void updateUserFilterItems() {
        if (userFilterCombo == null) return;

        Set<String> users = allOperations.stream()
                .map(o -> o.user)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));

        List<String> values = new ArrayList<>();
        values.add("Все пользователи");
        values.addAll(users);

        userFilterCombo.setItems(FXCollections.observableArrayList(values));
        userFilterCombo.getSelectionModel().selectFirst();
    }

    private void deleteOperation(OperationRow row) {
        if (row == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Удаление операции");
        alert.setHeaderText(null);
        alert.setContentText("Удалить операцию на сумму "
                             + String.format("%.0f BYN", row.amount)
                             + " из категории \"" + row.category + "\" ?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        try {
            String cmd = "DELETE_TRANSACTION " + row.id;
            String resp = ServerConnection.getInstance().sendCommand(cmd);

            if (resp != null && resp.startsWith("OK TRANSACTION_DELETED")) {
                // убираем из общего списка и обновляем отображение/диаграммы
                allOperations.removeIf(op -> op.id == row.id);
                applyFilters();
                statusLabel.setText("Операция удалена");
            } else if (resp != null && resp.startsWith("ERROR NOT_FOUND")) {
                statusLabel.setText("Операция не найдена (возможно, уже удалена).");
            } else {
                statusLabel.setText("Ошибка удаления: " + resp);
            }
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения при удалении: " + e.getMessage());
        }
    }

// -------------------- ОФОРМЛЕНИЕ СПИСКА --------------------

    // -------------------- ОФОРМЛЕНИЕ СПИСКА --------------------
    private void setupOperationsCellFactory() {
        operationsList.setStyle(
                "-fx-focus-color: transparent; " +
                "-fx-faint-focus-color: transparent;"
        );

        operationsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(OperationRow item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    return;
                }

                boolean income = "INCOME".equalsIgnoreCase(item.type);
                String sign = income ? "+" : "-";
                String amountText = sign + String.format("%.0f BYN", item.amount);

                Label amountLabel = new Label(amountText);
                amountLabel.setPrefWidth(150);
                amountLabel.setAlignment(Pos.CENTER_LEFT);
                amountLabel.setStyle(
                        (income ? "-fx-text-fill: #2E7D32;" : "-fx-text-fill: #C62828;") +
                        "-fx-padding: 6 8 6 8;" +
                        "-fx-font-size: 14;" +
                        "-fx-border-color: #E0E0E0; -fx-border-width: 0 1 0 0;"
                );

                Label categoryLabel = new Label(item.category);
                categoryLabel.setPrefWidth(250);
                categoryLabel.setAlignment(Pos.CENTER_LEFT);
                categoryLabel.setStyle(
                        "-fx-text-fill: #424242;" +
                        "-fx-padding: 6 8 6 8;" +
                        "-fx-font-size: 13;" +
                        "-fx-border-color: #E0E0E0; -fx-border-width: 0 1 0 0;"
                );

                Label userLabel = new Label(item.user);
                userLabel.setPrefWidth(180);
                userLabel.setAlignment(Pos.CENTER_LEFT);
                userLabel.setStyle(
                        "-fx-text-fill: #757575;" +
                        "-fx-padding: 6 8 6 8;" +
                        "-fx-font-size: 13;" +
                        "-fx-border-color: #E0E0E0; -fx-border-width: 0 1 0 0;"
                );

                Label dateLabel = new Label(item.date);
                dateLabel.setPrefWidth(130);
                dateLabel.setAlignment(Pos.CENTER_LEFT);
                dateLabel.setStyle(
                        "-fx-text-fill: #757575;" +
                        "-fx-padding: 6 8 6 8;" +
                        "-fx-font-size: 13;" +
                        "-fx-border-color: #E0E0E0; -fx-border-width: 0 1 0 0;"
                );

                Label timeLabel = new Label(item.time);
                timeLabel.setPrefWidth(80);
                timeLabel.setAlignment(Pos.CENTER_LEFT);
                timeLabel.setStyle(
                        "-fx-text-fill: #757575;" +
                        "-fx-padding: 6 8 6 8;" +
                        "-fx-font-size: 13;"
                );

                Button deleteBtn = new Button();
                deleteBtn.setMinWidth(40);
                deleteBtn.setPrefWidth(40);
                deleteBtn.setMaxWidth(40);
                deleteBtn.setStyle(
                        "-fx-background-color: transparent;" +
                        "-fx-padding: 4 6 4 6;" +
                        "-fx-cursor: hand;"
                );

                javafx.scene.shape.SVGPath trashIcon = new javafx.scene.shape.SVGPath();
                trashIcon.setContent(
                        "M6.5 1h3a.5.5 0 0 1 .5.5v1H6v-1a.5.5 0 0 1 .5-.5M11 2.5v-1A1.5 1.5 0 0 0 9.5 0h-3A1.5 1.5 0 0 0 5 1.5v1H1.5a.5.5 0 0 0 0 1h.538l.853 10.66A2 2 0 0 0 4.885 16h6.23a2 2 0 0 0 1.994-1.84l.853-10.66h.538a.5.5 0 0 0 0-1zm1.958 1-.846 10.58a1 1 0 0 1-.997.92h-6.23a1 1 0 0 1-.997-.92L3.042 3.5zm-7.487 1a.5.5 0 0 1 .528.47l.5 8.5a.5.5 0 0 1-.998.06L5 5.03a.5.5 0 0 1 .47-.53Zm5.058 0a.5.5 0 0 1 .47.53l-.5 8.5a.5.5 0 1 1-.998-.06l.5-8.5a.5.5 0 0 1 .528-.47M8 4.5a.5.5 0 0 1 .5.5v8.5a.5.5 0 0 1-1 0V5a.5.5 0 0 1 .5-.5"
                );
                trashIcon.setStyle("-fx-fill: #DC2626;"); // красный цвет иконки

                deleteBtn.setGraphic(trashIcon);

                // hover-эффект для кнопки
                deleteBtn.setOnMouseEntered(e ->
                        deleteBtn.setStyle("-fx-background-color: #FEE2E2; -fx-padding: 4 6 4 6; -fx-cursor: hand;"));
                deleteBtn.setOnMouseExited(e ->
                        deleteBtn.setStyle("-fx-background-color: transparent; -fx-padding: 4 6 4 6; -fx-cursor: hand;"));

                // действие удаления
                deleteBtn.setOnAction(e -> deleteOperation(item));

                HBox row = new HBox(0);
                row.setAlignment(Pos.CENTER_LEFT);
                String bg = (getIndex() % 2 == 0) ? "#FFFFFF" : "#F9F9F9";
                row.setStyle("-fx-background-color: " + bg + ";");
                row.getChildren().addAll(
                        amountLabel,
                        categoryLabel,
                        userLabel,
                        dateLabel,
                        timeLabel,
                        deleteBtn
                );

                setText(null);
                setGraphic(row);
                setStyle("-fx-border-color: #EFEFEF; -fx-border-width: 0 0 1 0;");
            }
        });
    }


// -------------------- ДАННЫЕ СЕМЬИ --------------------

    private void loadFamilyInfo() {
        try {
            String resp = ServerConnection.getInstance().sendCommand("GET_FAMILY_NAME");
            if (resp == null) {
                familyNameLabel.setText("Семья: (нет данных)");
                return;
            }

            if (resp.startsWith("OK FAMILY_NAME=")) {
                String name = resp.substring("OK FAMILY_NAME=".length()).trim();
                if (name.isEmpty()) {
                    familyNameLabel.setText("Семья: (без имени)");
                } else {
                    familyNameLabel.setText("Семья: " + name);
                }
                return;
            }

            if (resp.startsWith("OK FAMILY ")) {
                int nameIdx = resp.indexOf("name=");
                if (nameIdx >= 0) {
                    int start = nameIdx + "name=".length();
                    int codeIdx = resp.indexOf(" code=", start);
                    String name = (codeIdx > 0)
                            ? resp.substring(start, codeIdx)
                            : resp.substring(start);
                    name = name.trim();
                    if (name.isEmpty()) {
                        familyNameLabel.setText("Семья: (без имени)");
                    } else {
                        familyNameLabel.setText("Семья: " + name);
                    }
                } else {
                    familyNameLabel.setText("Семья: (без имени)");
                }
                return;
            }

            familyNameLabel.setText("Семья: (ошибка)");
            System.out.println("GET_FAMILY_NAME/FAMILY response: " + resp);

        } catch (IOException e) {
            e.printStackTrace();
            familyNameLabel.setText("Семья: (ошибка соединения)");
        }
    }

// -------------------- ДИАГРАММЫ ДОХОДОВ/РАСХОДОВ --------------------

    private void updateChartsFromList(List<OperationRow> rows) {
        // собираем суммы по категориям для доходов и расходов
        Map<String, Double> incomeMap = new HashMap<>();
        Map<String, Double> expenseMap = new HashMap<>();

        for (OperationRow o : rows) {
            if (o == null || o.category == null) continue;
            double amt = o.amount;
            if (amt <= 0) continue;

            if ("INCOME".equalsIgnoreCase(o.type)) {
                incomeMap.merge(o.category, amt, Double::sum);
            } else if ("EXPENSE".equalsIgnoreCase(o.type)) {
                expenseMap.merge(o.category, amt, Double::sum);
            }
        }

        incomeTotalsByCategory = incomeMap;
        expenseTotalsByCategory = expenseMap;

        // перерисовываем диаграмму в соответствии с выбранным типом
        refreshCategoryChart();
    }

    /**
     * Перезаполняет единственную круговую диаграмму categoryPieChart
     * на основании выбранного в chartTypeCombo типа:
     * - "Структура расходов"  -> расходы
     * - "Структура доходов"   -> доходы
     * <p>
     * Подпись каждого сектора: "<Категория> (XX.X%)"
     */
    private void refreshCategoryChart() {
        if (categoryPieChart == null) return;

        String chartType = chartTypeCombo != null ? chartTypeCombo.getValue() : null;
        Map<String, Double> sourceMap;

        if ("Структура доходов".equals(chartType)) {
            sourceMap = incomeTotalsByCategory;
        } else {
            // по умолчанию — структура расходов
            sourceMap = expenseTotalsByCategory;
        }

        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();

        double total = sourceMap.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        for (Map.Entry<String, Double> e : sourceMap.entrySet()) {
            String name = e.getKey();
            double sum = e.getValue();
            double percent = (total == 0) ? 0 : sum / total * 100.0;

            // только название категории и процент
            String label = String.format("%s (%.1f%%)", name, percent);
            data.add(new PieChart.Data(label, sum));
        }

        categoryPieChart.setData(data);
        categoryPieChart.setLabelsVisible(true);
        categoryPieChart.setLegendVisible(true);
    }


    // -------------------- НАСТРОЙКА ВЫБОРА ТИПА ДИАГРАММЫ --------------------

    private void setupChartsControls() {
        if (chartTypeCombo == null) return;

        chartTypeCombo.setItems(FXCollections.observableArrayList(
                "Структура расходов",
                "Структура доходов"
        ));

        // по умолчанию — структура расходов
        chartTypeCombo.getSelectionModel().select("Структура расходов");

        chartTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            refreshCategoryChart();
        });

        // если данные уже будут, после загрузки сразу перерисуем
        refreshCategoryChart();
    }


// -------------------- КНОПКИ --------------------

    @FXML
    protected void onOpenAnalyticsClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("analytics-view.fxml")
            );
            Scene scene = new Scene(loader.load(), 900, 600);
            Stage stage = new Stage();
            stage.setTitle("Аналитика ");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия аналитики: " + e.getMessage());
        }
    }

    @FXML
    private void onAddOperationClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/familybudget/familybudget/add-operation-view.fxml")
            );
            Parent root = loader.load();

            AddOperationController controller = loader.getController();

            // передаём текущий активный счёт в окно добавления операции
            if (currentAccount != null) {
                controller.setCurrentAccount(currentAccount);
            }

            Stage stage = new Stage();
            stage.setTitle("Новая операция");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();

            // 🔄 После закрытия окна – обновляем баланс
            refreshAccountBalance();

            // и при необходимости обновляем список операций/транзакций
            // loadOperations();
        } catch (IOException e) {
            e.printStackTrace();
            // можно вывести в статусбар, если он есть
        }
    }


    @FXML
    protected void onManageCategoriesClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("categories-view.fxml")
            );
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Категории семьи");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();

            onRefreshOperations();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия окна категорий: " + e.getMessage());
        }
    }

    @FXML
    protected void onLogoutClick() {
        SessionContext.clear();
        Stage current = (Stage) balanceLabel.getScene().getWindow();
        current.close();

        try {
            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("hello-view.fxml")
            );
            Scene scene = new Scene(loader.load(), 800, 600);
            Stage stage = new Stage();
            stage.setTitle("Семейный бюджет — вход");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onOpenPlannedListClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/familybudget/familybudget/planned-operations-view.fxml")
            );
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Запланированные списания");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(addOperationButton.getScene().getWindow()); // или любое окно-родитель
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            // можно вывести алерт
        }
    }

    @FXML
    private void onOpenAccountsClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("accounts-view.fxml"));
            Scene scene = new Scene(loader.load(), 650, 300);
            Stage stage = new Stage();
            stage.setTitle("Счета");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(balanceLabel.getScene().getWindow());
            stage.setScene(scene);
            stage.setResizable(false);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Не удалось открыть окно счетов: " + e.getMessage());
        }
    }


// -------------------- ЛИЧНЫЙ КАБИНЕТ --------------------

    @FXML
    private void onOpenAccountClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("account-view.fxml")
            );
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Личный кабинет");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия личного кабинета: " + e.getMessage());
        }
    }
// -------------------- ЭКСПОРТ (dat) --------------------

    @FXML
    private void onExportOperationsClick() {
        if (allOperations.isEmpty()) {
            statusLabel.setText("Нет операций для экспорта");
            return;
        }

        if (currentAccount == null) {
            statusLabel.setText("Счёт не выбран");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт операций (dat)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Файл операций (*.dat)", "*.dat")
        );

        File file = chooser.showSaveDialog(balanceLabel.getScene().getWindow());
        if (file == null) return;

        long accId = currentAccount.getId();
        String accName = currentAccount.getName();

        List<OperationExportItem> exportList = allOperations.stream()
                .map(row -> new OperationExportItem(row, accId, accName))
                .collect(Collectors.toList());

        try (ObjectOutputStream oos =
                     new ObjectOutputStream(new FileOutputStream(file))) {

            oos.writeObject(exportList);
            statusLabel.setText("Экспортировано (dat): " + exportList.size());

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка экспорта: " + e.getMessage());
        }
    }


    // -------------------- ЭКСПОРТ CSV --------------------

    @FXML
    private void onExportOperationsCsvClick() {
        List<OperationRow> toExport = new ArrayList<>(operationsList.getItems());
        if (toExport.isEmpty()) {
            statusLabel.setText("Нет операций для экспорта в CSV");
            return;
        }

        if (currentAccount == null) {
            statusLabel.setText("Счёт не выбран");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт операций в CSV");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV файлы (*.csv)", "*.csv")
        );

        File file = chooser.showSaveDialog(balanceLabel.getScene().getWindow());
        if (file == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("type;amount;category;user;date;time;account\n");

        for (OperationRow o : toExport) {
            sb.append(o.type).append(";")
                    .append(o.amount).append(";")
                    .append(escapeCsv(o.category)).append(";")
                    .append(escapeCsv(o.user)).append(";")
                    .append(o.date).append(";")
                    .append(o.time == null ? "" : o.time).append(";")
                    .append(escapeCsv(currentAccount.getName()))
                    .append("\n");
        }

        try (OutputStream os = new FileOutputStream(file);
             Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {

            writer.write('\uFEFF');
            writer.write(sb.toString());
            statusLabel.setText("Экспортировано CSV: " + toExport.size());

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка экспорта CSV: " + e.getMessage());
        }
    }


    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(";") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }


    // -------------------- ИМПОРТ (dat) --------------------

    @FXML
    private void onImportOperationsClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Импорт операций (dat)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Файл операций (*.dat)", "*.dat")
        );

        File file = chooser.showOpenDialog(balanceLabel.getScene().getWindow());
        if (file == null) return;

        try (ObjectInputStream ois =
                     new ObjectInputStream(new FileInputStream(file))) {

            Object obj = ois.readObject();
            if (!(obj instanceof List<?> rawList)) {
                statusLabel.setText("Неверный формат файла");
                return;
            }

            List<OperationExportItem> imported = new ArrayList<>();
            for (Object o : rawList) {
                if (o instanceof OperationExportItem item) {
                    imported.add(item);
                }
            }

            if (imported.isEmpty()) {
                statusLabel.setText("В файле нет операций");
                return;
            }

            ServerConnection conn = ServerConnection.getInstance();
            Map<String, Long> categoryMap = loadCategoryMap();

            int okCount = 0;
            int skipCount = 0;

            // ===== определяем / создаём счёт =====
            String accName = imported.get(0).getAccountName();
            long accId = findOrCreateAccount(accName, conn);

            // ===== импортируем операции =====
            for (OperationExportItem it : imported) {

                long categoryId = resolveCategoryId(it.getCategory(), categoryMap, conn);

                String type = it.getType();
                double amount = it.getAmount();

                String cmd;
                if ("INCOME".equalsIgnoreCase(type)) {
                    cmd = "ADD_INCOME_ACCOUNT " + accId + " " + categoryId + " " + amount + " Импорт";
                } else if ("EXPENSE".equalsIgnoreCase(type)) {
                    cmd = "ADD_EXPENSE_ACCOUNT " + accId + " " + categoryId + " " + amount + " Импорт";
                } else {
                    skipCount++;
                    continue;
                }

                String respOp = conn.sendCommand(cmd);
                if (respOp != null && respOp.startsWith("OK")) {
                    okCount++;
                } else {
                    skipCount++;
                }
            }

            loadAccountsForSelector();
            refreshAccountBalance();
            onRefreshOperations();

            statusLabel.setText("Импортировано: " + okCount +
                                (skipCount > 0 ? " (пропущено: " + skipCount + ")" : ""));

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка импорта: " + e.getMessage());
        }
    }


    private Map<String, Long> loadCategoryMap() throws IOException {
        Map<String, Long> result = new HashMap<>();

        String resp = ServerConnection.getInstance().sendCommand("LIST_CATEGORIES");
        if (resp == null) {
            throw new IOException("Нет ответа от сервера при LIST_CATEGORIES");
        }

        if (!resp.startsWith("OK CATEGORIES=")) {
            throw new IOException("Ошибка LIST_CATEGORIES: " + resp);
        }

        String payload = resp.substring("OK CATEGORIES=".length()).trim();
        if (payload.isEmpty()) {
            return result;
        }

        String[] parts = payload.split(",");
        for (String p : parts) {
            String line = p.trim();
            if (line.isEmpty()) continue;

            String[] idName = line.split(":", 2);
            if (idName.length == 2) {
                try {
                    long id = Long.parseLong(idName[0]);
                    String name = idName[1];
                    result.put(name, id);
                } catch (NumberFormatException ignored) {
                    System.out.println("Некорректная категория: " + line);
                }
            }
        }

        return result;
    }

    private long findOrCreateAccount(String accName, ServerConnection conn) throws IOException {
        if (accName == null || accName.isBlank()) {
            throw new IOException("Имя счёта пустое в файле импорта");
        }

        String resp = conn.sendCommand("LIST_ACCOUNTS");
        if (resp != null && resp.startsWith("OK ACCOUNTS=")) {
            String payload = resp.substring("OK ACCOUNTS=".length()).trim();
            if (!payload.isEmpty()) {
                String[] rows = payload.split(",");
                for (String row : rows) {
                    row = row.trim();
                    if (row.isEmpty()) continue;

                    String[] p = row.split(":", 4); // id:name:currency:isArchived
                    if (p.length >= 2) {
                        long id = Long.parseLong(p[0]);
                        String nm = p[1];
                        if (accName.equals(nm)) {
                            return id; // нашли уже существующий счёт
                        }
                    }
                }
            }
        }

        // не нашли — создаём
        String respAdd = conn.sendCommand("ADD_ACCOUNT " + accName);
        if (respAdd != null && respAdd.startsWith("OK ACCOUNT_ADDED")) {
            // формат: "OK ACCOUNT_ADDED <id>"
            String tail = respAdd.substring("OK ACCOUNT_ADDED".length()).trim();
            try {
                return Long.parseLong(tail.split("\\s+")[0]);
            } catch (NumberFormatException e) {
                throw new IOException("Не удалось распарсить id нового счёта: " + respAdd, e);
            }
        }
        throw new IOException("Ошибка создания счёта: " + respAdd);
    }

    // поиск/создание категории по имени
    private long resolveCategoryId(String catName,
                                   Map<String, Long> categoryMap,
                                   ServerConnection conn) throws IOException {

        if (catName == null || catName.isBlank()) {
            throw new IOException("Имя категории пустое");
        }

        Long existingId = categoryMap.get(catName);
        if (existingId != null) {
            return existingId;
        }

        String respCat = conn.sendCommand("ADD_CATEGORY " + catName);
        if (respCat != null && respCat.startsWith("OK CATEGORY_CREATED")) {
            String tail = respCat.substring("OK CATEGORY_CREATED".length()).trim();
            String[] idName = tail.split(":", 2);
            if (idName.length == 2) {
                try {
                    long newId = Long.parseLong(idName[0]);
                    categoryMap.put(catName, newId);
                    return newId;
                } catch (NumberFormatException e) {
                    throw new IOException("Не удалось распарсить id категории: " + respCat, e);
                }
            }
        }
        throw new IOException("Ошибка создания категории '" + catName + "': " + respCat);
    }


    // ================== ОБНОВЛЕНИЕ ГЛАВНОГО ОКНА ПОСЛЕ JOIN_FAMILY ==================
    public void refreshAfterJoinFamily() {
        // заново загрузить инфу о семье
        loadFamilyInfo();

        // заново загрузить счета и баланс
        initAccounts();
        loadAccountsForSelector();
        refreshAccountBalance();

        // обновить операции
        onRefreshOperations();

        // пересчитать права (вдруг роль стала ADMIN)
        String rawRole = SessionContext.getRole();
        boolean isAdmin = "ADMIN".equalsIgnoreCase(rawRole) || "1".equals(rawRole);

        if (manageCategoriesButton != null) {
            manageCategoriesButton.setVisible(isAdmin);
            manageCategoriesButton.setManaged(isAdmin);
        }

        // обновить подпись про пользователя (на всякий случай)
        String login = SessionContext.getLogin();
        userInfoLabel.setText("Пользователь: " + login);
    }

}