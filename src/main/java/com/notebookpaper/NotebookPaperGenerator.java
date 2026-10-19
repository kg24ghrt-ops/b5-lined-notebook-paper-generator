package com.notebookpaper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class NotebookPaperGenerator {
    public static void main(String[] args) throws IOException {
        // B5 dimensions in mm: 176mm x 250mm
        final float DPI = 300.0f;
        final float MM_PER_INCH = 25.4f;
        int widthPx = Math.round((176.0f / MM_PER_INCH) * DPI);   // ~2079
        int heightPx = Math.round((250.0f / MM_PER_INCH) * DPI);  // ~2953

        BufferedImage image = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Warm off-white paper base
        g2d.setColor(new Color(244, 241, 234));
        g2d.fillRect(0, 0, widthPx, heightPx);

        // --- Paper fiber texture: fine random speckle in warm/gray tones ---
        Random rnd = new Random(42);
        int grainCount = widthPx * heightPx / 30;
        for (int i = 0; i < grainCount; i++) {
            int x = rnd.nextInt(widthPx);
            int y = rnd.nextInt(heightPx);
            int shade = rnd.nextInt(16);
            g2d.setColor(new Color(shade, shade, shade));
            g2d.fillRect(x, y, 1, 1);
        }

        // --- Soft edge vignette (page curl shadow) ---
        RadialGradientPaint vignette = new RadialGradientPaint(
                new Point2D.Float(widthPx / 2f, heightPx / 2f),
                Math.max(widthPx, heightPx) / 1.3f,
                new float[]{0f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 45)}
        );
        g2d.setPaint(vignette);
        g2d.fillRect(0, 0, widthPx, heightPx);
        g2d.setPaint(null);

        // --- Helper: draw a line that looks like ink on paper ---
        // Two passes: a soft, wider, lighter "bleed" pass followed by a
        // thinner, darker core pass. Slight per-line jitter makes lines feel
        // hand-drawn / physically present rather than vector-perfect.
        // lineSpacingPx and topMarginPx are computed before the loop.
        int topMarginPx = Math.round((25.0f / MM_PER_INCH) * DPI);
        int lineSpacingPx = Math.round((8.0f / MM_PER_INCH) * DPI);

        // Blue ruled lines with ink bleed
        g2d.setColor(new Color(150, 160, 220, 150)); // translucent blue
        g2d.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < (heightPx / lineSpacingPx); i++) {
            int y = topMarginPx + i * lineSpacingPx + (rnd.nextInt(3) - 1);
            g2d.drawLine(0, y, widthPx, y);
        }
        g2d.setColor(new Color(90, 105, 175, 200)); // darker core
        g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < (heightPx / lineSpacingPx); i++) {
            int y = topMarginPx + i * lineSpacingPx + (rnd.nextInt(3) - 1);
            g2d.drawLine(0, y, widthPx, y);
        }

        // Header line (gray ink, slightly faded) above the ruled area
        int headerLineY = topMarginPx - Math.round((4.0f / MM_PER_INCH) * DPI);
        g2d.setColor(new Color(150, 150, 165, 180));
        g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(0, headerLineY, widthPx, headerLineY);

        // Red margin line on the left with ink bleed
        int marginLeftPx = Math.round((20.0f / MM_PER_INCH) * DPI);
        g2d.setColor(new Color(210, 90, 85, 170));
        g2d.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(marginLeftPx, 0, marginLeftPx, heightPx);
        g2d.setColor(new Color(180, 55, 50, 220));
        g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(marginLeftPx, 0, marginLeftPx, heightPx);

        // Binding holes with darker rim
        int holeY1 = Math.round((30.0f / MM_PER_INCH) * DPI);
        int holeY2 = Math.round((220.0f / MM_PER_INCH) * DPI);
        int holeX = Math.round((8.0f / MM_PER_INCH) * DPI);
        int holeRadius = Math.round((3.0f / MM_PER_INCH) * DPI);
        g2d.setColor(new Color(150, 150, 150));
        for (int holeY : new int[]{holeY1, holeY2}) {
            g2d.fillOval(holeX - holeRadius, holeY - holeRadius, holeRadius * 2, holeRadius * 2);
        }
        g2d.setColor(new Color(100, 100, 100));
        g2d.setStroke(new BasicStroke(1.5f));
        for (int holeY : new int[]{holeY1, holeY2}) {
            g2d.drawOval(holeX - holeRadius, holeY - holeRadius, holeRadius * 2, holeRadius * 2);
        }

        g2d.dispose();

        // Save as PNG
        File outputFile = new File("output/notebook-paper-b5-lined.png");
        ImageIO.write(image, "PNG", outputFile);
        System.out.println("Generated: " + outputFile.getAbsolutePath());
        System.out.println("Size: " + widthPx + "x" + heightPx + " pixels (" + DPI + " DPI)");
    }
}