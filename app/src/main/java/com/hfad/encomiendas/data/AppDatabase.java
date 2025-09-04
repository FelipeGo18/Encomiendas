package com.hfad.encomiendas.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// Asegúrate de importar TODAS las entidades reales que tienes:
import com.hfad.encomiendas.data.User;
import com.hfad.encomiendas.data.Recolector;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.Asignacion;

@Database(
        entities = {
                User.class,
                Recolector.class,
                Solicitud.class,
                Asignacion.class
        },
        version = 8,                 // ⬅⬅⬅ SUBE ESTA VERSIÓN
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    ctx.getApplicationContext(),
                                    AppDatabase.class,
                                    "encomiendas.db"
                            )
                            // En desarrollo: borra y recrea si el esquema cambió
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract UserDao userDao();
    public abstract RecolectorDao recolectorDao();
    public abstract SolicitudDao solicitudDao();
    public abstract AsignacionDao asignacionDao();
}
