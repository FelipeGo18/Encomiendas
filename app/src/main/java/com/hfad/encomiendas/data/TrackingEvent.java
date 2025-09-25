package com.hfad.encomiendas.data;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tracking_event")
public class TrackingEvent {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "shipmentId")
    public long shipmentId;           // = Solicitud.id

    @ColumnInfo(name = "type")
    public String type;               // PICKED_UP, EVIDENCE_PHOTO, SIGNATURE_SAVED, ...

    @ColumnInfo(name = "detail")
    @Nullable public String detail;

    @ColumnInfo(name = "lat")
    @Nullable public Double lat;

    @ColumnInfo(name = "lon")
    @Nullable public Double lon;

    @ColumnInfo(name = "occurredAt")
    public String occurredAt;         // ISO-8601
}
