package org.familybudget.familybudget.Controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.familybudget.familybudget.HelloApplication;
import org.familybudget.familybudget.Server.ServerConnection;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;

//список план по категориям
//category-plan-list-view.fxml
public class CategoryPlanListController {

    @FXML
    private TableView<CategoryPlanItem> plansTable;

    @FXML
    private TableColumn<CategoryPlanItem, String> colCategory;
    @FXML
    private TableColumn<CategoryPlanItem, String> colFrom;
    @FXML
    private TableColumn<CategoryPlanItem, String> colTo;
    @FXML
    private TableColumn<CategoryPlanItem, String> colPlanned;
    @FXML
    private TableColumn<CategoryPlanItem, String> colActual;
    @FXML
    private TableColumn<CategoryPlanItem, String> colPercent;

    @FXML
    private Label statusLabel;

    @FXML
    private Button onAddClick;

    @FXML
    private void initialize() {
        colCategory.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getCategoryName()));
        colFrom.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getFrom().toString()));
        colTo.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getTo().toString()));
        colPlanned.setCellValueFactory(data ->
                new SimpleStringProperty(String.format(Locale.US, "%.2f", data.getValue().getPlannedAmount())));
        colActual.setCellValueFactory(data ->
                new SimpleStringProperty(String.format(Locale.US, "%.2f", data.getValue().getActualAmount())));
        colPercent.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getPercentText()));

        loadPlans();
    }

    //загрузка планов с сервера
    private void loadPlans() {
        try {
            String resp = ServerConnection.getInstance()
                    .sendCommand("GET_CATEGORY_PLANS");

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
                plansTable.setItems(FXCollections.observableArrayList());
                statusLabel.setText("Планы ещё не заданы");
                return;
            }

            var items = FXCollections.<CategoryPlanItem>observableArrayList();

            String[] rows = payload.split(",");
            for (String row : rows) {
                row = row.trim();
                if (row.isEmpty()) continue;

                String[] p = row.split(":", 7);
                if (p.length < 7) continue;

                long id = Long.parseLong(p[0]);
                long categoryId = Long.parseLong(p[1]);
                String categoryName = p[2];
                LocalDate from = LocalDate.parse(p[3]);
                LocalDate to = LocalDate.parse(p[4]);
                double planned = Double.parseDouble(p[5]);
                double actual = Double.parseDouble(p[6]);

                items.add(new CategoryPlanItem(id, categoryId, categoryName, from, to, planned, actual));
            }

            plansTable.setItems(items);
            statusLabel.setText("");

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка загрузки: " + e.getMessage());
        }
    }

    //обновить
    @FXML
    private void onRefreshClick() {
        loadPlans();
    }

    //добавить
    @FXML
    private void onAddClick() {
        openEditDialog(null);
    }

    //редактировать
    @FXML
    private void onEditClick() {
        CategoryPlanItem item = plansTable.getSelectionModel().getSelectedItem();
        if (item == null) {
            statusLabel.setText("Выберите план для редактирования");
            return;
        }
        openEditDialog(item);
    }

    //удалить
    @FXML
    private void onDeleteClick() {
        CategoryPlanItem item = plansTable.getSelectionModel().getSelectedItem();
        if (item == null) {
            statusLabel.setText("Выберите план для удаления");
            return;
        }

        try {
            String resp = ServerConnection.getInstance()
                    .sendCommand("DELETE_CATEGORY_PLAN " + item.getId());

            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("OK PLAN_DELETED")) {
                statusLabel.setText("План удалён");
                loadPlans();
            } else {
                statusLabel.setText("Ошибка удаления: " + resp);
            }
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    //закрыть окно
    @FXML
    private void onCloseClick() {
        Stage stage = (Stage) plansTable.getScene().getWindow();
        stage.close();
    }

    //редактирование
    private void openEditDialog(CategoryPlanItem item) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/familybudget/familybudget/category-plan-view.fxml"));
            Parent root = loader.load();

            CategoryPlanController editController = loader.getController();
            if (item != null) {
                editController.setInitialData(item.getId(), item.getCategoryId(), item.getCategoryName(), item.getFrom(), item.getTo(), item.getPlannedAmount());
            }

            Stage stage = new Stage();
            stage.setTitle(item == null ? "Новый план по категории" : "Редактирование плана");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            // после закрытия диалога обновляем таблицу
            loadPlans();

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия окна: " + e.getMessage());
        }
    }

    @FXML
    protected void onOpenCategoryPlanAddClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("category-plan-view.fxml")
            );
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("добавление записи");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка открытия окна категорий: " + e.getMessage());
        }
    }

    //модель для списка
    public static class CategoryPlanItem {
        private final long id;
        private final long categoryId;
        private final String categoryName;
        private final LocalDate from;
        private final LocalDate to;
        private final double plannedAmount;
        private final double actualAmount;

        public CategoryPlanItem(long id,
                                long categoryId,
                                String categoryName,
                                LocalDate from,
                                LocalDate to,
                                double plannedAmount,
                                double actualAmount) {
            this.id = id;
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.from = from;
            this.to = to;
            this.plannedAmount = plannedAmount;
            this.actualAmount = actualAmount;
        }

        public long getId() {
            return id;
        }

        public long getCategoryId() {
            return categoryId;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public LocalDate getFrom() {
            return from;
        }

        public LocalDate getTo() {
            return to;
        }

        public double getPlannedAmount() {
            return plannedAmount;
        }

        public double getActualAmount() {
            return actualAmount;
        }

        public String getPercentText() {
            if (plannedAmount <= 0.0) return "";
            double percent = (actualAmount / plannedAmount) * 100.0;
            return String.format(Locale.US, "%.0f%%", percent);
        }
    }
}
