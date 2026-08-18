package com.cell.demo.fonts;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Java2D font rendering "Hello, World": render the letter A in three ways.
 *
 * Usage:
 *   javac RenderLetterA.java
 *   java RenderLetterA
 *   java RenderLetterA /path/to/font.ttf letter-a.png
 *
 * When a TTF path is provided, the program also prints its sfnt table directory.
 */
public final class RenderLetterA {
    private static final int WIDTH = 1200;
    private static final int HEIGHT = 500;
    private static final int PANEL_WIDTH = WIDTH / 3;
    private static final float FONT_SIZE = 260f;
    private static final float BASELINE_Y = 370f;

    public static void main(String[] args) throws Exception {
        File fontFile = args.length >= 1 ? new File(args[0]) : null;
        Path output = args.length >= 2
                ? Paths.get(args[1])
                : Paths.get("letter-a.png");

        Font baseFont;
        if (fontFile != null) {
            baseFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
            printSfntTableDirectory(fontFile);
        } else {
            // A Java logical font is portable, but its physical backing font is platform-dependent.
            baseFont = new Font(Font.SERIF, Font.PLAIN, 12);
            System.out.println("No TTF path supplied; using Java logical font: "
                    + baseFont.getFamily());
        }
        Font font = baseFont.deriveFont(FONT_SIZE);

        BufferedImage image = new BufferedImage(
                WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            configureRendering(g);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            FontRenderContext frc = g.getFontRenderContext();
            GlyphVector glyphVector = font.createGlyphVector(frc, new char[]{'A'});
            Shape rawOutline = glyphVector.getGlyphOutline(0);

            drawPanelFrame(g, 0, "1. drawString(\"A\")");
            float drawStringX = centeredOriginX(font.getStringBounds("A", frc), 0);
            g.setFont(font);
            g.setColor(new Color(25, 52, 96));
            g.drawString("A", drawStringX, BASELINE_Y);

            drawPanelFrame(g, 1, "2. drawGlyphVector(...)");
            float glyphVectorX = centeredOriginX(rawOutline.getBounds2D(), 1);
            g.setColor(new Color(18, 111, 91));
            g.drawGlyphVector(glyphVector, glyphVectorX, BASELINE_Y);

            drawPanelFrame(g, 2, "3. getGlyphOutline() + fill()");
            float outlineX = centeredOriginX(rawOutline.getBounds2D(), 2);
            AffineTransform move = AffineTransform.getTranslateInstance(
                    outlineX, BASELINE_Y);
            Shape positionedOutline = move.createTransformedShape(rawOutline);

            // Here Java2D receives an ordinary vector Shape rather than text.
            g.setColor(new Color(150, 55, 79));
            g.fill(positionedOutline);

            // Debug overlays: baseline and exact visual bounds.
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(new Color(220, 35, 35, 180));
            g.drawLine(2 * PANEL_WIDTH + 24, Math.round(BASELINE_Y),
                    3 * PANEL_WIDTH - 24, Math.round(BASELINE_Y));
            g.setColor(new Color(55, 95, 210, 180));
            g.draw(positionedOutline.getBounds2D());

            printGlyphInformation(font, frc, glyphVector, rawOutline);
        } finally {
            g.dispose();
        }

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!ImageIO.write(image, "png", output.toFile())) {
            throw new IllegalStateException("No PNG ImageIO writer is available");
        }
        System.out.println("\nPNG written to: " + output.toAbsolutePath());
    }

    private static void configureRendering(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static void drawPanelFrame(Graphics2D g, int panel, String title) {
        int left = panel * PANEL_WIDTH;
        g.setColor(new Color(242, 245, 249));
        g.fillRect(left, 0, PANEL_WIDTH, 62);
        g.setColor(new Color(60, 66, 75));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        g.drawString(title, left + 20, 38);

        if (panel > 0) {
            g.setColor(new Color(215, 220, 227));
            g.drawLine(left, 0, left, HEIGHT);
        }
    }

    /**
     * The supplied bounds are relative to the glyph origin (0, 0). This method
     * chooses an origin x that visually centers those bounds inside a panel.
     */
    private static float centeredOriginX(Rectangle2D bounds, int panel) {
        double panelLeft = panel * PANEL_WIDTH;
        double visualLeft = panelLeft + (PANEL_WIDTH - bounds.getWidth()) / 2.0;
        return (float) (visualLeft - bounds.getX());
    }

    private static void printGlyphInformation(Font font,
                                              FontRenderContext frc,
                                              GlyphVector glyphVector,
                                              Shape rawOutline) {
        GlyphMetrics metrics = glyphVector.getGlyphMetrics(0);
        LineMetrics lineMetrics = font.getLineMetrics("A", frc);

        System.out.println("\n=== Java2D glyph information ===");
        System.out.println("Font family : " + font.getFamily());
        System.out.println("Font name   : " + font.getFontName());
        System.out.println("Point size  : " + font.getSize2D());
        System.out.println("Character   : A (U+0041)");
        System.out.println("Glyph ID    : " + glyphVector.getGlyphCode(0));
        System.out.printf("Advance     : %.3f%n", metrics.getAdvance());
        System.out.printf("Ascent      : %.3f%n", lineMetrics.getAscent());
        System.out.printf("Descent     : %.3f%n", lineMetrics.getDescent());
        System.out.println("Visual box  : " + rawOutline.getBounds2D());
        System.out.println("Logical box : " + glyphVector.getLogicalBounds());

        System.out.println("\n=== Outline path in Java2D user-space coordinates ===");
        PathIterator iterator = rawOutline.getPathIterator(null);
        double[] p = new double[6];
        int contour = 0;
        int segment = 0;
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(p);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    contour++;
                    System.out.printf("%02d  contour %d  M  %.3f %.3f%n",
                            segment, contour, p[0], p[1]);
                    break;
                case PathIterator.SEG_LINETO:
                    System.out.printf("%02d             L  %.3f %.3f%n",
                            segment, p[0], p[1]);
                    break;
                case PathIterator.SEG_QUADTO:
                    System.out.printf("%02d             Q  %.3f %.3f  %.3f %.3f%n",
                            segment, p[0], p[1], p[2], p[3]);
                    break;
                case PathIterator.SEG_CUBICTO:
                    System.out.printf(
                            "%02d             C  %.3f %.3f  %.3f %.3f  %.3f %.3f%n",
                            segment, p[0], p[1], p[2], p[3], p[4], p[5]);
                    break;
                case PathIterator.SEG_CLOSE:
                    System.out.printf("%02d             Z%n", segment);
                    break;
                default:
                    throw new IllegalStateException("Unknown path segment: " + type);
            }
            segment++;
            iterator.next();
        }
        System.out.println("Contours    : " + contour);
        System.out.println("Path segments: " + segment);
    }

    /**
     * Reads only the top-level sfnt table directory. It is intentionally small:
     * enough to demonstrate how a TTF points to tables such as cmap and glyf.
     */
    private static void printSfntTableDirectory(File file) throws Exception {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long scalerType = Integer.toUnsignedLong(in.readInt());
            if (scalerType == 0x74746366L) { // "ttcf"
                System.out.println("The supplied file is a TTC collection, not a single TTF.");
                System.out.println("This minimal directory reader handles single sfnt fonts only.");
                return;
            }

            int numTables = in.readUnsignedShort();
            int searchRange = in.readUnsignedShort();
            int entrySelector = in.readUnsignedShort();
            int rangeShift = in.readUnsignedShort();

            List<TableRecord> records = new ArrayList<TableRecord>();
            for (int i = 0; i < numTables; i++) {
                byte[] tagBytes = new byte[4];
                in.readFully(tagBytes);
                String tag = new String(tagBytes, StandardCharsets.ISO_8859_1);
                long checksum = Integer.toUnsignedLong(in.readInt());
                long offset = Integer.toUnsignedLong(in.readInt());
                long length = Integer.toUnsignedLong(in.readInt());
                records.add(new TableRecord(tag, checksum, offset, length));
            }

            System.out.println("=== sfnt / TTF table directory ===");
            System.out.println("File          : " + file.getAbsolutePath());
            System.out.printf("Scaler type   : 0x%08X%n", scalerType);
            System.out.println("Number tables : " + numTables);
            System.out.println("searchRange   : " + searchRange);
            System.out.println("entrySelector : " + entrySelector);
            System.out.println("rangeShift    : " + rangeShift);
            System.out.println();
            System.out.printf("%-6s %-12s %-12s %-12s%n",
                    "tag", "checksum", "offset", "length");
            for (TableRecord record : records) {
                System.out.printf("%-6s 0x%08X   %-12d %-12d%n",
                        record.tag, record.checksum, record.offset, record.length);
            }

            TableRecord head = findTable(records, "head");
            TableRecord maxp = findTable(records, "maxp");
            TableRecord hhea = findTable(records, "hhea");
            TableRecord cmap = findTable(records, "cmap");
            if (head != null && maxp != null && hhea != null && cmap != null) {
                in.seek(head.offset + 18);
                int unitsPerEm = in.readUnsignedShort();
                in.seek(head.offset + 50);
                int indexToLocFormat = in.readShort();

                in.seek(maxp.offset + 4);
                int numGlyphs = in.readUnsignedShort();

                in.seek(hhea.offset + 34);
                int numberOfHMetrics = in.readUnsignedShort();

                in.seek(cmap.offset + 2);
                int cmapSubtables = in.readUnsignedShort();

                System.out.println("\nSelected values used during glyph lookup/rendering:");
                System.out.println("head.unitsPerEm       : " + unitsPerEm);
                System.out.println("head.indexToLocFormat : " + indexToLocFormat
                        + (indexToLocFormat == 0 ? " (short loca offsets)"
                        : " (long loca offsets)"));
                System.out.println("maxp.numGlyphs        : " + numGlyphs);
                System.out.println("hhea.numberOfHMetrics : " + numberOfHMetrics);
                System.out.println("cmap subtables        : " + cmapSubtables);
                System.out.printf("Scale at %.1f pt       : %.6f user units/font unit%n",
                        FONT_SIZE, FONT_SIZE / unitsPerEm);
            }
        }
    }

    private static TableRecord findTable(List<TableRecord> records, String tag) {
        for (TableRecord record : records) {
            if (record.tag.equals(tag)) {
                return record;
            }
        }
        return null;
    }

    private static final class TableRecord {
        final String tag;
        final long checksum;
        final long offset;
        final long length;

        TableRecord(String tag, long checksum, long offset, long length) {
            this.tag = tag;
            this.checksum = checksum;
            this.offset = offset;
            this.length = length;
        }
    }
}
