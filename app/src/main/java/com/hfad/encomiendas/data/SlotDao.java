package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SlotDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(Slot s);

    @Query("SELECT * FROM slots WHERE fecha=:fecha AND inicioMin=:inicioMin AND finMin=:finMin AND ((:zonaId IS NULL AND zonaId IS NULL) OR zonaId = :zonaId) LIMIT 1")
    Slot findSlot(String fecha, int inicioMin, int finMin, Long zonaId);

    @Query("UPDATE slots SET ocupadas = ocupadas + 1, updatedAt = :ts WHERE id = :id AND ocupadas < capacidadMax")
    int tryIncrement(long id, long ts);

    @Query("SELECT * FROM slots WHERE fecha=:fecha ORDER BY inicioMin ASC")
    List<Slot> listByFecha(String fecha);

    // Método simplificado para obtener slots ordenados por ocupación (sin campos nuevos hasta que se ejecuten migraciones)
    @Query("SELECT * FROM slots WHERE fecha=:fecha ORDER BY (CAST(ocupadas AS REAL) / CAST(capacidadMax AS REAL)) ASC, inicioMin ASC")
    List<Slot> listByFechaOrderedByOccupancy(String fecha);

    @Query("SELECT * FROM slots WHERE fecha=:fecha AND zonaId=:zonaId ORDER BY (CAST(ocupadas AS REAL) / CAST(capacidadMax AS REAL)) ASC, inicioMin ASC")
    List<Slot> listByFechaAndZonaOrderedByOccupancy(String fecha, Long zonaId);
}
