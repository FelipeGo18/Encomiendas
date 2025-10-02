package com.hfad.encomiendas.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

public class IconUtils {
    public static BitmapDescriptor bitmapFromVector(@NonNull Context ctx, @DrawableRes int resId){
        try {
            Drawable d = ContextCompat.getDrawable(ctx, resId);
            if (d == null) return BitmapDescriptorFactory.defaultMarker();
            int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 64;
            int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 64;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0,0,w,h);
            d.draw(c);
            return BitmapDescriptorFactory.fromBitmap(bmp);
        } catch (Exception e){
            return BitmapDescriptorFactory.defaultMarker();
        }
    }
}

