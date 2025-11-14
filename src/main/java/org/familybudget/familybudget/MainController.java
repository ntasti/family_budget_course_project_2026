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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        String role  = SessionContext.getRole();

        // инфо о пользователе
        userInfoLabel.setText("Пользователь: " + login + " (роль: " + role + ")");

        // показать/спрятать кнопку категорий в зависимости от роли
        if (manageCategoriesButton != null) {
            boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
            manageCategoriesButton.setVisible(isAdmin);
            manageCategoriesButton.setManaged(isAdmin);
        }

        loadFamilyInfo();          // имя семьи
        setupOperationsCellFactory();

        // сразу грузим данные
        onRefreshBalance();
        onRefreshOperations();
    }

    // ----- БАЛАНС -----
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

    // ----- ИСТОРИЯ ОПЕРАЦИЙ -----
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
            if (payload.isEmpty()) {
                operationsList.setItems(FXCollections.observableArrayList());
                statusLabel.setText("Операций пока нет");
                return;
            }

            List<OperationRow> rows = new ArrayList<>();

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

                String type     = parts[1];
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

                rows.add(new OperationRow(type, amount, category, user, date));
            }

            // новые сверху
            rows.sort(Comparator.comparing((OperationRow o) -> o.date).reversed());

            operationsList.setItems(FXCollections.observableArrayList(rows));
            statusLabel.setText("");

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    // ----- ListView: оформление строк -----
    private void setupOperationsCellFactory() {
        operationsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(OperationRow item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                boolean income = "INCOME".equalsIgnoreCase(item.type);
                String sign = income ? "+" : "-";
                String amountText = sign + String.format("%.0f BYN", item.amount);

                Label amountLabel = new Label(amountText);
                amountLabel.setPrefWidth(140);
                amountLabel.setAlignment(Pos.CENTER_LEFT);
                amountLabel.setStyle(
                        (income ? "-fx-text-fill: #5BD75B;" : "-fx-text-fill: #FF7070;")
                                + "-fx-padding: 0 10 0 10;"
                );

                Label categoryLabel = new Label(item.category);
                categoryLabel.setPrefWidth(220);
                categoryLabel.setAlignment(Pos.CENTER_LEFT);
                categoryLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-padding: 0 10 0 10;");

                Label userLabel = new Label(item.user);
                userLabel.setPrefWidth(180);
                userLabel.setAlignment(Pos.CENTER_LEFT);
                userLabel.setStyle("-fx-text-fill: #CCCCCC; -fx-padding: 0 10 0 10;");

                Label dateLabel = new Label(item.date);
                dateLabel.setPrefWidth(140);
                dateLabel.setAlignment(Pos.CENTER_LEFT);
                dateLabel.setStyle("-fx-text-fill: #CCCCCC; -fx-padding: 0 10 0 10;");

                HBox hbox = new HBox(0);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(amountLabel, categoryLabel, userLabel, dateLabel);

                setText(null);
                setGraphic(hbox);
            }
        });
    }

    // ----- ДАННЫЕ СЕМЬИ -----
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

            // старый формат: OK FAMILY name=... code=...
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

    // ----- КНОПКИ -----
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

    // ----- LOGOUT -----
    @FXML
    private void onLogoutClick() {
        // 1. очищаем сессию
        SessionContext.clear();

        // 2. закрываем соединение с сервером
        ServerConnection.disconnect();

        // 3. закрываем это окно
        Stage currentStage = (Stage) familyNameLabel.getScene().getWindow();
        currentStage.close();

        // 4. открываем окно логина
        try {
            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("hello-view.fxml")
            );
            Scene scene = new Scene(loader.load(),800,600);
            Stage stage = new Stage();

            stage.setTitle("Вход в систему");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            // если не получилось открыть логин, хотя бы покажем ошибку
            // (эта надпись уже не увидят, если окно закрыто, но на всякий случай)
            // statusLabel может быть уже недоступен, поэтому без него
        }
    }
}
