package com.hfad.encomiendas.data;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Calificación y comentario que deja el remitente tras la entrega. */
@Entity(tableName = "ratings",
        indices = {@Index(value = "shipmentId", unique = true), @Index(value={"recolectorId"}), @Index(value={"remitenteId"})},
        foreignKeys = {
                @ForeignKey(entity = Solicitud.class, parentColumns = "id", childColumns = "shipmentId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Recolector.class, parentColumns = "id", childColumns = "recolectorId", onDelete = ForeignKey.SET_NULL),
                @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "remitenteId", onDelete = ForeignKey.CASCADE)
        })
public class Rating {
    @PrimaryKey(autoGenerate = true) public long id;
    public long shipmentId;          // FK a Solicitud
    public long remitenteId;         // remitente que califica
    @Nullable public Integer recolectorId; // recolector relacionado
    public int stars;                // 1..5
    @Nullable public String comment; // opcional
    public long createdAt;
    @Nullable public Long updatedAt; // timestamp de última edición
    public int repartidorId;        // ID del repartidor que recibe la calificación
    public String clienteEmail;     // Email del cliente que califica
    public long solicitudId;        // ID de la solicitud relacionada
    public int puntuacion;          // Calificación de 1 a 5 estrellas
    public String comentario;       // Comentario opcional del cliente
    public long fechaMillis;        // Timestamp de cuando se creó la calificación

    /**
     * Verifica si el rating puede ser editado dentro del tiempo límite
     * @param editWindowMinutes minutos permitidos para edición después de creación
     * @return true si aún puede ser editado
     */
    public boolean canBeEdited(long editWindowMinutes) {
        long currentTime = System.currentTimeMillis();
        long timeWindow = editWindowMinutes * 60 * 1000; // convertir a milisegundos
        return (createdAt + timeWindow) > currentTime;
    }

    // Constructor vacío requerido por Room
    public Rating() {
        this.fechaMillis = System.currentTimeMillis();
        this.createdAt = this.fechaMillis;
        this.updatedAt = this.fechaMillis;
    }

    // Constructor con parámetros
    public Rating(int repartidorId, String clienteEmail, long solicitudId, int puntuacion, String comentario) {
        this();
        this.repartidorId = repartidorId;
        this.clienteEmail = clienteEmail;
        this.solicitudId = solicitudId;
        this.puntuacion = puntuacion;
        this.comentario = comentario;
    }
}
