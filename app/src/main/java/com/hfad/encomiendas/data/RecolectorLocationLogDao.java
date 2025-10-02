package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RecolectorLocationLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(RecolectorLocationLog log);

    @Query("SELECT * FROM recolector_location_logs WHERE recolectorId = :rid ORDER BY ts DESC LIMIT :limit")
    List<RecolectorLocationLog> lastLogs(int rid, int limit);
}

