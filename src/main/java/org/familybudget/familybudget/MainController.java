package org.familybudget.familybudget;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
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

    // список операций
    @FXML
    private ListView<OperationRow> operationsList;
    //кнопка для админа по добавлению категорий
    @FXML
    private javafx.scene.control.Button manageCategoriesButton;


    // модель для одной строки в истории
    public static class OperationRow {
        public String type;      // INCOME или EXPENSE
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
        // пока жёстко забиваем название семьи


        loadFamilyInfo();      // выгрузка данных семьи
        onRefreshBalance();
        onRefreshOperations();

        userInfoLabel.setText("Пользователь: " + login + " (роль: " + role + ")");

        // кнопка категорий доступна только админу
        if (!"ADMIN".equalsIgnoreCase(role) && manageCategoriesButton != null) {
            manageCategoriesButton.setVisible(false);
            manageCategoriesButton.setManaged(false);
        }

        setupOperationsCellFactory();

        // при старте сразу грузим данные
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
            // здесь важно, чтобы на сервере была команда GET_OPERATIONS
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

            // формат строки от сервера:
            // id:type:categoryName:amount:userLogin:date
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

                rows.add(new OperationRow(type, amount, category, user, date));
            }

            // сортировка по дате НОВЫЕ ВВЕРХУ (дата в ISO, можно сортировать как строку)
            rows.sort(Comparator.comparing((OperationRow o) -> o.date).reversed());

            operationsList.setItems(FXCollections.observableArrayList(rows));
            statusLabel.setText("");

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    // оформление строк ListView
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
                        (income
                                ? "-fx-text-fill: #5BD75B;"
                                : "-fx-text-fill: #FF7070;")
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

    //данные семьи
    private void loadFamilyInfo() {
        try {
            String resp = ServerConnection.getInstance().sendCommand("GET_FAMILY_INFO");
            if (resp == null) {
                familyNameLabel.setText("Семья: (нет данных)");
                return;
            }

            if (!resp.startsWith("OK FAMILY ")) {
                familyNameLabel.setText("Семья: (ошибка)");
                System.out.println("GET_FAMILY_INFO error: " + resp);
                return;
            }

            // формат: OK FAMILY name=Моя новая семья code=FAM-B8878X
            // распарсим по пробелам
            String[] parts = resp.split("\\s+");
            String namePart = null;
            for (String p : parts) {
                if (p.startsWith("name=")) {
                    namePart = p.substring("name=".length());
                    break;
                }
            }

            if (namePart == null || namePart.isBlank()) {
                familyNameLabel.setText("Семья: (без имени)");
            } else {
                familyNameLabel.setText("Семья: " + namePart);
            }

        } catch (IOException e) {
            e.printStackTrace();
            familyNameLabel.setText("Семья: (ошибка соединения)");
        }
    }


    //для кнопки обновления баланса
    @FXML
    protected void onAddOperationClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("add-operation-view.fxml")
            );
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Новая операция");
            stage.initModality(Modality.APPLICATION_MODAL); // модальное окно
            stage.setScene(scene);
            stage.showAndWait();

            // после закрытия окна — обновим баланс и список
            onRefreshBalance();
            onRefreshOperations();

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия окна: " + e.getMessage());
        }
    }

    //кнопка для добавления категорий
    @FXML
    protected void onManageCategoriesClick() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    HelloApplication.class.getResource("categories-view.fxml")
            );
            javafx.scene.Scene scene = new javafx.scene.Scene(loader.load());
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Категории семьи");
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();

            // после закрытия окна категорий можно обновить операции,
            // вдруг добавили новые категории
            onRefreshOperations();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия окна категорий: " + e.getMessage());
        }
    }
}
