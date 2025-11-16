package org.familybudget.familybudget;

public class SessionContext {

    private static final SessionContext INSTANCE = new SessionContext();

    private String login;
    private String role;

    private SessionContext() {}

    public static SessionContext getInstance() {
        return INSTANCE;
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

    // очистка сессии при logout
    public static void clear() {
        INSTANCE.login = null;
        INSTANCE.role  = null;
    }
}