package com.hfad.encomiendas.ui;

import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;

/** Item de clustering genérico para puntos de mapa (pendiente / parada / recolector). */
public class ZonaClusterItem implements ClusterItem {
    private final LatLng position;
    private final String title;
    private final String snippet;
    private final Float zIndex; // opcional

    public enum Tipo { PENDIENTE, PARADA, RECOLECTOR }
    public final Tipo tipo;

    public ZonaClusterItem(LatLng pos, String title, @Nullable String snippet, Tipo tipo) {
        this(pos, title, snippet, tipo, null);
    }

    public ZonaClusterItem(LatLng pos, String title, @Nullable String snippet, Tipo tipo, @Nullable Float zIndex) {
        this.position = pos; this.title = title; this.snippet = snippet; this.tipo = tipo; this.zIndex = zIndex;
    }

    @Override public LatLng getPosition() { return position; }
    @Override public String getTitle() { return title; }
    @Override public String getSnippet() { return snippet; }
    @Nullable @Override public Float getZIndex() { return zIndex; }
}
