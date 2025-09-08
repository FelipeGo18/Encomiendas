package com.hfad.encomiendas.ui.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;

public class SignatureView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path   = new Path();
    private boolean empty = true;

    public SignatureView(Context c) { super(c); init(); }
    public SignatureView(Context c, @Nullable AttributeSet a) { super(c, a); init(); }
    public SignatureView(Context c, @Nullable AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(6f);
        paint.setColor(0xFF111111);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Si está deshabilitado, ignorar
        if (!isEnabled()) return false;

        // Evitar que el padre (scroll) robe el touch
        getParent().requestDisallowInterceptTouchEvent(true);

        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                path.moveTo(x, y);
                empty = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                path.lineTo(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    public void clear() {
        path.reset();
        empty = true;
        invalidate();
    }

    public boolean isEmpty() { return empty; }

    public String getBitmapBase64() {
        Bitmap bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        draw(c);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
        byte[] bytes = bos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}