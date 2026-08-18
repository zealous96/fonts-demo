package com.cell.demo.fonts;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately small TrueType renderer.
 *
 * It does NOT use java.awt.Font, GlyphVector, Shape, Path2D or drawString.
 * It manually performs:
 *
 *   sfnt -> cmap -> glyph ID -> loca -> glyf -> contours
 *        -> quadratic Bezier flattening -> non-zero winding fill
 *        -> 4x4 supersampling -> Graphics2D.fillRect(x, y, 1, 1)
 *
 * Drawing is limited to fillRect, drawRect and drawLine. ImageIO is used only
 * to save the BufferedImage as PNG.
 *
 * Usage:
 *   javac ManualTtfLetterA.java
 *   java -Djava.awt.headless=true ManualTtfLetterA font.ttf output.png [hexCodePoint]
 *
 * Example:
 *   java -Djava.awt.headless=true ManualTtfLetterA Arial.ttf manual-a.png 0041
 *
 * This hello-world implementation supports simple TrueType glyf outlines.
 * Compound glyphs are detected and reported, but are intentionally left for a
 * follow-up step because they require component transforms and recursion.
 */
public final class ManualTtfLetterA {
    private static final int WIDTH = 720;
    private static final int HEIGHT = 720;
    private static final int MARGIN = 80;
    private static final int SUPERSAMPLE = 4;

    private static final Color BACKGROUND = new Color(250, 250, 247);
    private static final Color FOREGROUND = new Color(23, 51, 87);
    private static final Color DEBUG_FOREGROUND = new Color(225, 230, 236);

