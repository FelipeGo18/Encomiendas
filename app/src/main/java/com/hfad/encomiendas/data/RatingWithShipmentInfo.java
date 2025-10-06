package com.hfad.encomiendas.data;

import androidx.annotation.Nullable;

/**
 * Clase para mostrar ratings con información adicional de la solicitud
 */
public class RatingWithShipmentInfo {
    // Campos del Rating
    public long id;
    public long shipmentId;
    public long remitenteId;
    @Nullable public Integer recolectorId;
    public int stars;
    @Nullable public String comment;
    public long createdAt;
    @Nullable public Long updatedAt;

    // Campos adicionales de la Solicitud
    public String guia;
    public String direccion;

    // Método helper para verificar si puede ser editado
    public boolean canBeEdited(long editWindowMinutes) {
        long currentTime = System.currentTimeMillis();
        long timeWindow = editWindowMinutes * 60 * 1000; // convertir a milisegundos
        return (createdAt + timeWindow) > currentTime;
    }
}
