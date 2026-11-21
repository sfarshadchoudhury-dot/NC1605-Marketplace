package com.marketplace.products.service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Draws simple coloured placeholder pictures for the sample products, entirely on this computer
 * (no downloads, no copyrighted photos). Used by the seed command.
 */
public final class PlaceholderImageGenerator {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    // Background colours as 0xRRGGBB numbers; colourIndex picks one (wrapping around the array).
    private static final int[] PALETTE = {
            0x4F46E5, 0x0EA5E9, 0x10B981, 0xF59E0B, 0xEF4444, 0x8B5CF6, 0xEC4899, 0x14B8A6
    };

    private PlaceholderImageGenerator() {
    }

    /** Writes a PNG into the folder and returns its path. */
    public static Path generate(Path folder, String fileName, String title, String caption, int colourIndex)
            throws IOException {
        Color base = new Color(PALETTE[Math.floorMod(colourIndex, PALETTE.length)]);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setPaint(new GradientPaint(0, 0, base, WIDTH, HEIGHT, base.darker().darker()));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            g.setColor(new Color(255, 255, 255, 38));
            g.fillOval(-90, -90, 340, 340);
            g.fillOval(WIDTH - 220, HEIGHT - 220, 380, 380);

            g.setColor(new Color(255, 255, 255, 235));
            g.fillOval(WIDTH / 2 - 80, 70, 160, 160);
            g.setColor(base.darker());
            drawCentred(g, initialsOf(title), new Font(Font.SANS_SERIF, Font.BOLD, 64), 0, 70, WIDTH, 160);

            g.setColor(Color.WHITE);
            drawFitted(g, title, 300, WIDTH - 80);
            g.setColor(new Color(255, 255, 255, 200));
            drawCentred(g, caption, new Font(Font.SANS_SERIF, Font.PLAIN, 24), 0, 340, WIDTH, 40);
        } finally {
            g.dispose();
        }

        Path file = folder.resolve(fileName);
        ImageIO.write(image, "png", file.toFile());
        return file;
    }

    /** First letter of the first two words, upper-case: "Yoga Mat (6 mm)" becomes "YM". */
    static String initialsOf(String title) {
        StringBuilder initials = new StringBuilder();
        String[] words = title.trim().split("\\s+");
        for (int i = 0; i < words.length && initials.length() < 2; i++) {
            if (!words[i].isEmpty() && Character.isLetterOrDigit(words[i].charAt(0))) {
                initials.append(Character.toUpperCase(words[i].charAt(0)));
            }
        }
        return initials.length() == 0 ? "?" : initials.toString();
    }

    /** Shrinks the font until the text fits the width, then draws it centred. */
    private static void drawFitted(Graphics2D g, String text, int y, int maxWidth) {
        int size = 44;
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, size);
        FontMetrics metrics = g.getFontMetrics(font);
        while (size > 16 && metrics.stringWidth(text) > maxWidth) {
            size -= 2;
            font = new Font(Font.SANS_SERIF, Font.BOLD, size);
            metrics = g.getFontMetrics(font);
        }
        drawCentred(g, text, font, 0, y, WIDTH, 50);
    }

    private static void drawCentred(Graphics2D g, String text, Font font, int x, int y, int width, int height) {
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int textX = x + (width - metrics.stringWidth(text)) / 2;
        int textY = y + (height - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(text, textX, textY);
    }
}
