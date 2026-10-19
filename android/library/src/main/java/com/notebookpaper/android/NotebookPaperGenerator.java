package com.notebookpaper.android;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * Generates a realistic B5 lined notebook paper bitmap using Android Canvas.
 * Drop-in replacement for com.notebookpaper.NotebookPaperGenerator.
 */
public final class NotebookPaperGenerator {

    // B5 dimensions in mm: 176 x 250
    private static final float DPI = 300.0f;
    private static final float MM_PER_INCH = 25.4f;

    private NotebookPaperGenerator() {
    }

    /**
     * Generates the notebook paper bitmap.
     *
     * @param widthPx  desired width in pixels
     * @param heightPx desired height in pixels
     * @return ARGB_8888 bitmap of the generated paper
     */
    public static android.graphics.Bitmap generate(int widthPx, int heightPx) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                widthPx, heightPx, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Warm off-white paper base
        paint.setColor(Color.argb(255, 244, 241, 234));
        canvas.drawRect(0, 0, widthPx, heightPx, paint);

        // Paper fiber texture
        Random rnd = new Random(42);
        int grainCount = widthPx * heightPx / 30;
        for (int i = 0; i < grainCount; i++) {
            int x = rnd.nextInt(widthPx);
            int y = rnd.nextInt(heightPx);
            int shade = rnd.nextInt(16);
            paint.setColor(Color.rgb(shade, shade, shade));
            canvas.drawPoint(x, y, paint);
        }

        // Soft vignette
        RadialGradient vignette = new RadialGradient(
                widthPx / 2f, heightPx / 2f, Math.max(widthPx, heightPx) / 1.3f,
                new int[]{Color.argb(0, 0, 0, 0), Color.argb(45, 0, 0, 0)},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP);
        paint.setShader(vignette);
        canvas.drawRect(0, 0, widthPx, heightPx, paint);
        paint.setShader(null);

        int topMarginPx = Math.round((25.0f / MM_PER_INCH) * DPI);
        int lineSpacingPx = Math.round((8.0f / MM_PER_INCH) * DPI);

        // Blue ruled lines with ink bleed
        paint.setColor(Color.argb(150, 150, 160, 220));
        paint.setStrokeWidth(2.5f);
        for (int i = 0; i < (heightPx / lineSpacingPx); i++) {
            float y = topMarginPx + i * lineSpacingPx + (rnd.nextInt(3) - 1);
            canvas.drawLine(0, y, widthPx, y, paint);
        }
        paint.setColor(Color.argb(200, 90, 105, 175));
        paint.setStrokeWidth(1.0f);
        for (int i = 0; i < (heightPx / lineSpacingPx); i++) {
            float y = topMarginPx + i * lineSpacingPx + (rnd.nextInt(3) - 1);
            canvas.drawLine(0, y, widthPx, y, paint);
        }

        // Header line
        int headerLineY = topMarginPx - Math.round((4.0f / MM_PER_INCH) * DPI);
        paint.setColor(Color.argb(180, 150, 150, 165));
        paint.setStrokeWidth(2.0f);
        canvas.drawLine(0, headerLineY, widthPx, headerLineY, paint);

        // Red margin line
        int marginLeftPx = Math.round((20.0f / MM_PER_INCH) * DPI);
        paint.setColor(Color.argb(170, 210, 90, 85));
        paint.setStrokeWidth(3.0f);
        canvas.drawLine(marginLeftPx, 0, marginLeftPx, heightPx, paint);
        paint.setColor(Color.argb(220, 180, 55, 50));
        paint.setStrokeWidth(1.0f);
        canvas.drawLine(marginLeftPx, 0, marginLeftPx, heightPx, paint);

        // Binding holes
        int holeY1 = Math.round((30.0f / MM_PER_INCH) * DPI);
        int holeY2 = Math.round((220.0f / MM_PER_INCH) * DPI);
        int holeX = Math.round((8.0f / MM_PER_INCH) * DPI);
        int holeRadius = Math.round((3.0f / MM_PER_INCH) * DPI);
        paint.setColor(Color.argb(255, 150, 150, 150));
        canvas.drawCircle(holeX, holeY1, holeRadius, paint);
        canvas.drawCircle(holeX, holeY2, holeRadius, paint);
        paint.setColor(Color.argb(255, 100, 100, 100));
        paint.setStrokeWidth(1.5f);
        canvas.drawCircle(holeX, holeY1, holeRadius, paint);
        canvas.drawCircle(holeX, holeY2, holeRadius, paint);

        return bitmap;
    }
}