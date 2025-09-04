package com.hfad.encomiendas.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "solicitudes")
public class Solicitud {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String tipoProducto;
    public String ciudadOrigen;
    public String ciudadDestino;
    public String formaPago;
    public Double valorDeclarado;

    // NUEVO
    public String tamanoPaquete; // SOBRE | PEQUENO | MEDIANO | GRANDE | VOLUMINOSO

    public String municipio;
    public String direccion;
    public String barrioVereda;

    public String fecha;
    public String horaDesde;
    public String horaHasta;

    public long createdAt;
    public Long updatedAt;
}
