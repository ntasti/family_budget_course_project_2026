package org.familybudget.familybudget.DTO;

import org.familybudget.familybudget.Controllers.MainController;

import java.io.Serializable;


//класс для сериализации операций в файл.
public class OperationExportItem implements Serializable {

    private static final long serialVersionUID = 2L;

    private String type;      // INCOME / EXPENSE
    private double amount;
    private String category;
    private String user;
    private String date;      // YYYY-MM-DD
    private String time;      // HH:mm или пусто

    private long accountId;
    private String accountName;

    public OperationExportItem(MainController.OperationRow row, long accountId, String accountName) {
        this.type = row.type;
        this.amount = row.amount;
        this.category = row.category;
        this.user = row.user;
        this.date = row.date;
        this.time = row.time;
        this.accountId = accountId;
        this.accountName = accountName;
    }

    // пустой конструктор для сериализации/десериализации
    public OperationExportItem() {
    }

    public String getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public String getUser() {
        return user;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }
}
