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

}

