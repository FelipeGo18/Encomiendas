package com.hfad.encomiendas.data;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Franja horaria reservable para recolección. Se mide en minutos desde medianoche para inicio/fin.
 * fecha: formato "yyyy-MM-dd" (local) usado para agrupación rápida.
 */
@Entity(tableName = "slots",
        indices = {@Index(value = {"fecha"}), @Index(value = {"fecha","zonaId"})},
        foreignKeys = {
                @ForeignKey(entity = Zone.class, parentColumns = "id", childColumns = "zonaId", onDelete = ForeignKey.SET_NULL)
        })
public class Slot {
    @PrimaryKey(autoGenerate = true) public long id;
    public String fecha;              // yyyy-MM-dd
    public int inicioMin;             // minutos desde 00:00
    public int finMin;                // minutos desde 00:00
    public int capacidadMax;          // cupos totales
    public int ocupadas;              // cupos usados totales
    @Nullable public Long zonaId;     // opcional
    public long createdAt;            // ts creación
    public Long updatedAt;            // ts actualización


}
