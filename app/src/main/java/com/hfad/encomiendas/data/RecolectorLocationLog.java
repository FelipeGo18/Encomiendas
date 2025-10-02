package com.hfad.encomiendas.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recolector_location_logs")
public class RecolectorLocationLog {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public int recolectorId;
    public double lat;
    public double lon;
    public long ts; // epoch millis
}

