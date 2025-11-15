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
 * Clase de utilidad para probar rápidamente la conexión con la API
 * Úsala para verificar que todo funcione antes de integrar en tus fragments
 */
public class ApiTester {

    private static final String TAG = "ApiTester";

    /**
     * Prueba la conexión con el servidor
     * Llama a GET /api/users y muestra el resultado en Logcat
     * <p>
     * Uso:
     * ApiTester.testConnection(this);
     */
    public static void testConnection(Context context) {
        Log.d(TAG, "========== PROBANDO CONEXIÓN CON API ==========");

        UserApi api = ApiClient.getUserApi();

        api.getAllUsers().enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call, @NonNull Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<User> users = response.body();
                    Log.d(TAG, "✅ CONEXIÓN EXITOSA!");
                    Log.d(TAG, "Usuarios obtenidos: " + users.size());

                    for (User user : users) {
                        Log.d(TAG, "  - ID: " + user.id + " | Email: " + user.email + " | Rol: " + user.rol);
                    }

                    if (context != null) {
                        Toast.makeText(context, "✅ API conectada! " + users.size() + " usuarios", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.e(TAG, "❌ Respuesta del servidor con código: " + response.code());
                    Log.e(TAG, "Mensaje: " + response.message());

                    if (context != null) {
                        Toast.makeText(context, "❌ Error: " + response.code(), Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<User>> call, @NonNull Throwable t) {
                Log.e(TAG, "❌ ERROR DE CONEXIÓN!");
                Log.e(TAG, "Mensaje: " + t.getMessage());
                t.printStackTrace();

                if (context != null) {
                    Toast.makeText(context,
                            "❌ No se puede conectar al servidor\n" + t.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}