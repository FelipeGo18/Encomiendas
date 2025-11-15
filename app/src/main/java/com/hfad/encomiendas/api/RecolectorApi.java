package com.hfad.encomiendas.api;

import com.hfad.encomiendas.data.Recolector;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * API para gestionar Recolectores desde el servidor GlassFish
 */
public interface RecolectorApi {

    /**
     * GET /api/recolectores
     * Obtener todos los recolectores
     */
    @GET("recolectores")
    Call<List<Recolector>> getAllRecolectores();

    /**
     * GET /api/recolectores/{id}
     * Obtener recolector por ID
     */
    @GET("recolectores/{id}")
    Call<Recolector> getRecolectorById(@Path("id") long id);

    /**
     * POST /api/recolectores
     * Crear nuevo recolector
     */
    @POST("recolectores")
    Call<Recolector> createRecolector(@Body Recolector recolector);

    /**
     * PUT /api/recolectores/{id}
     * Actualizar recolector
     */
    @PUT("recolectores/{id}")
    Call<Recolector> updateRecolector(@Path("id") long id, @Body Recolector recolector);

    /**
     * DELETE /api/recolectores/{id}
     * Eliminar recolector
     */
    @DELETE("recolectores/{id}")
    Call<Void> deleteRecolector(@Path("id") long id);

    /**
     * GET /api/recolectores/activos
     * Obtener recolectores activos
     */
    @GET("recolectores/activos")
    Call<List<Recolector>> getRecolectoresActivos();
}

