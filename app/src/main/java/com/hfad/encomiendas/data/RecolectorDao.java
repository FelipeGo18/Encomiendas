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

    @Query("SELECT r.id AS id, r.lat AS lat, r.lon AS lon FROM recolectores r WHERE r.lat IS NOT NULL AND r.lon IS NOT NULL AND LOWER(r.zona) = LOWER(:zona) GROUP BY r.id")
    List<RecolectorPos> positionsByZona(String zona);

    class RecolectorPos { public int id; public Double lat; public Double lon; }

    // ========== QUERIES PARA ESTADÍSTICAS ADMIN ==========

    @Query("SELECT COUNT(*) FROM recolectores")
    int getTotalRecolectores();

    @Query("SELECT COUNT(*) FROM recolectores WHERE activo = 1")
    int getTotalRecolectoresActivos();

    @Query("SELECT r.id, r.nombre, COUNT(s.id) as completadas " +
           "FROM recolectores r " +
           "LEFT JOIN Solicitud s ON s.recolectorId = r.id AND s.estado = 'RECOLECTADA' " +
           "GROUP BY r.id ORDER BY completadas DESC")
    List<RecolectorStats> getRecolectorStats();
}
