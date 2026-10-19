package com.notebookpaper;

import javax.imageio.ImageIO;
import java.awt.*;
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

        // Subtle paper grain (deterministic seed for reproducibility)
        Random rnd = new Random(42);
        int grainCount = widthPx * heightPx / 40;
        g2d.setColor(new Color(0, 0, 0));
        for (int i = 0; i < grainCount; i++) {
            int x = rnd.nextInt(widthPx);
            int y = rnd.nextInt(heightPx);
            int shade = rnd.nextInt(14); // 0..13 darker speckle
            g2d.setColor(new Color(shade, shade, shade));
            g2d.fillRect(x, y, 1, 1);
        }

        // Soft vignette around edges
        RadialGradientPaint vignette = new RadialGradientPaint(
                new Point2D.Float(widthPx / 2f, heightPx / 2f),
                Math.max(widthPx, heightPx) / 1.4f,
                new float[]{0f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 45)}
        );
        g2d.setPaint(vignette);
        g2d.fillRect(0, 0, widthPx, heightPx);
        g2d.setPaint(null);

        // Top margin area with a thicker header line
        int topMarginPx = Math.round((25.0f / MM_PER_INCH) * DPI);
        int headerLineY = topMarginPx - Math.round((4.0f / MM_PER_INCH) * DPI);
        g2d.setColor(new Color(120, 120, 140));
        g2d.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(0, headerLineY, widthPx, headerLineY);

        // Red margin line on the left
        int marginLeftPx = Math.round((20.0f / MM_PER_INCH) * DPI);
        g2d.setColor(new Color(200, 60, 60));
        g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(marginLeftPx, 0, marginLeftPx, heightPx);

        // Blue ruled lines with slight transparency and soft edges
        int lineSpacingPx = Math.round((8.0f / MM_PER_INCH) * DPI);
        g2d.setColor(new Color(150, 160, 220, 170)); // translucent blue
        g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int y = topMarginPx; y < heightPx; y += lineSpacingPx) {
            g2d.drawLine(0, y, widthPx, y);
        }

        // Binding holes on the left side (subtle gray with darker rim)
        int holeY1 = Math.round((30.0f / MM_PER_INCH) * DPI);
        int holeY2 = Math.round((220.0f / MM_PER_INCH) * DPI);
        int holeX = Math.round((8.0f / MM_PER_INCH) * DPI);
        int holeRadius = Math.round((3.0f / MM_PER_INCH) * DPI);
        g2d.setColor(new Color(150, 150, 150));
        for (int holeY : new int[]{holeY1, holeY2}) {
            g2d.fillOval(holeX - holeRadius, holeY - holeRadius, holeRadius * 2, holeRadius * 2);
        }
        g2d.setColor(new Color(110, 110, 110));
        g2d.setStroke(new BasicStroke(1.0f));
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