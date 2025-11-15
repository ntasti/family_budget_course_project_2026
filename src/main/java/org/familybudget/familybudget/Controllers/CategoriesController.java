package org.familybudget.familybudget.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.familybudget.familybudget.ServerConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CategoriesController {

    // Модель одной строки списка категорий
    public static class CategoryRow {
        public long id;
        public String name;

        public CategoryRow(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            // по умолчанию ListView покажет name
            return name;
        }
    }

    @FXML
    private ListView<CategoryRow> categoriesList;

    @FXML
    private TextField newCategoryField;

    @FXML
    private TextField renameCategoryField;

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        // при выборе категории — подставляем её название в поле переименования
        categoriesList.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldV, newV) -> {
                    if (newV != null) {
                        renameCategoryField.setText(newV.name);
                    } else {
                        renameCategoryField.clear();
                    }
                });

        loadCategories();
    }

    // ================== ЗАГРУЗКА КАТЕГОРИЙ ==================
    private void loadCategories() {
        statusLabel.setText("");
        try {
            String resp = ServerConnection.getInstance()
                    .sendCommand("LIST_CATEGORIES");

            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (!resp.startsWith("OK CATEGORIES=")) {
                statusLabel.setText("Ошибка загрузки: " + resp);
                return;
            }

            String payload = resp.substring("OK CATEGORIES=".length()).trim();
            List<CategoryRow> items = new ArrayList<>();

            if (!payload.isEmpty()) {
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
                            items.add(new CategoryRow(id, name));
                        } catch (NumberFormatException ignored) {
                            System.out.println("Некорректная категория: " + line);
                        }
                    }
                }
            }

            categoriesList.setItems(FXCollections.observableArrayList(items));
            if (items.isEmpty()) {
                statusLabel.setText("Категорий пока нет");
            } else {
                statusLabel.setText("");
            }

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    // ================== ДОБАВЛЕНИЕ КАТЕГОРИИ ==================
    @FXML
    private void onAddCategoryClick() {
        statusLabel.setText("");

        String name = newCategoryField.getText();
        if (name == null || name.isBlank()) {
            statusLabel.setText("Введите название категории");
            return;
        }

        name = name.trim();
        if (name.length() > 50) {
            statusLabel.setText("Название слишком длинное");
            return;
        }

        try {
            String resp = ServerConnection.getInstance()
                    .sendCommand("ADD_CATEGORY " + name);

            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("OK CATEGORY_CREATED")) {
                newCategoryField.clear();
                loadCategories();
                statusLabel.setText("");
            } else if (resp.startsWith("ERROR CATEGORY_EXISTS")) {
                statusLabel.setText("Такая категория уже существует");
            } else if (resp.startsWith("ERROR NO_FAMILY")) {
                statusLabel.setText("У вас ещё нет семьи");
            } else if (resp.startsWith("ERROR ACCESS_DENIED")) {
                statusLabel.setText("Только админ может изменять категории");
            } else {
                statusLabel.setText("Ошибка: " + resp);
            }

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    // ================== ПЕРЕИМЕНОВАНИЕ КАТЕГОРИИ ==================
    @FXML
    private void onRenameCategoryClick() {
        statusLabel.setText("");

        CategoryRow selected = categoriesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Выберите категорию для переименования");
            return;
        }

        String newName = renameCategoryField.getText();
        if (newName == null || newName.isBlank()) {
            statusLabel.setText("Введите новое название");
            return;
        }

        newName = newName.trim();
        if (newName.length() > 50) {
            statusLabel.setText("Название слишком длинное");
            return;
        }

        try {
            String cmd = "RENAME_CATEGORY " + selected.id + " " + newName;
            String resp = ServerConnection.getInstance().sendCommand(cmd);

            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("OK CATEGORY_RENAMED")) {
                loadCategories();
                statusLabel.setText("");
            } else if (resp.startsWith("ERROR CATEGORY_EXISTS")) {
                statusLabel.setText("Категория с таким именем уже есть");
            } else if (resp.startsWith("ERROR NO_FAMILY")) {
                statusLabel.setText("У вас нет семьи");
            } else if (resp.startsWith("ERROR ACCESS_DENIED")) {
                statusLabel.setText("Только админ может изменять категории");
            } else {
                statusLabel.setText("Ошибка: " + resp);
            }

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    // ================== УДАЛЕНИЕ КАТЕГОРИИ ==================
    @FXML
    private void onDeleteCategoryClick() {
        statusLabel.setText("");

        CategoryRow selected = categoriesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Выберите категорию для удаления");
            return;
        }

        // при желании можно добавить Alert-подтверждение

        try {
            String cmd = "DELETE_CATEGORY " + selected.id;
            String resp = ServerConnection.getInstance().sendCommand(cmd);

            if (resp == null) {
                statusLabel.setText("Нет ответа от сервера");
                return;
            }

            if (resp.startsWith("OK CATEGORY_DELETED")) {
                loadCategories();
                statusLabel.setText("");
            } else if (resp.startsWith("ERROR CATEGORY_IN_USE")) {
                statusLabel.setText("Нельзя удалить: есть операции с этой категорией");
            } else if (resp.startsWith("ERROR NO_FAMILY")) {
                statusLabel.setText("У вас нет семьи");
            } else if (resp.startsWith("ERROR ACCESS_DENIED")) {
                statusLabel.setText("Только админ может изменять категории");
            } else {
                statusLabel.setText("Ошибка: " + resp);
            }

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Ошибка соединения: " + e.getMessage());
        }
    }

    // ================== ЗАКРЫТЬ ОКНО ==================
    @FXML
    private void onCloseClick() {
        Stage stage = (Stage) categoriesList.getScene().getWindow();
        stage.close();
    }
}
