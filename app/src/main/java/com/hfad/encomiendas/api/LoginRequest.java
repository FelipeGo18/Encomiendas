package com.hfad.encomiendas.api;

/**
 * Clase para el request de login
 * Se envía al endpoint POST /api/users/login
 */
public class LoginRequest {
    public String email;
    public String passwordHash;

    public LoginRequest(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }
}

