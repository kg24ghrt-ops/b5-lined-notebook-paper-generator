package com.notebookpaper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class NotebookPaperGenerator {

    private static final float B5_WIDTH_MM = 176.0f;
    private static final float B5_HEIGHT_MM = 250.0f;
    private static final float DPI = 300.0f;
    private static final float MM_PER_INCH = 25.4f;

    private static final float TOP_MARGIN_MM = 25.0f;
    private static final float HEADER_GAP_MM = 4.0f;
    private static final float LINE_SPACING_MM = 8.0f;
    private static final float LEFT_MARGIN_MM = 20.0f;
    private static final float HOLE_X_MM = 8.0f;
    private static final float HOLE_RADIUS_MM = 3.0f;
    private static final float FIRST_HOLE_Y_MM = 30.0f;
    private static final float LAST_HOLE_Y_MM = 220.0f;
    private static final int DEFAULT_HOLE_COUNT = 2;

    public static void main(String[] args) throws IOException {
        int widthPx = mmToPx(B5_WIDTH_MM);
        int heightPx = mmToPx(B5_HEIGHT_MM);

        BufferedImage image = generate(widthPx, heightPx, args);

        File outputFile = new File("output/notebook-paper-b5-lined.png");
        outputFile.getParentFile().mkdirs();
        ImageIO.write(image, "PNG", outputFile);
        System.out.println("Generated: " + outputFile.getAbsolutePath());
        System.out.println("Size: " + widthPx + "x" + heightPx + " pixels (" + DPI + " DPI)");
    }

    public static BufferedImage generate(int widthPx, int heightPx, String[] typedLines) {
        BufferedImage image = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = createGraphics(image);

        drawPaperBase(g2d, widthPx, heightPx);
        drawPaperTexture(g2d, widthPx, heightPx);
        drawVignette(g2d, widthPx, heightPx);

        int topMarginPx = mmToPx(TOP_MARGIN_MM, heightPx, B5_HEIGHT_MM);
        int lineSpacingPx = mmToPx(LINE_SPACING_MM, heightPx, B5_HEIGHT_MM);
        int marginLeftPx = mmToPx(LEFT_MARGIN_MM, widthPx, B5_WIDTH_MM);

        drawRuledLines(g2d, widthPx, heightPx, topMarginPx, lineSpacingPx);
        drawHeaderLine(g2d, widthPx, topMarginPx, heightPx);
        drawMarginLine(g2d, heightPx, marginLeftPx);
        drawTypedText(g2d, typedLines, widthPx, heightPx, topMarginPx, lineSpacingPx, marginLeftPx);
        drawBindingHoles(g2d, widthPx, heightPx);

        g2d.dispose();
        return image;
    }

    private static Graphics2D createGraphics(BufferedImage image) {
        Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2d;
    }

    private static void drawPaperBase(Graphics2D g2d, int w, int h) {
        g2d.setColor(new Color(244, 241, 234));
        g2d.fillRect(0, 0, w, h);
    }

    private static void drawPaperTexture(Graphics2D g2d, int w, int h) {
        int tileSize = 128;
        BufferedImage tile = createTextureTile(tileSize);
        for (int y = 0; y < h; y += tileSize) {
            for (int x = 0; x < w; x += tileSize) {
                int tw = Math.min(tileSize, w - x);
                int th = Math.min(tileSize, h - y);
                g2d.drawImage(tile, x, y, x + tw, y + th, 0, 0, tw, th, null);
            }
        }
    }

    private static BufferedImage createTextureTile(int size) {
        BufferedImage tile = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tile.createGraphics();
        Random rnd = new Random(42);
        int grainCount = size * size / 8;
        for (int i = 0; i < grainCount; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            int shade = rnd.nextInt(12);
            g.setColor(new Color(shade, shade, shade, rnd.nextInt(40) + 10));
            g.fillRect(x, y, 1, 1);
        }
        g.dispose();
        return tile;
    }

    private static void drawVignette(Graphics2D g2d, int w, int h) {
        RadialGradientPaint vignette = new RadialGradientPaint(
                new Point2D.Float(w / 2f, h / 2f),
                Math.max(w, h) / 1.3f,
                new float[]{0f, 0.7f, 1f},
                new Color[]{
                        new Color(0, 0, 0, 0),
                        new Color(0, 0, 0, 20),
                        new Color(0, 0, 0, 55)
                }
        );
        g2d.setPaint(vignette);
        g2d.fillRect(0, 0, w, h);
        g2d.setPaint(null);
    }

    private static void drawRuledLines(Graphics2D g2d, int w, int h, int topMarginPx, int lineSpacingPx) {
        Random rnd = new Random(42);
        int lineCount = h / lineSpacingPx;

        g2d.setColor(new Color(150, 160, 220, 140));
        g2d.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < lineCount; i++) {
            int y = topMarginPx + i * lineSpacingPx + (rnd.nextInt(3) - 1);
            g2d.drawLine(0, y, w, y);
        }
        g2d.setColor(new Color(90, 105, 175, 200));
        g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < lineCount; i++) {
            int y = topMarginPx + i * lineSpacingPx + (rnd.nextInt(3) - 1);
            g2d.drawLine(0, y, w, y);
        }
    }

    private static void drawHeaderLine(Graphics2D g2d, int w, int topMarginPx, int h) {
        int headerLineY = topMarginPx - mmToPx(HEADER_GAP_MM, h, B5_HEIGHT_MM);
        g2d.setColor(new Color(150, 150, 165, 180));
        g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(0, headerLineY, w, headerLineY);
    }

    private static void drawMarginLine(Graphics2D g2d, int h, int marginLeftPx) {
        g2d.setColor(new Color(210, 90, 85, 170));
        g2d.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(marginLeftPx, 0, marginLeftPx, h);
        g2d.setColor(new Color(180, 55, 50, 220));
        g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(marginLeftPx, 0, marginLeftPx, h);
    }

    private static void drawTypedText(Graphics2D g2d, String[] lines, int w, int h,
                                      int topMarginPx, int lineSpacingPx, int marginLeftPx) {
        if (lines == null || lines.length == 0) return;
        g2d.setFont(new Font("Serif", Font.PLAIN, mmToPx(4.0f, h, B5_HEIGHT_MM)));
        g2d.setColor(new Color(40, 40, 60, 210));
        for (int i = 0; i < lines.length; i++) {
            int y = topMarginPx + i * lineSpacingPx - mmToPx(1.0f, h, B5_HEIGHT_MM);
            g2d.drawString(lines[i], marginLeftPx + mmToPx(4.0f, w, B5_WIDTH_MM), y);
        }
    }

    private static void drawBindingHoles(Graphics2D g2d, int w, int h) {
        int holeX = mmToPx(HOLE_X_MM, w, B5_WIDTH_MM);
        int holeRadius = mmToPx(HOLE_RADIUS_MM, w, B5_WIDTH_MM);
        int holeY1 = mmToPx(FIRST_HOLE_Y_MM, h, B5_HEIGHT_MM);
        int holeY2 = mmToPx(LAST_HOLE_Y_MM, h, B5_HEIGHT_MM);

        g2d.setColor(new Color(150, 150, 150));
        for (int holeY : new int[]{holeY1, holeY2}) {
            g2d.fillOval(holeX - holeRadius, holeY - holeRadius, holeRadius * 2, holeRadius * 2);
        }
        g2d.setColor(new Color(100, 100, 100));
        g2d.setStroke(new BasicStroke(1.5f));
        for (int holeY : new int[]{holeY1, holeY2}) {
            g2d.drawOval(holeX - holeRadius, holeY - holeRadius, holeRadius * 2, holeRadius * 2);
        }
    }

    private static int mmToPx(float mm) {
        return Math.round((mm / MM_PER_INCH) * DPI);
    }

    private static int mmToPx(float mm, int dimensionPx, float b5DimensionMm) {
        float scale = dimensionPx / b5DimensionMm;
        return Math.round(mm * scale);
    }
}