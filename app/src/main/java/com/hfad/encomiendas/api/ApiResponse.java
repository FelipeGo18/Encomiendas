package com.hfad.encomiendas.api;

/**
 * Clase genérica para manejar respuestas de la API
 * Útil para endpoints que retornan objetos con status y mensaje
 */
public class ApiResponse<T> {
    public boolean success;
    public String message;
    public T data;

    public ApiResponse() {
    }

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
}

