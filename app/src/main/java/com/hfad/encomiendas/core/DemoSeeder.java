// com.hfad.encomiendas.core.DemoSeeder.java
package com.hfad.encomiendas.core;

import android.content.Context;
import android.util.Log;

import com.hfad.encomiendas.api.ApiClient;
import com.hfad.encomiendas.api.UserApi;
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

            // ✅ NUEVA ESTRATEGIA: Sincronizar usuarios desde la API (GlassFish JPA)
            syncUsersFromAPI(ctx);

            // ✅ Sincronizar recolectores desde la API
            syncRecolectoresFromAPI(ctx);

            // ✅ CORREGIDO: No borrar datos existentes, solo agregar datos demo si no existen
            db.runInTransaction(() -> {
                UserDao udao = db.userDao();

                // 1) Obtener ID del remitente demo para las solicitudes
                User remitenteDemo = udao.findByEmail("remitente.demo@gmail.com");
                long remitenteId = (remitenteDemo != null) ? remitenteDemo.id : 0;

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

                // ❌ REMOVIDO: Ya no creamos recolectores ni usuarios localmente
                // Todo viene de la API de GlassFish ahora

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

            Log.d(TAG, "Seeder DEMO OK: Usuarios sincronizados desde API");
        } catch (Exception e) {
            Log.e(TAG, "Seeder error", e);
        }
    }

    /**
     * ✅ NUEVO: Sincronizar usuarios desde la API de GlassFish
     * Descarga los 5 usuarios definidos en el servidor y los guarda en Room
     */
    private static void syncUsersFromAPI(Context ctx) {
        new Thread(() -> {
            try {
                Log.d(TAG, "📡 Sincronizando usuarios desde API GlassFish...");

                UserApi api = ApiClient.getUserApi();
                retrofit2.Response<List<User>> response = api.getAllUsers().execute();

                if (response.isSuccessful() && response.body() != null) {
                    List<User> usuariosAPI = response.body();
                    Log.d(TAG, "✅ Descargados " + usuariosAPI.size() + " usuarios de la API");

                    AppDatabase db = AppDatabase.getInstance(ctx);
                    UserDao udao = db.userDao();

                    for (User user : usuariosAPI) {
                        try {
                            User existente = udao.findByEmail(user.email);

                            if (existente != null) {
                                // Actualizar si existe
                                user.id = existente.id;
                                udao.update(user);
                                Log.d(TAG, "📝 Actualizado: " + user.email + " (" + user.rol + ")");
                            } else {
                                // Insertar si no existe
                                udao.insert(user);
                                Log.d(TAG, "➕ Insertado: " + user.email + " (" + user.rol + ")");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error procesando usuario: " + e.getMessage());
                        }
                    }

                    Log.d(TAG, "💾 ✅ Usuarios sincronizados correctamente desde API");

                } else {
                    Log.e(TAG, "❌ Error en respuesta API: " + response.code());
                    Log.w(TAG, "⚠️ Usando usuarios locales como fallback");
                    createLocalUsersAsFallback(ctx);
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error conectando con API: " + e.getMessage());
                Log.w(TAG, "⚠️ Usando usuarios locales como fallback");
                createLocalUsersAsFallback(ctx);
            }
        }).start();

        // Esperar un momento para que se complete la sincronización
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Crear usuarios locales como fallback si la API no está disponible
     */
    private static void createLocalUsersAsFallback(Context ctx) {
        try {
            AppDatabase db = AppDatabase.getInstance(ctx);
            UserDao udao = db.userDao();

            ensureUser(udao, "remitente.demo@gmail.com", "123456", "REMITENTE");
            ensureUser(udao, "operador@gmail.com", "123456", "OPERADOR_HUB");
            ensureUser(udao, "repartidor1@gmail.com", "123456", "REPARTIDOR");
            ensureUser(udao, "asignador@gmail.com", "123456", "ASIGNADOR");
            ensureUser(udao, "admin@gmail.com", "123456", "ADMIN");

            Log.d(TAG, "✅ Usuarios locales creados como fallback");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creando usuarios fallback: " + e.getMessage());
        }
    }

    private static long ensureUser(UserDao udao, String email, String pass, String rol) {
        User u = udao.findByEmail(email);
        if (u != null) return u.id;
        u = new User();
        u.email = email;
        u.passwordHash = PasswordUtils.sha256(pass);
        u.rol = rol;
        u.createdAt = System.currentTimeMillis();
        return udao.insert(u);
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

    /**
     * ✅ NUEVO: Sincronizar recolectores desde la API de GlassFish
     * Descarga los recolectores definidos en el servidor y los guarda en Room
     */
    private static void syncRecolectoresFromAPI(Context ctx) {
        new Thread(() -> {
            try {
                Log.d(TAG, "📡 Sincronizando recolectores desde API GlassFish...");

                // ✅ CORREGIDO: Usar RecolectorApi en lugar de UserApi
                com.hfad.encomiendas.api.RecolectorApi api = ApiClient.getRecolectorApi();
                retrofit2.Response<List<Recolector>> response = api.getAllRecolectores().execute();

                if (response.isSuccessful() && response.body() != null) {
                    List<Recolector> recolectoresAPI = response.body();
                    Log.d(TAG, "✅ Descargados " + recolectoresAPI.size() + " recolectores de la API");

                    AppDatabase db = AppDatabase.getInstance(ctx);
                    RecolectorDao rdao = db.recolectorDao();

                    for (Recolector recolector : recolectoresAPI) {
                        try {
                            // ✅ CORREGIDO: Usar userEmail en lugar de email
                            Recolector existente = rdao.findByUserEmail(recolector.userEmail);

                            if (existente != null) {
                                // Actualizar si existe
                                recolector.id = existente.id;
                                rdao.update(recolector);
                                Log.d(TAG, "📝 Actualizado recolector: " + recolector.nombre);
                            } else {
                                // Insertar si no existe
                                rdao.insert(recolector);
                                Log.d(TAG, "➕ Insertado recolector: " + recolector.nombre);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error procesando recolector: " + e.getMessage());
                        }
                    }

                    Log.d(TAG, "💾 ✅ Recolectores sincronizados correctamente desde API");

                } else {
                    Log.e(TAG, "❌ Error en respuesta API de recolectores: " + response.code());
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error conectando con API de recolectores: " + e.getMessage());
            }
        }).start();

        // Esperar un momento para que se complete la sincronización
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
