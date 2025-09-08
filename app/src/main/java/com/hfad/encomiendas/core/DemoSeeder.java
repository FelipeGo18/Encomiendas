// com.hfad.encomiendas.core.DemoSeeder.java
package com.hfad.encomiendas.core;

import android.content.Context;
import android.util.Log;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Manifiesto;
import com.hfad.encomiendas.data.ManifiestoDao;
import com.hfad.encomiendas.data.ManifiestoItem;
import com.hfad.encomiendas.data.Recolector;
import com.hfad.encomiendas.data.RecolectorDao;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.SolicitudDao;
import com.hfad.encomiendas.data.User;
import com.hfad.encomiendas.data.UserDao;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DemoSeeder {
    private static final String TAG = "DemoSeeder";
    private DemoSeeder() {}

    /** Llama esto una vez al arrancar (desde MainActivity.seedDemoData) */
    public static void seedOnce(Context ctx) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(ctx);

                // ⚠ Solo para desarrollo: limpia todo y siembra datos consistentes
                db.runInTransaction(() -> {
                    db.clearAllTables();

                    UserDao udao = db.userDao();

                    // 1) Usuarios
                    long remitenteId = ensureUser(udao,
                            "remitente.demo@gmail.com", "123456", "REMITENTE");
                    ensureUser(udao, "operador@gmail.com",    "123456", "OPERADOR_HUB");
                    ensureUser(udao, "repartidor1@gmail.com", "123456", "REPARTIDOR");
                    ensureUser(udao,"asignador@gmail.com","123456","ASIGNADOR");

                    // 2) Solicitudes del remitente DEMO
                    long now = System.currentTimeMillis();
                    SolicitudDao sdao = db.solicitudDao();
                    for (int i = 1; i <= 3; i++) {
                        Solicitud s = new Solicitud();
                        s.remitenteId = remitenteId;              // <- ID válido !
                        s.recolectorId = null;
                        s.direccion = "CL. " + (100 + i) + " #45-" + (60 + i) + ", Bogotá";
                        s.fechaEpochMillis = now;
                        s.ventanaInicioMillis = now + i * 60 * 60 * 1000L;
                        s.ventanaFinMillis   = s.ventanaInicioMillis + 60 * 60 * 1000L;
                        s.tipoPaquete = "Paquete DEMO " + i;

                        // Meta mínima para que el hub pueda clasificar
                        String fechaTxt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(new Date(s.ventanaInicioMillis));
                        s.notas =
                                "Origen: Bogotá. " +
                                        "Destino: Ciudad " + (6 + i) + ". " +
                                        "DestinoDir: Calle Falsa " + i + " #00-00. " +
                                        "Fecha: " + fechaTxt + ". ";

                        s.guia = "EC-DEMO-" + i;
                        s.estado = "RECOLECTADA"; // <- requisito para que el HUB las tome
                        sdao.insert(s);
                    }

                    RecolectorDao rdao = db.recolectorDao();
                    if (rdao.listAll().isEmpty()) {
                        Recolector r1 = new Recolector();
                        r1.nombre = "Juan R."; r1.municipio = "Bogotá"; r1.zona = "Chicó";
                        r1.vehiculo = "MOTO"; r1.capacidad = 10; r1.cargaActual = 0; r1.activo = true;
                        r1.userEmail = "recolector@gmail.com"; r1.createdAt = System.currentTimeMillis();
                        db.recolectorDao().insert(r1);

                        Recolector r2 = new Recolector();
                        r2.nombre = "Ana P."; r2.municipio = "Bogotá"; r2.zona = "Chapinero";
                        r2.vehiculo = "BICI"; r2.capacidad = 8; r2.cargaActual = 0; r2.activo = true;
                        r2.userEmail = "recolector2@gmail.com"; r2.createdAt = System.currentTimeMillis();
                        db.recolectorDao().insert(r2);
                    }

                    if (udao.findByEmail("recolector@gmail.com") == null) {
                        User u = new User();
                        u.email = "recolector@gmail.com";
                        u.passwordHash = PasswordUtils.sha256("123456");
                        u.rol = "RECOLECTOR";
                        u.createdAt = System.currentTimeMillis();
                        udao.insert(u);
                    } else if (udao.findByEmail("recolector2@gmail.com") == null) {
                        User u = new User();
                        u.email = "recolector2@gmail.com";
                        u.passwordHash = PasswordUtils.sha256("123456");
                        u.rol = "RECOLECTOR";
                        u.createdAt = System.currentTimeMillis();
                        udao.insert(u);
                    }

                    // 3) Manifiesto + 3 ítems
                    ManifiestoDao mdao = db.manifiestoDao();
                    Manifiesto m = new Manifiesto();
                    m.codigo = "M-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
                            .format(new Date(now));
                    m.fechaMillis = now;
                    m.estado = "ABIERTO";
                    m.createdAt = now;
                    int mid = (int) mdao.insertManifiesto(m);

                    List<Solicitud> hoy = sdao.listByFecha(
                            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
                    if (hoy != null) {
                        for (Solicitud s : hoy) {
                            ManifiestoItem it = new ManifiestoItem();
                            it.manifiestoId = mid;
                            it.solicitudId = s.id; // FK válido a solicitud
                            it.guia = s.guia;
                            it.destinoCiudad = "Ciudad " + s.id;
                            it.destinoDireccion = "Calle Falsa " + s.id + " #00-00";
                            it.otp = String.valueOf((int)(Math.random()*900000)+100000);
                            it.estado = "EN_HUB";
                            it.createdAt = System.currentTimeMillis();
                            mdao.insertItem(it);
                        }
                    }
                });

                Log.d(TAG, "Seeder DEMO OK: 1 manifiesto + 3 ítems");
            } catch (Exception e) {
                Log.e(TAG, "Seeder error", e);
            }
        }).start();
    }

    private static long ensureUser(UserDao udao, String email, String pass, String rol) {
        User u = udao.findByEmail(email);
        if (u != null) return u.id;           // usa el id existente
        u = new User();
        u.email = email;
        u.passwordHash = PasswordUtils.sha256(pass);
        u.rol = rol;
        u.createdAt = System.currentTimeMillis();
        return udao.insert(u);                // devuelve el id autogenerado
    }
}