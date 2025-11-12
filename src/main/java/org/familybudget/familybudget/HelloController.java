package org.familybudget.familybudget;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class HelloController {
    @FXML
    private Button helloText;

    @FXML
    private Label welcomeText;

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
        welcomeText.getStyleClass().addAll("btn", "btn-success");
        helloText.getStyleClass().addAll("btn", "btn-warning");
    }

    @FXML
    protected void initialize(){
        helloText.setText("Hello");
        helloText.getStyleClass().addAll("btn", "btn-success");

    };


}