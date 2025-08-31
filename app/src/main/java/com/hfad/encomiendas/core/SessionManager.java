package com.hfad.encomiendas.core;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private final SharedPreferences sp;

    public SessionManager(Context ctx) {
        sp = ctx.getSharedPreferences("session", Context.MODE_PRIVATE);
    }

    public void login(String email) {
        sp.edit().putBoolean("loggedIn", true)
                .putString("email", email)
                .apply();
    }

    public void logout() {
        sp.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return sp.getBoolean("loggedIn", false);
    }

    public String getEmail() {
        return sp.getString("email", null);
    }
}
