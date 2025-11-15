package com.hfad.encomiendas.core;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import com.hfad.encomiendas.api.ApiClient;
import com.hfad.encomiendas.api.AutoSyncManager;
import com.hfad.encomiendas.api.SolicitudApi;
import com.hfad.encomiendas.api.SolicitudSyncManager;
import com.hfad.encomiendas.api.UserApi;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.User;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ✅ MEJORADO: Servicio que sincroniza datos con el servidor periódicamente
 *
 * Cambios importantes:
 * - Sincroniza cada 10 segundos (configurable)
 * - Usa ScheduledExecutorService (más confiable que Handler)
 * - Sincronización proactiva (no solo cuando detecta cambios)
 * - Thread pool separado (no bloquea el main thread)
 */
public class ServerMonitoringService extends Service {

    private static final String TAG = "ServerMonitoring";
    private static final String PREFS_NAME = "server_monitoring";
    private static final String KEY_LAST_SYNC = "last_sync_time";

    // ✅ INTERVALO DE SINCRONIZACIÓN: 10 segundos (para ver cambios inmediatamente)
    // Cambio rápido: cambiar este valor si lo necesitas
    // Valores recomendados:
    // - 5 segundos = muy frecuente (puede consumir batería)
    // - 10 segundos = recomendado (buen balance)
    // - 20 segundos = moderado
    // - 30 segundos = menos frecuente
    private static final long SYNC_INTERVAL_SECONDS = 10;

    private ScheduledExecutorService executorService;
    private SharedPreferences prefs;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚀 Servicio de sincronización iniciado");
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Crear thread pool para ejecutar sincronización en background
        executorService = Executors.newScheduledThreadPool(1);

        startSyncScheduler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "📡 Comando recibido - Sincronizador en ejecución");

        // Si el servicio fue matado, reiniciarlo
        if (!isRunning) {
            startSyncScheduler();
        }

        return START_STICKY; // Reintentar si el SO mata el servicio
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "⛔ Servicio de sincronización detenido");
        isRunning = false;

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    /**
     * Iniciar el planificador de sincronización
     * Se ejecuta cada SYNC_INTERVAL_SECONDS
     */
    private void startSyncScheduler() {
        if (isRunning) {
            Log.d(TAG, "⚠️ Sincronizador ya está en ejecución");
            return;
        }

        isRunning = true;
        Log.d(TAG, "✅ Iniciando sincronizador con intervalo de " + SYNC_INTERVAL_SECONDS + " segundos");

        // Ejecutar sincronización inmediatamente
        executorService.execute(() -> {
            Log.d(TAG, "🔄 Sincronización inicial...");
            performSync();
        });

        // Luego repetir cada SYNC_INTERVAL_SECONDS
        executorService.scheduleAtFixedRate(
                () -> {
                    Log.d(TAG, "🔄 Sincronización periódica...");
                    performSync();
                },
                SYNC_INTERVAL_SECONDS,      // Delay inicial
                SYNC_INTERVAL_SECONDS,      // Intervalo de repetición
                TimeUnit.SECONDS
        );
    }

    /**
     * Realizar sincronización de datos
     * Se ejecuta cada 10 segundos
     */
    private void performSync() {
        try {
            // 1️⃣ SINCRONIZAR USUARIOS
            UserApi userApi = ApiClient.getUserApi();
            retrofit2.Response<List<User>> userResponse = userApi.getAllUsers().execute();

            if (userResponse.isSuccessful() && userResponse.body() != null) {
                List<User> usuarios = userResponse.body();
                Log.d(TAG, "✅ Descargados " + usuarios.size() + " usuarios de la API");

                // Guardar usuarios en Room
                AutoSyncManager.syncNowDirect(getApplicationContext(), usuarios);
            } else {
                Log.w(TAG, "⚠️ Error en respuesta de usuarios: Código " + userResponse.code());
            }

            // 2️⃣ SINCRONIZAR SOLICITUDES
            SolicitudApi solicitudApi = SolicitudSyncManager.getSolicitudApi();
            retrofit2.Response<List<Solicitud>> solicitudResponse = solicitudApi.getAllSolicitudes().execute();

            if (solicitudResponse.isSuccessful() && solicitudResponse.body() != null) {
                List<Solicitud> solicitudes = solicitudResponse.body();
                Log.d(TAG, "✅ Descargadas " + solicitudes.size() + " solicitudes de la API");

                // Guardar solicitudes en Room
                SolicitudSyncManager.syncSolicitudes(getApplicationContext(), solicitudes);
            } else {
                Log.w(TAG, "⚠️ Error en respuesta de solicitudes: Código " + solicitudResponse.code());
            }

            // Guardar timestamp de última sincronización
            long currentTime = System.currentTimeMillis();
            prefs.edit()
                    .putLong(KEY_LAST_SYNC, currentTime)
                    .apply();

            Log.d(TAG, "💾 ✅ Sincronización completada exitosamente");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error en sincronización: " + e.getMessage());
            // Continuar intentando - no es crítico si falla una vez
        }
    }

    /**
     * Obtener la hora de última sincronización
     */
    public static long getLastSyncTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getLong(KEY_LAST_SYNC, 0);
    }

    /**
     * Iniciar el servicio
     */
    public static void start(Context context) {
        Intent intent = new Intent(context, ServerMonitoringService.class);
        try {
            context.startService(intent);
            Log.d(TAG, "🚀 Servicio iniciado");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error iniciando servicio", e);
        }
    }

    /**
     * Detener el servicio
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, ServerMonitoringService.class);
        context.stopService(intent);
        Log.d(TAG, "⛔ Servicio detenido");
    }
}
