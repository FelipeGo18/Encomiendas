package com.hfad.encomiendas.api;

import com.hfad.encomiendas.data.Solicitud;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Interfaz que define los endpoints de la API REST de solicitudes/recolecciones
 *
 * ENDPOINTS QUE NECESITAS CREAR EN EL SERVIDOR:
 * - GET    /api/solicitudes              - Todas las solicitudes
 * - GET    /api/solicitudes/{id}         - Solicitud por ID
 * - POST   /api/solicitudes              - Crear solicitud
 * - PUT    /api/solicitudes/{id}         - Actualizar solicitud
 * - DELETE /api/solicitudes/{id}         - Eliminar solicitud
 * - GET    /api/solicitudes/remitente/{id} - Solicitudes por remitente
 * - GET    /api/solicitudes/estado/{estado} - Solicitudes por estado
 */
public interface SolicitudApi {

    /**
     * GET /api/solicitudes
     * Obtener todas las solicitudes
     */
    @GET("solicitudes")
    Call<List<Solicitud>> getAllSolicitudes();

    /**
     * GET /api/solicitudes/{id}
     * Obtener solicitud por ID
     */
    @GET("solicitudes/{id}")
    Call<Solicitud> getSolicitudById(@Path("id") long id);

    /**
     * POST /api/solicitudes
     * Crear nueva solicitud
     */
    @POST("solicitudes")
    Call<Solicitud> createSolicitud(@Body Solicitud solicitud);

    /**
     * PUT /api/solicitudes/{id}
     * Actualizar solicitud
     */
    @PUT("solicitudes/{id}")
    Call<Solicitud> updateSolicitud(@Path("id") long id, @Body Solicitud solicitud);

    /**
     * DELETE /api/solicitudes/{id}
     * Eliminar solicitud
     */
    @DELETE("solicitudes/{id}")
    Call<Void> deleteSolicitud(@Path("id") long id);

    /**
     * GET /api/solicitudes/remitente/{remitenteId}
     * Obtener solicitudes por remitente
     */
    @GET("solicitudes/remitente/{remitenteId}")
    Call<List<Solicitud>> getSolicitudesByRemitente(@Path("remitenteId") long remitenteId);

    /**
     * GET /api/solicitudes/estado/{estado}
     * Obtener solicitudes por estado
     */
    @GET("solicitudes/estado/{estado}")
    Call<List<Solicitud>> getSolicitudesByEstado(@Path("estado") String estado);
}

