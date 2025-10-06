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
        if (isSeeded(ctx)) return;

        try {
            AppDatabase db = AppDatabase.getInstance(ctx);

            // ✅ CORREGIDO: No borrar datos existentes, solo agregar datos demo si no existen
            db.runInTransaction(() -> {
                // ❌ REMOVIDO: db.clearAllTables(); <- ESTO CAUSABA LA PÉRDIDA DE DATOS

                UserDao udao = db.userDao();

                // 1) Usuarios - Solo crear si no existen
                long remitenteId = ensureUser(udao,
                        "remitente.demo@gmail.com", "123456", "REMITENTE");
                ensureUser(udao, "operador@gmail.com",    "123456", "OPERADOR_HUB");
                ensureUser(udao, "repartidor1@gmail.com", "123456", "REPARTIDOR");
                ensureUser(udao,"asignador@gmail.com","123456","ASIGNADOR");

                // 2) Verificar si ya hay solicitudes antes de crear nuevas
                SolicitudDao sdao = db.solicitudDao();
                List<Solicitud> existentes = sdao.listByFecha(getCurrentDateString());

                // Solo crear solicitudes demo si no hay datos del día actual
                if (existentes == null || existentes.size() < 3) {
                    // 2) Solicitudes del remitente DEMO (solo si no existen suficientes)
                    long now = System.currentTimeMillis();

                    // Coordenadas reales de Bogotá para que las rutas funcionen correctamente
                    double[][] coordenadas = {
                        {4.6097, -74.0817}, // Zona Norte Bogotá
                        {4.5981, -74.0758}, // Zona Chapinero
                        {4.5709, -74.0900}  // Zona Sur Bogotá
                    };

                    for (int i = 1; i <= 3; i++) {
                        Solicitud s = new Solicitud();
                        s.remitenteId = remitenteId;              // <- ID válido !
                        s.recolectorId = null;
                        s.direccion = "CL. " + (100 + i) + " #45-" + (60 + i) + ", Bogotá";
                        s.fechaEpochMillis = now;
                        s.ventanaInicioMillis = now + i * 60 * 60 * 1000L;
                        s.ventanaFinMillis   = s.ventanaInicioMillis + 60 * 60 * 1000L;
                        s.tipoPaquete = "Paquete DEMO " + i;

                        // Agregar coordenadas reales de Bogotá
                        s.lat = coordenadas[i-1][0];
                        s.lon = coordenadas[i-1][1];

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

                // 3) Manifiesto + 3 ítems (solo si no existe ya)
                ManifiestoDao mdao = db.manifiestoDao();

                // Verificar si ya hay manifiestos para evitar duplicados
                String hoyString = getCurrentDateString();
                List<Solicitud> hoy = sdao.listByFecha(hoyString);

                if (hoy != null && !hoy.isEmpty()) {
                    // Solo crear manifiesto si hay solicitudes y no hay manifiestos duplicados
                    long currentTime = System.currentTimeMillis();

                    Manifiesto m = new Manifiesto();
                    m.codigo = "M-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
                            .format(new Date(currentTime));
                    m.fechaMillis = currentTime;
                    m.estado = "ABIERTO";
                    m.createdAt = currentTime;
                    int mid = (int) mdao.insertManifiesto(m);

                    // Agregar items al manifiesto
                    for (Solicitud s : hoy) {
                        // Verificar si ya está en un manifiesto
                        if (mdao.existsBySolicitud(s.id) > 0) continue;

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

    private static String getCurrentDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    /** Verifica si ya se ejecutó el seeder antes para evitar duplicar datos */
    private static boolean isSeeded(Context ctx) {
        try {
            AppDatabase db = AppDatabase.getInstance(ctx);
            // Verificar múltiples indicadores para estar seguros
            User demoUser = db.userDao().findByEmail("remitente.demo@gmail.com");
            if (demoUser == null) return false;

            // También verificar que hay datos básicos
            List<User> users = db.userDao().listAll();
            if (users == null || users.size() < 3) return false;

            return true; // Todo parece estar en orden
        } catch (Exception e) {
            Log.w(TAG, "Error verificando si está seeded, asumiendo que no", e);
            return false; // En caso de error, forzar re-seeding
        }
    }
}