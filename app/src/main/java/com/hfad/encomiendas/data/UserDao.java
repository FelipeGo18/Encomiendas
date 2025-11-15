package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)  // ⭐ CAMBIAR A IGNORE
    long insert(User u);



    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User findByEmail(String email);

    @Query("SELECT * FROM users WHERE email = :email AND passwordHash = :hash LIMIT 1")
    User login(String email, String hash);

    @Query("SELECT * FROM users WHERE id=:id LIMIT 1")
    User byId(long id);

    // Recolectores por rol
    @Query("SELECT * FROM users WHERE LOWER(rol)='recolector'")
    List<User> listRecolectores();

    // (Opcional) Todos
    @Query("SELECT * FROM users ORDER BY id DESC")
    List<User> listAll();

    // ========== QUERIES PARA ESTADÍSTICAS (SOLO LAS QUE SE USAN) ==========

    @Query("SELECT COUNT(*) FROM users")
    int getTotalUsuarios();

    @Query("SELECT rol, COUNT(*) as count FROM users GROUP BY rol")
    List<RolCount> getCountByRol();

    @Update
    void update(User user);

    @androidx.room.Delete
    void delete(User user);

    /**
     * Obtener todos los usuarios como lista (para sincronización)
     */
    @Query("SELECT * FROM users")
    List<User> getAllUsersList();

    /**
     * ⭐ Eliminar usuarios duplicados manteniendo solo el primero
     */
    @Query("DELETE FROM users WHERE id NOT IN (SELECT MIN(id) FROM users GROUP BY email)")
    void deleteDuplicates();
}
