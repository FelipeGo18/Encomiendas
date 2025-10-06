package com.hfad.encomiendas.core;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Servicio foreground para tracking continuo de recolectores */
public class TrackingForegroundService extends Service {

    public static final String ACTION_START = "TRACKING_START";
    public static final String ACTION_STOP = "TRACKING_STOP";
    private static final String CHANNEL_ID = "tracking_channel";
    private static final int NOTIFICATION_ID = 1001;

    private FusedLocationProviderClient fused;
    private LocationCallback callback;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Integer recolectorId = null;
    private LocationRequest locationRequest;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        fused = LocationServices.getFusedLocationProviderClient(this);

        // Configurar LocationRequest
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000) // 30 segundos
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(15000) // mínimo 15 segundos
                .build();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null || result.getLastLocation() == null || recolectorId == null) return;

                double lat = result.getLastLocation().getLatitude();
                double lon = result.getLastLocation().getLongitude();
                long ts = System.currentTimeMillis();

                android.util.Log.d("TrackingService", "Nueva ubicación: " + lat + "," + lon + " para recolector " + recolectorId);

                io.execute(() -> {
                    try {
                        AppDatabase db = AppDatabase.getInstance(getApplicationContext());

                        // 1. Actualizar tabla Recolector
                        db.recolectorDao().updateLocation(recolectorId, lat, lon, ts);

                        // 2. Crear TrackingEvents para todas las solicitudes ASIGNADAS de este recolector
                        List<com.hfad.encomiendas.data.Solicitud> solicitudesAsignadas =
                            db.solicitudDao().listByRecolectorAndEstado(recolectorId, "ASIGNADA");

                        if (solicitudesAsignadas != null && !solicitudesAsignadas.isEmpty()) {
                            android.util.Log.d("TrackingService", "Creando tracking events para " + solicitudesAsignadas.size() +
                                " solicitudes asignadas del recolector " + recolectorId + " en " + lat + "," + lon);

                            for (com.hfad.encomiendas.data.Solicitud solicitud : solicitudesAsignadas) {
                                // Crear TrackingEvent con la ubicación GPS real del recolector
                                com.hfad.encomiendas.data.TrackingEvent trackingEvent = new com.hfad.encomiendas.data.TrackingEvent();
                                trackingEvent.shipmentId = solicitud.id;
                                trackingEvent.lat = lat;
                                trackingEvent.lon = lon;
                                trackingEvent.type = "EN_RUTA";
                                trackingEvent.detail = "Recolector en camino - ubicación actualizada";
                                trackingEvent.occurredAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                                        .format(new Date(ts));

                                db.trackingEventDao().insert(trackingEvent);
                            }

                            android.util.Log.d("TrackingService", "✓ Tracking events creados exitosamente para recolector " + recolectorId);
                        }

                    } catch (Exception e) {
                        android.util.Log.e("TrackingService", "Error actualizando ubicación y tracking", e);
                    }
                });
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();

        if (ACTION_START.equals(action)) {
            recolectorId = intent.getIntExtra("recolectorId", -1);
            if (recolectorId == -1) {
                android.util.Log.e("TrackingService", "No se proporcionó recolectorId válido");
                stopSelf();
                return START_NOT_STICKY;
            }

            startTracking();

        } else if (ACTION_STOP.equals(action)) {
            stopTracking();
        }

        return START_STICKY; // Reiniciar si el sistema mata el servicio
    }

    private void startTracking() {
        // Verificar permisos
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.e("TrackingService", "No hay permisos de ubicación");
            stopSelf();
            return;
        }

        // Crear notificación
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Tracking Activo")
                .setContentText("Rastreando ubicación del recolector " + recolectorId)
                .setSmallIcon(R.drawable.ic_recolector)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        // Iniciar como servicio foreground
        startForeground(NOTIFICATION_ID, notification);

        // Iniciar actualizaciones de ubicación
        try {
            fused.requestLocationUpdates(locationRequest, callback, getMainLooper());
            android.util.Log.d("TrackingService", "✓ Tracking iniciado para recolector " + recolectorId);
        } catch (SecurityException e) {
            android.util.Log.e("TrackingService", "Error de seguridad al solicitar ubicación", e);
            stopSelf();
        }
    }

    private void stopTracking() {
        try {
            if (fused != null && callback != null) {
                fused.removeLocationUpdates(callback);
                android.util.Log.d("TrackingService", "✓ Tracking detenido para recolector " + recolectorId);
            }
        } catch (Exception e) {
            android.util.Log.e("TrackingService", "Error deteniendo tracking", e);
        }

        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Tracking de Recolectores",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Seguimiento de ubicación en segundo plano");
            channel.setShowBadge(false);

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        android.util.Log.d("TrackingService", "Servicio destruido");

        try {
            if (fused != null && callback != null) {
                fused.removeLocationUpdates(callback);
            }
        } catch (Exception e) {
            android.util.Log.e("TrackingService", "Error en onDestroy", e);
        }

        if (io != null && !io.isShutdown()) {
            io.shutdown();
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
