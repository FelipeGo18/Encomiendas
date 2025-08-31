package com.hfad.encomiendas.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "solicitudes")
public class Solicitud {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String userEmail;      // “FK” simple por email (para demo)
    public String municipio;      // etCiudadRecogida
    public String tipoZona;       // Urbana/Rural
    public String barrioVereda;   // requerido si Rural
    public String direccion;      // línea principal
    public String tipoVia;        // opcional (desglose)
    public String via;            // opcional
    public String numero;         // opcional
    public String aptoBloque;     // opcional
    public String indicaciones;   // opcional

    public String fecha;          // YYYY-MM-DD
    public String horaDesde;      // HH:MM
    public String horaHasta;      // HH:MM

    public String tipoProducto;   // dropdown
    public String ciudadOrigen;   // dropdown
    public String ciudadDestino;  // dropdown
    public String formaPago;      // dropdown
    public long   valorDeclarado; // en COP (entero)

    public long createdAt;        // epoch millis
}
