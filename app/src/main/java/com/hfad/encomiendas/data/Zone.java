package com.hfad.encomiendas.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Zona operativa dibujada (polígono) para agrupar solicitudes y generar rutas. */
@Entity(tableName = "zones")
public class Zone {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull public String nombre = "";          // Nombre legible
    @NonNull public String polygonJson = "[]";   // Lista de puntos en JSON (GeoJSON simple o array de {lat,lon})

    public boolean activo = true;           // Soft delete / habilitado
    public String colorHex;                 // Color sugerido para UI (ej. #FF9800)

    public long createdAt;                  // System.currentTimeMillis al crear
    public long updatedAt;                  // Actualizar en modificaciones
}
