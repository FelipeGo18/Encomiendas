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
                RecolectorLocationLog.class
        },
        version = 21,
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

    private static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `zones` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`nombre` TEXT NOT NULL, " +
                    "`polygonJson` TEXT NOT NULL, " +
                    "`activo` INTEGER NOT NULL DEFAULT 1, " +
                    "`colorHex` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)");
        }
    };

    private static final Migration MIGRATION_18_19 = new Migration(18,19) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            // Nuevas columnas en manifiestos
            db.execSQL("ALTER TABLE manifiestos ADD COLUMN zoneId INTEGER");
            db.execSQL("ALTER TABLE manifiestos ADD COLUMN paradas INTEGER");
            db.execSQL("ALTER TABLE manifiestos ADD COLUMN distanciaTotalM INTEGER");
            db.execSQL("ALTER TABLE manifiestos ADD COLUMN duracionEstimadaMin INTEGER");
            db.execSQL("ALTER TABLE manifiestos ADD COLUMN polylineEncoded TEXT");
            db.execSQL("ALTER TABLE manifiestos ADD COLUMN horaInicioPlanificada INTEGER");
            // Orden en items si no existe
            try { db.execSQL("ALTER TABLE manifiesto_items ADD COLUMN orden INTEGER"); } catch (Exception ignore) {}
        }
    };

    private static final Migration MIGRATION_19_20 = new Migration(19,20) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `recolector_location_logs` ( `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recolectorId` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `ts` INTEGER NOT NULL )");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_recolector_logs_rid_ts ON recolector_location_logs(recolectorId, ts)");
        }
    };

    public static AppDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(ctx.getApplicationContext(), AppDatabase.class, "encomiendas.db")
                            .addMigrations(MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                            .fallbackToDestructiveMigration() // BORRA la BD si faltan migraciones (aceptado por el usuario)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
