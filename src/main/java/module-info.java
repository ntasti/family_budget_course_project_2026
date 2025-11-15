module org.familybudget.familybudget {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires org.controlsfx.controls;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;

    opens org.familybudget.familybudget to javafx.fxml;
    exports org.familybudget.familybudget;
    exports org.familybudget.familybudget.Controllers;
    opens org.familybudget.familybudget.Controllers to javafx.fxml;
    exports org.familybudget.familybudget.DTO;
    opens org.familybudget.familybudget.DTO to javafx.fxml;
    exports org.familybudget.familybudget.Server;
    opens org.familybudget.familybudget.Server to javafx.fxml;
}

