package com.hfad.encomiendas.core;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREFS = "encomiendas.session";
    private static final String K_EMAIL = "email";
    private static final String K_ROLE  = "role";

    private final SharedPreferences sp;

    public SessionManager(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void login(String email, String role) {
        sp.edit().putString(K_EMAIL, email)
                .putString(K_ROLE, role == null ? "" : role)
                .apply();
    }

    public void setRole(String role) {
        sp.edit().putString(K_ROLE, role == null ? "" : role).apply();
    }

    public String getEmail() {
        return sp.getString(K_EMAIL, "");
    }

    public String getRole() {
        return sp.getString(K_ROLE, "");
    }

    public boolean isLoggedIn() {
        return getEmail() != null && !getEmail().isEmpty();
    }

    public void logout() {
        sp.edit().clear().apply();
    }
}
