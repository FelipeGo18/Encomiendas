package com.hfad.encomiendas.api;

import com.hfad.encomiendas.data.User;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Cliente Retrofit para conectarse a la API REST Java EE en GlassFish
 * Singleton que mantiene una única instancia de Retrofit
 */
public class ApiClient {
    
    // URL base de tu API Java EE
    // PARA BLUESTACKS: usa localhost o 127.0.0.1
    // PARA ANDROID STUDIO EMULATOR: usa 10.0.2.2 ⚠️ IMPORTANTE
    // PARA DISPOSITIVO REAL: usa tu IP local (ej: 192.168.1.100)

    // ✅ ANDROID STUDIO EMULATOR - USA ESTA:
    private static final String BASE_URL = "http://10.0.2.2:8080/EncomiendasAPI/api/";

    // Alternativas según tu caso:
    // private static final String BASE_URL = "http://localhost:8080/EncomiendasAPI/api/"; // ❌ NO funciona en emulador
    // private static final String BASE_URL = "http://127.0.0.1:8080/EncomiendasAPI/api/"; // ❌ NO funciona en emulador
    // private static final String BASE_URL = "http://192.168.1.100:8080/EncomiendasAPI/api/"; // Para dispositivo real

    private static Retrofit retrofit = null;
    
    /**
     * Obtiene la instancia de Retrofit (Singleton)
     * @return Retrofit configurado
     */
    public static Retrofit getClient() {
        if (retrofit == null) {
            // Interceptor para logging (ver las peticiones en Logcat)
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            
            // Cliente HTTP con el interceptor
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(interceptor)
                    .build();
            
            // Construir Retrofit
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    /**
     * Método auxiliar para obtener directamente la API de usuarios
     * @return Instancia de UserApi
     */
    public static UserApi getUserApi() {
        return getClient().create(UserApi.class);
    }

    /**
     * Método auxiliar para obtener directamente la API de recolectores
     * @return Instancia de RecolectorApi
     */
    public static RecolectorApi getRecolectorApi() {
        return getClient().create(RecolectorApi.class);
    }

    /**
     * Método auxiliar para obtener directamente la API de solicitudes
     * @return Instancia de SolicitudApi
     */
    public static SolicitudApi getSolicitudApi() {
        return getClient().create(SolicitudApi.class);
    }

    /**
     * Método auxiliar para registrar un usuario con contraseña en texto plano
     * La API hasheará automáticamente la contraseña
     * @param email Email del usuario
     * @param password Contraseña en texto plano
     * @param rol Rol del usuario (ADMIN, REMITENTE, RECOLECTOR, etc.)
     * @param telefono Teléfono del usuario
     * @return Call con el usuario creado
     */
    public static Call<User> registerUser(String email, String password, String rol, String telefono) {
        UserCreateRequest request = new UserCreateRequest(email, password, rol, telefono);
        return getUserApi().registerUser(request);
    }
}
