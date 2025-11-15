package org.familybudget.familybudget;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javafx.stage.FileChooser;

import java.io.*;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class MainController {

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

    //  НОВОЕ: кнопка импорта .dat
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

    // полный список операций (до фильтрации)
    private final List<OperationRow> allOperations = new ArrayList<>();

    // модель строки
    public static class OperationRow {
        public String type;      // INCOME / EXPENSE
        public double amount;
        public String category;
        public String user;
        public String date;      // "2025-11-14"

        public OperationRow(String type, double amount,
                            String category, String user, String date) {
            this.type = type;
            this.amount = amount;
            this.category = category;
            this.user = user;
            this.date = date;
        }
    }

    @FXML
    private void initialize() {
        String login = SessionContext.getLogin();
        String role = SessionContext.getRole();

        userInfoLabel.setText("Пользователь: " + login + " (роль: " + role + ")");

        if ("ADMIN".equalsIgnoreCase(role) && manageCategoriesButton != null) {
            manageCategoriesButton.setVisible(true);
            manageCategoriesButton.setManaged(true);
        } else if (manageCategoriesButton != null) {
            manageCategoriesButton.setVisible(false);
            manageCategoriesButton.setManaged(false);
        }

        // тулбар-кнопки
        setupToolbarButton(addOperationButton);
        setupToolbarButton(manageCategoriesButton);
        setupToolbarButton(exportButton);
        setupToolbarButton(exportCsvButton);
        setupToolbarButton(importButton);  // ✅ НОВОЕ

        loadFamilyInfo();
        setupOperationsCellFactory();
        setupFilters();

        onRefreshBalance();
        onRefreshOperations();
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
                            LocalDate d = LocalDate.parse(o.date);
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

    @FXML
    protected void onRefreshBalance() {
        try {
            String resp = ServerConnection.getInstance().sendCommand("GET_BALANCE");
            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }
            if (resp.startsWith("OK BALANCE=")) {
                String value = resp.substring("OK BALANCE=".length()).trim();
                balanceLabel.setText(value + " BYN");
                statusLabel.setText("");
            } else {
                statusLabel.setText("Ошибка: " + resp);
            }
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    // -------------------- ИСТОРИЯ ОПЕРАЦИЙ --------------------

    @FXML
    protected void onRefreshOperations() {
        try {
            String resp = ServerConnection.getInstance().sendCommand("GET_OPERATIONS");
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

                    String[] parts = line.split(":");
                    if (parts.length < 6) {
                        System.out.println("Некорректная строка: " + line);
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
                    String date = parts[5];

                    allOperations.add(new OperationRow(type, amount, category, user, date));
                }

                allOperations.sort(Comparator.comparing((OperationRow o) -> o.date).reversed());
            }

            statusLabel.setText(allOperations.isEmpty() ? "Операций пока нет" : "");

            updateCategoryFilterItems();
            updateUserFilterItems();
            applyFilters();

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
                userLabel.setPrefWidth(200);
                userLabel.setAlignment(Pos.CENTER_LEFT);
                userLabel.setStyle(
                        "-fx-text-fill: #757575;" +
                                "-fx-padding: 6 8 6 8;" +
                                "-fx-font-size: 13;" +
                                "-fx-border-color: #E0E0E0; -fx-border-width: 0 1 0 0;"
                );

                Label dateLabel = new Label(item.date);
                dateLabel.setPrefWidth(180);
                dateLabel.setAlignment(Pos.CENTER_LEFT);
                dateLabel.setStyle(
                        "-fx-text-fill: #757575;" +
                                "-fx-padding: 6 8 6 8;" +
                                "-fx-font-size: 13;"
                );

                HBox row = new HBox(0);
                row.setAlignment(Pos.CENTER_LEFT);
                String bg = (getIndex() % 2 == 0) ? "#FFFFFF" : "#F9F9F9";
                row.setStyle("-fx-background-color: " + bg + ";");
                row.getChildren().addAll(amountLabel, categoryLabel, userLabel, dateLabel);

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

    // -------------------- КНОПКИ --------------------

    @FXML
    protected void onAddOperationClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("add-operation-view.fxml")
            );
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Новая операция");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();

            onRefreshBalance();
            onRefreshOperations();

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия окна: " + e.getMessage());
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

    // -------------------- ЭКСПОРТ (dat) --------------------

    @FXML
    private void onExportOperationsClick() {
        if (allOperations.isEmpty()) {
            statusLabel.setText("Нет операций для экспорта");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт операций (dat)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Файл операций (*.dat)", "*.dat")
        );

        File file = chooser.showSaveDialog(balanceLabel.getScene().getWindow());
        if (file == null) return;

        List<OperationExportItem> exportList = allOperations.stream()
                .map(OperationExportItem::new)
                .collect(Collectors.toList());

        try (ObjectOutputStream oos =
                     new ObjectOutputStream(new FileOutputStream(file))) {

            oos.writeObject(exportList);
            statusLabel.setText("Экспортировано (dat) операций: " + exportList.size());

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

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт операций в CSV");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV файлы (*.csv)", "*.csv")
        );

        File file = chooser.showSaveDialog(balanceLabel.getScene().getWindow());
        if (file == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("type;amount;category;user;date\n");
        for (OperationRow o : toExport) {
            sb.append(o.type).append(";");
            sb.append(o.amount).append(";");
            sb.append(escapeCsv(o.category)).append(";");
            sb.append(escapeCsv(o.user)).append(";");
            sb.append(o.date).append("\n");
        }

        try (OutputStream os = new FileOutputStream(file);
             Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {

            // 👇 ДОБАВЛЯЕМ BOM
            writer.write('\uFEFF');

            writer.write(sb.toString());
            statusLabel.setText("Экспортировано в CSV: " + toExport.size());
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

    // -------------------- ИМПОРТ (dat) С ЗАПИСЬЮ НА СЕРВЕР --------------------
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
                     new ObjectInputStream(new java.io.FileInputStream(file))) {

            Object obj = ois.readObject();
            if (!(obj instanceof java.util.List<?> rawList)) {
                statusLabel.setText("Неверный формат файла");
                return;
            }

            java.util.List<OperationExportItem> imported = new java.util.ArrayList<>();
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

            // 1) Загружаем текущие категории семьи
            Map<String, Long> categoryMap;
            try {
                categoryMap = loadCategoryMap();
            } catch (IOException e) {
                e.printStackTrace();
                statusLabel.setText("Не удалось загрузить категории: " + e.getMessage());
                return;
            }

            int okCount = 0;
            int skipCount = 0;

            // 2) Для каждой импортированной операции создаём запись на сервере
            for (OperationExportItem it : imported) {
                String catName = it.getCategory();
                if (catName == null || catName.isBlank()) {
                    System.out.println("Пропущена операция без категории");
                    skipCount++;
                    continue;
                }

                // ищем id категории
                Long categoryId = categoryMap.get(catName);
                if (categoryId == null) {
                    // такой категории пока нет – пробуем создать
                    String respCat = conn.sendCommand("ADD_CATEGORY " + catName);
                    if (respCat != null && respCat.startsWith("OK CATEGORY_CREATED")) {
                        // формат: OK CATEGORY_CREATED id:name
                        String tail = respCat.substring("OK CATEGORY_CREATED".length()).trim(); // "id:name"
                        String[] idName = tail.split(":", 2);
                        if (idName.length == 2) {
                            try {
                                long newId = Long.parseLong(idName[0]);
                                categoryId = newId;
                                categoryMap.put(catName, newId);
                            } catch (NumberFormatException ex) {
                                System.out.println("Не удалось распарсить id категории из ответа: " + respCat);
                                skipCount++;
                                continue;
                            }
                        } else {
                            System.out.println("Неожиданный формат ответа ADD_CATEGORY: " + respCat);
                            skipCount++;
                            continue;
                        }
                    } else {
                        // не удалось создать категорию (нет прав, ошибка и т.п.)
                        System.out.println("Нельзя создать категорию '" + catName + "': " + respCat);
                        skipCount++;
                        continue;
                    }
                }

                // 3) В зависимости от типа создаём доход или расход
                String type = it.getType();
                double amount = it.getAmount();
                if (amount <= 0) {
                    System.out.println("Пропущена операция с некорректной суммой: " + amount);
                    skipCount++;
                    continue;
                }

                String cmd;
                if ("INCOME".equalsIgnoreCase(type)) {
                    cmd = "ADD_INCOME " + categoryId + " " + amount + " Импорт";
                } else if ("EXPENSE".equalsIgnoreCase(type)) {
                    cmd = "ADD_EXPENSE " + categoryId + " " + amount + " Импорт";
                } else {
                    System.out.println("Неизвестный тип операции: " + type);
                    skipCount++;
                    continue;
                }

                String respOp = conn.sendCommand(cmd);
                if (respOp != null && (respOp.startsWith("OK INCOME_ADDED") || respOp.startsWith("OK EXPENSE_ADDED"))) {
                    okCount++;
                } else {
                    System.out.println("Ошибка при создании операции: " + respOp);
                    skipCount++;
                }
            }

            // 4) После импорта обновляем данные с сервера
            onRefreshBalance();
            onRefreshOperations();

            statusLabel.setText("Импортировано в семью операций: " + okCount +
                    (skipCount > 0 ? (" (пропущено: " + skipCount + ")") : ""));

            // (опционально) всплывающее окно
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Импорт завершён");
            alert.setHeaderText("Успешно создано операций: " + okCount);
            alert.setContentText(
                    "Все операции записаны на сервер в вашу семью.\n" +
                            "Дата и пользователь берутся как при обычном добавлении (текущий пользователь, текущая дата)." +
                            (skipCount > 0 ? ("\nПропущено операций: " + skipCount + " (смотрите лог в консоли).") : "")
            );
            alert.showAndWait();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка импорта: " + e.getMessage());
        }
    }


    // Загружаем категории семьи с сервера и строим карту "Название -> id"
    private Map<String, Long> loadCategoryMap() throws IOException {
        Map<String, Long> result = new HashMap<>();

        String resp = ServerConnection.getInstance().sendCommand("LIST_CATEGORIES");
        if (resp == null) {
            throw new IOException("Нет ответа от сервера при LIST_CATEGORIES");
        }

        if (!resp.startsWith("OK CATEGORIES=")) {
            // если семей нет, сервер может вернуть OK CATEGORIES= или ошибку
            if (resp.startsWith("OK CATEGORIES=")) {
                return result;
            } else {
                throw new IOException("Ошибка LIST_CATEGORIES: " + resp);
            }
        }

        String payload = resp.substring("OK CATEGORIES=".length()).trim();
        if (payload.isEmpty()) {
            return result; // категорий ещё нет
        }

        // формат: id:name,id:name,...
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


}
