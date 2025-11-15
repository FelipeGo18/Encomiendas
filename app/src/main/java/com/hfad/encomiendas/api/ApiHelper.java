package com.hfad.encomiendas.api;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.hfad.encomiendas.data.User;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Clase de ayuda para simplificar las llamadas a la API
 * Maneja errores comunes y callbacks
 */
public class ApiHelper {

    private static final String TAG = "ApiHelper";

    /**
     * Interfaz para callbacks simplificados
     */
    public interface ApiCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    /**
     * Login con email y contraseña
     */
    public static void login(Context context, String email, String passwordHash, ApiCallback<User> callback) {
        UserApi api = ApiClient.getUserApi();
        LoginRequest request = new LoginRequest(email, passwordHash);

        api.login(request).enqueue(new Callback<User>() {
            @Override
            public void onResponse(@NonNull Call<User> call, @NonNull Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else if (response.code() == 401) {
                    callback.onError("Credenciales incorrectas");
                } else if (response.code() == 404) {
                    callback.onError("Usuario no encontrado");
                } else {
                    callback.onError("Error del servidor: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<User> call, @NonNull Throwable t) {
                Log.e(TAG, "Error de conexión: " + t.getMessage());
                callback.onError("Sin conexión con el servidor");
            }
        });
    }

    /**
     * Registrar nuevo usuario
     */
    public static void register(Context context, User newUser, ApiCallback<User> callback) {
        UserApi api = ApiClient.getUserApi();

        api.createUser(newUser).enqueue(new Callback<User>() {
            @Override
            public void onResponse(@NonNull Call<User> call, @NonNull Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else if (response.code() == 409) {
                    callback.onError("El email ya está registrado");
                } else {
                    callback.onError("Error al registrar: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<User> call, @NonNull Throwable t) {
                Log.e(TAG, "Error de conexión: " + t.getMessage());
                callback.onError("Sin conexión con el servidor");
            }
        });
    }

    /**
     * Obtener todos los usuarios
     */
    public static void getAllUsers(Context context, ApiCallback<List<User>> callback) {
        UserApi api = ApiClient.getUserApi();

        api.getAllUsers().enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call, @NonNull Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Error al obtener usuarios: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<User>> call, @NonNull Throwable t) {
                Log.e(TAG, "Error de conexión: " + t.getMessage());
                callback.onError("Sin conexión con el servidor");
            }
        });
    }

    /**
     * Obtener usuario por ID
     */
    public static void getUserById(Context context, long userId, ApiCallback<User> callback) {
        UserApi api = ApiClient.getUserApi();

        api.getUserById(userId).enqueue(new Callback<User>() {
            @Override
            public void onResponse(@NonNull Call<User> call, @NonNull Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else if (response.code() == 404) {
                    callback.onError("Usuario no encontrado");
                } else {
                    callback.onError("Error: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<User> call, @NonNull Throwable t) {
                Log.e(TAG, "Error de conexión: " + t.getMessage());
                callback.onError("Sin conexión con el servidor");
            }
        });
    }

    /**
     * Actualizar usuario
     */
    public static void updateUser(Context context, long userId, User user, ApiCallback<User> callback) {
        UserApi api = ApiClient.getUserApi();

        api.updateUser(userId, user).enqueue(new Callback<User>() {
            @Override
            public void onResponse(@NonNull Call<User> call, @NonNull Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else if (response.code() == 404) {
                    callback.onError("Usuario no encontrado");
                } else {
                    callback.onError("Error al actualizar: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<User> call, @NonNull Throwable t) {
                Log.e(TAG, "Error de conexión: " + t.getMessage());
                callback.onError("Sin conexión con el servidor");
            }
        });
    }

    /**
     * Eliminar usuario
     */
    public static void deleteUser(Context context, long userId, ApiCallback<Void> callback) {
        UserApi api = ApiClient.getUserApi();

        api.deleteUser(userId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else if (response.code() == 404) {
                    callback.onError("Usuario no encontrado");
                } else {
                    callback.onError("Error al eliminar: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.e(TAG, "Error de conexión: " + t.getMessage());
                callback.onError("Sin conexión con el servidor");
            }
        });
    }

    /**
     * Obtener lista de recolectores
     */
    public static void getRecolectores(Context context, ApiCallback<List<User>> callback) {
        UserApi api = ApiClient.getUserApi();

        api.getRecolectores().enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call, @NonNull Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Error al obtener recolectores: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<User>> call, @NonNull Throwable t) {
                Log.e(TAG, "Error de conexión: " + t.getMessage());
                callback.onError("Sin conexión con el servidor");
            }
        });
    }
}

