package com.hfad.encomiendas.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TrackingEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(TrackingEvent e);

    @Query("SELECT * FROM tracking_event WHERE shipmentId=:sid ORDER BY occurredAt DESC")
    List<TrackingEvent> listByShipment(long sid);

    // Última ubicación registrada para ese envío
    @Query("SELECT lat AS lat, lon AS lon, occurredAt AS whenIso " +
            "FROM tracking_event " +
            "WHERE shipmentId=:sid AND lat IS NOT NULL AND lon IS NOT NULL " +
            "ORDER BY occurredAt DESC LIMIT 1")
    LastLoc lastLocationForShipment(long sid);

    class LastLoc { public Double lat; public Double lon; public String whenIso; }
}
