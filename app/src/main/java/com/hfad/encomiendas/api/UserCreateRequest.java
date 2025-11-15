package com.hfad.encomiendas.api;

/**
 * Modelo para crear usuarios vía API con contraseña en texto plano
 * La API se encargará de hashear la contraseña
 */
public class UserCreateRequest {
    public String email;
    public String password;      // ⬅️ Contraseña en texto plano
    public String rol;
    public String telefono;

    // Constructor vacío
    public UserCreateRequest() {}

    // Constructor con parámetros
    public UserCreateRequest(String email, String password, String rol, String telefono) {
        this.email = email;
        this.password = password;
        this.rol = rol;
        this.telefono = telefono;
    }
}

