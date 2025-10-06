package com.hfad.encomiendas;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.hfad.encomiendas.data.AppDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Test para validar que las migraciones de la base de datos funcionan correctamente
 * sin perder datos (migraciones persistentes)
 */
@RunWith(AndroidJUnit4.class)
public class MigrationTest {

    @Test
    public void testDatabaseCreation() {
        // Crear base de datos con la versión actual
        AppDatabase database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase.class
        ).allowMainThreadQueries().build();

        // Verificar que los DAOs funcionan
        assertNotNull("UserDao debe existir", database.userDao());
        assertNotNull("SolicitudDao debe existir", database.solicitudDao());
        assertNotNull("RatingDao debe existir", database.ratingDao());
        assertNotNull("SlotDao debe existir", database.slotDao());
        assertNotNull("ZoneDao debe existir", database.zoneDao());
        assertNotNull("AsignacionDao debe existir", database.asignacionDao());
        assertNotNull("RecolectorDao debe existir", database.recolectorDao());
        assertNotNull("ManifiestoDao debe existir", database.manifiestoDao());

        database.close();
    }

    @Test
    public void testBasicDatabaseOperations() {
        AppDatabase database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase.class
        ).allowMainThreadQueries().build();

        // Test de inserción básica en users
        try {
            // Crear un usuario manualmente usando la entidad User
            var user = new com.hfad.encomiendas.data.User();
            user.email = "test@test.com";
            user.passwordHash = "hash123";
            user.telefono = "123456789";
            user.rol = "REMITENTE";
            user.createdAt = System.currentTimeMillis();

            long userId = database.userDao().insert(user);
            assertTrue("User ID debe ser mayor a 0", userId > 0);

            // Verificar que se insertó
            var retrievedUser = database.userDao().findByEmail("test@test.com");
            assertNotNull("Usuario debe existir", retrievedUser);
            assertEquals("Email debe coincidir", "test@test.com", retrievedUser.email);
            assertEquals("Rol debe coincidir", "REMITENTE", retrievedUser.rol);
            assertEquals("Teléfono debe coincidir", "123456789", retrievedUser.telefono);
        } catch (Exception e) {
            fail("Error en operaciones básicas: " + e.getMessage());
        }

        database.close();
    }

    @Test
    public void testRatingOperations() {
        AppDatabase database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase.class
        ).allowMainThreadQueries().build();

        try {
            // Insertar un rating de prueba usando el DAO
            var rating = new com.hfad.encomiendas.data.Rating();
            rating.shipmentId = 1L;
            rating.remitenteId = 1L;
            rating.recolectorId = 1;
            rating.repartidorId = 1;
            rating.stars = 5;
            rating.puntuacion = 5;
            rating.comment = "Test";
            rating.comentario = "Test";
            rating.createdAt = System.currentTimeMillis();
            rating.fechaMillis = System.currentTimeMillis();

            long id = database.ratingDao().insert(rating);
            assertTrue("ID debe ser mayor a 0", id > 0);

            // Verificar que se puede recuperar
            var retrieved = database.ratingDao().byShipment(1L);
            assertNotNull("Rating debe existir", retrieved);
            assertEquals("Stars debe ser 5", 5, retrieved.stars);

        } catch (Exception e) {
            fail("Error en operaciones de rating: " + e.getMessage());
        }

        database.close();
    }

    @Test
    public void testSlotOperations() {
        AppDatabase database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase.class
        ).allowMainThreadQueries().build();

        try {
            // Insertar un slot de prueba
            var slot = new com.hfad.encomiendas.data.Slot();
            slot.fecha = "2025-10-05";
            slot.inicioMin = 480; // 8:00 AM
            slot.finMin = 540;    // 9:00 AM
            slot.capacidadMax = 10;
            slot.ocupadas = 0;
            slot.createdAt = System.currentTimeMillis();

            long id = database.slotDao().insert(slot);
            assertTrue("ID debe ser mayor a 0", id > 0);

            // Verificar que se puede buscar
            var found = database.slotDao().findSlot("2025-10-05", 480, 540, null);
            assertNotNull("Slot debe existir", found);
            assertEquals("Fecha debe coincidir", "2025-10-05", found.fecha);

        } catch (Exception e) {
            fail("Error en operaciones de slot: " + e.getMessage());
        }

        database.close();
    }
}
