package org.familybudget.familybudget;

import java.io.Serializable;

/**
 * DTO для сериализации операций в файл.
 */
public class OperationExportItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;      // INCOME / EXPENSE
    private double amount;
    private String category;
    private String user;
    private String date;      // YYYY-MM-DD

    public OperationExportItem(MainController.OperationRow row) {
        this.type = row.type;
        this.amount = row.amount;
        this.category = row.category;
        this.user = row.user;
        this.date = row.date;
    }

    public String getType()     { return type; }
    public double getAmount()   { return amount; }
    public String getCategory() { return category; }
    public String getUser()     { return user; }
    public String getDate()     { return date; }
}
