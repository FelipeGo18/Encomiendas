package com.hfad.encomiendas.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// >>> AÑADE Manifiesto y ManifiestoItem al listado de entidades
@Database(
        entities = {
                User.class,
                Solicitud.class,
                Asignacion.class,
                Recolector.class,
                Manifiesto.class,         // <—
                ManifiestoItem.class      // <—
        },
        version =   /* súbelo +1 respecto a tu versión actual */  12,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract UserDao userDao();
    public abstract SolicitudDao solicitudDao();
    public abstract AsignacionDao asignacionDao();
    public abstract RecolectorDao recolectorDao();
    public abstract ManifiestoDao manifiestoDao();   // <— **ESTE ES EL QUE FALTABA**

    public static AppDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(ctx.getApplicationContext(),
                                    AppDatabase.class, "encomiendas.db")
                            // si no tienes migraciones, usa fallback mientras desarrollas
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
