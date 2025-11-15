package com.hfad.encomiendas.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.User;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Helper para sincronizar datos entre Room (local) y la API (GlassFish)
 */
public class SyncHelper {

    private static final String TAG = "SyncHelper";

    /**
     * Interface para callbacks de sincronización
     */
    public interface SyncCallback {
        void onSuccess(String message);
        void onError(String message);
        void onProgress(int usuariosEnviados, int totalUsuarios);
    }

    /**
     * Sincronizar todos los usuarios de Room hacia la API
     * Esto permite que los datos creados localmente se vean en Postman
     */
    public static void syncRoomToApi(Context context, SyncCallback callback) {
        new Thread(() -> {
            // Handler para ejecutar callbacks en el hilo principal (UI thread)
            Handler mainHandler = new Handler(Looper.getMainLooper());

            try {
                // Obtener todos los usuarios de Room
                AppDatabase db = AppDatabase.getInstance(context);
                List<User> usuarios = db.userDao().listAll();

                if (usuarios == null || usuarios.isEmpty()) {
                    mainHandler.post(() -> callback.onError("No hay usuarios en Room para sincronizar"));
                    return;
                }

                Log.d(TAG, "📤 Enviando " + usuarios.size() + " usuarios a la API...");

                // Contador de éxitos
                final int[] enviados = {0};
                final int total = usuarios.size();

                // Enviar cada usuario a la API
                for (int i = 0; i < usuarios.size(); i++) {
                    User user = usuarios.get(i);

                    try {
                        // Usar la API para crear/actualizar usuario
                        UserApi api = ApiClient.getUserApi();

                        // Intentar crear el usuario en la API
                        Call<User> call = api.createUser(user);

                        // Ejecutar de forma síncrona
                        Response<User> response = call.execute();

                        if (response.isSuccessful()) {
                            enviados[0]++;
                            Log.d(TAG, "✅ Usuario enviado: " + user.email + " (" + enviados[0] + "/" + total + ")");

                            // Ejecutar callback de progreso en el hilo principal
                            final int currentCount = enviados[0];
                            mainHandler.post(() -> callback.onProgress(currentCount, total));

                        } else if (response.code() == 409) {
                            // Usuario ya existe - intentar actualizar
                            Call<User> updateCall = api.updateUser(user.id, user);
                            Response<User> updateResponse = updateCall.execute();

                            if (updateResponse.isSuccessful()) {
                                enviados[0]++;
                                Log.d(TAG, "✅ Usuario actualizado: " + user.email);

                                // Ejecutar callback de progreso en el hilo principal
                                final int currentCount = enviados[0];
                                mainHandler.post(() -> callback.onProgress(currentCount, total));
                            } else {
                                Log.e(TAG, "❌ Error actualizando: " + user.email);
                            }
                        } else {
                            Log.e(TAG, "❌ Error creando: " + user.email + " - Código: " + response.code());
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "❌ Excepción enviando usuario: " + e.getMessage());
                    }
                }

                // Resultado final - ejecutar en el hilo principal
                if (enviados[0] == total) {
                    final String successMessage = "✅ " + enviados[0] + " usuarios sincronizados exitosamente";
                    mainHandler.post(() -> callback.onSuccess(successMessage));
                } else if (enviados[0] > 0) {
                    final String partialMessage = "⚠️ " + enviados[0] + "/" + total + " usuarios sincronizados";
                    mainHandler.post(() -> callback.onSuccess(partialMessage));
                } else {
                    mainHandler.post(() -> callback.onError("❌ No se pudo sincronizar ningún usuario"));
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error general de sincronización: " + e.getMessage());
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Sincronizar usuarios de la API hacia Room (descargar)
     */
    public static void syncApiToRoom(Context context, SyncCallback callback) {
        UserApi api = ApiClient.getUserApi();

        api.getAllUsers().enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call, @NonNull Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<User> usuariosAPI = response.body();

                    // Guardar en Room en segundo plano
                    new Thread(() -> {
                        try {
                            AppDatabase db = AppDatabase.getInstance(context);

                            for (User user : usuariosAPI) {
                                User existente = db.userDao().byId(user.id);
                                if (existente != null) {
                                    db.userDao().update(user);
                                } else {
                                    db.userDao().insert(user);
                                }
                            }

                            callback.onSuccess("✅ " + usuariosAPI.size() + " usuarios descargados de la API");

                        } catch (Exception e) {
                            callback.onError("Error guardando en Room: " + e.getMessage());
                        }
                    }).start();

                } else {
                    callback.onError("Error en respuesta API: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<User>> call, @NonNull Throwable t) {
                callback.onError("Error de conexión: " + t.getMessage());
            }
        });
    }

    /**
     * Sincronización bidireccional completa
     */
    public static void syncBidirectional(Context context, SyncCallback callback) {
        // Primero subir datos locales
        syncRoomToApi(context, new SyncCallback() {
            @Override
            public void onSuccess(String message) {
                // Luego descargar datos del servidor
                syncApiToRoom(context, new SyncCallback() {
                    @Override
                    public void onSuccess(String message2) {
                        callback.onSuccess("Sincronización completa: " + message + " | " + message2);
                    }

                    @Override
                    public void onError(String message2) {
                        callback.onSuccess(message + " | Error descargando: " + message2);
                    }

                    @Override
                    public void onProgress(int enviados, int total) {
                        // No usado aquí
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }

            @Override
            public void onProgress(int enviados, int total) {
                callback.onProgress(enviados, total);
            }
        });
    }
}
