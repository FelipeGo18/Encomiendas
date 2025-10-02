package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ZoneDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(Zone z);

    @Update
    int update(Zone z);

    @Query("SELECT * FROM zones WHERE id = :id LIMIT 1")
    Zone getById(long id);

    @Query("SELECT * FROM zones WHERE nombre = :nombre LIMIT 1")
    Zone getByNombre(String nombre);

    @Query("SELECT * FROM zones WHERE activo = 1 ORDER BY nombre ASC")
    List<Zone> listActivas();

    @Query("UPDATE zones SET activo = 0, updatedAt = :ts WHERE id = :id")
    int softDelete(long id, long ts);
}
