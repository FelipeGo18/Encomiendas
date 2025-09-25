package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AsignacionDao {

    // -------- CRUD básico --------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Asignacion a);

    @Query("SELECT * FROM asignaciones WHERE id = :id LIMIT 1")
    Asignacion getById(int id);

    @Query("SELECT * FROM asignaciones " +
            "WHERE recolectorId = :recolectorId AND fecha = :fecha " +
            "ORDER BY ordenRuta ASC")
    List<Asignacion> listByRecolectorAndFecha(int recolectorId, String fecha);

    // ============================================================
    // LISTA PARA RECOLECTOR (incluye origen/destino/pago/valor)
    // ============================================================
    @Query("""
SELECT
  a.id          AS id,
  a.estado      AS estado,
  a.ordenRuta   AS ordenRuta,
  s.tipoPaquete AS tipoProducto,
  CASE
    WHEN LOWER(s.tipoPaquete) LIKE '%documento%' THEN 'SOBRE'
    WHEN LOWER(s.tipoPaquete) LIKE '%peque%'     THEN 'PEQUENO'
    WHEN LOWER(s.tipoPaquete) LIKE '%mediano%'   THEN 'MEDIANO'
    WHEN LOWER(s.tipoPaquete) LIKE '%grande%'    THEN 'GRANDE'
    WHEN LOWER(s.tipoPaquete) LIKE '%frágil%' OR LOWER(s.tipoPaquete) LIKE '%fragil%' THEN 'MEDIANO'
    ELSE 'MEDIANO'
  END AS tamanoPaquete,

  -- Ciudad origen
  TRIM(CASE
    WHEN INSTR(LOWER(COALESCE(s.notas,'')),'origen: ')>0 THEN
      CASE
        WHEN INSTR(SUBSTR(LOWER(COALESCE(s.notas,'')),
                           INSTR(LOWER(COALESCE(s.notas,'')),'origen: ')+8),' | ')>0
        THEN SUBSTR(
               s.notas,
               INSTR(LOWER(COALESCE(s.notas,'')),'origen: ')+8,
               INSTR(SUBSTR(LOWER(COALESCE(s.notas,'')),
                            INSTR(LOWER(COALESCE(s.notas,'')),'origen: ')+8),' | ')-1
             )
        ELSE SUBSTR(s.notas, INSTR(LOWER(COALESCE(s.notas,'')),'origen: ')+8)
      END
    ELSE NULL
  END) AS ciudadOrigen,

  -- Ciudad destino
  TRIM(CASE
    WHEN INSTR(LOWER(COALESCE(s.notas,'')),'destino: ')>0 THEN
      CASE
        WHEN INSTR(SUBSTR(LOWER(COALESCE(s.notas,'')),
                           INSTR(LOWER(COALESCE(s.notas,'')),'destino: ')+9),' | ')>0
        THEN SUBSTR(
               s.notas,
               INSTR(LOWER(COALESCE(s.notas,'')),'destino: ')+9,
               INSTR(SUBSTR(LOWER(COALESCE(s.notas,'')),
                            INSTR(LOWER(COALESCE(s.notas,'')),'destino: ')+9),' | ')-1
             )
        ELSE SUBSTR(s.notas, INSTR(LOWER(COALESCE(s.notas,'')),'destino: ')+9)
      END
    ELSE NULL
  END) AS ciudadDestino,

  -- Dirección completa del destino
  TRIM(CASE
    WHEN INSTR(LOWER(COALESCE(s.notas,'')),'destinodir: ')>0 THEN
      CASE
        WHEN INSTR(SUBSTR(LOWER(COALESCE(s.notas,'')),
                           INSTR(LOWER(COALESCE(s.notas,'')),'destinodir: ')+12),' | ')>0
        THEN SUBSTR(
               s.notas,
               INSTR(LOWER(COALESCE(s.notas,'')),'destinodir: ')+12,
               INSTR(SUBSTR(LOWER(COALESCE(s.notas,'')),
                            INSTR(LOWER(COALESCE(s.notas,'')),'destinodir: ')+12),' | ')-1
             )
        ELSE SUBSTR(s.notas, INSTR(LOWER(COALESCE(s.notas,'')),'destinodir: ')+12)
      END
    ELSE NULL
  END) AS destinoDir,

  s.direccion AS direccion,  -- origen completo
  TRIM(CASE
    WHEN INSTR(LOWER(COALESCE(s.notas,'')),'pago: ')>0 THEN
      CASE
        WHEN INSTR(SUBSTR(LOWER(COALESCE(s.notas,'')),
                           INSTR(LOWER(COALESCE(s.notas,'')),'pago: ')+6),' | ')>0
        THEN SUBSTR(
               s.notas,
               INSTR(LOWER(COALESCE(s.notas,'')),'pago: ')+6,
               INSTR(SUBSTR(LOWER(COALESCE(s.notas,'')),
                            INSTR(LOWER(COALESCE(s.notas,'')),'pago: ')+6),' | ')-1
             )
        ELSE SUBSTR(s.notas, INSTR(LOWER(COALESCE(s.notas,'')),'pago: ')+6)
      END
    ELSE NULL
  END) AS pago,

  TRIM(CASE
    WHEN INSTR(LOWER(COALESCE(s.notas,'')),'valor: ')>0 THEN
      CASE
        WHEN INSTR(SUBSTR(LOWER(COALESCE(s.notas,'')),
                           INSTR(LOWER(COALESCE(s.notas,'')),'valor: ')+7),' | ')>0
        THEN SUBSTR(
               s.notas,
               INSTR(LOWER(COALESCE(s.notas,'')),'valor: ')+7,
               INSTR(SUBSTR(LOWER(COALESCE(s.notas,'')),
                            INSTR(LOWER(COALESCE(s.notas,'')),'valor: ')+7),' | ')-1
             )
        ELSE SUBSTR(s.notas, INSTR(LOWER(COALESCE(s.notas,'')),'valor: ')+7)
      END
    ELSE NULL
  END) AS valor,

  strftime('%H:%M', s.ventanaInicioMillis/1000, 'unixepoch','localtime') AS horaDesde,
  strftime('%H:%M', s.ventanaFinMillis/1000,  'unixepoch','localtime') AS horaHasta
FROM asignaciones a
JOIN Solicitud s ON s.id = a.solicitudId
WHERE a.recolectorId = :recolectorId AND a.fecha = :fecha
ORDER BY a.ordenRuta ASC
""")
    List<AsignacionDetalle> listDetalleByRecolectorFecha(int recolectorId, String fecha);


    // ============================================================
    // MÉTRICAS / LISTADOS PARA EL ASIGNADOR
    // ============================================================
    @Query("SELECT COUNT(*) FROM asignaciones WHERE fecha = :fecha")
    int countByFecha(String fecha);

    @Query("SELECT COUNT(*) FROM asignaciones WHERE fecha = :fecha AND recolectorId = :recolectorId")
    int countByFechaAndRecolector(String fecha, int recolectorId);

    // Conteo agrupado por zona del recolector
    @Query("SELECT r.zona AS zona, COUNT(a.id) AS asignadas " +
            "FROM asignaciones a " +
            "JOIN recolectores r ON r.id = a.recolectorId " +
            "WHERE a.fecha = :fecha " +
            "GROUP BY r.zona " +
            "ORDER BY asignadas DESC")
    List<ZonaAsignada> countAsignadasPorZona(String fecha);

    // Detalle por fecha + zona (para tarjetas del asignador)
// Detalle por fecha + zona (para tarjetas del asignador)
    @Query("SELECT " +
            "a.id AS id, " +
            "a.estado AS estado, " +
            "a.ordenRuta AS ordenRuta, " +
            "s.tipoPaquete AS tipoProducto, " +
            "CASE " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%documento%' THEN 'SOBRE' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%peque%'     THEN 'PEQUENO' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%mediano%'   THEN 'MEDIANO' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%grande%'    THEN 'GRANDE' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%frágil%' OR LOWER(s.tipoPaquete) LIKE '%fragil%' THEN 'MEDIANO' " +
            "  ELSE 'MEDIANO' END AS tamanoPaquete, " +

            // Origen/Destino (ciudades) hasta ' | ' en lugar de '. '
            "TRIM(CASE WHEN INSTR(LOWER(s.notas),'origen: ')>0 THEN " +
            "     SUBSTR(s.notas, INSTR(LOWER(s.notas),'origen: ')+8, " +
            "           CASE WHEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'origen: ')+8),' | ')>0 " +
            "                THEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'origen: ')+8),' | ')-1 " +
            "                ELSE LENGTH(SUBSTR(s.notas, INSTR(LOWER(s.notas),'origen: ')+8)) END) " +
            "     ELSE NULL END) AS ciudadOrigen, " +
            "TRIM(CASE WHEN INSTR(LOWER(s.notas),'destino: ')>0 THEN " +
            "     SUBSTR(s.notas, INSTR(LOWER(s.notas),'destino: ')+9, " +
            "           CASE WHEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'destino: ')+9),' | ')>0 " +
            "                THEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'destino: ')+9),' | ')-1 " +
            "                ELSE LENGTH(SUBSTR(s.notas, INSTR(LOWER(s.notas),'destino: ')+9)) END) " +
            "     ELSE NULL END) AS ciudadDestino, " +

            // Dirección completa de destino (sin cortar por '. ')
            "TRIM(CASE WHEN INSTR(LOWER(s.notas),'destinodir: ')>0 THEN " +
            "     SUBSTR(s.notas, INSTR(LOWER(s.notas),'destinodir: ')+12, " +
            "           CASE WHEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'destinodir: ')+12),' | ')>0 " +
            "                THEN INSTR(SUBSTR(s.notas, INSTR(LOWER(s.notas),'destinodir: ')+12),' | ')-1 " +
            "                ELSE LENGTH(SUBSTR(s.notas, INSTR(LOWER(s.notas),'destinodir: ')+12)) END) " +
            "     ELSE NULL END) AS destinoDir, " +

            "s.direccion AS direccion, " +
            "strftime('%H:%M', s.ventanaInicioMillis/1000, 'unixepoch','localtime') AS horaDesde, " +
            "strftime('%H:%M', s.ventanaFinMillis/1000,  'unixepoch','localtime') AS horaHasta " +
            "FROM asignaciones a " +
            "JOIN Solicitud s ON s.id = a.solicitudId " +
            "JOIN recolectores r ON r.id = a.recolectorId " +
            "WHERE a.fecha = :fecha AND r.zona = :zona " +
            "ORDER BY a.ordenRuta ASC")
    List<AsignacionDetalle> listDetalleByFechaZona(String fecha, String zona);


    // -------- Detalle por ID (opcional, si lo usas en otra pantalla) --------
    @Query("SELECT " +
            "a.id AS id, " +
            "a.estado AS estado, " +
            "a.ordenRuta AS ordenRuta, " +
            "s.tipoPaquete AS tipoProducto, " +
            "CASE " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%documento%' THEN 'SOBRE' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%peque%'     THEN 'PEQUENO' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%mediano%'   THEN 'MEDIANO' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%grande%'    THEN 'GRANDE' " +
            "  WHEN LOWER(s.tipoPaquete) LIKE '%frágil%' OR LOWER(s.tipoPaquete) LIKE '%fragil%' THEN 'MEDIANO' " +
            "  ELSE 'MEDIANO' END AS tamanoPaquete, " +
            "s.direccion AS direccion, " +
            "strftime('%H:%M', s.ventanaInicioMillis/1000, 'unixepoch', 'localtime') AS horaDesde, " +
            "strftime('%H:%M', s.ventanaFinMillis/1000,  'unixepoch', 'localtime') AS horaHasta " +
            "FROM asignaciones a " +
            "JOIN Solicitud s ON s.id = a.solicitudId " +
            "WHERE a.id = :id " +
            "LIMIT 1")
    AsignacionDetalle getDetalleById(int id);

    // -------- Evidencias --------
    @Query("UPDATE asignaciones SET evidenciaFotoUri = :uri WHERE id = :id")
    void guardarFoto(int id, String uri);

    @Query("UPDATE asignaciones SET firmaBase64 = :b64 WHERE id = :id")
    void guardarFirma(int id, String b64);

    @Query("UPDATE asignaciones SET guiaActiva = 1, estado = 'RECOLECTADA' WHERE id = :id")
    void activarGuia(int id);

    // ======= PUNTOS DE RUTA PARA EL MAPA (por fecha + zona) =======
    // AsignacionDao.java
    @Query(
            "SELECT " +
                    "  a.id        AS asignacionId, " +
                    "  a.ordenRuta AS orden, " +
                    "  s.lat       AS lat, " +
                    "  s.lon       AS lon, " +
                    "  s.direccion AS direccion " +
                    "FROM asignaciones a " +
                    "JOIN Solicitud s ON s.id = a.solicitudId " +
                    "JOIN recolectores r ON r.id = a.recolectorId " +
                    "WHERE a.fecha = :fecha AND r.zona = :zona " +
                    "ORDER BY a.ordenRuta ASC"
    )
    List<RutaPunto> rutaByFechaZona(String fecha, String zona);



    /** Proyección mínima para dibujar en el mapa. */
    class RutaPunto {
        public int asignacionId;
        public Integer orden;   // a.ordenRuta
        public Double lat;      // s.lat
        public Double lon;      // s.lon
        public String direccion;// s.direccion (opcional para snippet)
    }


    @Insert(onConflict = OnConflictStrategy.ABORT)
    List<Long> insertAll(List<Asignacion> list);

    // -------- POJOs --------
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

        // para cabecera/sub
        public String ciudadOrigen;
        public String ciudadDestino;

        // direcciones y pago/valor (cuando corresponda)
        public String direccion;     // origen
        public String destinoDir;    // destino (desde notas)
        public String pago;
        public String valor;

        public String horaDesde;
        public String horaHasta;
    }
}
