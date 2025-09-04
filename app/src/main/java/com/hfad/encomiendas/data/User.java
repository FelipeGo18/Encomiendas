package com.hfad.encomiendas.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "users", indices = {@Index(value = {"email"}, unique = true)})
public class User {
    @PrimaryKey(autoGenerate = true) public int id;
    @NonNull public String email = "";
    public String passwordHash;
    public String rol; // "REMITENTE", "ASIGNADOR", "RECOLECTOR"
    public long createdAt;
}