    private ManualTtfLetterA() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java ManualTtfLetterA font.ttf output.png [hexCodePoint]");
            System.err.println("Example code point for A: 0041");
            System.exit(2);
        }

        Path fontPath = Paths.get(args[0]);
        Path outputPath = Paths.get(args[1]);
        int codePoint = args.length >= 3
                ? Integer.parseInt(args[2].replaceFirst("^(?i)U\\+", ""), 16)
                : 0x0041;

        TtfFont font = new TtfFont(fontPath);
        font.printDirectory();

        int glyphId = font.glyphIndex(codePoint);
        if (glyphId == 0) {
            throw new IllegalArgumentException(String.format(
                    "The selected cmap maps U+%04X to missing glyph 0", codePoint));
        }

        Glyph glyph = font.readSimpleGlyph(glyphId);
        HorizontalMetrics metrics = font.horizontalMetrics(glyphId);
        ScreenTransform transform = ScreenTransform.fit(glyph, WIDTH, HEIGHT, MARGIN);
        List<List<PointD>> flattenedContours = flattenGlyph(glyph, transform);

        System.out.println("\n=== Selected glyph ===");
        System.out.printf("Code point       : U+%04X%n", codePoint);
        System.out.println("cmap format      : " + font.getSelectedCmapFormat());
        System.out.println("Glyph ID         : " + glyphId);
        System.out.println("Contours         : " + glyph.contours.size());
        System.out.println("Raw points       : " + glyph.pointCount());
        System.out.println("Instructions     : " + glyph.instructionLength + " bytes");
        System.out.println("Font bbox        : [" + glyph.xMin + ", " + glyph.yMin
                + "] - [" + glyph.xMax + ", " + glyph.yMax + "]");
        System.out.println("Advance width    : " + metrics.advanceWidth);
        System.out.println("Left side bearing: " + metrics.leftSideBearing);
        System.out.println("unitsPerEm       : " + font.unitsPerEm);
        System.out.printf("Screen scale     : %.6f px/font-unit%n", transform.scale);
        for (int i = 0; i < glyph.contours.size(); i++) {
            System.out.println("Contour " + i + "        : "
                    + glyph.contours.get(i).size() + " raw points -> "
                    + flattenedContours.get(i).size() + " line vertices");
        }

        BufferedImage rendered = rasterize(
                flattenedContours, WIDTH, HEIGHT, BACKGROUND, FOREGROUND);
        writePng(rendered, outputPath);

        Path debugPath = debugPathFor(outputPath);
        BufferedImage debug = rasterize(
                flattenedContours, WIDTH, HEIGHT, BACKGROUND, DEBUG_FOREGROUND);
        drawDebugOverlay(debug, glyph, transform, flattenedContours);
        writePng(debug, debugPath);

        System.out.println("\nManual raster image: " + outputPath.toAbsolutePath());
        System.out.println("Outline debug image: " + debugPath.toAbsolutePath());
        System.out.println("Java2D font APIs used: none");
        System.out.println("Drawing calls used: fillRect, drawRect, drawLine");
    }

    private static BufferedImage rasterize(List<List<PointD>> contours,
                                           int width,
                                           int height,
                                           Color background,
                                           Color foreground) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(background);
            g.fillRect(0, 0, width, height);

            Color[] coveragePalette = new Color[SUPERSAMPLE * SUPERSAMPLE + 1];
            for (int hits = 0; hits < coveragePalette.length; hits++) {
                double coverage = hits / (double) (SUPERSAMPLE * SUPERSAMPLE);
                coveragePalette[hits] = mix(background, foreground, coverage);
            }

            Bounds bounds = boundsOf(contours);
            int minX = clamp((int) Math.floor(bounds.minX) - 1, 0, width - 1);
            int maxX = clamp((int) Math.ceil(bounds.maxX) + 1, 0, width - 1);
            int minY = clamp((int) Math.floor(bounds.minY) - 1, 0, height - 1);
            int maxY = clamp((int) Math.ceil(bounds.maxY) + 1, 0, height - 1);

            int currentCoverage = -1;
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    int hits = 0;
                    for (int sampleY = 0; sampleY < SUPERSAMPLE; sampleY++) {
                        double py = y + (sampleY + 0.5) / SUPERSAMPLE;
                        for (int sampleX = 0; sampleX < SUPERSAMPLE; sampleX++) {
                            double px = x + (sampleX + 0.5) / SUPERSAMPLE;
                            if (insideNonZeroWinding(contours, px, py)) {
                                hits++;
                            }
                        }
                    }

                    if (hits != 0) {
                        if (hits != currentCoverage) {
                            g.setColor(coveragePalette[hits]);
                            currentCoverage = hits;
                        }
                        // The actual raster operation: paint one manually classified pixel.
                        g.fillRect(x, y, 1, 1);
                    }
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /** Non-zero winding rule over already-flattened contour edges. */
    private static boolean insideNonZeroWinding(List<List<PointD>> contours,
                                                double px,
                                                double py) {
        int winding = 0;
        for (List<PointD> contour : contours) {
            for (int i = 0; i + 1 < contour.size(); i++) {
                PointD a = contour.get(i);
                PointD b = contour.get(i + 1);

                if (a.y <= py) {
                    if (b.y > py && isLeft(a, b, px, py) > 0.0) {
                        winding++;
                    }
                } else if (b.y <= py && isLeft(a, b, px, py) < 0.0) {
                    winding--;
                }
            }
        }
        return winding != 0;
    }

    private static double isLeft(PointD a, PointD b, double px, double py) {
        return (b.x - a.x) * (py - a.y) - (px - a.x) * (b.y - a.y);
    }

    private static List<List<PointD>> flattenGlyph(Glyph glyph,
                                                    ScreenTransform transform) {
        List<List<PointD>> result = new ArrayList<List<PointD>>();
        for (List<GlyphPoint> contour : glyph.contours) {
            result.add(flattenContour(contour, transform));
        }
        return result;
    }

    /**
     * Inserts TrueType's implied on-curve points, then samples each quadratic
     * Bezier into line segments. No Java2D Shape or curve API is involved.
     */
    private static List<PointD> flattenContour(List<GlyphPoint> original,
                                               ScreenTransform transform) {
        if (original.isEmpty()) {
            throw new IllegalArgumentException("Empty TrueType contour");
        }

        List<GlyphPoint> expanded = new ArrayList<GlyphPoint>();
        for (int i = 0; i < original.size(); i++) {
            GlyphPoint current = original.get(i);
            GlyphPoint next = original.get((i + 1) % original.size());
            expanded.add(current);
            if (!current.onCurve && !next.onCurve) {
                expanded.add(new GlyphPoint(
                        (current.x + next.x) / 2.0,
                        (current.y + next.y) / 2.0,
                        true));
            }
        }

        int startIndex = -1;
        for (int i = 0; i < expanded.size(); i++) {
            if (expanded.get(i).onCurve) {
                startIndex = i;
                break;
            }
        }
        if (startIndex < 0) {
            throw new IllegalArgumentException("Contour has no on-curve start point");
        }

        List<PointD> polyline = new ArrayList<PointD>();
        PointD start = transform.apply(expanded.get(startIndex));
        PointD current = start;
        polyline.add(start);

        int consumed = 1;
        while (consumed < expanded.size()) {
            GlyphPoint nextRaw = expanded.get((startIndex + consumed) % expanded.size());
            if (nextRaw.onCurve) {
                PointD next = transform.apply(nextRaw);
                polyline.add(next);
                current = next;
                consumed++;
            } else {
                GlyphPoint endRaw = expanded.get(
                        (startIndex + consumed + 1) % expanded.size());
                if (!endRaw.onCurve) {
                    throw new IllegalStateException("Implied point expansion failed");
                }

                PointD control = transform.apply(nextRaw);
                PointD end = transform.apply(endRaw);
                int steps = curveStepCount(current, control, end);
                for (int step = 1; step <= steps; step++) {
                    double t = step / (double) steps;
                    double oneMinusT = 1.0 - t;
                    double x = oneMinusT * oneMinusT * current.x
                            + 2.0 * oneMinusT * t * control.x
                            + t * t * end.x;
                    double y = oneMinusT * oneMinusT * current.y
                            + 2.0 * oneMinusT * t * control.y
                            + t * t * end.y;
                    polyline.add(new PointD(x, y));
                }
                current = end;
                consumed += 2;
            }
        }

        if (!samePoint(polyline.get(polyline.size() - 1), start)) {
            polyline.add(start);
        }
        return polyline;
    }

    private static int curveStepCount(PointD start, PointD control, PointD end) {
        double controlPolygonLength = distance(start, control) + distance(control, end);
        return clamp((int) Math.ceil(controlPolygonLength / 3.0), 4, 96);
    }

    private static void drawDebugOverlay(BufferedImage image,
                                         Glyph glyph,
                                         ScreenTransform transform,
                                         List<List<PointD>> flattenedContours) {
        Graphics2D g = image.createGraphics();
        try {
            // The font baseline is y=0 in TrueType coordinates.
            int baselineY = (int) Math.round(transform.screenY(0));
            g.setColor(new Color(200, 50, 50));
            g.drawLine(MARGIN / 2, baselineY, WIDTH - MARGIN / 2, baselineY);

            int x = (int) Math.round(transform.screenX(glyph.xMin));
            int y = (int) Math.round(transform.screenY(glyph.yMax));
            int w = (int) Math.round((glyph.xMax - glyph.xMin) * transform.scale);
            int h = (int) Math.round((glyph.yMax - glyph.yMin) * transform.scale);
            g.setColor(new Color(80, 120, 210));
            g.drawRect(x, y, w, h);

            // Original point/control polygon.
            for (List<GlyphPoint> contour : glyph.contours) {
                g.setColor(new Color(160, 165, 173));
                for (int i = 0; i < contour.size(); i++) {
                    PointD a = transform.apply(contour.get(i));
                    PointD b = transform.apply(contour.get((i + 1) % contour.size()));
                    g.drawLine(round(a.x), round(a.y), round(b.x), round(b.y));
                }

                for (GlyphPoint point : contour) {
                    PointD p = transform.apply(point);
                    int px = round(p.x);
                    int py = round(p.y);
                    if (point.onCurve) {
                        g.setColor(new Color(20, 145, 90));
                        g.fillRect(px - 3, py - 3, 7, 7);
                    } else {
                        g.setColor(new Color(230, 125, 25));
                        g.drawRect(px - 4, py - 4, 8, 8);
                    }
                }
            }

            // The final flattened edges consumed by the winding test.
            g.setColor(new Color(25, 45, 65));
            for (List<PointD> contour : flattenedContours) {
                for (int i = 0; i + 1 < contour.size(); i++) {
                    PointD a = contour.get(i);
                    PointD b = contour.get(i + 1);
                    g.drawLine(round(a.x), round(a.y), round(b.x), round(b.y));
                }
            }
        } finally {
            g.dispose();
        }
    }

    private static Bounds boundsOf(List<List<PointD>> contours) {
        Bounds bounds = new Bounds();
        for (List<PointD> contour : contours) {
            for (PointD point : contour) {
                bounds.include(point);
            }
        }
        return bounds;
    }

    private static Color mix(Color background, Color foreground, double coverage) {
        int red = (int) Math.round(background.getRed() * (1.0 - coverage)
                + foreground.getRed() * coverage);
        int green = (int) Math.round(background.getGreen() * (1.0 - coverage)
                + foreground.getGreen() * coverage);
        int blue = (int) Math.round(background.getBlue() * (1.0 - coverage)
                + foreground.getBlue() * coverage);
        return new Color(red, green, blue);
    }

    private static void writePng(BufferedImage image, Path path) throws IOException {
        Path absolute = path.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!ImageIO.write(image, "png", absolute.toFile())) {
            throw new IOException("PNG writer is unavailable");
        }
    }

    private static Path debugPathFor(Path output) {
        Path fileName = output.getFileName();
        String name = fileName == null ? "manual-a.png" : fileName.toString();
        int dot = name.lastIndexOf('.');
        String debugName = dot > 0
                ? name.substring(0, dot) + "-debug" + name.substring(dot)
                : name + "-debug.png";
        Path parent = output.getParent();
        return parent == null ? Paths.get(debugName) : parent.resolve(debugName);
    }

    private static boolean samePoint(PointD a, PointD b) {
        return Math.abs(a.x - b.x) < 1.0e-9 && Math.abs(a.y - b.y) < 1.0e-9;
    }

    private static double distance(PointD a, PointD b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class TtfFont {
        private static final long TTC_TAG = 0x74746366L; // "ttcf"

        final Path path;
        final byte[] data;
        final Map<String, Table> tables = new LinkedHashMap<String, Table>();
        final long scalerType;
        final int unitsPerEm;
        final int indexToLocFormat;
        final int numGlyphs;
        final int numberOfHMetrics;
        int selectedCmapFormat = -1;

        TtfFont(Path path) throws IOException {
            this.path = path;
            this.data = Files.readAllBytes(path);
            check(0, 12);

            scalerType = u32(0);
            if (scalerType == TTC_TAG) {
                throw new IllegalArgumentException(
                        "This example expects one TTF/sfnt font, not a TTC collection");
            }

            int numTables = u16(4);
            check(12, numTables * 16);
            for (int i = 0; i < numTables; i++) {
                int record = 12 + i * 16;
                String tag = ascii(record, 4);
                long checksum = u32(record + 4);
                long offset = u32(record + 8);
                long length = u32(record + 12);
                if (offset > Integer.MAX_VALUE || length > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Table is too large for this demo: " + tag);
                }
                check((int) offset, (int) length);
                tables.put(tag, new Table(tag, checksum, (int) offset, (int) length));
            }

            Table head = require("head");
            Table maxp = require("maxp");
            Table hhea = require("hhea");
            require("cmap");
            require("loca");
            require("glyf");
            require("hmtx");

            unitsPerEm = u16(head.offset + 18);
            indexToLocFormat = s16(head.offset + 50);
            numGlyphs = u16(maxp.offset + 4);
            numberOfHMetrics = u16(hhea.offset + 34);
        }

        void printDirectory() {
            System.out.println("=== Manually parsed sfnt table directory ===");
            System.out.println("File            : " + path.toAbsolutePath());
            System.out.printf("Scaler type     : 0x%08X%n", scalerType);
            System.out.println("Table count     : " + tables.size());
            System.out.println("unitsPerEm      : " + unitsPerEm);
            System.out.println("numGlyphs       : " + numGlyphs);
            System.out.println("indexToLocFormat: " + indexToLocFormat);
            System.out.println();
            System.out.printf("%-6s %-12s %-12s %-12s%n",
                    "tag", "checksum", "offset", "length");
            for (Table table : tables.values()) {
                System.out.printf("%-6s 0x%08X   %-12d %-12d%n",
                        table.tag, table.checksum, table.offset, table.length);
            }
        }

        int getSelectedCmapFormat() {
            return selectedCmapFormat;
        }

        int glyphIndex(int codePoint) {
            Table cmap = require("cmap");
            int numSubtables = u16(cmap.offset + 2);
            CmapChoice best = null;

            for (int i = 0; i < numSubtables; i++) {
                int record = cmap.offset + 4 + i * 8;
                int platformId = u16(record);
                int encodingId = u16(record + 2);
                long relativeOffset = u32(record + 4);
                if (relativeOffset > Integer.MAX_VALUE) {
                    continue;
                }
                int subtable = cmap.offset + (int) relativeOffset;
                int format = u16(subtable);
                if (format != 4 && format != 12) {
                    continue;
                }

                int score = format == 12 ? 200 : 100;
                if (platformId == 0) {
                    score += 40;
                } else if (platformId == 3 && encodingId == 10) {
                    score += 35;
                } else if (platformId == 3 && encodingId == 1) {
                    score += 30;
                }

                if (best == null || score > best.score) {
                    best = new CmapChoice(subtable, format, score);
                }
            }

            if (best == null) {
                throw new IllegalArgumentException("No supported cmap format 4 or 12 found");
            }
            selectedCmapFormat = best.format;
            return best.format == 12
                    ? glyphIndexFormat12(best.offset, codePoint)
                    : glyphIndexFormat4(best.offset, codePoint);
        }

        private int glyphIndexFormat4(int offset, int codePoint) {
            if (codePoint > 0xFFFF) {
                return 0;
            }
            int length = u16(offset + 2);
            int segCount = u16(offset + 6) / 2;
            int endCodeOffset = offset + 14;
            int startCodeOffset = endCodeOffset + segCount * 2 + 2;
            int idDeltaOffset = startCodeOffset + segCount * 2;
            int idRangeOffsetOffset = idDeltaOffset + segCount * 2;

            for (int i = 0; i < segCount; i++) {
                int endCode = u16(endCodeOffset + i * 2);
                if (codePoint > endCode) {
                    continue;
                }
                int startCode = u16(startCodeOffset + i * 2);
                if (codePoint < startCode) {
                    return 0;
                }
                int delta = s16(idDeltaOffset + i * 2);
                int rangeOffsetAddress = idRangeOffsetOffset + i * 2;
                int rangeOffset = u16(rangeOffsetAddress);
                if (rangeOffset == 0) {
                    return (codePoint + delta) & 0xFFFF;
                }

                int glyphAddress = rangeOffsetAddress + rangeOffset
                        + 2 * (codePoint - startCode);
                if (glyphAddress < offset || glyphAddress + 2 > offset + length) {
                    return 0;
                }
                int glyph = u16(glyphAddress);
                return glyph == 0 ? 0 : (glyph + delta) & 0xFFFF;
            }
            return 0;
        }

        private int glyphIndexFormat12(int offset, int codePoint) {
            long groupCountLong = u32(offset + 12);
            if (groupCountLong > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Too many cmap format 12 groups");
            }
            int low = 0;
            int high = (int) groupCountLong - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int group = offset + 16 + mid * 12;
                long start = u32(group);
                long end = u32(group + 4);
                if (codePoint < start) {
                    high = mid - 1;
                } else if (codePoint > end) {
                    low = mid + 1;
                } else {
                    long startGlyph = u32(group + 8);
                    long glyph = startGlyph + codePoint - start;
                    if (glyph > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException("Glyph ID is too large");
                    }
                    return (int) glyph;
                }
            }
            return 0;
        }

        HorizontalMetrics horizontalMetrics(int glyphId) {
            if (glyphId < 0 || glyphId >= numGlyphs) {
                throw new IllegalArgumentException("Invalid Glyph ID: " + glyphId);
            }
            Table hmtx = require("hmtx");
            if (glyphId < numberOfHMetrics) {
                int offset = hmtx.offset + glyphId * 4;
                return new HorizontalMetrics(u16(offset), s16(offset + 2));
            }

            int lastMetric = hmtx.offset + (numberOfHMetrics - 1) * 4;
            int lsbOffset = hmtx.offset + numberOfHMetrics * 4
                    + (glyphId - numberOfHMetrics) * 2;
            return new HorizontalMetrics(u16(lastMetric), s16(lsbOffset));
        }

        Glyph readSimpleGlyph(int glyphId) {
            if (glyphId < 0 || glyphId >= numGlyphs) {
                throw new IllegalArgumentException("Invalid Glyph ID: " + glyphId);
            }

            Table loca = require("loca");
            Table glyf = require("glyf");
            int start = glyphOffset(loca, glyphId);
            int end = glyphOffset(loca, glyphId + 1);
            if (start == end) {
                throw new IllegalArgumentException("Glyph " + glyphId + " has no outline");
            }
            if (start < 0 || end < start || end > glyf.length) {
                throw new IllegalArgumentException("Invalid loca offsets for glyph " + glyphId);
            }

            int cursor = glyf.offset + start;
            int numberOfContours = s16(cursor);
            int xMin = s16(cursor + 2);
            int yMin = s16(cursor + 4);
            int xMax = s16(cursor + 6);
            int yMax = s16(cursor + 8);
            cursor += 10;

            if (numberOfContours < 0) {
                throw new UnsupportedOperationException(
                        "Glyph " + glyphId + " is compound; this hello-world handles simple glyf outlines");
            }

            int[] endPoints = new int[numberOfContours];
            for (int i = 0; i < numberOfContours; i++) {
                endPoints[i] = u16(cursor);
                cursor += 2;
            }
            int pointCount = numberOfContours == 0
                    ? 0
                    : endPoints[numberOfContours - 1] + 1;

            int instructionLength = u16(cursor);
            cursor += 2 + instructionLength;

            int[] flags = new int[pointCount];
            for (int i = 0; i < pointCount; ) {
                int flag = u8(cursor++);
                flags[i++] = flag;
                if ((flag & 0x08) != 0) {
                    int repeats = u8(cursor++);
                    if (i + repeats > pointCount) {
                        throw new IllegalArgumentException("Invalid repeated glyf flags");
                    }
                    for (int repeat = 0; repeat < repeats; repeat++) {
                        flags[i++] = flag;
                    }
                }
            }

            int[] xs = new int[pointCount];
            int x = 0;
            for (int i = 0; i < pointCount; i++) {
                int flag = flags[i];
                int delta;
                if ((flag & 0x02) != 0) {
                    int magnitude = u8(cursor++);
                    delta = (flag & 0x10) != 0 ? magnitude : -magnitude;
                } else {
                    delta = (flag & 0x10) != 0 ? 0 : s16(cursor);
                    if ((flag & 0x10) == 0) {
                        cursor += 2;
                    }
                }
                x += delta;
                xs[i] = x;
            }

            int[] ys = new int[pointCount];
            int y = 0;
            for (int i = 0; i < pointCount; i++) {
                int flag = flags[i];
                int delta;
                if ((flag & 0x04) != 0) {
                    int magnitude = u8(cursor++);
                    delta = (flag & 0x20) != 0 ? magnitude : -magnitude;
                } else {
                    delta = (flag & 0x20) != 0 ? 0 : s16(cursor);
                    if ((flag & 0x20) == 0) {
                        cursor += 2;
                    }
                }
                y += delta;
                ys[i] = y;
            }

            List<List<GlyphPoint>> contours = new ArrayList<List<GlyphPoint>>();
            int firstPoint = 0;
            for (int contour = 0; contour < numberOfContours; contour++) {
                int lastPoint = endPoints[contour];
                List<GlyphPoint> points = new ArrayList<GlyphPoint>();
                for (int i = firstPoint; i <= lastPoint; i++) {
                    points.add(new GlyphPoint(xs[i], ys[i], (flags[i] & 0x01) != 0));
                }
                contours.add(points);
                firstPoint = lastPoint + 1;
            }

            return new Glyph(glyphId, xMin, yMin, xMax, yMax,
                    instructionLength, contours);
        }

        private int glyphOffset(Table loca, int glyphId) {
            if (indexToLocFormat == 0) {
                return u16(loca.offset + glyphId * 2) * 2;
            }
            if (indexToLocFormat == 1) {
                long offset = u32(loca.offset + glyphId * 4);
                if (offset > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("glyf offset is too large");
                }
                return (int) offset;
            }
            throw new IllegalArgumentException("Unsupported indexToLocFormat: "
                    + indexToLocFormat);
        }

        private Table require(String tag) {
            Table table = tables.get(tag);
            if (table == null) {
                throw new IllegalArgumentException("Required TTF table is missing: " + tag);
            }
            return table;
        }

        private int u8(int offset) {
            check(offset, 1);
            return data[offset] & 0xFF;
        }

        private int u16(int offset) {
            check(offset, 2);
            return ((data[offset] & 0xFF) << 8)
                    | (data[offset + 1] & 0xFF);
        }

        private int s16(int offset) {
            return (short) u16(offset);
        }

        private long u32(int offset) {
            check(offset, 4);
            return ((long) (data[offset] & 0xFF) << 24)
                    | ((long) (data[offset + 1] & 0xFF) << 16)
                    | ((long) (data[offset + 2] & 0xFF) << 8)
                    | (long) (data[offset + 3] & 0xFF);
        }

        private String ascii(int offset, int length) {
            check(offset, length);
            return new String(data, offset, length, StandardCharsets.ISO_8859_1);
        }

        private void check(int offset, int length) {
            if (offset < 0 || length < 0 || offset > data.length - length) {
                throw new IllegalArgumentException(
                        "TTF read outside file: offset=" + offset + ", length=" + length);
            }
        }
    }

    private static final class ScreenTransform {
        final double scale;
        final double left;
        final double top;
        final double xMin;
        final double yMax;

        private ScreenTransform(double scale,
                                double left,
                                double top,
                                double xMin,
                                double yMax) {
            this.scale = scale;
            this.left = left;
            this.top = top;
            this.xMin = xMin;
            this.yMax = yMax;
        }

        static ScreenTransform fit(Glyph glyph, int width, int height, int margin) {
            double glyphWidth = glyph.xMax - glyph.xMin;
            double glyphHeight = glyph.yMax - glyph.yMin;
            if (glyphWidth <= 0 || glyphHeight <= 0) {
                throw new IllegalArgumentException("Glyph has an empty bounding box");
            }

            double availableWidth = width - 2.0 * margin;
            double availableHeight = height - 2.0 * margin;
            double scale = Math.min(
                    availableWidth / glyphWidth,
                    availableHeight / glyphHeight);
            double drawnWidth = glyphWidth * scale;
            double drawnHeight = glyphHeight * scale;
            double left = (width - drawnWidth) / 2.0;
            double top = (height - drawnHeight) / 2.0;
            return new ScreenTransform(scale, left, top, glyph.xMin, glyph.yMax);
        }

        PointD apply(GlyphPoint point) {
            return new PointD(screenX(point.x), screenY(point.y));
        }

        double screenX(double fontX) {
            return left + (fontX - xMin) * scale;
        }

        double screenY(double fontY) {
            return top + (yMax - fontY) * scale;
        }
    }

    private static final class Glyph {
        final int glyphId;
        final int xMin;
        final int yMin;
        final int xMax;
        final int yMax;
        final int instructionLength;
        final List<List<GlyphPoint>> contours;

        Glyph(int glyphId,
              int xMin,
              int yMin,
              int xMax,
              int yMax,
              int instructionLength,
              List<List<GlyphPoint>> contours) {
            this.glyphId = glyphId;
            this.xMin = xMin;
            this.yMin = yMin;
            this.xMax = xMax;
            this.yMax = yMax;
            this.instructionLength = instructionLength;
            this.contours = contours;
        }

        int pointCount() {
            int result = 0;
            for (List<GlyphPoint> contour : contours) {
                result += contour.size();
            }
            return result;
        }
    }

    private static final class GlyphPoint {
        final double x;
        final double y;
        final boolean onCurve;

        GlyphPoint(double x, double y, boolean onCurve) {
            this.x = x;
            this.y = y;
            this.onCurve = onCurve;
        }
    }

    private static final class PointD {
        final double x;
        final double y;

        PointD(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Bounds {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        void include(PointD point) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
    }

    private static final class Table {
        final String tag;
        final long checksum;
        final int offset;
        final int length;

        Table(String tag, long checksum, int offset, int length) {
            this.tag = tag;
            this.checksum = checksum;
            this.offset = offset;
            this.length = length;
        }
    }

    private static final class CmapChoice {
        final int offset;
        final int format;
        final int score;

        CmapChoice(int offset, int format, int score) {
            this.offset = offset;
            this.format = format;
            this.score = score;
        }
    }

    private static final class HorizontalMetrics {
        final int advanceWidth;
        final int leftSideBearing;

        HorizontalMetrics(int advanceWidth, int leftSideBearing) {
            this.advanceWidth = advanceWidth;
            this.leftSideBearing = leftSideBearing;
        }
    }
}
