package com.hfad.encomiendas.core;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper para obtener rutas reales usando Google Directions API
 */
public class DirectionsHelper {
    private static final String TAG = "DirectionsHelper";

    // Usar la API key desde BuildConfig
    private static String getApiKey() {
        return com.hfad.encomiendas.BuildConfig.MAPS_API_KEY;
    }

    public interface DirectionsCallback {
        void onRouteReady(PolylineOptions polylineOptions, String duration, String distance);
        void onError(String error);
    }

    /**
     * Obtiene una ruta real entre dos puntos usando Google Directions API
     */
    public static void getRoute(LatLng origin, LatLng destination, DirectionsCallback callback) {
        new Thread(() -> {
            try {
                String apiKey = getApiKey();
                Log.d(TAG, "Using API Key: " + (apiKey != null && !apiKey.isEmpty() ? "***CONFIGURED***" : "NOT CONFIGURED"));

                String url = buildDirectionsUrl(origin, destination);
                Log.d(TAG, "Requesting route URL: " + url.replace(apiKey, "***API_KEY***"));

                String response = makeHttpRequest(url);

                if (response != null) {
                    Log.d(TAG, "Received response: " + response.substring(0, Math.min(200, response.length())) + "...");

                    RouteResult result = parseDirectionsResponse(response);
                    if (result != null && result.points.size() >= 2) {
                        Log.d(TAG, "Successfully parsed route with " + result.points.size() + " points");
                        PolylineOptions polylineOptions = new PolylineOptions()
                                .addAll(result.points)
                                .width(8f)
                                .color(0xFF2196F3);

                        callback.onRouteReady(polylineOptions, result.duration, result.distance);
                    } else {
                        Log.w(TAG, "Failed to parse route or no points found. Using fallback straight line.");
                        // Fallback a línea recta si no hay ruta disponible
                        PolylineOptions fallback = new PolylineOptions()
                                .add(origin, destination)
                                .width(8f)
                                .color(0xFF2196F3)
                                .pattern(java.util.Arrays.asList(
                                    new com.google.android.gms.maps.model.Dash(20),
                                    new com.google.android.gms.maps.model.Gap(10)
                                ));
                        callback.onRouteReady(fallback, "N/A", "N/A");
                    }
                } else {
                    Log.e(TAG, "No response received from Directions API");
                    callback.onError("No se pudo obtener la ruta");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error obteniendo ruta", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Obtiene una ruta optimizada para múltiples puntos (waypoints)
     */
    public static void getOptimizedRoute(LatLng origin, List<LatLng> waypoints, LatLng destination, DirectionsCallback callback) {
        new Thread(() -> {
            try {
                String url = buildOptimizedDirectionsUrl(origin, waypoints, destination);
                String response = makeHttpRequest(url);

                if (response != null) {
                    RouteResult result = parseDirectionsResponse(response);
                    if (result != null && result.points.size() >= 2) {
                        PolylineOptions polylineOptions = new PolylineOptions()
                                .addAll(result.points)
                                .width(8f)
                                .color(0xFF2196F3);

                        callback.onRouteReady(polylineOptions, result.duration, result.distance);
                    } else {
                        callback.onError("No se pudo calcular la ruta optimizada");
                    }
                } else {
                    callback.onError("No se pudo obtener la ruta optimizada");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error obteniendo ruta optimizada", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    private static String buildDirectionsUrl(LatLng origin, LatLng destination) {
        return "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + origin.latitude + "," + origin.longitude +
                "&destination=" + destination.latitude + "," + destination.longitude +
                "&mode=driving" +
                "&key=" + getApiKey();
    }

    private static String buildOptimizedDirectionsUrl(LatLng origin, List<LatLng> waypoints, LatLng destination) {
        StringBuilder waypointsStr = new StringBuilder();
        if (waypoints != null && !waypoints.isEmpty()) {
            waypointsStr.append("&waypoints=optimize:true");
            for (LatLng waypoint : waypoints) {
                waypointsStr.append("|").append(waypoint.latitude).append(",").append(waypoint.longitude);
            }
        }

        return "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + origin.latitude + "," + origin.longitude +
                "&destination=" + destination.latitude + "," + destination.longitude +
                waypointsStr.toString() +
                "&mode=driving" +
                "&key=" + getApiKey();
    }

    private static String makeHttpRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            InputStream inputStream = connection.getInputStream();
            return readInputStream(inputStream);
        } else {
            Log.e(TAG, "HTTP Error: " + responseCode);
            return null;
        }
    }

    private static String readInputStream(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        reader.close();
        return result.toString();
    }

    private static RouteResult parseDirectionsResponse(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            String status = jsonResponse.getString("status");

            Log.d(TAG, "Directions API Status: " + status);

            if (!"OK".equals(status)) {
                Log.e(TAG, "Directions API error: " + status);
                if (jsonResponse.has("error_message")) {
                    Log.e(TAG, "Error message: " + jsonResponse.getString("error_message"));
                }
                return null;
            }

            JSONArray routes = jsonResponse.getJSONArray("routes");
            if (routes.length() == 0) {
                Log.w(TAG, "No routes found in response");
                return null;
            }

            JSONObject route = routes.getJSONObject(0);

            // Verificar que existe overview_polyline
            if (!route.has("overview_polyline")) {
                Log.e(TAG, "No overview_polyline found in route");
                return null;
            }

            JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
            if (!overviewPolyline.has("points")) {
                Log.e(TAG, "No points found in overview_polyline");
                return null;
            }

            String encodedPolyline = overviewPolyline.getString("points");
            Log.d(TAG, "Encoded polyline length: " + encodedPolyline.length());

            // Obtener duración y distancia
            JSONArray legs = route.getJSONArray("legs");
            String totalDuration = "0 mins";
            String totalDistance = "0 km";

            if (legs.length() > 0) {
                JSONObject firstLeg = legs.getJSONObject(0);
                if (firstLeg.has("duration")) {
                    totalDuration = firstLeg.getJSONObject("duration").getString("text");
                }
                if (firstLeg.has("distance")) {
                    totalDistance = firstLeg.getJSONObject("distance").getString("text");
                }
            }

            List<LatLng> points = decodePolyline(encodedPolyline);
            Log.d(TAG, "Decoded " + points.size() + " points from polyline");

            RouteResult result = new RouteResult();
            result.points = points;
            result.duration = totalDuration;
            result.distance = totalDistance;

            return result;

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing directions response", e);
            Log.e(TAG, "Response that failed to parse: " + response);
            return null;
        }
    }

    /**
     * Decodifica un polyline codificado de Google Maps
     */
    private static List<LatLng> decodePolyline(String encoded) {
        List<LatLng> points = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;

        while (index < encoded.length()) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            points.add(new LatLng(lat / 1E5, lng / 1E5));
        }

        return points;
    }

    private static class RouteResult {
        List<LatLng> points;
        String duration;
        String distance;
    }
}
