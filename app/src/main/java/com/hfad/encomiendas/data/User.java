package com.hfad.encomiendas.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "users", indices = {@Index(value = {"email"}, unique = true)})
public class User {
    @PrimaryKey(autoGenerate = true) public long id;       // <- long (mejor con FKs)
    @NonNull public String email = "";
    @NonNull public String telefono = "";                  // <- default vacío para NOT NULL
    @NonNull public String passwordHash = "";              // <- quita ';;' y pon default
    @NonNull public String rol = "REMITENTE";
    public long createdAt;
}