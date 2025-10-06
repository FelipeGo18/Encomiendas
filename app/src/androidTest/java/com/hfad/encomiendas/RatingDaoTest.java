package com.hfad.encomiendas;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Rating;
import com.hfad.encomiendas.data.RatingDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RatingDaoTest {

    private AppDatabase database;
    private RatingDao ratingDao;

    @Before
    public void setUp() {
        // Crear base de datos en memoria para tests
        database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase.class
        ).allowMainThreadQueries().build();

        ratingDao = database.ratingDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void insertAndRetrieveRating() {
        // Crear un rating de prueba
        Rating rating = new Rating();
        rating.shipmentId = 1L;
        rating.remitenteId = 1L;
        rating.recolectorId = 1;
        rating.repartidorId = 1;
        rating.stars = 5;
        rating.puntuacion = 5;
        rating.comment = "Excelente servicio";
        rating.comentario = "Excelente servicio";
        rating.clienteEmail = "test@test.com";
        rating.solicitudId = 1L;
        rating.createdAt = System.currentTimeMillis();
        rating.fechaMillis = System.currentTimeMillis();

        // Insertar rating
        long id = ratingDao.insert(rating);
        assertTrue("El ID debe ser mayor a 0", id > 0);

        // Recuperar rating por shipment
        Rating retrieved = ratingDao.byShipment(1L);
        assertNotNull("Debe encontrar el rating", retrieved);
        assertEquals("El rating debe ser 5 estrellas", 5, retrieved.stars);
        assertEquals("El comentario debe coincidir", "Excelente servicio", retrieved.comment);
    }

    @Test
    public void listByRepartidorId() {
        // Insertar varios ratings para el mismo repartidor
        for (int i = 1; i <= 3; i++) {
            Rating rating = new Rating();
            rating.shipmentId = i;
            rating.remitenteId = 1L;
            rating.recolectorId = 1;
            rating.repartidorId = 1;
            rating.stars = 4 + (i % 2); // Alternar entre 4 y 5 estrellas
            rating.puntuacion = 4 + (i % 2);
            rating.comment = "Test comment " + i;
            rating.comentario = "Test comment " + i;
            rating.clienteEmail = "test" + i + "@test.com";
            rating.solicitudId = i;
            rating.createdAt = System.currentTimeMillis();
            rating.fechaMillis = System.currentTimeMillis();

            ratingDao.insert(rating);
        }

        // Recuperar todos los ratings del repartidor
        List<Rating> ratings = ratingDao.listByRepartidorId(1);
        assertNotNull("La lista no debe ser null", ratings);
        assertEquals("Debe haber 3 ratings", 3, ratings.size());
    }

    @Test
    public void byShipmentNotFound() {
        // Buscar un rating que no existe
        Rating notFound = ratingDao.byShipment(999L);
        assertNull("No debe encontrar rating para shipment inexistente", notFound);
    }

    @Test
    public void updateRating() {
        // Insertar rating inicial
        Rating rating = new Rating();
        rating.shipmentId = 1L;
        rating.remitenteId = 1L;
        rating.recolectorId = 1;
        rating.repartidorId = 1;
        rating.stars = 3;
        rating.puntuacion = 3;
        rating.comment = "Regular";
        rating.comentario = "Regular";
        rating.clienteEmail = "test@test.com";
        rating.solicitudId = 1L;
        rating.createdAt = System.currentTimeMillis();
        rating.fechaMillis = System.currentTimeMillis();

        long id = ratingDao.insert(rating);

        // Actualizar el rating
        Rating toUpdate = ratingDao.byShipment(1L);
        toUpdate.stars = 5;
        toUpdate.puntuacion = 5;
        toUpdate.comment = "Excelente actualizado";
        toUpdate.comentario = "Excelente actualizado";
        toUpdate.updatedAt = System.currentTimeMillis();

        ratingDao.update(toUpdate);

        // Verificar la actualización
        Rating updated = ratingDao.byShipment(1L);
        assertEquals("El rating debe ser 5 estrellas", 5, updated.stars);
        assertEquals("El comentario debe estar actualizado", "Excelente actualizado", updated.comment);
        assertNotNull("Debe tener updatedAt", updated.updatedAt);
    }
}
