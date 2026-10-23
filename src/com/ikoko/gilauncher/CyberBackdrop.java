package com.ikoko.gilauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Static obsidian-and-gold circuit field. Drawn once so Redmi 9A stays smooth. */
public final class CyberBackdrop extends View {
    private Bitmap cache;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CyberBackdrop(Context context) {
        super(context);
    }

    public CyberBackdrop(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w < 2 || h < 2) return;
        if (cache != null && !cache.isRecycled()) cache.recycle();
        cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(cache);
        c.drawColor(0xFF07060A);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(
                w * 0.5f, h * 0.36f, Math.max(w, h) * 0.55f,
                0x55E0B15A, 0x0007060A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        paint.setColor(0x10FFE7A8);
        paint.setStrokeWidth(1f);
        for (int y = 0; y < h; y += 5) {
            c.drawLine(0, y, w, y, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, w * 0.0035f));
        paint.setColor(0x99E0B15A);
        float[][] lines = new float[][]{
                {0f, 0.15f, 0.24f, 0.15f, 0.32f, 0.21f, 0.66f, 0.21f},
                {1f, 0.24f, 0.74f, 0.24f, 0.66f, 0.30f, 0.40f, 0.30f},
                {0.04f, 0.58f, 0.30f, 0.58f, 0.38f, 0.64f, 0.72f, 0.64f},
                {1f, 0.72f, 0.70f, 0.72f, 0.62f, 0.66f, 0.28f, 0.66f},
                {0f, 0.86f, 0.22f, 0.86f, 0.30f, 0.80f, 0.58f, 0.80f}
        };
        for (int i = 0; i < lines.length; i++) {
            float[] m = lines[i];
            c.drawLine(m[0] * w, m[1] * h, m[2] * w, m[3] * h, paint);
            c.drawLine(m[2] * w, m[3] * h, m[4] * w, m[5] * h, paint);
            c.drawLine(m[4] * w, m[5] * h, m[6] * w, m[7] * h, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFE7A8);
        float dot = Math.max(3f, w * 0.008f);
        c.drawCircle(0.32f * w, 0.21f * h, dot, paint);
        c.drawCircle(0.66f * w, 0.30f * h, dot, paint);
        c.drawCircle(0.38f * w, 0.64f * h, dot, paint);
        c.drawCircle(0.62f * w, 0.66f * h, dot, paint);
        c.drawCircle(0.30f * w, 0.80f * h, dot, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xB3E0B15A);
        paint.setStrokeWidth(Math.max(2f, w * 0.004f));
        float mgn = w * 0.035f;
        float len = w * 0.055f;
        c.drawLines(new float[]{
                mgn, mgn, mgn + len, mgn,
                mgn, mgn, mgn, mgn + len,
                w - mgn, mgn, w - mgn - len, mgn,
                w - mgn, mgn, w - mgn, mgn + len,
                mgn, h - mgn, mgn + len, h - mgn,
                mgn, h - mgn, mgn, h - mgn - len,
                w - mgn, h - mgn, w - mgn - len, h - mgn,
                w - mgn, h - mgn, w - mgn, h - mgn - len
        }, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (cache != null && !cache.isRecycled()) {
            canvas.drawBitmap(cache, 0, 0, null);
        } else {
            canvas.drawColor(0xFF07060A);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (cache != null && !cache.isRecycled()) cache.recycle();
        cache = null;
        super.onDetachedFromWindow();
    }
}
