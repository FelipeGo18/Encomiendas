package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AsignacionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Asignacion a);

    // ----- Lecturas base -----
    @Query("SELECT * FROM asignaciones WHERE id = :id LIMIT 1")
    Asignacion getById(int id);

    @Query("SELECT COUNT(*) FROM asignaciones WHERE fecha = :fecha")
    int countByFecha(String fecha);

    @Query("SELECT COUNT(*) FROM asignaciones WHERE fecha = :fecha AND recolectorId = :recolectorId")
    int countByFechaAndRecolector(String fecha, int recolectorId);

    // Usado por el asignador para calcular el siguiente orden de ruta
    @Query("SELECT * FROM asignaciones WHERE recolectorId = :recolectorId AND fecha = :fecha ORDER BY ordenRuta ASC")
    List<Asignacion> listByRecolectorAndFecha(int recolectorId, String fecha);

    // ----- Actualizaciones desde DetalleRecoleccionFragment -----
    @Query("UPDATE asignaciones SET evidenciaFotoUri = :uri WHERE id = :id")
    void guardarFoto(int id, String uri);

    @Query("UPDATE asignaciones SET firmaBase64 = :b64 WHERE id = :id")
    void guardarFirma(int id, String b64);

    @Query("UPDATE asignaciones SET estado = 'ACTIVADA', guiaActiva = 1 WHERE id = :id")
    void activarGuia(int id);

    // ----- Tablero por zona (para AsignadorFragment) -----
    @Query("SELECT s.barrioVereda AS zona, COUNT(a.id) AS asignadas " +
            "FROM asignaciones a " +
            "JOIN solicitudes s ON s.id = a.solicitudId " +
            "WHERE a.fecha = :fecha " +
            "GROUP BY s.barrioVereda " +
            "ORDER BY asignadas DESC")
    List<ZonaAsignada> countAsignadasPorZona(String fecha);

    @Query("SELECT a.id AS id, a.estado AS estado, a.ordenRuta AS ordenRuta, " +
            "s.tipoProducto AS tipoProducto, s.tamanoPaquete AS tamanoPaquete, " +
            "s.ciudadOrigen AS ciudadOrigen, s.ciudadDestino AS ciudadDestino, " +
            "s.direccion AS direccion, s.horaDesde AS horaDesde, s.horaHasta AS horaHasta " +
            "FROM asignaciones a " +
            "JOIN solicitudes s ON s.id = a.solicitudId " +
            "WHERE a.fecha = :fecha AND s.barrioVereda = :zona " +
            "ORDER BY a.ordenRuta ASC")
    List<AsignacionDetalle> listDetalleByFechaZona(String fecha, String zona);

    @Query("SELECT a.id AS id, a.estado AS estado, a.ordenRuta AS ordenRuta, " +
            "s.tipoProducto AS tipoProducto, s.tamanoPaquete AS tamanoPaquete, " +
            "s.ciudadOrigen AS ciudadOrigen, s.ciudadDestino AS ciudadDestino, " +
            "s.direccion AS direccion, s.horaDesde AS horaDesde, s.horaHasta AS horaHasta " +
            "FROM asignaciones a " +
            "JOIN solicitudes s ON s.id = a.solicitudId " +
            "WHERE a.id = :id LIMIT 1")
    AsignacionDetalle getDetalleById(int id);

    // ----- Proyecciones -----
    class ZonaAsignada {
        public String zona;
        public int asignadas;
    }

    class AsignacionDetalle {
        public int id;
        public String estado;
        public Integer ordenRuta;
        public String tipoProducto;
        public String tamanoPaquete;
        public String ciudadOrigen;
        public String ciudadDestino;
        public String direccion;
        public String horaDesde;
        public String horaHasta;
    }
}
