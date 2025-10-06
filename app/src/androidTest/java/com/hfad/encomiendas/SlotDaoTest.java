package com.hfad.encomiendas;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Slot;
import com.hfad.encomiendas.data.SlotDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SlotDaoTest {

    private AppDatabase database;
    private SlotDao slotDao;

    @Before
    public void setUp() {
        database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase.class
        ).allowMainThreadQueries().build();

        slotDao = database.slotDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void insertAndFindSlot() {
        // Crear slot de prueba
        Slot slot = new Slot();
        slot.fecha = "2025-10-05";
        slot.inicioMin = 8 * 60; // 8:00 AM
        slot.finMin = 10 * 60;   // 10:00 AM
        slot.capacidadMax = 3;
        slot.ocupadas = 0;
        slot.createdAt = System.currentTimeMillis();
        slot.updatedAt = null;
        slot.zonaId = null;

        // Insertar slot
        long id = slotDao.insert(slot);
        assertTrue("El ID debe ser mayor a 0", id > 0);

        // Buscar slot
        Slot found = slotDao.findSlot("2025-10-05", 8 * 60, 10 * 60, null);
        assertNotNull("Debe encontrar el slot", found);
        assertEquals("La fecha debe coincidir", "2025-10-05", found.fecha);
        assertEquals("El inicio debe coincidir", 8 * 60, found.inicioMin);
        assertEquals("El fin debe coincidir", 10 * 60, found.finMin);
        assertEquals("La capacidad debe coincidir", 3, found.capacidadMax);
        assertEquals("Debe estar vacío inicialmente", 0, found.ocupadas);
    }

    @Test
    public void testSlotCapacityIncrement() {
        // Crear slot con capacidad limitada
        Slot slot = new Slot();
        slot.fecha = "2025-10-05";
        slot.inicioMin = 8 * 60;
        slot.finMin = 10 * 60;
        slot.capacidadMax = 2; // Solo 2 espacios
        slot.ocupadas = 0;
        slot.createdAt = System.currentTimeMillis();
        slot.updatedAt = null;
        slot.zonaId = null;

        long id = slotDao.insert(slot);

        // Primer incremento - debe funcionar
        int result1 = slotDao.tryIncrement(id, System.currentTimeMillis());
        assertEquals("Primer incremento debe ser exitoso", 1, result1);

        // Segundo incremento - debe funcionar
        int result2 = slotDao.tryIncrement(id, System.currentTimeMillis());
        assertEquals("Segundo incremento debe ser exitoso", 1, result2);

        // Tercer incremento - debe fallar (capacidad llena)
        int result3 = slotDao.tryIncrement(id, System.currentTimeMillis());
        assertEquals("Tercer incremento debe fallar", 0, result3);

        // Verificar estado final
        Slot finalSlot = slotDao.findSlot("2025-10-05", 8 * 60, 10 * 60, null);
        assertEquals("Debe tener 2 espacios ocupados", 2, finalSlot.ocupadas);
    }

    @Test
    public void listAvailableSlots() {
        // Crear varios slots para el mismo día
        for (int i = 0; i < 3; i++) {
            Slot slot = new Slot();
            slot.fecha = "2025-10-05";
            slot.inicioMin = (8 + i * 2) * 60; // 8:00, 10:00, 12:00
            slot.finMin = (10 + i * 2) * 60;   // 10:00, 12:00, 14:00
            slot.capacidadMax = 2;
            slot.ocupadas = i; // 0, 1, 2 ocupados respectivamente
            slot.createdAt = System.currentTimeMillis();
            slot.updatedAt = null;
            slot.zonaId = null;

            slotDao.insert(slot);
        }

        // Verificar que se insertaron correctamente
        Slot slot1 = slotDao.findSlot("2025-10-05", 8 * 60, 10 * 60, null);
        assertNotNull("Primer slot debe existir", slot1);
        assertEquals("Primer slot debe tener 0 ocupadas", 0, slot1.ocupadas);

        Slot slot2 = slotDao.findSlot("2025-10-05", 10 * 60, 12 * 60, null);
        assertNotNull("Segundo slot debe existir", slot2);
        assertEquals("Segundo slot debe tener 1 ocupada", 1, slot2.ocupadas);

        Slot slot3 = slotDao.findSlot("2025-10-05", 12 * 60, 14 * 60, null);
        assertNotNull("Tercer slot debe existir", slot3);
        assertEquals("Tercer slot debe tener 2 ocupadas (completo)", 2, slot3.ocupadas);
    }

    @Test
    public void findSlotNotFound() {
        // Buscar un slot que no existe
        Slot notFound = slotDao.findSlot("2025-12-25", 12 * 60, 14 * 60, null);
        assertNull("No debe encontrar slot inexistente", notFound);
    }
}
