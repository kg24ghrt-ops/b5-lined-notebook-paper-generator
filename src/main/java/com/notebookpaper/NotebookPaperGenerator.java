package com.notebookpaper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class NotebookPaperGenerator {
    public static void main(String[] args) throws IOException {
        // B5 dimensions in mm: 176mm x 250mm
        // Convert to pixels at 300 DPI: 1 inch = 25.4mm, 300 DPI = 300 pixels/inch
        final float DPI = 300.0f;
        final float MM_PER_INCH = 25.4f;
        int widthPx = Math.round((176.0f / MM_PER_INCH) * DPI);  // ~2079
        int heightPx = Math.round((250.0f / MM_PER_INCH) * DPI); // ~2953

        BufferedImage image = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = (Graphics2D) image.getGraphics();

        // Set rendering hints for smoother lines
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Fill background with white
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, widthPx, heightPx);

        // Draw red margin line on the left (typical notebook paper)
        int marginLeftPx = Math.round((20.0f / MM_PER_INCH) * DPI); // 20mm margin
        g2d.setColor(new Color(255, 100, 100)); // Light red
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawLine(marginLeftPx, 0, marginLeftPx, heightPx);

        // Draw blue ruled lines (typical notebook paper)
        int lineSpacingPx = Math.round((8.0f / MM_PER_INCH) * DPI); // 8mm line spacing (common)
        int topMarginPx = Math.round((25.0f / MM_PER_INCH) * DPI);  // 25mm top margin
        g2d.setColor(new Color(150, 150, 255)); // Light blue
        g2d.setStroke(new BasicStroke(1.0f));

        // Draw horizontal ruled lines
        for (int y = topMarginPx; y < heightPx; y += lineSpacingPx) {
            g2d.drawLine(0, y, widthPx, y);
        }

        // Optionally draw some holes on the left side (for ring binder) - subtle
        int holeY1 = Math.round((30.0f / MM_PER_INCH) * DPI);
        int holeY2 = Math.round((220.0f / MM_PER_INCH) * DPI);
        int holeX = Math.round((8.0f / MM_PER_INCH) * DPI);
        int holeRadius = Math.round((3.0f / MM_PER_INCH) * DPI);
        g2d.setColor(new Color(200, 200, 200));
        g2d.fillOval(holeX - holeRadius, holeY1 - holeRadius, holeRadius * 2, holeRadius * 2);
        g2d.fillOval(holeX - holeRadius, holeY2 - holeRadius, holeRadius * 2, holeRadius * 2);

        g2d.dispose();

        // Save as PNG
        File outputFile = new File("output/notebook-paper-b5-lined.png");
        ImageIO.write(image, "PNG", outputFile);
        System.out.println("Generated: " + outputFile.getAbsolutePath());
        System.out.println("Size: " + widthPx + "x" + heightPx + " pixels (" + DPI + " DPI)");
    }
}
