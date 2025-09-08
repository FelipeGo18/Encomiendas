package com.hfad.encomiendas.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "manifiesto_items",
        indices = {@Index("manifiestoId"), @Index("solicitudId")}
)
public class ManifiestoItem {
    @PrimaryKey(autoGenerate = true) public int id;

    public int    manifiestoId;
    public long   solicitudId;           // FK lógica a Solicitud
    public String guia;

    public String destinoCiudad;         // para el repartidor
    public String destinoDireccion;

    // POD / evidencia
    public String otp;                   // 6 dígitos
    public String podFotoUri;            // uri de la foto (TakePicture)
    public String podFirmaB64;           // firma Base64

    public String estado;                // EN_HUB, EN_RUTA, ENTREGADA
    public long   createdAt;
    public Long   entregadaMillis;
}