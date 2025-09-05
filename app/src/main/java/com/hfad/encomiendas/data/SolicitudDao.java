package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SolicitudDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Solicitud s);

    @Query("SELECT * FROM solicitudes WHERE fecha = :fecha ORDER BY createdAt DESC")
    List<Solicitud> listByFecha(String fecha);

    // ---- Pendientes (no tienen asignación ese día) ----
    @Query("SELECT COUNT(*) FROM solicitudes s " +
            "WHERE s.fecha = :fecha AND s.id NOT IN (" +
            "  SELECT a.solicitudId FROM asignaciones a WHERE a.fecha = :fecha" +
            ")")
    int countUnassignedByFecha(String fecha);

    @Query("SELECT * FROM solicitudes s " +
            "WHERE s.fecha = :fecha AND s.id NOT IN (" +
            "  SELECT a.solicitudId FROM asignaciones a WHERE a.fecha = :fecha" +
            ") AND (:zona IS NULL OR :zona = '' OR s.barrioVereda = :zona)")
    List<Solicitud> listUnassignedByFechaZona(String fecha, String zona);

    // Conteo de pendientes por zona (para tablero)
    class ZonaPendiente {
        public String zona;
        public int pendientes;
    }

    @Query("SELECT s.barrioVereda AS zona, COUNT(s.id) AS pendientes " +
            "FROM solicitudes s " +
            "WHERE s.fecha = :fecha AND s.id NOT IN (" +
            "  SELECT a.solicitudId FROM asignaciones a WHERE a.fecha = :fecha" +
            ") GROUP BY s.barrioVereda ORDER BY pendientes DESC")
    List<ZonaPendiente> countPendientesPorZona(String fecha);

    // Detalle compacto de pendientes por zona (para previews en tarjetas)
    class PendienteDetalle {
        public int id;
        public String tipoProducto;
        public String tamanoPaquete;
        public String direccion;
        public String horaDesde;
        public String horaHasta;
        public String zona;
    }

    @Query("SELECT s.id AS id, s.tipoProducto AS tipoProducto, s.tamanoPaquete AS tamanoPaquete, " +
            "s.direccion AS direccion, s.horaDesde AS horaDesde, s.horaHasta AS horaHasta, " +
            "s.barrioVereda AS zona " +
            "FROM solicitudes s " +
            "WHERE s.fecha = :fecha AND s.barrioVereda = :zona AND " +
            "      s.id NOT IN (SELECT a.solicitudId FROM asignaciones a WHERE a.fecha = :fecha) " +
            "ORDER BY s.horaDesde LIMIT :limit")
    List<PendienteDetalle> listPendienteDetalleByZonaAndFecha(String zona, String fecha, int limit);

    @Query("SELECT s.id AS id, s.tipoProducto AS tipoProducto, s.tamanoPaquete AS tamanoPaquete, " +
            "s.direccion AS direccion, s.horaDesde AS horaDesde, s.horaHasta AS horaHasta, " +
            "s.barrioVereda AS zona " +
            "FROM solicitudes s " +
            "WHERE s.fecha = :fecha AND s.barrioVereda = :zona AND " +
            "      s.id NOT IN (SELECT a.solicitudId FROM asignaciones a WHERE a.fecha = :fecha) " +
            "ORDER BY s.horaDesde")
    List<PendienteDetalle> listPendienteDetalleFullByZonaAndFecha(String zona, String fecha);
}
