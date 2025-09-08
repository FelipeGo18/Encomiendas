// com.hfad.encomiendas.data.ManifiestoItem.java
package com.hfad.encomiendas.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "manifiesto_items")
public class ManifiestoItem {
    @PrimaryKey(autoGenerate = true) public int id;

    public int manifiestoId;
    public long solicitudId;

    public String guia;
    public String destinoCiudad;
    public String destinoDireccion;

    public String otp;
    public String estado;           // EN_HUB | EN_RUTA | ENTREGADA

    // POD
    public String podFotoUri;       // <— usado por EntregaFragment
    public String podFirmaB64;      // <— usado por EntregaFragment
    public Long   entregadaMillis;  // timestamp de entrega

    // NUEVO: coordenadas al momento de la entrega
    public Double podLat;           // puede ser null
    public Double podLon;           // puede ser null

    public long createdAt;
}