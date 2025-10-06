package com.hfad.encomiendas.data;

import androidx.room.Ignore;

/**
 * Clase para representar la distribución de calificaciones por número de estrellas
 */
public class RatingStarCount {
    public int stars;    // Número de estrellas (1-5)
    public int count;    // Cantidad de calificaciones con esas estrellas

    public RatingStarCount() {}

    @Ignore
    public RatingStarCount(int stars, int count) {
        this.stars = stars;
        this.count = count;
    }
}
