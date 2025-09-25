package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RecolectorDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(Recolector r);

    @Update
    int update(Recolector r);

    @Query("SELECT * FROM recolectores WHERE id = :id LIMIT 1")
    Recolector getById(int id);

    @Query("SELECT * FROM recolectores WHERE activo = 1 AND municipio = :municipio ORDER BY cargaActual ASC")
    List<Recolector> disponiblesPorMunicipio(String municipio);

    @Query("UPDATE recolectores SET cargaActual = cargaActual + 1 WHERE id = :id")
    int incrementarCarga(int id);


    @Query("SELECT * FROM recolectores ORDER BY id ASC")
    List<Recolector> listAll();


    @Query("SELECT * FROM recolectores WHERE userEmail = :email LIMIT 1")
    Recolector getByUserEmail(String email);

    @Query("SELECT * FROM recolectores WHERE activo = 1")
    List<Recolector> listActivos();


    @Query("SELECT * FROM recolectores WHERE LOWER(zona) = LOWER(:zona) LIMIT 1")
    Recolector findByZona(String zona);


    // Update location (room-friendly)
    @Query("UPDATE recolectores SET lat = :lat, lon = :lon, lastSeenMillis = :ts WHERE id = :id")
    void updateLocation(int id, double lat, double lon,long ts);

}
