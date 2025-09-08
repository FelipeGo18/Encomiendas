package com.hfad.encomiendas.core;

import android.content.Context;
import android.util.Log;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Manifiesto;
import com.hfad.encomiendas.data.ManifiestoDao;
import com.hfad.encomiendas.data.ManifiestoItem;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.SolicitudDao;
import com.hfad.encomiendas.data.User;
import com.hfad.encomiendas.data.UserDao;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DemoSeeder {
    private static final String TAG = "DemoSeeder";
    private DemoSeeder() {}

    public static void seed(Context ctx) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(ctx);
                UserDao udao = db.userDao();
                SolicitudDao sdao = db.solicitudDao();
                ManifiestoDao mdao = db.manifiestoDao();

                // 1) Limpia TODO primero (solo en desarrollo)
                db.clearAllTables();

                // 2) Usuarios base (REMÍTENTE de demo + roles del sprint)
                long remitenteId = upsertUserReturnId(udao,
                        "remitente_demo@gmail.com", "123456", "REMITENTE");
                upsertUserReturnId(udao, "operador@gmail.com",    "123456", "OPERADOR_HUB");
                upsertUserReturnId(udao, "repartidor1@gmail.com", "123456", "REPARTIDOR");

                // 3) 3 solicitudes RECOLECTADAS (con remitenteId válido)
                long now = System.currentTimeMillis();
                for (int i = 1; i <= 3; i++) {
                    Solicitud s = new Solicitud();
                    s.remitenteId = remitenteId;                 // <-- FK válida
                    s.recolectorId = null;
                    s.direccion = "Cl. " + (100 + i) + " #45-" + (60 + i) + ", Bogotá, Colombia";
                    s.fechaEpochMillis = now;
                    s.ventanaInicioMillis = now + i * 60 * 60 * 1000L;
                    s.ventanaFinMillis   = now + (i + 1) * 60 * 60 * 1000L;
                    s.tipoPaquete = "Paquete DEMO " + i;
                    s.notas = "Origen: Bogotá. OrigenDir: " + s.direccion +
                            ". Destino: Ciudad " + i + ". DestinoDir: Calle Falsa " + i + " #00-00.";
                    s.guia = "EC-DEMO-" + i;
                    s.estado = "RECOLECTADA";
                    sdao.insert(s);
                }

                // 4) 1 manifiesto ABIERTO
                Manifiesto m = new Manifiesto();
                m.codigo = "M-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
                        .format(new Date(now));
                m.fechaMillis = now;
                m.estado = "ABIERTO";
                m.createdAt = now;
                int mid = (int) mdao.insertManifiesto(m);

                // 5) 3 ítems EN_HUB (uno por solicitud de hoy)
                String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                for (Solicitud s : sdao.listByFecha(hoy)) {
                    ManifiestoItem it = new ManifiestoItem();
                    it.manifiestoId = mid;            // <-- FK válida a manifiestos.id
                    it.solicitudId = s.id;            // <-- FK válida a solicitudes.id
                    it.guia = s.guia;
                    it.destinoCiudad = "Ciudad " + s.id;
                    it.destinoDireccion = "Calle Falsa " + s.id + " #00-00";
                    it.otp = String.valueOf((int) (Math.random() * 900000) + 100000);
                    it.estado = "EN_HUB";
                    it.createdAt = System.currentTimeMillis();
                    mdao.insertItem(it);
                }

                Log.d(TAG, "OK: 1 manifiesto ABIERTO + 3 ítems EN_HUB sembrados.");
            } catch (Exception e) {
                Log.e(TAG, "Seeder error", e);
            }
        }).start();
    }

    /** Crea usuario si falta y devuelve su id (útil para FKs) */
    private static long upsertUserReturnId(UserDao dao, String email, String pass, String rol) {
        User u = dao.findByEmail(email);
        if (u != null) return u.id;   // ya existe, devolvemos su id
        u = new User();
        u.email = email;
        u.passwordHash = PasswordUtils.sha256(pass);
        u.rol = rol;
        u.createdAt = System.currentTimeMillis();
        return dao.insert(u);
    }
}