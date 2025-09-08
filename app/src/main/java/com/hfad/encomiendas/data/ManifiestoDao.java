package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ManifiestoDao {

    // --- Manifiestos ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertManifiesto(Manifiesto m);

    @Query("SELECT * FROM manifiestos WHERE estado IN ('ABIERTO','DESPACHADO') ORDER BY fechaMillis  DESC")
    List<Manifiesto> listAbiertosODespachados();


    @Query("SELECT * FROM manifiestos WHERE id=:id LIMIT 1")
    Manifiesto getById(int id);

    @Query("SELECT * FROM manifiestos WHERE estado='ABIERTO' ORDER BY fechaMillis DESC LIMIT 1")
    Manifiesto getUltimoAbierto();

    @Query("SELECT COUNT(*) FROM manifiestos")
    int countAll();

    @Query("UPDATE manifiestos SET estado='DESPACHADO', despachoMillis=:ts, repartidorEmail=:email WHERE id=:id")
    void despacharManifiesto(int id, long ts, String email);



    // --- Items ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertItem(ManifiestoItem it);

    @Query("SELECT * FROM manifiesto_items WHERE manifiestoId=:mid ORDER BY id ASC")
    List<ManifiestoItem> listItemsByManifiesto(int mid);

    @Query("SELECT COUNT(*) FROM manifiesto_items WHERE manifiestoId=:mid")
    int countItems(int mid);

    @Query("SELECT COUNT(*) FROM manifiesto_items WHERE manifiestoId=:mid AND estado='ENTREGADA'")
    int countEntregadas(int mid);

    @Query("SELECT * FROM manifiesto_items WHERE id=:id LIMIT 1")
    ManifiestoItem getItem(int id);

    @Query("UPDATE manifiesto_items SET podFotoUri=:uri WHERE id=:id")
    void guardarPodFoto(int id, String uri);

    @Query("UPDATE manifiesto_items SET podFirmaB64=:b64 WHERE id=:id")
    void guardarPodFirma(int id, String b64);

    @Query("UPDATE manifiesto_items SET estado='ENTREGADA', entregadaMillis=:ts WHERE id=:id")
    void marcarEntregada(int id, long ts);

    @Query("SELECT COUNT(*) FROM manifiesto_items WHERE solicitudId=:solId")
    int existsBySolicitud(long solId);

    // === NUEVO: items para el repartidor logueado (no entregados) ===
    @Query("SELECT mi.* " +
            "FROM manifiesto_items mi " +
            "JOIN manifiestos m ON m.id = mi.manifiestoId " +
            "WHERE m.repartidorEmail = :email " +
            "AND mi.estado IN ('EN_HUB','EN_RUTA') " +
            "ORDER BY mi.id ASC")
    List<ManifiestoItem> listItemsParaRepartidor(String email);

    // === NUEVO: al despachar, poner los items EN_RUTA ===

    // ManifiestoDao.java

    @Query("UPDATE manifiesto_items " +
            "SET estado='EN_RUTA' " +
            "WHERE manifiestoId=:mid AND estado IN ('EN_HUB','CLASIFICADA')")
    void ponerItemsEnRuta(int mid);

    @Query("SELECT mi.* " +
            "FROM manifiesto_items mi " +
            "JOIN manifiestos m ON m.id = mi.manifiestoId " +
            "WHERE m.repartidorEmail=:email AND mi.estado in ('EN_RUTA','ENTREGADA')  " +
            "ORDER BY mi.id DESC")
    List<ManifiestoItem> listEntregasPara(String email);

    // ********** CONSULTAS PARA EL REPARTIDOR **********
    // Toma los ítems EN_RUTA del repartidor, usando JOIN con la tabla manifiestos
    @Query("""
           SELECT mi.*
           FROM manifiesto_items mi
           JOIN manifiestos m ON m.id = mi.manifiestoId
           WHERE m.repartidorEmail = :email
             AND mi.estado = 'EN_RUTA'
           ORDER BY mi.id DESC
           """)
    List<ManifiestoItem> listEnRutaParaRepartidor(String email);

    @Query("""
           SELECT mi.*
           FROM manifiesto_items mi
           JOIN manifiestos m ON m.id = mi.manifiestoId
           WHERE m.repartidorEmail = :email
           ORDER BY mi.id DESC
           """)
    List<ManifiestoItem> listParaRepartidor(String email);


}
