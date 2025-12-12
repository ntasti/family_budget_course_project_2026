package org.familybudget.familybudget;

//хранит данные о текущей сессии
public class SessionContext {

    private static final SessionContext INSTANCE = new SessionContext();

    private String login;
    private String role;
    private String userName;

    // Приватный конструктор запрещает создание объектов извне
    private SessionContext() {
    }

    public static SessionContext getInstance() {
        return INSTANCE;
    }

    public static void setUser(String login, String role, String userName) {
        INSTANCE.login = login;
        INSTANCE.role = role;
        INSTANCE.userName = userName;

    }

    public static void setUser(String login, String role) {
        INSTANCE.login = login;
        INSTANCE.role = role;
    }

    public static String getLogin() {
        return INSTANCE.login;
    }

    public static String getRole() {
        return INSTANCE.role;
    }

    public static String getUserName() {
        return INSTANCE.userName;
    }

    // очистка сессии при logout
    public static void clear() {
        INSTANCE.login = null;
        INSTANCE.role = null;

    }
}