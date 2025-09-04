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

    @Query("SELECT * FROM recolectores WHERE id = :id")
    Recolector getById(int id);

    @Query("SELECT * FROM recolectores WHERE activo = 1 AND municipio = :municipio ORDER BY cargaActual ASC")
    List<Recolector> disponiblesPorMunicipio(String municipio);

    @Query("UPDATE recolectores SET cargaActual = cargaActual + 1 WHERE id = :id")
    int incrementarCarga(int id);

    @Query("SELECT * FROM recolectores")
    List<Recolector> listAll();

    @Query("SELECT * FROM recolectores WHERE userEmail = :email LIMIT 1")
    Recolector getByUserEmail(String email);

    @Query("SELECT * FROM recolectores WHERE activo = 1")
    List<Recolector> listActivos();

}
