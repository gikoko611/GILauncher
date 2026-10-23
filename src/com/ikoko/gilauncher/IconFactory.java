package com.ikoko.gilauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

final class IconFactory {
    private IconFactory() {}

    static Bitmap frame(Drawable src, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float d = size;
        RectF plate = new RectF(d * 0.06f, d * 0.04f, d * 0.94f, d * 0.90f);
        float r = d * 0.22f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        p.setColor(0x66000000);
        c.drawRoundRect(shift(plate, d * 0.045f), r, r, p);

        p.setShader(new LinearGradient(
                plate.left, plate.top, plate.right, plate.bottom,
                new int[]{0xFFFFE7A8, 0xFFE0B15A, 0xFF8A6420, 0xFF3A2A10},
                new float[]{0f, 0.36f, 0.72f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRoundRect(plate, r, r, p);

        RectF inner = new RectF(
                plate.left + d * 0.08f,
                plate.top + d * 0.08f,
                plate.right - d * 0.08f,
                plate.bottom - d * 0.09f);
        p.setShader(new LinearGradient(
                inner.left, inner.top, inner.left, inner.bottom,
                0xFF2A2418, 0xFF07060A, Shader.TileMode.CLAMP));
        c.drawRoundRect(inner, r * 0.72f, r * 0.72f, p);
        p.setShader(null);

        if (src != null) {
            int pad = Math.round(d * 0.07f);
            try {
                src.setBounds(
                        Math.round(inner.left) + pad,
                        Math.round(inner.top) + pad,
                        Math.round(inner.right) - pad,
                        Math.round(inner.bottom) - pad);
                src.draw(c);
            } catch (RuntimeException ignored) {
                // A broken app icon should not take down the home screen.
            }
        }

        p.setShader(new LinearGradient(
                plate.left, plate.top, plate.left, plate.top + d * 0.34f,
                0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(
                new RectF(plate.left + 2f, plate.top + 2f, plate.right - 2f, plate.top + d * 0.4f),
                r, r, p);
        return out;
    }

    static Bitmap letter(String label, int size) {
        Bitmap out = frame(null, size);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFE7A8);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(size * 0.32f);
        String ch = (label == null || label.length() == 0) ? "G" : label.substring(0, 1).toUpperCase();
        c.drawText(ch, size / 2f, size * 0.58f, p);
        return out;
    }

    private static RectF shift(RectF r, float dy) {
        return new RectF(r.left, r.top + dy, r.right, r.bottom + dy);
    }
}
