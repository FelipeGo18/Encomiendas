package com.hfad.encomiendas.core;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Recolector;
import com.hfad.encomiendas.data.RecolectorDao;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Worker periódico (>=15 min) que refresca la última ubicación si hay permisos. */
public class LocationPeriodicWorker extends Worker {

    public static final String KEY_EMAIL = "email";

    public LocationPeriodicWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    @NonNull @Override public Result doWork() {
        Context ctx = getApplicationContext();
        boolean fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) return Result.success(); // sin permisos, nada que hacer

        String email = getInputData().getString(KEY_EMAIL);
        if (email == null) return Result.success();
        try {
            AppDatabase db = AppDatabase.getInstance(ctx);
            RecolectorDao rdao = db.recolectorDao();
            Recolector r = rdao.getByUserEmail(email);
            if (r == null) return Result.success();

            FusedLocationProviderClient fused = LocationServices.getFusedLocationProviderClient(ctx);
            CountDownLatch latch = new CountDownLatch(1);
            final Location[] holder = new Location[1];
            fused.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override public void onSuccess(Location location) { holder[0] = location; latch.countDown(); }
            }).addOnFailureListener(new OnFailureListener() {
                @Override public void onFailure(@NonNull Exception e) { latch.countDown(); }
            });
            latch.await(3, TimeUnit.SECONDS);
            if (holder[0] != null) {
                rdao.updateLocation(r.id, holder[0].getLatitude(), holder[0].getLongitude(), System.currentTimeMillis());
            }
        } catch (Exception ignore) {}
        return Result.success();
    }
}
