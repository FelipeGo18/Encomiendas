package com.hfad.encomiendas.api;

import android.content.Context;
import android.util.Log;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Recolector;

import java.util.List;

/**
 * Gestor de sincronización de Recolectores desde la API
 * Similar a AutoSyncManager pero para recolectores
 */
public class RecolectorSyncManager {

    private static final String TAG = "RecolectorSyncManager";

    /**
     * Sincronizar recolectores al iniciar la app
     * Descarga TODOS los recolectores de la API y los guarda en Room
     */
    public static void syncOnAppStart(Context context) {
        Log.d(TAG, "🔄 Sincronizando recolectores desde API...");

        new Thread(() -> {
            try {
                // Obtener la API
                RecolectorApi api = ApiClient.getRecolectorApi();

                // 📡 Descargar TODOS los recolectores de la API
                retrofit2.Response<List<Recolector>> response = api.getAllRecolectores().execute();

                if (response.isSuccessful() && response.body() != null) {
                    List<Recolector> recolectoresAPI = response.body();
                    Log.d(TAG, "✅ Descargados " + recolectoresAPI.size() + " recolectores de la API");

                    // 💾 Guardar en Room
                    AppDatabase db = AppDatabase.getInstance(context);

                    for (Recolector recolector : recolectoresAPI) {
                        try {
                            // Buscar si ya existe por userEmail
                            Recolector existente = db.recolectorDao().findByUserEmail(recolector.userEmail);

                            if (existente != null) {
                                // Actualizar si existe
                                recolector.id = existente.id; // Mantener ID local
                                db.recolectorDao().update(recolector);
                                Log.d(TAG, "📝 Actualizado: " + recolector.nombre);
                            } else {
                                // Insertar si no existe
                                db.recolectorDao().insert(recolector);
                                Log.d(TAG, "➕ Insertado: " + recolector.nombre);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error procesando recolector: " + e.getMessage());
                        }
                    }

                    Log.d(TAG, "💾 ✅ Sincronización completa - " + recolectoresAPI.size() + " recolectores guardados");

                } else {
                    Log.e(TAG, "❌ Error en respuesta API: " + response.code());
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error en sincronización de recolectores: " + e.getMessage());
                // No hacer nada - usar datos locales si hay error
            }
        }).start();
    }

    /**
     * Forzar sincronización inmediata (para uso manual)
     */
    public static void syncNow(Context context, SyncCallback callback) {
        Log.d(TAG, "🔄 Forzando sincronización manual de recolectores...");

        new Thread(() -> {
            try {
                RecolectorApi api = ApiClient.getRecolectorApi();
                retrofit2.Response<List<Recolector>> response = api.getAllRecolectores().execute();

                if (response.isSuccessful() && response.body() != null) {
                    List<Recolector> recolectoresAPI = response.body();
                    AppDatabase db = AppDatabase.getInstance(context);

                    int insertados = 0;
                    int actualizados = 0;

                    for (Recolector recolector : recolectoresAPI) {
                        Recolector existente = db.recolectorDao().findByUserEmail(recolector.userEmail);
                        if (existente != null) {
                            recolector.id = existente.id;
                            db.recolectorDao().update(recolector);
                            actualizados++;
                        } else {
                            db.recolectorDao().insert(recolector);
                            insertados++;
                        }
                    }

                    String mensaje = "✅ Recolectores sincronizados: " + insertados + " nuevos, " + actualizados + " actualizados";
                    Log.d(TAG, mensaje);

                    if (callback != null) {
                        callback.onSuccess(mensaje);
                    }

                } else {
                    String error = "Error API: " + response.code();
                    Log.e(TAG, error);
                    if (callback != null) {
                        callback.onError(error);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error: " + e.getMessage());
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Sincronizar datos ya descargados directamente en Room
     */
    public static void syncNowDirect(Context context, List<Recolector> recolectoresAPI) {
        Log.d(TAG, "💾 Sincronizando " + recolectoresAPI.size() + " recolectores en Room...");

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                int insertados = 0;
                int actualizados = 0;

                for (Recolector recolector : recolectoresAPI) {
                    try {
                        Recolector existente = db.recolectorDao().findByUserEmail(recolector.userEmail);

                        if (existente != null) {
                            recolector.id = existente.id;
                            db.recolectorDao().update(recolector);
                            actualizados++;
                        } else {
                            db.recolectorDao().insert(recolector);
                            insertados++;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error procesando recolector: " + e.getMessage());
                    }
                }

                Log.d(TAG, "💾 ✅ " + insertados + " insertados, " + actualizados + " actualizados");

            } catch (Exception e) {
                Log.e(TAG, "❌ Error en sincronización directa: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Callback para sincronización manual
     */
    public interface SyncCallback {
        void onSuccess(String message);
        void onError(String error);
    }
}
