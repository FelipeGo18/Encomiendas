package com.hfad.encomiendas.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "eta_cache")
public class EtaCache {
    @PrimaryKey
    public long shipmentId;   // = Solicitud.id
    public String eta;        // ISO-8601
    public String source;     // "calc" / "manual" / etc.
}
