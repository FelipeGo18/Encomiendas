package com.hfad.encomiendas.core;

import android.content.Context;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Solicitud;

import java.util.List;

/** Use case explícito para generar una ruta optimizada a partir de un conjunto de solicitudes pendientes. */
public class GenerateRouteUseCase {

    private final AppDatabase db;

    public static class Result {
        public final List<Solicitud> ordenOptimizado;
        public final double distanciaTotalM;
        public Result(List<Solicitud> ordenOptimizado, double distanciaTotalM) {
            this.ordenOptimizado = ordenOptimizado; this.distanciaTotalM = distanciaTotalM; }
    }

    public GenerateRouteUseCase(Context ctx){ this.db = AppDatabase.getInstance(ctx); }

    /** Optimiza la ruta (greedy + 2-opt) sobre la lista recibida (la lista ya debe venir filtrada y con coordenadas). */
    public Result optimize(List<Solicitud> base){
        if (base == null || base.isEmpty()) return new Result(base, 0);
        List<Solicitud> orden = GeoUtils.optimizeRoute(base);
        double dist = GeoUtils.pathDistance(orden);
        return new Result(orden, dist);
    }
}

