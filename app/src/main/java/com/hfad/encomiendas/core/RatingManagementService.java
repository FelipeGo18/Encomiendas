package com.hfad.encomiendas.core;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Rating;
import com.hfad.encomiendas.data.RatingDao;

import java.util.List;

/**
 * Servicio para gestión avanzada de calificaciones con funcionalidad de edición
 */
public class RatingManagementService {

    private final RatingDao ratingDao;
    private static final long DEFAULT_EDIT_WINDOW_MINUTES = 30; // 30 minutos por defecto

    public RatingManagementService(AppDatabase database) {
        this.ratingDao = database.ratingDao();
    }

    /**
     * Crea una nueva calificación
     */
    public long createRating(long shipmentId, long remitenteId, Integer recolectorId,
                           int stars, String comment) {
        Rating rating = new Rating();
        rating.shipmentId = shipmentId;
        rating.remitenteId = remitenteId;
        rating.recolectorId = recolectorId;
        rating.stars = stars;
        rating.comment = comment;
        rating.createdAt = System.currentTimeMillis();
        rating.updatedAt = null;

        return ratingDao.insert(rating);
    }

    /**
     * Obtiene estadísticas completas de calificaciones para un repartidor
     * @param recolectorId ID del repartidor
     * @return estadísticas detalladas
     */
    public RatingStats getRepartidorStats(int recolectorId) {
        // Usar el método que realmente existe en RatingDao
        List<Rating> allRatings = ratingDao.listByRepartidorId(recolectorId);

        if (allRatings == null || allRatings.isEmpty()) {
            return new RatingStats(0, 0.0, 0, 0, 0, 0, 0);
        }

        int total = allRatings.size();
        int suma = 0;
        int count1 = 0, count2 = 0, count3 = 0, count4 = 0, count5 = 0;

        for (Rating rating : allRatings) {
            // Usar el campo correcto según la entidad Rating
            int puntuacion = rating.puntuacion > 0 ? rating.puntuacion : rating.stars;
            suma += puntuacion;
            switch (puntuacion) {
                case 1: count1++; break;
                case 2: count2++; break;
                case 3: count3++; break;
                case 4: count4++; break;
                case 5: count5++; break;
            }
        }

        double promedio = (double) suma / total;

        return new RatingStats(total, promedio, count1, count2, count3, count4, count5);
    }

    /**
     * Clase para estadísticas detalladas de calificaciones
     */
    public static class RatingStats {
        public final int totalRatings;
        public final double averageRating;
        public final int oneStars, twoStars, threeStars, fourStars, fiveStars;

        public RatingStats(int total, double average, int one, int two, int three, int four, int five) {
            this.totalRatings = total;
            this.averageRating = average;
            this.oneStars = one;
            this.twoStars = two;
            this.threeStars = three;
            this.fourStars = four;
            this.fiveStars = five;
        }

        public double getPercentage(int stars) {
            if (totalRatings == 0) return 0.0;
            int count = 0;
            switch (stars) {
                case 1: count = oneStars; break;
                case 2: count = twoStars; break;
                case 3: count = threeStars; break;
                case 4: count = fourStars; break;
                case 5: count = fiveStars; break;
            }
            return (double) count / totalRatings * 100.0;
        }
    }
}
