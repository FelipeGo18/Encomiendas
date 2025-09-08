package com.hfad.encomiendas.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "manifiestos")
public class Manifiesto {
    @PrimaryKey(autoGenerate = true) public int id;

    @NonNull public String codigo;       // p.ej. M-2025-0001
    public long   fechaMillis;           // fecha del manifiesto (millis)
    public String origenCiudad;          // opcional
    public String origenDir;             // opcional
    public String destinoCiudad;         // opcional
    public String destinoDir;            // opcional

    public String estado;                // ABIERTO, DESPACHADO, CERRADO
    public String repartidorEmail;       // asignado al despachar (opcional)
    public Long   despachoMillis;        // cuándo se despachó
    public long   createdAt;
}