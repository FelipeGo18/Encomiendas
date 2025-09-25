package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SolicitudDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(Solicitud s);

    @Query("SELECT * FROM Solicitud WHERE id=:id LIMIT 1")
    Solicitud byId(long id);

    @Query("SELECT s.* FROM Solicitud s " +
            "JOIN asignaciones a ON a.solicitudId = s.id " +
            "WHERE a.id = :asignacionId LIMIT 1")
    Solicitud byAsignacionId(int asignacionId);


    @Query("UPDATE Solicitud SET estado='RECOLECTADA' " +
            "WHERE id = (SELECT solicitudId FROM asignaciones WHERE id=:asignacionId)")
    void marcarRecolectadaPorAsignacion(int asignacionId);




    // Marcar como ASIGNADA y guardar el recolector
    @Query("UPDATE Solicitud SET recolectorId=:rid, estado='ASIGNADA' WHERE id=:id")
    void marcarAsignada(long id, Integer rid);

    // ---- helpers de estado ----
    @Query("UPDATE Solicitud SET estado='ASIGNADA', recolectorId=:rid WHERE id=:id")
    void asignar(long id, int rid);

    // <<< nuevo: para cuando el recolector termina la recolección
    @Query("UPDATE Solicitud SET estado='RECOLECTADA' WHERE id=:id")
    void marcarRecolectada(long id);


    // Listar por fecha (YYYY-MM-DD)
    @Query("SELECT * FROM Solicitud " +
            "WHERE date(ventanaInicioMillis/1000,'unixepoch','localtime') = :fecha " +
            "ORDER BY ventanaInicioMillis ASC")
    List<Solicitud> listByFecha(String fecha);

    // Conteo de pendientes por fecha
    @Query("SELECT COUNT(*) FROM Solicitud " +
            "WHERE estado = 'PENDIENTE' " +
            "AND date(ventanaInicioMillis/1000,'unixepoch','localtime') = :fecha")
    int countUnassignedByFecha(String fecha);

    // Pendientes por zona (zona viene en 'notas' como 'Zona: <valor>.')
    @Query("SELECT " +
            "TRIM(CASE WHEN INSTR(LOWER(s.notas),'zona: ')>0 " +
            "     THEN SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6, " +
            "          CASE WHEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6), '.')>0 " +
            "               THEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6), '.')-1 " +
            "               ELSE LENGTH(SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6)) END) " +
            "     ELSE '(sin zona)' END) AS zona, " +
            "COUNT(s.id) AS pendientes " +
            "FROM Solicitud s " +
            "WHERE s.estado = 'PENDIENTE' " +
            "AND date(s.ventanaInicioMillis/1000,'unixepoch','localtime') = :fecha " +
            "GROUP BY zona " +
            "ORDER BY pendientes DESC")
    List<ZonaPendiente> countPendientesPorZona(String fecha);

    // Preview (con límite) para tarjetas del listado
    @Query("SELECT " +
            "s.tipoPaquete AS tipoProducto, " +
            "CASE " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%documento%' THEN 'SOBRE' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%peque%'     THEN 'PEQUENO' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%mediano%'   THEN 'MEDIANO' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%grande%'    THEN 'GRANDE' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%frágil%' OR LOWER(s.tipoPaquete) LIKE '%fragil%' THEN 'MEDIANO' " +
            "  ELSE 'MEDIANO' END AS tamanoPaquete, " +
            "s.direccion AS direccion, " +
            "strftime('%H:%M', s.ventanaInicioMillis/1000, 'unixepoch','localtime') AS horaDesde, " +
            "strftime('%H:%M', s.ventanaFinMillis/1000,  'unixepoch','localtime') AS horaHasta " +
            "FROM Solicitud s " +
            "WHERE s.estado = 'PENDIENTE' " +
            "AND date(s.ventanaInicioMillis/1000,'unixepoch','localtime') = :fecha " +
            "AND LOWER( TRIM(CASE WHEN INSTR(LOWER(s.notas),'zona: ')>0 " +
            "     THEN SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6, " +
            "          CASE WHEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6), '.')>0 " +
            "               THEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6), '.')-1 " +
            "               ELSE LENGTH(SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6)) END) " +
            "     ELSE '(sin zona)' END) ) = LOWER(:zona) " +
            "ORDER BY s.ventanaInicioMillis ASC " +
            "LIMIT :limit")
    List<PendienteDetalle> listPendienteDetalleByZonaAndFecha(String zona, String fecha, int limit);

    // *** NUEVO ***: versión "full" SIN límite (lo que pide ZonaDetalleFragment)
    @Query("SELECT " +
            "s.tipoPaquete AS tipoProducto, " +
            "CASE " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%documento%' THEN 'SOBRE' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%peque%'     THEN 'PEQUENO' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%mediano%'   THEN 'MEDIANO' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%grande%'    THEN 'GRANDE' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%frágil%' OR LOWER(s.tipoPaquete) LIKE '%fragil%' THEN 'MEDIANO' " +
            "  ELSE 'MEDIANO' END AS tamanoPaquete, " +
            "s.direccion AS direccion, " +
            "strftime('%H:%M', s.ventanaInicioMillis/1000, 'unixepoch','localtime') AS horaDesde, " +
            "strftime('%H:%M', s.ventanaFinMillis/1000,  'unixepoch','localtime') AS horaHasta " +
            "FROM Solicitud s " +
            "WHERE s.estado = 'PENDIENTE' " +
            "AND date(s.ventanaInicioMillis/1000,'unixepoch','localtime') = :fecha " +
            "AND LOWER( TRIM(CASE WHEN INSTR(LOWER(s.notas),'zona: ')>0 " +
            "     THEN SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6, " +
            "          CASE WHEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6), '.')>0 " +
            "               THEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6), '.')-1 " +
            "               ELSE LENGTH(SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6)) END) " +
            "     ELSE '(sin zona)' END) ) = LOWER(:zona) " +
            "ORDER BY s.ventanaInicioMillis ASC")
    List<PendienteDetalle> listPendienteDetalleFullByZonaAndFecha(String zona, String fecha);

    // Pendientes SIN asignación para fecha/zona (usado por el asignador)
    @Query("SELECT s.* " +
            "FROM Solicitud s " +
            "LEFT JOIN asignaciones a ON a.solicitudId = s.id " +
            "WHERE s.estado = 'PENDIENTE' " +
            "AND date(s.ventanaInicioMillis/1000,'unixepoch','localtime') = :fecha " +
            "AND ( :zona = '' OR " +
            "      LOWER( TRIM(CASE WHEN INSTR(LOWER(s.notas),'zona: ')>0 " +
            "           THEN SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6, " +
            "                CASE WHEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6), '.')>0 " +
            "                     THEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6), '.')-1 " +
            "                     ELSE LENGTH(SUBSTR(s.notas, INSTR(LOWER(s.notas),'zona: ')+6)) END) " +
            "           ELSE '(sin zona)' END) ) = LOWER(:zona) " +
            "    ) " +
            "AND a.id IS NULL " +
            "ORDER BY s.ventanaInicioMillis ASC")
    List<Solicitud> listUnassignedByFechaZona(String fecha, String zona);

    @Query("SELECT COUNT(*) FROM Solicitud WHERE remitenteId=:uid AND estado=:estado")
    int countByEstado(long uid, String estado);

    @Query("SELECT * FROM Solicitud WHERE remitenteId=:uid ORDER BY fechaEpochMillis DESC LIMIT :limit")
    List<Solicitud> listRecentByUser(long uid, int limit);

    @Query("SELECT * FROM Solicitud WHERE remitenteId=:uid ORDER BY fechaEpochMillis DESC")
    List<Solicitud> listAllByUser(long uid);


    // POJOs de proyección
    class ZonaPendiente { public String zona; public int pendientes; }
    // Dentro de SolicitudDao:

    class SolicitudConEta {
        @androidx.room.Embedded public Solicitud s;
        @androidx.room.ColumnInfo(name = "eta") public String eta;
    }

    @Query("SELECT s.*, ec.eta AS eta " +
            "FROM Solicitud s " +
            "LEFT JOIN eta_cache ec ON ec.shipmentId = s.id " +
            "WHERE s.remitenteId=:uid " +
            "ORDER BY s.fechaEpochMillis DESC " +
            "LIMIT :limit")
    List<SolicitudConEta> listAllByUserWithEta(long uid ,int limit);


    class PendienteDetalle {
        public String tipoProducto;
        public String tamanoPaquete;
        public String direccion;
        public String horaDesde;
        public String horaHasta;
    }
}
