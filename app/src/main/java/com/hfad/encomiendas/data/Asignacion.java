package com.hfad.encomiendas.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "asignaciones",
        foreignKeys = {
                @ForeignKey(
                        entity = Solicitud.class,
                        parentColumns = "id",
                        childColumns = "solicitudId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = Recolector.class,            // Asegúrate: @Entity(tableName="recolectores")
                        parentColumns = "id",
                        childColumns = "recolectorId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("solicitudId"),
                @Index("recolectorId"),
                @Index("fecha") // muy usado en filtros y en el mapa
        }
)
public class Asignacion {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull public Integer solicitudId;     // FK a Solicitud.id
    @NonNull public Integer recolectorId;    // FK a Recolector.id

    @NonNull public String fecha;            // "yyyy-MM-dd" (requerido por tus queries)
    @NonNull public String estado;           // "ASIGNADA","RECOLECTADA",...

    @NonNull public Integer ordenRuta;       // 1,2,3...

    // Evidencias
    @Nullable public String evidenciaFotoUri;
    @Nullable public String firmaBase64;

    public boolean guiaActiva;               // false al crear
    public long createdAt;                   // System.currentTimeMillis() al crear
}
