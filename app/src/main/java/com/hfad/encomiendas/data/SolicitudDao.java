package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SolicitudDao {

    // Insert / UPSERT simple
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Solicitud s);

    // Listado por fecha (para el asignador)
    @Query("SELECT * FROM solicitudes WHERE fecha = :fecha ORDER BY createdAt ASC")
    List<Solicitud> listByFecha(String fecha);

    // # pendientes (no asignadas) en una fecha
    @Query(
            "SELECT COUNT(*) " +
                    "FROM solicitudes s " +
                    "WHERE s.fecha = :fecha " +
                    "AND NOT EXISTS ( " +
                    "   SELECT 1 FROM asignaciones a " +
                    "   WHERE a.solicitudId = s.id AND a.fecha = :fecha" +
                    ")"
    )
    int countUnassignedByFecha(String fecha);

    // Pendientes filtradas por zona (para asignar por zona)
    @Query(
            "SELECT * FROM solicitudes s " +
                    "WHERE s.fecha = :fecha " +
                    "AND (:zona = '' OR s.barrioVereda = :zona) " +
                    "AND NOT EXISTS ( " +
                    "   SELECT 1 FROM asignaciones a " +
                    "   WHERE a.solicitudId = s.id AND a.fecha = :fecha" +
                    ") " +
                    "ORDER BY s.createdAt ASC"
    )
    List<Solicitud> listUnassignedByFechaZona(String fecha, String zona);

    // Conteo de pendientes agrupado por zona para el tablero
    @Query(
            "SELECT s.barrioVereda AS zona, COUNT(s.id) AS pendientes " +
                    "FROM solicitudes s " +
                    "LEFT JOIN asignaciones a " +
                    "  ON a.solicitudId = s.id AND a.fecha = :fecha " +
                    "WHERE s.fecha = :fecha AND a.id IS NULL " +
                    "GROUP BY s.barrioVereda " +
                    "ORDER BY pendientes DESC"
    )
    List<ZonaPendiente> countPendientesPorZona(String fecha);


    @Query(
            "SELECT * FROM solicitudes s " +
                    "WHERE s.fecha = :fecha " +
                    "AND NOT EXISTS (SELECT 1 FROM asignaciones a WHERE a.solicitudId = s.id AND a.fecha = :fecha) " +
                    "ORDER BY s.createdAt ASC"
    )
    List<Solicitud> listUnassignedByFecha(String fecha);
}
