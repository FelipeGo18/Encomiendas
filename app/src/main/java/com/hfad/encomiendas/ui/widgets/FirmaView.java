package com.hfad.encomiendas.ui.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class FirmaView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private boolean empty = true;

    public FirmaView(Context c) { super(c); init(); }
    public FirmaView(Context c, AttributeSet a) { super(c, a); init(); }
    public FirmaView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                path.moveTo(e.getX(), e.getY());
                empty = false;
                break;
            case MotionEvent.ACTION_MOVE:
                path.lineTo(e.getX(), e.getY());
                break;
        }
        invalidate();
        return true;
    }

    public void clear() {
        path.reset();
        empty = true;
        invalidate();
    }

    public boolean isEmpty() { return empty; }

    public Bitmap exportBitmap() {
        Bitmap bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);
        c.drawPath(path, paint);
        return bmp;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.4f);
    }
}
