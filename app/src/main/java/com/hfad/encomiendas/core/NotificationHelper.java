package com.hfad.encomiendas.core;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.hfad.encomiendas.MainActivity;
import com.hfad.encomiendas.R;

public final class NotificationHelper {
    private NotificationHelper() {}

    public static final String CH_ASSIGNMENTS = "assignments";

    /** Crea el canal (llamar una vez, por ejemplo en MainActivity.onCreate()). */
    public static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CH_ASSIGNMENTS) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CH_ASSIGNMENTS,
                        "Asignaciones",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                ch.setDescription("Avisos cuando se asignan recolecciones");
                nm.createNotificationChannel(ch);
            }
        }
    }

    /** Devuelve true si ya tenemos permiso POST_NOTIFICATIONS (o no es necesario < 33). */
    public static boolean hasPostNotifications(Context ctx) {
        return Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
    }

    /** Notificación genérica para “asignaciones listas”. */
    @SuppressLint("MissingPermission")
    public static void showAssignments(Context ctx, String title, String text, int notifyId) {
        if (!hasPostNotifications(ctx)) return;

        PendingIntent pi = PendingIntent.getActivity(
                ctx, 0, new Intent(ctx, MainActivity.class),
                (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_ASSIGNMENTS)
                .setSmallIcon(R.drawable.ic_stat_assignment) // asegúrate de tener este icono
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        try {
            NotificationManagerCompat.from(ctx.getApplicationContext()).notify(notifyId, b.build());
        } catch (SecurityException ignored) { }
    }

    /** NUEVO: notifica al “recolector” localmente en este dispositivo (demo sin backend). */
    @SuppressLint("MissingPermission")
    public static void notifyRecolector(Context ctx,
                                        int recolectorId,
                                        String zona,
                                        String fecha,
                                        int cantidadAsignadas) {
        if (!hasPostNotifications(ctx)) return;

        String title = "Tienes nuevas asignaciones";
        String text  = "Se generaron " + cantidadAsignadas +
                " recolecciones para " + fecha + " en la zona " + zona + ".";

        // Un id estable para que no se solapen entre recolectores/fechas/zonas.
        int notifyId = ("reco_" + recolectorId + "_" + fecha + "_" + zona).hashCode();

        PendingIntent pi = PendingIntent.getActivity(
                ctx, 0, new Intent(ctx, MainActivity.class),
                (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_ASSIGNMENTS)
                .setSmallIcon(R.drawable.ic_stat_assignment)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        try {
            NotificationManagerCompat.from(ctx.getApplicationContext()).notify(notifyId, b.build());
        } catch (SecurityException ignored) { }
    }


}
