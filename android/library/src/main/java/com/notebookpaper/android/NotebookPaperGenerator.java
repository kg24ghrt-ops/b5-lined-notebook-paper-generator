package com.notebookpaper.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a realistic B5 lined notebook paper bitmap using Android Canvas.
 * Fully Android-compatible (no java.awt dependency).
 * <p>
 * Default generation produces paper with two ring-binder holes ("buckles").
 * Use {@link #generate(int, int, int)} to request a different number of holes.
 */
public final class NotebookPaperGenerator {

    // B5 dimensions in mm: 176 x 250
    private static final float B5_WIDTH_MM = 176.0f;
    private static final float B5_HEIGHT_MM = 250.0f;

    // Notebook measurements in mm (relative to the B5 page)
    private static final float TOP_MARGIN_MM = 25.0f;
    private static final float HEADER_GAP_MM = 4.0f;
    private static final float LINE_SPACING_MM = 8.0f;
    private static final float LEFT_MARGIN_MM = 20.0f;
    private static final float HOLE_X_MM = 8.0f;
    private static final float HOLE_RADIUS_MM = 3.0f;
    private static final float FIRST_HOLE_Y_MM = 30.0f;
    private static final float LAST_HOLE_Y_MM = 220.0f;

    // Default number of ring-binder holes ("buckles")
    public static final int DEFAULT_BUCKLE_COUNT = 2;

    /**
     * Horizontal text position relative to the red margin line.
     */
    public enum HorizontalPosition {
        LEFT_OF_MARGIN,
        RIGHT_OF_MARGIN,
        SPAN_FULL
    }

    /**
     * A single line of typed text on the notebook paper.
     */
    public static final class TypedLine {
        public final int lineIndex;
        public final String text;
        public final HorizontalPosition position;

        public TypedLine(int lineIndex, String text) {
            this(lineIndex, text, HorizontalPosition.RIGHT_OF_MARGIN);
        }

        public TypedLine(int lineIndex, String text, HorizontalPosition position) {
            this.lineIndex = lineIndex;
            this.text = text;
            this.position = position;
        }

        public static TypedLine leftOfMargin(int lineIndex, String text) {
            return new TypedLine(lineIndex, text, HorizontalPosition.LEFT_OF_MARGIN);
        }

        public static TypedLine rightOfMargin(int lineIndex, String text) {
            return new TypedLine(lineIndex, text, HorizontalPosition.RIGHT_OF_MARGIN);
        }

        public static TypedLine spanFull(int lineIndex, String text) {
            return new TypedLine(lineIndex, text, HorizontalPosition.SPAN_FULL);
        }
    }

    /**
     * Top margin in pixels for a bitmap of the given height.
     */
    public static int topMarginPx(int heightPx) {
        return Math.round(TOP_MARGIN_MM * (heightPx / B5_HEIGHT_MM));
    }

    /**
     * Ruled line spacing in pixels for a bitmap of the given height.
     */
    public static int lineSpacingPx(int heightPx) {
        return Math.round(LINE_SPACING_MM * (heightPx / B5_HEIGHT_MM));
    }

    /**
     * Left margin line position in pixels for a bitmap of the given width.
     */
    public static int marginLeftPx(int widthPx) {
        return Math.round(LEFT_MARGIN_MM * (widthPx / B5_WIDTH_MM));
    }

    private NotebookPaperGenerator() {
    }

    /**
     * Generates the notebook paper bitmap with the default number of buckles.
     *
     * @param widthPx  desired width in pixels
     * @param heightPx desired height in pixels
     * @return ARGB_8888 bitmap of the generated paper
     */
    public static Bitmap generate(int widthPx, int heightPx) {
        return generate(widthPx, heightPx, DEFAULT_BUCKLE_COUNT);
    }

    /**
     * Generates the notebook paper bitmap.
     * <p>
     * All notebook measurements are scaled proportionally to the actual bitmap
     * dimensions (using B5 proportions as the reference), so the layout stays
     * correct regardless of the bitmap size.
     *
     * @param widthPx     desired width in pixels
     * @param heightPx    desired height in pixels
     * @param buckleCount number of ring-binder holes along the left margin (>= 0)
     * @return ARGB_8888 bitmap of the generated paper
     */
    public static Bitmap generate(int widthPx, int heightPx, int buckleCount) {
        return generate(widthPx, heightPx, buckleCount, (List<TypedLine>) null);
    }

    /**
     * Generates the notebook paper bitmap with typed lines.
     *
     * @param widthPx     desired width in pixels
     * @param heightPx    desired height in pixels
     * @param buckleCount number of ring-binder holes along the left margin (>= 0)
     * @param typedLines  list of typed lines (null or empty for no text)
     * @return ARGB_8888 bitmap of the generated paper
     */
    public static Bitmap generate(int widthPx, int heightPx, int buckleCount, List<TypedLine> typedLines) {
        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float sx = widthPx / B5_WIDTH_MM;
        float sy = heightPx / B5_HEIGHT_MM;

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        drawPaperBase(canvas, paint, widthPx, heightPx);
        drawPaperTexture(canvas, paint, widthPx, heightPx);
        drawVignette(canvas, paint, widthPx, heightPx);

        int topMarginPx = Math.round(TOP_MARGIN_MM * sy);
        int lineSpacingPx = Math.round(LINE_SPACING_MM * sy);

        drawRuledLines(canvas, paint, widthPx, heightPx, topMarginPx, lineSpacingPx);
        drawHeaderLine(canvas, paint, widthPx, topMarginPx, sy);
        drawMarginLine(canvas, paint, heightPx, sx);
        drawBindingHoles(canvas, paint, widthPx, heightPx, buckleCount);

        if (typedLines != null && !typedLines.isEmpty()) {
            drawTypedLines(canvas, paint, widthPx, heightPx, topMarginPx, lineSpacingPx, sx, sy, typedLines);
        }

        return bitmap;
    }

    /**
     * Legacy API using SparseArray (line index -> text).
     * Text is positioned RIGHT_OF_MARGIN by default.
     */
    public static Bitmap generate(int widthPx, int heightPx, int buckleCount, SparseArray<String> lines) {
        if (lines == null || lines.size() == 0) return generate(widthPx, heightPx, buckleCount);
        List<TypedLine> typedLines = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            typedLines.add(new TypedLine(lines.keyAt(i), lines.valueAt(i)));
        }
        return generate(widthPx, heightPx, buckleCount, typedLines);
    }

    /**
     * Legacy convenience: renders text starting at ruled line 0, one line per array entry.
     */
    public static Bitmap generate(int widthPx, int heightPx, int buckleCount, String[] lines) {
        if (lines == null || lines.length == 0) return generate(widthPx, heightPx, buckleCount);
        List<TypedLine> typedLines = new ArrayList<>(lines.length);
        for (int i = 0; i < lines.length; i++) {
            typedLines.add(new TypedLine(i, lines[i]));
        }
        return generate(widthPx, heightPx, buckleCount, typedLines);
    }

    private static void drawPaperBase(Canvas canvas, Paint paint, int w, int h) {
        paint.setColor(Color.argb(255, 244, 241, 234));
        canvas.drawRect(0, 0, w, h, paint);
    }

    private static void drawPaperTexture(Canvas canvas, Paint paint, int w, int h) {
        int tileSize = 128;
        Bitmap tile = createTextureTile(tileSize);
        android.graphics.Rect src = new android.graphics.Rect();
        android.graphics.RectF dst = new android.graphics.RectF();
        for (int y = 0; y < h; y += tileSize) {
            for (int x = 0; x < w; x += tileSize) {
                int tw = Math.min(tileSize, w - x);
                int th = Math.min(tileSize, h - y);
                src.set(0, 0, tw, th);
                dst.set(x, y, x + tw, y + th);
                canvas.drawBitmap(tile, src, dst, paint);
            }
        }
    }

    private static Bitmap createTextureTile(int size) {
        Bitmap tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tile);
        Paint p = new Paint();
        Random rnd = new Random(42);
        int grainCount = size * size / 8;
        for (int i = 0; i < grainCount; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            int shade = rnd.nextInt(12);
            p.setColor(Color.argb(rnd.nextInt(40) + 10, shade, shade, shade));
            c.drawPoint(x, y, p);
        }
        return tile;
    }

    private static void drawVignette(Canvas canvas, Paint paint, int w, int h) {
        RadialGradient vignette = new RadialGradient(
                w / 2f, h / 2f, Math.max(w, h) / 1.3f,
                new int[]{Color.argb(0, 0, 0, 0), Color.argb(20, 0, 0, 0), Color.argb(55, 0, 0, 0)},
                new float[]{0f, 0.7f, 1f},
                Shader.TileMode.CLAMP);
        paint.setShader(vignette);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    private static void drawRuledLines(Canvas canvas, Paint paint, int w, int h, int topMarginPx, int lineSpacingPx) {
        Random rnd = new Random(42);
        int lineCount = h / lineSpacingPx;

        paint.setColor(Color.argb(150, 150, 160, 220));
        paint.setStrokeWidth(2.5f);
        for (int i = 0; i < lineCount; i++) {
            float y = topMarginPx + i * lineSpacingPx + (rnd.nextInt(3) - 1);
            canvas.drawLine(0, y, w, y, paint);
        }
        paint.setColor(Color.argb(200, 90, 105, 175));
        paint.setStrokeWidth(1.0f);
        for (int i = 0; i < lineCount; i++) {
            float y = topMarginPx + i * lineSpacingPx + (rnd.nextInt(3) - 1);
            canvas.drawLine(0, y, w, y, paint);
        }
    }

    private static void drawHeaderLine(Canvas canvas, Paint paint, int w, int topMarginPx, float sy) {
        int headerLineY = topMarginPx - Math.round(HEADER_GAP_MM * sy);
        paint.setColor(Color.argb(180, 150, 150, 165));
        paint.setStrokeWidth(2.0f);
        canvas.drawLine(0, headerLineY, w, headerLineY, paint);
    }

    private static void drawMarginLine(Canvas canvas, Paint paint, int h, float sx) {
        int marginLeftPx = Math.round(LEFT_MARGIN_MM * sx);
        paint.setColor(Color.argb(170, 210, 90, 85));
        paint.setStrokeWidth(3.0f);
        canvas.drawLine(marginLeftPx, 0, marginLeftPx, h, paint);
        paint.setColor(Color.argb(220, 180, 55, 50));
        paint.setStrokeWidth(1.0f);
        canvas.drawLine(marginLeftPx, 0, marginLeftPx, h, paint);
    }

    private static void drawTypedLines(Canvas canvas, Paint paint, int w, int h,
                                        int topMarginPx, int lineSpacingPx, float sx, float sy,
                                        List<TypedLine> typedLines) {
        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setSubpixelText(true);
        textPaint.setColor(Color.argb(220, 40, 40, 60));
        textPaint.setTextSize(Math.max(12f, 4f * sy));
        textPaint.setTypeface(MyanmarSupport.resolveTypeface());

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        int descent = (int) Math.ceil(fm.descent);
        Random rnd = new Random(42);

        for (TypedLine tl : typedLines) {
            if (tl.text == null || tl.text.isEmpty()) continue;

            float lineY = topMarginPx + tl.lineIndex * lineSpacingPx;
            float baselineY = lineY + descent / 2f;

            float x;
            switch (tl.position) {
                case LEFT_OF_MARGIN:
                    x = 3f * sx;
                    break;
                case RIGHT_OF_MARGIN:
                    int marginLeftPx = Math.round(LEFT_MARGIN_MM * sx);
                    x = marginLeftPx + 4f * sx;
                    break;
                case SPAN_FULL:
                    float textWidth = textPaint.measureText(tl.text);
                    x = (w - textWidth) / 2f;
                    break;
                default:
                    int marginLeftPx2 = Math.round(LEFT_MARGIN_MM * sx);
                    x = marginLeftPx2 + 4f * sx;
                    break;
            }

            drawHandwrittenText(canvas, textPaint, tl.text, x, baselineY, rnd);
        }
    }

    private static void drawHandwrittenText(Canvas canvas, Paint textPaint, String text, float x, float baselineY, Random rnd) {
        List<String> clusters = MyanmarSupport.graphemeClusters(text);
        float charX = x;

        for (String cluster : clusters) {
            if (cluster.isEmpty()) continue;

            boolean isSpace = cluster.trim().isEmpty();
            if (isSpace) {
                charX += textPaint.measureText(cluster);
                continue;
            }

            float jitterX = (rnd.nextFloat() - 0.5f) * 0.3f;
            float jitterY = (rnd.nextFloat() - 0.5f) * 0.2f;
            float rotation = (rnd.nextFloat() - 0.5f) * 0.005f;

            int alphaVariation = rnd.nextInt(30) - 15;
            int alpha = Math.max(160, Math.min(240, 210 + alphaVariation));
            textPaint.setAlpha(alpha);

            canvas.save();
            canvas.translate(charX + jitterX, baselineY + jitterY);
            canvas.rotate(rotation * 57.29578f); // radians to degrees
            canvas.drawText(cluster, 0, 0, textPaint);
            canvas.restore();

            charX += textPaint.measureText(cluster) + (rnd.nextFloat() - 0.5f) * 0.5f;
        }
    }

    private static void drawBindingHoles(Canvas canvas, Paint paint, int w, int h, int buckleCount) {
        int holeX = Math.round(HOLE_X_MM * (w / B5_WIDTH_MM));
        int holeRadius = Math.round(HOLE_RADIUS_MM * (w / B5_WIDTH_MM));
        int firstHoleY = Math.round(FIRST_HOLE_Y_MM * (h / B5_HEIGHT_MM));
        int lastHoleY = Math.round(LAST_HOLE_Y_MM * (h / B5_HEIGHT_MM));
        if (buckleCount <= 1) {
            addBuckle(canvas, paint, holeX, firstHoleY, holeRadius);
        } else {
            float span = lastHoleY - firstHoleY;
            for (int i = 0; i < buckleCount; i++) {
                float fraction = (buckleCount == 1) ? 0f : (i / (float) (buckleCount - 1));
                int holeY = Math.round(firstHoleY + span * fraction);
                addBuckle(canvas, paint, holeX, holeY, holeRadius);
            }
        }
    }

    private static void addBuckle(Canvas canvas, Paint paint, int cx, int cy, int radius) {
        paint.setColor(Color.argb(255, 150, 150, 150));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setColor(Color.argb(255, 100, 100, 100));
        paint.setStrokeWidth(1.5f);
        canvas.drawCircle(cx, cy, radius, paint);
    }
}