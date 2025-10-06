package com.hfad.encomiendas.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                User.class,
                Solicitud.class,
                Asignacion.class,
                Recolector.class,
                Manifiesto.class,
                ManifiestoItem.class,
                EtaCache.class,
                TrackingEvent.class,
                Zone.class,
                RecolectorLocationLog.class,
                Slot.class,
                Rating.class
        },
        version = 27, // Corregir versión a 27
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract UserDao userDao();
    public abstract SolicitudDao solicitudDao();
    public abstract AsignacionDao asignacionDao();
    public abstract RecolectorDao recolectorDao();
    public abstract ManifiestoDao manifiestoDao();
    public abstract EtaCacheDao etaCacheDao();
    public abstract TrackingEventDao trackingEventDao();
    public abstract ZoneDao zoneDao();
    public abstract RecolectorLocationLogDao recolectorLocationLogDao();
    public abstract SlotDao slotDao();
    public abstract RatingDao ratingDao();



    public static AppDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(ctx.getApplicationContext(), AppDatabase.class, "encomiendas.db")
                            .fallbackToDestructiveMigration() // Recrear BD si hay problemas de migración
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
