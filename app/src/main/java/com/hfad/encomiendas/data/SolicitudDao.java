package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SolicitudDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(Solicitud s);

    @Query("SELECT * FROM solicitudes WHERE userEmail = :email ORDER BY createdAt DESC")
    List<Solicitud> listByUser(String email);
}
