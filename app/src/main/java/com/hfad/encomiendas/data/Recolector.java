package com.hfad.encomiendas.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recolectores")
public class Recolector {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String nombre;
    public String municipio;  // ej: Bogotá

    // NUEVOS
    public String zona;       // ej: Chicó, Chapinero
    public String vehiculo;   // BICI | MOTO | CARRO | CAMIONETA

    public int capacidad;
    public int cargaActual;
    public boolean activo;

    public String userEmail;  // vínculo con User si aplica

    public long createdAt;
    public Long updatedAt;

    public Double lat;
    public Double lon;
    public Long lastSeenMillis; // cuando a

}
