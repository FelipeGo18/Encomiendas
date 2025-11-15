package com.hfad.encomiendas.api;

import android.content.Context;
import android.util.Log;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.User;

import java.util.List;

/**
 * Gestor de sincronización automática
 * Se ejecuta al iniciar la aplicación para sincronizar todos los datos
 */
public class AutoSyncManager {

    private static final String TAG = "AutoSyncManager";

    /**
     * Sincronizar automáticamente al iniciar la app
     * Descarga TODOS los usuarios de la API y los guarda en Room
     */
    public static void syncOnAppStart(Context context) {
        Log.d(TAG, "🔄 Iniciando sincronización automática al abrir la app...");

        new Thread(() -> {
            try {
                // Obtener la API
                UserApi api = ApiClient.getUserApi();

                // 📡 Descargar TODOS los usuarios de la API
                retrofit2.Response<List<User>> response = api.getAllUsers().execute();

                if (response.isSuccessful() && response.body() != null) {
                    List<User> usuariosAPI = response.body();
                    Log.d(TAG, "✅ Descargados " + usuariosAPI.size() + " usuarios de la API");

                    // 💾 Guardar en Room
                    AppDatabase db = AppDatabase.getInstance(context);

                    for (User user : usuariosAPI) {
                        try {
                            // Buscar si ya existe por email
                            User existente = db.userDao().findByEmail(user.email);

                            if (existente != null) {
                                // Actualizar si existe
                                user.id = existente.id; // Mantener ID local
                                db.userDao().update(user);
                                Log.d(TAG, "📝 Actualizado: " + user.email);
                            } else {
                                // Insertar si no existe
                                db.userDao().insert(user);
                                Log.d(TAG, "➕ Insertado: " + user.email);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error procesando usuario: " + e.getMessage());
                        }
                    }

                    Log.d(TAG, "💾 ✅ Sincronización completa - " + usuariosAPI.size() + " usuarios guardados");

                } else {
                    Log.e(TAG, "❌ Error en respuesta API: " + response.code());
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error en sincronización automática: " + e.getMessage());
                // No hacer nada - usar datos locales si hay error
            }
        }).start();
    }

    /**
     * Forzar sincronización inmediata (para uso manual)
     */
    public static void syncNow(Context context, SyncCallback callback) {
        Log.d(TAG, "🔄 Forzando sincronización manual...");

        new Thread(() -> {
            try {
                UserApi api = ApiClient.getUserApi();
                retrofit2.Response<List<User>> response = api.getAllUsers().execute();

                if (response.isSuccessful() && response.body() != null) {
                    List<User> usuariosAPI = response.body();
                    AppDatabase db = AppDatabase.getInstance(context);

                    int insertados = 0;
                    int actualizados = 0;

                    for (User user : usuariosAPI) {
                        User existente = db.userDao().findByEmail(user.email);
                        if (existente != null) {
                            user.id = existente.id;
                            db.userDao().update(user);
                            actualizados++;
                        } else {
                            db.userDao().insert(user);
                            insertados++;
                        }
                    }

                    String mensaje = "✅ Sincronizado: " + insertados + " nuevos, " + actualizados + " actualizados";
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
     * Se usa cuando ya tienes la lista de usuarios del servidor
     *
     * @param context Contexto de la app
     * @param usuariosAPI Lista de usuarios obtenidos de la API
     */
    public static void syncNowDirect(Context context, List<User> usuariosAPI) {
        Log.d(TAG, "💾 Sincronizando " + usuariosAPI.size() + " usuarios en Room...");

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                int insertados = 0;
                int actualizados = 0;
                int eliminados = 0;

                // 1️⃣ Insertar y actualizar usuarios del servidor
                for (User user : usuariosAPI) {
                    try {
                        // Buscar si ya existe por email
                        User existente = db.userDao().findByEmail(user.email);

                        if (existente != null) {
                            // Actualizar si existe
                            user.id = existente.id; // Mantener ID local
                            db.userDao().update(user);
                            actualizados++;
                            Log.d(TAG, "📝 Actualizado: " + user.email);
                        } else {
                            // Insertar si no existe
                            db.userDao().insert(user);
                            insertados++;
                            Log.d(TAG, "➕ Insertado: " + user.email);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error procesando usuario: " + e.getMessage());
                    }
                }

                // 2️⃣ ELIMINAR usuarios que YA NO EXISTEN en el servidor
                List<User> usuariosLocales = db.userDao().getAllUsersList();

                for (User localUser : usuariosLocales) {
                    // Verificar si este usuario local existe en la lista del servidor
                    boolean existeEnServidor = false;

                    for (User serverUser : usuariosAPI) {
                        if (localUser.email.equals(serverUser.email)) {
                            existeEnServidor = true;
                            break;
                        }
                    }

                    // Si NO existe en el servidor, eliminarlo de Room
                    if (!existeEnServidor) {
                        db.userDao().delete(localUser);
                        eliminados++;
                        Log.w(TAG, "🗑️ Eliminado (ya no existe en servidor): " + localUser.email);
                    }
                }

                Log.d(TAG, "✅ Sincronización completada: " +
                        insertados + " nuevos, " +
                        actualizados + " actualizados, " +
                        eliminados + " eliminados");

            } catch (Exception e) {
                Log.e(TAG, "❌ Error en sincronización directa: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Interface para callbacks de sincronización
     */
    public interface SyncCallback {
        void onSuccess(String message);
        void onError(String message);
    }
}
