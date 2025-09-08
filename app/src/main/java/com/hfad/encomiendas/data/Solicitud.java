package com.hfad.encomiendas.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@Entity(
        foreignKeys = {
                @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "remitenteId"),
                @ForeignKey(entity = Recolector.class, parentColumns = "id", childColumns = "recolectorId")
        },
        indices = {@Index("remitenteId"), @Index("recolectorId")}
)
public class Solicitud {
    @PrimaryKey(autoGenerate = true) public long id;

    public long remitenteId;
    @Nullable public Long recolectorId; // null hasta asignar

    @NonNull public String direccion;
    /** fecha/hora de creación (no confundir con ventana) */
    public long fechaEpochMillis;

    /** NUEVO: ventana de atención */
    public long ventanaInicioMillis;  // p.ej. 2025-09-05 14:00
    public long ventanaFinMillis;     // p.ej. 2025-09-05 16:00

    /** NUEVO: detalles logísticos */
    @NonNull public String tipoPaquete;     // usar arrays.xml
    @Nullable public Double pesoKg;         // opcional
    @Nullable public Double volumenM3;      // opcional

    /** opcional: para proximidad/rutas futuras */
    @Nullable public Double lat;
    @Nullable public Double lon;

    /** NUEVO: identificador de la guía/tracking */
    @NonNull public String guia;            // "EC-2025-000123"

    @NonNull public String estado;          // "PENDIENTE","ASIGNADA","RECOGIDA"
    @Nullable public String notas;
}