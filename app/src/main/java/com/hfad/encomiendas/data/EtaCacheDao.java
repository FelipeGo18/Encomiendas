package com.hfad.encomiendas.data;

import androidx.room.*;

@Dao
public interface EtaCacheDao {
    @Query("SELECT * FROM eta_cache WHERE shipmentId=:sid LIMIT 1")
    EtaCache get(long sid);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsert(EtaCache e);

    @Query("SELECT * FROM eta_cache WHERE shipmentId=:sid LIMIT 1")
    EtaCache byShipmentId(long sid);
}
