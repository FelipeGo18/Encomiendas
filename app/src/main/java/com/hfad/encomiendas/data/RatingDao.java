package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RatingDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(Rating r);

    @Update
    void update(Rating r);

    // Métodos que SÍ se utilizan en el proyecto
    @Query("SELECT * FROM ratings WHERE shipmentId=:shipmentId LIMIT 1")
    Rating byShipment(long shipmentId);

    @Query("SELECT * FROM ratings WHERE repartidorId = :repartidorId ORDER BY fechaMillis DESC")
    List<Rating> listByRepartidorId(int repartidorId);
}
