package com.hfad.encomiendas.api;

import android.content.Context;
import android.util.Log;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Solicitud;

import java.util.List;

/**
 * Gestor de sincronización automática de Solicitudes
 * Similar a AutoSyncManager pero para recolecciones/solicitudes
 */
public class SolicitudSyncManager {

    private static final String TAG = "SolicitudSyncManager";

    /**
     * Sincronizar solicitudes del servidor con Room
     * Inserta, actualiza y elimina según sea necesario
     *
     * @param context Contexto de la app
     * @param solicitudesAPI Lista de solicitudes del servidor
     */
    public static void syncSolicitudes(Context context, List<Solicitud> solicitudesAPI) {
        Log.d(TAG, "💾 Sincronizando " + solicitudesAPI.size() + " solicitudes en Room...");

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                int insertadas = 0;
                int actualizadas = 0;
                int eliminadas = 0;

                // 1️⃣ Insertar y actualizar solicitudes del servidor
                for (Solicitud solicitud : solicitudesAPI) {
                    try {
                        // Buscar si ya existe por ID
                        Solicitud existente = db.solicitudDao().findById(solicitud.id);

                        if (existente != null) {
                            // Actualizar si existe
                            db.solicitudDao().update(solicitud);
                            actualizadas++;
                            Log.d(TAG, "📝 Actualizada: Solicitud #" + solicitud.id + " (" + solicitud.guia + ")");
                        } else {
                            // Insertar si no existe
                            db.solicitudDao().insert(solicitud);
                            insertadas++;
                            Log.d(TAG, "➕ Insertada: Solicitud #" + solicitud.id + " (" + solicitud.guia + ")");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error procesando solicitud #" + solicitud.id + ": " + e.getMessage());
                    }
                }

                // 2️⃣ ELIMINAR solicitudes que YA NO EXISTEN en el servidor
                List<Solicitud> solicitudesLocales = db.solicitudDao().getAll();

                for (Solicitud localSolicitud : solicitudesLocales) {
                    // Verificar si esta solicitud local existe en la lista del servidor
                    boolean existeEnServidor = false;

                    for (Solicitud serverSolicitud : solicitudesAPI) {
                        if (localSolicitud.id == serverSolicitud.id) {
                            existeEnServidor = true;
                            break;
                        }
                    }

                    // Si NO existe en el servidor, eliminarla de Room
                    if (!existeEnServidor) {
                        db.solicitudDao().delete(localSolicitud);
                        eliminadas++;
                        Log.w(TAG, "🗑️ Eliminada (ya no existe en servidor): Solicitud #" + localSolicitud.id);
                    }
                }

                Log.d(TAG, "✅ Sincronización de solicitudes completada: " +
                        insertadas + " nuevas, " +
                        actualizadas + " actualizadas, " +
                        eliminadas + " eliminadas");

            } catch (Exception e) {
                Log.e(TAG, "❌ Error en sincronización de solicitudes: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Obtener la API de Solicitudes desde ApiClient
     */
    public static SolicitudApi getSolicitudApi() {
        return ApiClient.getClient().create(SolicitudApi.class);
    }
}
