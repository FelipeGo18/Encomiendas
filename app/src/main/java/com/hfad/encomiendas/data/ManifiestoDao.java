// com.hfad.encomiendas.data.ManifiestoDao.java
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

    @Query("SELECT * FROM manifiestos WHERE estado IN ('ABIERTO','DESPACHADO') ORDER BY id DESC")
    List<Manifiesto> listAbiertosODespachados();

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

    // ===== NUEVO =====

    // Pone todos los ítems del manifiesto EN_RUTA cuando se despacha
    @Query("UPDATE manifiesto_items SET estado='EN_RUTA' WHERE manifiestoId=:mid")
    void ponerItemsEnRuta(int mid);

    // Lista de entregas EN_RUTA para el email del repartidor asignado en el manifiesto
    @Query("""
           SELECT i.* FROM manifiesto_items i
           JOIN manifiestos m ON m.id = i.manifiestoId
           WHERE m.repartidorEmail = :repartidorEmail AND i.estado = 'EN_RUTA'
           ORDER BY i.id ASC
           """)
    List<ManifiestoItem> listEnRutaParaRepartidor(String repartidorEmail);

    // Guarda coordenadas al marcar entrega
    @Query("UPDATE manifiesto_items SET podLat=:lat, podLon=:lon WHERE id=:id")
    void guardarPodUbicacion(int id, Double lat, Double lon);

    // Busca un manifiesto por id
    @Query("SELECT * FROM manifiestos WHERE id=:id LIMIT 1")
    Manifiesto getById(int id);

    // Devuelve el último manifiesto en estado ABIERTO (por fecha)
    @Query("SELECT * FROM manifiestos WHERE estado='ABIERTO' ORDER BY fechaMillis DESC LIMIT 1")
    Manifiesto getUltimoAbierto();

    // Inserción batch de items
    @Insert(onConflict = OnConflictStrategy.ABORT)
    List<Long> insertItems(List<ManifiestoItem> items);

    // Cuenta manifiestos por código
    @Query("SELECT COUNT(*) FROM manifiestos WHERE codigo = :codigo")
    int countByCodigo(String codigo);
}