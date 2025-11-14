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

import java.io.IOException;
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

        // инфо о пользователе
        userInfoLabel.setText("Пользователь: " + login + " (роль: " + role + ")");

        // показать кнопку категорий только админу
        if ("ADMIN".equalsIgnoreCase(role) && manageCategoriesButton != null) {
            manageCategoriesButton.setVisible(true);
            manageCategoriesButton.setManaged(true);
        } else if (manageCategoriesButton != null) {
            manageCategoriesButton.setVisible(false);
            manageCategoriesButton.setManaged(false);
        }

        // кнопки тулбара (белые -> серые при наведении)
        setupToolbarButton(addOperationButton);
        setupToolbarButton(manageCategoriesButton);




        loadFamilyInfo();             // имя семьи
        setupOperationsCellFactory(); // оформление таблицы операций
        setupFilters();               // фильтры

        // сразу грузим данные
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

    // Белые кнопки тулбара, при наведении становятся серыми
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
        // тип операции
        if (typeFilterCombo != null) {
            typeFilterCombo.setItems(FXCollections.observableArrayList(
                    "Все операции",
                    "Только доходы",
                    "Только расходы"
            ));
            typeFilterCombo.getSelectionModel().selectFirst();
            typeFilterCombo.valueProperty().addListener((obs, o, n) -> applyFilters());
        }

        // категория
        if (categoryFilterCombo != null) {
            categoryFilterCombo.setItems(FXCollections.observableArrayList("Все категории"));
            categoryFilterCombo.getSelectionModel().selectFirst();
            categoryFilterCombo.valueProperty().addListener((obs, o, n) -> applyFilters());
        }

        // пользователь
        if (userFilterCombo != null) {
            userFilterCombo.setItems(FXCollections.observableArrayList("Все пользователи"));
            userFilterCombo.getSelectionModel().selectFirst();
            userFilterCombo.valueProperty().addListener((obs, o, n) -> applyFilters());
        }

        // диапазон дат
        if (fromDatePicker != null) {
            fromDatePicker.valueProperty().addListener((obs, o, n) -> applyFilters());
        }
        if (toDatePicker != null) {
            toDatePicker.valueProperty().addListener((obs, o, n) -> applyFilters());
        }
    }

    // применяем фильтры к allOperations
    private void applyFilters() {
        List<OperationRow> filtered = new ArrayList<>(allOperations);

        // --- тип операции ---
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

        // --- категория ---
        if (categoryFilterCombo != null) {
            String catFilter = categoryFilterCombo.getValue();
            if (catFilter != null && !"Все категории".equals(catFilter)) {
                filtered = filtered.stream()
                        .filter(o -> catFilter.equals(o.category))
                        .collect(Collectors.toList());
            }
        }

        // --- пользователь ---
        if (userFilterCombo != null) {
            String userFilter = userFilterCombo.getValue();
            if (userFilter != null && !"Все пользователи".equals(userFilter)) {
                filtered = filtered.stream()
                        .filter(o -> userFilter.equals(o.user))
                        .collect(Collectors.toList());
            }
        }

        // --- диапазон дат ---
        LocalDate from = (fromDatePicker != null) ? fromDatePicker.getValue() : null;
        LocalDate to = (toDatePicker != null) ? toDatePicker.getValue() : null;

        if (from != null || to != null) {
            filtered = filtered.stream()
                    .filter(o -> {
                        try {
                            LocalDate d = LocalDate.parse(o.date); // формат YYYY-MM-DD
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
                // формат: id:type:categoryName:amount:userLogin:date
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

                // новые сверху
                allOperations.sort(Comparator.comparing((OperationRow o) -> o.date).reversed());
            }

            statusLabel.setText(allOperations.isEmpty() ? "Операций пока нет" : "");

            // обновляем значения фильтров по категориям и пользователям
            updateCategoryFilterItems();
            updateUserFilterItems();

            // применяем фильтры к новому списку
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
                .collect(Collectors.toCollection(TreeSet::new)); // сортируем

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

    // -------------------- ОФОРМЛЕНИЕ ТАБЛИЦЫ --------------------

    private void setupOperationsCellFactory() {
        // убираем синий focus-бордер
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
                    setStyle(""); // сбрасываем стили
                    return;
                }

                boolean income = "INCOME".equalsIgnoreCase(item.type);
                String sign = income ? "+" : "-";
                String amountText = sign + String.format("%.0f BYN", item.amount);

                // Сумма
                Label amountLabel = new Label(amountText);
                amountLabel.setPrefWidth(150);
                amountLabel.setAlignment(Pos.CENTER_LEFT);
                amountLabel.setStyle(
                        (income ? "-fx-text-fill: #2E7D32;" : "-fx-text-fill: #C62828;") +
                                "-fx-padding: 6 8 6 8;" +
                                "-fx-font-size: 14;" +
                                "-fx-border-color: #E0E0E0; -fx-border-width: 0 1 0 0;"
                );

                // Категория
                Label categoryLabel = new Label(item.category);
                categoryLabel.setPrefWidth(250);
                categoryLabel.setAlignment(Pos.CENTER_LEFT);
                categoryLabel.setStyle(
                        "-fx-text-fill: #424242;" +
                                "-fx-padding: 6 8 6 8;" +
                                "-fx-font-size: 13;" +
                                "-fx-border-color: #E0E0E0; -fx-border-width: 0 1 0 0;"
                );

                // Пользователь
                Label userLabel = new Label(item.user);
                userLabel.setPrefWidth(200);
                userLabel.setAlignment(Pos.CENTER_LEFT);
                userLabel.setStyle(
                        "-fx-text-fill: #757575;" +
                                "-fx-padding: 6 8 6 8;" +
                                "-fx-font-size: 13;" +
                                "-fx-border-color: #E0E0E0; -fx-border-width: 0 1 0 0;"
                );

                // Дата
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

                // фон строк (лёгкий зебра-эффект)
                String bg = (getIndex() % 2 == 0) ? "#FFFFFF" : "#F9F9F9";
                row.setStyle("-fx-background-color: " + bg + ";");

                row.getChildren().addAll(amountLabel, categoryLabel, userLabel, dateLabel);

                setText(null);
                setGraphic(row);

                // горизонтальная линия под строкой
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
        // чистим сессию
        SessionContext.clear();
        // закрываем текущее окно
        Stage current = (Stage) balanceLabel.getScene().getWindow();
        current.close();

        // снова открываем окно логина
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
}
