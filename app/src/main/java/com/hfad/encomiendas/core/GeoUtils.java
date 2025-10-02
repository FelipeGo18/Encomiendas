package com.hfad.encomiendas.core;

import com.google.android.gms.maps.model.LatLng;
import com.hfad.encomiendas.data.Solicitud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Utilidades geométricas simples para polígonos y rutas. */
public final class GeoUtils {
    private GeoUtils() {}

    /** Retorna true si el punto (lat,lng) está dentro del polígono (lista de vértices). Algoritmo ray casting. */
    public static boolean pointInPolygon(double lat, double lng, List<LatLng> poly) {
        if (poly == null || poly.size() < 3) return false;
        boolean inside = false;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            double xi = poly.get(i).latitude, yi = poly.get(i).longitude;
            double xj = poly.get(j).latitude, yj = poly.get(j).longitude;
            boolean intersect = ((yi > lng) != (yj > lng)) &&
                    (lat < (xj - xi) * (lng - yi) / (yj - yi + 1e-12) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    /** Distancia aproximada en metros entre dos coordenadas (Haversine). */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0; // m
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*
                        Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    /** Distancia total de una secuencia de solicitudes (usa sus lat/lon). */
    public static double pathDistance(List<Solicitud> orden) {
        double sum = 0; if (orden == null) return 0;
        for (int i=0;i<orden.size()-1;i++) {
            Solicitud a = orden.get(i); Solicitud b = orden.get(i+1);
            if (a.lat==null||a.lon==null||b.lat==null||b.lon==null) continue;
            sum += distanceMeters(a.lat,a.lon,b.lat,b.lon);
        }
        return sum;
    }

    /** Greedy + 2-opt ligero (reutiliza lógica local) */
    public static List<Solicitud> optimizeRoute(List<Solicitud> input) {
        if (input == null || input.size() <= 2) return input == null? Collections.emptyList(): new ArrayList<>(input);
        List<Solicitud> greedy = greedyOrder(input);
        return twoOptImprove(greedy, 40);
    }

    private static List<Solicitud> greedyOrder(List<Solicitud> input) {
        List<Solicitud> pool = new ArrayList<>(input);
        List<Solicitud> out  = new ArrayList<>();
        if (pool.isEmpty()) return out;
        double cx=0, cy=0; int n=0;
        for (Solicitud s: pool){ if (s.lat!=null && s.lon!=null){ cx+=s.lat; cy+=s.lon; n++; }}
        if (n==0) return pool;
        double cLat = cx/n, cLon = cy/n;
        Solicitud current = pool.get(0); double best = Double.MAX_VALUE;
        for (Solicitud s: pool){ if (s.lat==null||s.lon==null) continue; double d=distanceMeters(cLat,cLon,s.lat,s.lon); if (d<best){best=d; current=s;} }
        out.add(current); pool.remove(current);
        while(!pool.isEmpty()){
            Solicitud next = pool.get(0); double bestD = Double.MAX_VALUE;
            for (Solicitud s: pool){ if (current.lat==null||current.lon==null||s.lat==null||s.lon==null) continue; double d=distanceMeters(current.lat,current.lon,s.lat,s.lon); if (d<bestD){bestD=d; next=s;} }
            out.add(next); pool.remove(next); current=next;
        }
        return out;
    }

    private static List<Solicitud> twoOptImprove(List<Solicitud> route, int maxIt){
        if (route.size()<4) return route;
        List<Solicitud> best = new ArrayList<>(route);
        double bestDist = pathDistance(best);
        boolean improved; int it=0;
        do {
            improved=false;
            for (int i=1;i<best.size()-2;i++) {
                for (int k=i+1;k<best.size()-1;k++) {
                    List<Solicitud> cand = twoOptSwap(best,i,k);
                    double d = pathDistance(cand);
                    if (d + 0.001 < bestDist) { best=cand; bestDist=d; improved=true; }
                }
            }
            it++;
        } while(improved && it<maxIt);
        return best;
    }

    private static List<Solicitud> twoOptSwap(List<Solicitud> route,int i,int k){
        List<Solicitud> out = new ArrayList<>();
        for(int c=0;c<i;c++) out.add(route.get(c));
        for(int c=k;c>=i;c--) out.add(route.get(c));
        for(int c=k+1;c<route.size();c++) out.add(route.get(c));
        return out;
    }

    public static String polygonToJson(List<LatLng> pts){
        if (pts==null||pts.isEmpty()) return "[]";
        StringBuilder sb=new StringBuilder("[");
        for (int i=0;i<pts.size();i++){
            LatLng p=pts.get(i);
            sb.append('{').append("\"lat\":").append(p.latitude).append(',')
              .append("\"lng\":").append(p.longitude).append('}');
            if (i<pts.size()-1) sb.append(',');
        }
        return sb.append(']').toString();
    }
    public static List<LatLng> jsonToPolygon(String json){
        List<LatLng> out=new ArrayList<>();
        if (json==null||json.trim().isEmpty()) return out;
        String j=json.trim();
        if (!j.startsWith("[")) return out;
        j=j.substring(1,j.length()-1).trim();
        if (j.isEmpty()) return out;
        String[] parts = j.split("\\},\\{");
        for (String part: parts){
            String p=part.replace("{","").replace("}","");
            String[] kv=p.split(",");
            double lat=0,lng=0; boolean ok=false;
            for (String kvp: kv){
                String[] pair=kvp.split(":");
                if (pair.length==2){
                    String key=pair[0].replace("\"","").trim();
                    String val=pair[1].replace("\"","").trim();
                    try {
                        if (key.equals("lat")) { lat=Double.parseDouble(val); ok=true; }
                        else if (key.equals("lng")) { lng=Double.parseDouble(val); }
                    } catch (NumberFormatException ignore) {}
                }
            }
            if (ok) out.add(new LatLng(lat,lng));
        }
        return out;
    }

    public static String encodePolyline(List<Solicitud> orden){
        List<LatLng> pts = new ArrayList<>();
        if (orden!=null) for (Solicitud s: orden) if (s.lat!=null&&s.lon!=null) pts.add(new LatLng(s.lat,s.lon));
        return encodeLatLngs(pts);
    }
    private static String encodeLatLngs(List<LatLng> path){
        if (path==null||path.isEmpty()) return "";
        long lastLat=0,lastLng=0; StringBuilder sb=new StringBuilder();
        for (LatLng p: path){
            long lat = Math.round(p.latitude * 1e5);
            long lng = Math.round(p.longitude * 1e5);
            long dLat = lat - lastLat; long dLng = lng - lastLng;
            encodeNumber(dLat,sb); encodeNumber(dLng,sb);
            lastLat = lat; lastLng = lng;
        }
        return sb.toString();
    }
    private static void encodeNumber(long v,StringBuilder sb){
        v <<=1; if (v<0) v = ~v;
        while (v>=0x20){ sb.append(Character.toChars((int)((0x20 | (v & 0x1f)) + 63))); v >>=5; }
        sb.append(Character.toChars((int)(v+63)));
    }
}
