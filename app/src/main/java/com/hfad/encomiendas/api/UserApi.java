package com.hfad.encomiendas.api;

import com.hfad.encomiendas.data.User;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Interfaz que define los endpoints de la API REST de usuarios
 * Cada método representa una petición HTTP a la API Java EE en GlassFish
 *
 * ENDPOINTS DISPONIBLES:
 * - GET    /api/users              - Todos los usuarios
 * - GET    /api/users/{id}         - Usuario por ID
 * - GET    /api/users/email/{email} - Usuario por email
 * - GET    /api/users/role/{rol}   - Usuarios por rol
 * - POST   /api/users              - Crear usuario
 * - PUT    /api/users/{id}         - Actualizar usuario
 * - DELETE /api/users/{id}         - Eliminar usuario
 * - POST   /api/users/login        - Login
 * - GET    /api/users/recolectores - Lista de recolectores
 */
public interface UserApi {

    /**
     * GET /api/users
     * Obtener todos los usuarios
     */
    @GET("users")
    Call<List<User>> getAllUsers();

    /**
     * GET /api/users/{id}
     * Obtener usuario por ID
     */
    @GET("users/{id}")
    Call<User> getUserById(@Path("id") long id);

    /**
     * POST /api/users
     * Crear nuevo usuario (registro) - CON PASSWORD HASHEADO
     * Usa este método solo para sincronización interna
     */
    @POST("users")
    Call<User> createUser(@Body User user);

    /**
     * POST /api/users/register
     * Crear nuevo usuario con contraseña en texto plano
     * ⭐ USA ESTE MÉTODO para crear usuarios desde Postman o formularios
     * La API hasheará automáticamente la contraseña
     */
    @POST("users/register")
    Call<User> registerUser(@Body UserCreateRequest request);

    /**
     * PUT /api/users/{id}
     * Actualizar usuario existente
     */
    @PUT("users/{id}")
    Call<User> updateUser(@Path("id") long id, @Body User user);

    /**
     * DELETE /api/users/{id}
     * Eliminar usuario
     */
    @DELETE("users/{id}")
    Call<Void> deleteUser(@Path("id") long id);

    /**
     * POST /api/users/login
     * Login de usuario (endpoint personalizado)
     */
    @POST("users/login")
    Call<User> login(@Body LoginRequest loginRequest);

    /**
     * GET /api/users/recolectores
     * Obtener lista de recolectores
     */
    @GET("users/recolectores")
    Call<List<User>> getRecolectores();

    /**
     * GET /api/users/email/{email}
     * Obtener usuario por email
     */
    @GET("users/email/{email}")
    Call<User> getUserByEmail(@Path("email") String email);

    /**
     * GET /api/users/role/{rol}
     * Obtener usuarios por rol
     */
    @GET("users/role/{rol}")
    Call<List<User>> getUsersByRole(@Path("rol") String rol);
}
