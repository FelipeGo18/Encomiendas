package com.hfad.encomiendas.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "asignaciones")
public class Asignacion {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public Integer solicitudId;      // FK a solicitudes.id
    public Integer recolectorId;     // FK a recolectores.id

    public String fecha;             // YYYY-MM-DD
    public String estado;            // ASIGNADA, RECOLECTADA, etc.
    public Integer ordenRuta;        // 1,2,3...



    // Evidencias
    public String evidenciaFotoUri;  // content://... (o file://...)
    public String firmaBase64;       // PNG en base64

    // Guía activada (cuando hay foto+firma válidas)
    public boolean guiaActiva;

    public long createdAt;
}
