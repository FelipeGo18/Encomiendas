package com.hfad.encomiendas.ui.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

public class SignatureView extends View {
    private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Bitmap layerBitmap;
    private Canvas layerCanvas;

    private boolean disallowParentIntercept = true;
    private boolean locked = false;
    private boolean hasInk = false;

    public SignatureView(Context ctx) { super(ctx); init(); }
    public SignatureView(Context ctx, @Nullable AttributeSet attrs) { super(ctx, attrs); init(); }

    private void init() {
        setBackgroundColor(Color.WHITE);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        paint.setColor(Color.BLACK);
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        paint.setStrokeWidth(6f);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            layerBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            layerCanvas = new Canvas(layerBitmap);
            layerCanvas.drawColor(Color.WHITE);
        }
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (layerBitmap != null) c.drawBitmap(layerBitmap, 0, 0, null);
        c.drawPath(path, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (locked) return false;

        if (disallowParentIntercept && getParent() != null) {
            boolean active = e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE;
            getParent().requestDisallowInterceptTouchEvent(active);
        }

        float x = e.getX(), y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                path.moveTo(x, y);
                hasInk = true;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                path.lineTo(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (layerCanvas != null) {
                    layerCanvas.drawPath(path, paint);
                }
                path.reset();
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    /** Limpia la firma (si no está bloqueada). */
    public void clear() {
        if (locked) return;
        if (layerCanvas != null) {
            layerCanvas.drawColor(Color.WHITE);
        }
        path.reset();
        hasInk = false;
        invalidate();
    }

    /** True si no hay trazos dibujados desde el último clear. */
    public boolean isEmpty() {
        return !hasInk && layerBitmapIsBlank();
    }

    private boolean layerBitmapIsBlank() {
        // comprobación ligera: revisa un rectángulo central buscando un pixel distinto a blanco
        if (layerBitmap == null) return true;
        int w = layerBitmap.getWidth(), h = layerBitmap.getHeight();
        int left = w/4, top = h/4, right = (w*3)/4, bottom = (h*3)/4;
        for (int j = top; j < bottom; j+=8) {
            for (int i = left; i < right; i+=8) {
                if (layerBitmap.getPixel(i, j) != Color.WHITE) return false;
            }
        }
        return true;
    }

    /** Devuelve la imagen firmada; si no hay firma, devuelve null. */
    @Nullable
    public Bitmap exportBitmap() {
        if (isEmpty()) return null;
        if (!path.isEmpty() && layerCanvas != null) {
            layerCanvas.drawPath(path, paint);
            path.reset();
        }
        return layerBitmap == null ? null : layerBitmap.copy(Bitmap.Config.ARGB_8888, false);
    }

    /** Bloquea/Desbloquea la edición. */
    public void setLocked(boolean locked) {
        this.locked = locked;
        setAlpha(locked ? 0.9f : 1f);
    }

    /** Opcional: ancho del trazo. */
    public void setStrokeWidth(float w) { paint.setStrokeWidth(w); invalidate(); }

    /** Opcional: color del trazo. */
    public void setPenColor(@ColorInt int color) { paint.setColor(color); invalidate(); }

    public void setDisallowParentIntercept(boolean disallow) { this.disallowParentIntercept = disallow; }

    /** Dibuja una firma ya guardada (Base64 PNG) y la deja bloqueada. */
    public void showFromBase64(String b64) {
        if (layerCanvas == null || b64 == null || b64.trim().isEmpty()) return;
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) return;
            layerCanvas.drawColor(Color.WHITE);

            // Ajuste al centro manteniendo proporción
            float vw = getWidth(), vh = getHeight();
            float bw = bmp.getWidth(), bh = bmp.getHeight();
            if (vw <= 0 || vh <= 0) {
                // aún sin medir: póstalo para después
                post(() -> showFromBase64(b64));
                return;
            }
            float scale = Math.min(vw / bw, vh / bh);
            float dw = bw * scale, dh = bh * scale;
            float left = (vw - dw) / 2f, top = (vh - dh) / 2f;

            RectF dst = new RectF(left, top, left + dw, top + dh);
            layerCanvas.drawBitmap(bmp, null, dst, null);
            hasInk = true;
            invalidate();
        } catch (Exception ignored) { }
    }
}
