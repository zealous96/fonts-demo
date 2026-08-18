package com.cell.demo.fonts;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.cell.demo.fonts.helper.BigUnsignedHelper;
import com.cell.demo.fonts.helper.ByteHelper;
import com.cell.demo.fonts.model.CmapSubTable;
import com.cell.demo.fonts.model.EncodingRecord;
import com.cell.demo.fonts.model.GlyphPoint;
import com.cell.demo.fonts.model.TableRecord;
import com.cell.demo.fonts.swing.FontPanel;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhaokai
 * @since 2026.07.25
 */
public class Demo01 {

    static Path projectHome = Paths.get(System.getProperty("user.dir"));

    public static void main(String[] args) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(projectHome.resolve("test.ttf").toFile(), "r");
        Map<String, TableRecord> tableRecordMap = readTableDirectory(randomAccessFile);

        char c = '赵';

        day02(randomAccessFile, tableRecordMap);

        // 读取 mapx
        day03(randomAccessFile, tableRecordMap);

        // 读取 Glyph
        day04(randomAccessFile, tableRecordMap, c);

        // 开始读取字形的轮廓
        day05(randomAccessFile, tableRecordMap, c);

//        System.out.println("============= table record =============");
//        System.out.println(JSON.toJSONString(tableRecordMap, JSONWriter.Feature.PrettyFormatWith4Space));
//        System.out.println("============= table record =============");
    }

    private static void day05(RandomAccessFile randomAccessFile, Map<String, TableRecord> tableRecordMap, int codePoint) throws IOException {
        // loca 表
        TableRecord loca = tableRecordMap.get("loca");
        // glyf 表
        TableRecord glyf = tableRecordMap.get("glyf");
        // glyph id
        int glyfId = glyphIndex(randomAccessFile, tableRecordMap.get("cmap"), codePoint);

        // 根据 indexToLocFormat 获取 start
        int indexToLocFormat = tableRecordMap.get("head").indexToLocFormat;
        short bsLength;
        if (indexToLocFormat == 1) {
            bsLength = 4;
        } else {
            bsLength = 2;
        }

        byte[] bs = new byte[bsLength];

        // start
        long start, end;
        if (indexToLocFormat == 1) {
            randomAccessFile.seek(loca.offset + 4L * glyfId);
            randomAccessFile.readFully(bs);
            start = BigUnsignedHelper.readUnsignedInt(bs);
            randomAccessFile.readFully(bs);
            end = BigUnsignedHelper.readUnsignedInt(bs);
        } else {
            randomAccessFile.seek(loca.offset + 2L * glyfId);
            randomAccessFile.readFully(bs);
            start = BigUnsignedHelper.readUnsignedShort(bs) * 2L;
            randomAccessFile.readFully(bs);
            end = BigUnsignedHelper.readUnsignedShort(bs) * 2L;
        }

        // 根据 start/end 读取 glyf 表的字形信息
        long cursor = glyf.offset + start;
        long length = end - start;
        if (length < 10) {
            throw new RuntimeException("字体有问题！");
        }

        // 从 cursor 读取 10 字节
        // 偏移  长度  字段
        // 0     2     numberOfContours
        // 2     2     xMin
        // 4     2     yMin
        // 6     2     xMax
        // 8     2     yMax
        bs = new byte[2];
        randomAccessFile.seek(cursor);

        int numberOfContours = randomAccessFile.readShort();
        System.out.println("numberOfContours = " + numberOfContours);
        if (numberOfContours < 0) {
            throw new UnsupportedOperationException("暂不支持复合字形！");
        }

        int xMin = randomAccessFile.readShort();
        int yMin = randomAccessFile.readShort();
        int xMax = randomAccessFile.readShort();
        int yMax = randomAccessFile.readShort();
        cursor += 10;

        // 读取 contour
        int[] endPoints = new int[numberOfContours];
        for (int i = 0; i < numberOfContours; i++) {
            randomAccessFile.readFully(bs);
            endPoints[i] = BigUnsignedHelper.readUnsignedShort(bs);
            cursor += 2;
        }

        int pointCount = numberOfContours == 0 ? 0 : endPoints[numberOfContours - 1] + 1;
        System.out.println("pointCount = " + pointCount);
        for (int i = 0; i < numberOfContours; i++) {
            if (endPoints[i] >= pointCount) {
                throw new IllegalArgumentException("endPtsOfContours 非法");
            }
        }

        randomAccessFile.readFully(bs);
        int instructionLength = BigUnsignedHelper.readUnsignedShort(bs);
        cursor += 2 + instructionLength;
        if (cursor > glyf.offset + end) {
            throw new IllegalArgumentException("instructionLength 超出 glyf 数据范围");
        }
        randomAccessFile.seek(cursor);
        System.out.println("instructionLength = " + instructionLength);

        // 继续读取 flags 了，这里要细心
        int[] flags = new int[pointCount];
        for (int i = 0; i < pointCount; ) {
            int flag = BigUnsignedHelper.readUnsignedByte(randomAccessFile.readByte());
            ++cursor;

            flags[i++] = flag;

            if ((flag & 0x08) == 0x08) {
                int repeat = BigUnsignedHelper.readUnsignedByte(randomAccessFile.readByte());
                ++cursor;

                if (i + repeat > pointCount) {
                    throw new IllegalArgumentException(
                            "flags 重复次数超出点数范围"
                    );
                }

                for (int j = 0; j < repeat; j++) {
                    flags[i++] = flag;
                }
            }
        }

        // 读取 x 坐标
        int[] xs = new int[pointCount];
        int x = 0;
        for (int i = 0; i < pointCount; i++) {
            int flag = flags[i];
            int delta;
            if ((flag & 0x02) != 0) {
                // 1 字节增量，符号由 bit4 决定
                int magnitude = randomAccessFile.readUnsignedByte();
                cursor++;
                delta = (flag & 0x10) != 0 ? magnitude : -magnitude;
            } else if ((flag & 0x10) != 0) {
                // 增量为 0
                delta = 0;
            } else {
                // 2 字节有符号增量
                delta = randomAccessFile.readShort();
                cursor += 2;
            }

            x += delta;
            xs[i] = x;
        }

        // 读取 y 坐标
        int[] ys = new int[pointCount];
        int y = 0;
        for (int i = 0; i < pointCount; i++) {
            int flag = flags[i];
            int delta;
            if ((flag & 0x04) != 0) {
                // 1 字节增量，符号由 bit4 决定
                int magnitude = randomAccessFile.readUnsignedByte();
                cursor++;
                delta = (flag & 0x20) != 0 ? magnitude : -magnitude;
            } else if ((flag & 0x20) != 0) {
                // 增量为 0
                delta = 0;
            } else {
                // 2 字节有符号增量
                delta = randomAccessFile.readShort();
                cursor += 2;
            }

            y += delta;
            ys[i] = y;
        }

        List<List<GlyphPoint>> contours = new ArrayList<>();

        int firstPoint = 0;

        for (int contour = 0; contour < numberOfContours; contour++) {
            int lastPoint = endPoints[contour];

            List<GlyphPoint> points = new ArrayList<>();

            for (int i = firstPoint; i <= lastPoint; i++) {
                points.add(new GlyphPoint(
                        xs[i],
                        ys[i],
                        (flags[i] & 0x01) == 0x01
                ));
            }

            contours.add(points);
            firstPoint = lastPoint + 1;
        }

        System.out.println("contours = " + JSON.toJSONString(contours, JSONWriter.Feature.PrettyFormatWith4Space));

        day06(contours, tableRecordMap.get("head").unitsPerEm);
    }

    // 尝试绘制字形
    private static void day06(List<List<GlyphPoint>> contours, int unitsPerEm) {
        System.out.println("contours.get(0).get(0) = " + contours.get(0).get(0));
        JFrame frame = new JFrame("字体绘画");
        frame.setSize(new Dimension(800, 600));
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        frame.setLayout(new BorderLayout());

        JPanel panel = new FontPanel(contours, 500f / unitsPerEm);
        frame.add(panel);

        frame.setVisible(true);
    }

    private static void day04(RandomAccessFile randomAccessFile, Map<String, TableRecord> tableRecordMap, int codePoint) throws IOException {
        System.out.println("============= day 04 ============");
        System.out.println("codePoint = " + codePoint);
        int glyphIndex = glyphIndex(randomAccessFile, tableRecordMap.get("cmap"), codePoint);
        System.out.println("glyphIndex = " + glyphIndex);
    }

    private static void day03(RandomAccessFile randomAccessFile, Map<String, TableRecord> tableRecordMap) throws IOException {
        TableRecord maxp = tableRecordMap.get("maxp");
        System.out.println(maxp);

        // 移动文件指针
        randomAccessFile.seek(maxp.offset);
        // 版本
        byte[] bs = new byte[4];
        randomAccessFile.readFully(bs);
        long v = BigUnsignedHelper.readUnsignedInt(bs);
        System.out.println("v = 0x" + Long.toHexString(v));

        // numGlyph
        bs = new byte[2];
        randomAccessFile.readFully(bs);
        int numGlyphs = BigUnsignedHelper.readUnsignedShort(bs);
        System.out.println("numGlyphs = " + numGlyphs);

        // loca
        TableRecord loca = tableRecordMap.get("loca");
        TableRecord head = tableRecordMap.get("head");
        int locaEntrySize = head.indexToLocFormat == 0 ? 2 : 4;
        long expectedLocaLength = (long) (numGlyphs + 1) * locaEntrySize;
        if (loca.length < expectedLocaLength) {
            throw new IllegalArgumentException(
                    "loca 表过短，期望至少 "
                            + expectedLocaLength
                            + " 字节，实际 "
                            + loca.length
            );
        } else {
            System.out.println("loca 表合法！");
        }
    }

    /**
     * 会使用 head 表和 maxp 表。
     *
     * @param randomAccessFile 字体文件随机读取对象。
     * @param tableRecordMap   表 map。
     */
    static void day02(RandomAccessFile randomAccessFile, Map<String, TableRecord> tableRecordMap) throws IOException {
        TableRecord head = tableRecordMap.get("head");
        System.out.println(head);
        head.calcPosition4Head(randomAccessFile);

        byte[] unitsPerEmBs = new byte[2];
        randomAccessFile.seek(head.offset + 18);
        randomAccessFile.readFully(unitsPerEmBs);
        int unitsPerEm = BigUnsignedHelper.readUnsignedShort(unitsPerEmBs);
        head.unitsPerEm = unitsPerEm;
        System.out.println("unitsPerEm = " + unitsPerEm);

        byte[] indexToLocFormatBs = new byte[2];
        randomAccessFile.seek(head.offset + 50);
        randomAccessFile.readFully(indexToLocFormatBs);
        int indexToLocFormat = BigUnsignedHelper.readUnsignedShort(indexToLocFormatBs);
        System.out.println("indexToLocFormat = " + indexToLocFormat);
        head.indexToLocFormat = indexToLocFormat;
    }

    /**
     * 读取字体表地图组装为map。
     *
     * @param randomAccessFile 字体文件随机读取对象。
     * @return 字体表map。
     */
    static Map<String, TableRecord> readTableDirectory(RandomAccessFile randomAccessFile) throws IOException {
        HeadMap headMap = new HeadMap();

        readScaleType(randomAccessFile, headMap);

        readNumOfTables(randomAccessFile, headMap);

        readSearchRange(randomAccessFile);

        readEntrySelector(randomAccessFile);

        readRangeShift(randomAccessFile);

        return readTables(randomAccessFile, headMap);
    }

    private static Map<String, TableRecord> readTables(RandomAccessFile randomAccessFile, HeadMap headMap) throws IOException {
        Map<String, TableRecord> tableRecordMap = new LinkedHashMap<>();
        if (randomAccessFile.getFilePointer() != 12L) {
            randomAccessFile.seek(12L);
        }

        for (int i = 0; i < headMap.numOfTables; i++) {
            TableRecord tableRecord = new TableRecord();

            byte[] bs = new byte[4];

            randomAccessFile.readFully(bs);
            tableRecord.tag = new String(bs);

            tableRecord.checksum = Integer.toUnsignedLong(randomAccessFile.readInt());

            tableRecord.offset = Integer.toUnsignedLong(randomAccessFile.readInt());

            tableRecord.length = Integer.toUnsignedLong(randomAccessFile.readInt());

            tableRecordMap.put(tableRecord.tag, tableRecord);
        }

        return tableRecordMap;
    }

    private static void readRangeShift(RandomAccessFile randomAccessFile) throws IOException {
        short i = randomAccessFile.readShort();
        System.out.printf("range shift: %d\n", i);
    }

    private static void readEntrySelector(RandomAccessFile randomAccessFile) throws IOException {
        short i = randomAccessFile.readShort();
        System.out.printf("entry selector: %d\n", i);
    }

    private static void readSearchRange(RandomAccessFile randomAccessFile) throws IOException {
        short i = randomAccessFile.readShort();
        System.out.printf("search range: %d\n", i);
    }

    private static void readNumOfTables(RandomAccessFile randomAccessFile, HeadMap headMap) throws IOException {
        byte[] bs = new byte[2];
        randomAccessFile.readFully(bs);
        int i = bs[0] & 0xff << 8 | (bs[1] & 0xff);
        System.out.printf("num of tables: %d\n", i);
        headMap.numOfTables = i;
    }

    private static void readScaleType(RandomAccessFile randomAccessFile, HeadMap headMap) throws IOException {
        byte[] scaleTypeBs = new byte[4];
        randomAccessFile.readFully(scaleTypeBs);
        System.out.println("scale type: ");
        ByteHelper.prettyPrint(scaleTypeBs);
        headMap.scalerType =
                ((long) (scaleTypeBs[0] & 0xff) << 24 | (scaleTypeBs[1] & 0xff << 16)
                        | (scaleTypeBs[2] & 0xff << 8)
                        | (scaleTypeBs[3] & 0xff));
    }

    /**
     * 解析 cmap 表，将 Unicode 解析为 Glyph ID。
     *
     * @param randomAccessFile 字体数据。
     * @param cmap             cmap 表。
     * @param codePoint        Unicode 编码。
     * @return Glyph ID。
     */
    static int glyphIndex(RandomAccessFile randomAccessFile, TableRecord cmap, int codePoint) throws IOException {
        long cmapOffset = cmap.offset;
        System.out.println("cmapOffset = " + cmapOffset);

        randomAccessFile.seek(cmapOffset);

        byte[] bs = new byte[2];
        randomAccessFile.readFully(bs);

        int version = BigUnsignedHelper.readUnsignedShort(bs);
        System.out.println("version = " + version);

        randomAccessFile.readFully(bs);
        int numOfTables = BigUnsignedHelper.readUnsignedShort(bs);
        System.out.println("numOfTables = " + numOfTables);

        // 读取 Encoding Record
        cmap.ers = new ArrayList<>();
        for (int i = 0; i < numOfTables; i++) {
            EncodingRecord er = new EncodingRecord();

            bs = new byte[2];

            randomAccessFile.seek(cmapOffset + 4 + i * 8L);

            randomAccessFile.readFully(bs);
            er.platformId = BigUnsignedHelper.readUnsignedShort(bs);
            randomAccessFile.readFully(bs);
            er.encodingId = BigUnsignedHelper.readUnsignedShort(bs);

            bs = new byte[4];
            randomAccessFile.readFully(bs);
            er.offset = BigUnsignedHelper.readUnsignedInt(bs);

            cmap.ers.add(er);

            // 读取子表
            readCmapSubTable(randomAccessFile, er, cmapOffset);
        }

        EncodingRecord bestEr = null;
        int bestScore = 0;
        int bestFormat = 0;

        for (EncodingRecord er : cmap.ers) {
            // 计算分数
            int score;
            if (er.platformId == 0) {
                score = 300;
            } else if (er.platformId == 3 && er.encodingId == 10) {
                score = 260;
            } else if (er.platformId == 3 && er.encodingId == 1) {
                score = 220;
            } else {
                continue;
            }

            if (er.cst.format == 12) {
                score += 10;
            }

            if (score > bestScore) {
                bestScore = score;
                bestEr = er;
                bestFormat = er.cst.format;
            }
        }

        if (bestEr != null) {
            if (bestFormat == 4) {
                return readFormat4(randomAccessFile, cmapOffset, bestEr, codePoint);
            }
        }

        return 0;
    }

    private static int readFormat4(RandomAccessFile randomAccessFile, long cmapOffset, EncodingRecord er, int codePoint) throws IOException {
        // segCount
        byte[] bs = new byte[2];
        randomAccessFile.seek(cmapOffset + er.offset + 6);
        randomAccessFile.readFully(bs);
        int segCount = BigUnsignedHelper.readUnsignedShort(bs) / 2;
        System.out.println("segCount = " + segCount);

        final long endCodeOffset = cmapOffset + er.offset + 14,
                startCodeOffset = cmapOffset + er.offset + 16 + 2 * segCount,
                idDeltaOffset = cmapOffset + er.offset + 16 + 4L * segCount,
                idRangeOffsetOffset = cmapOffset + er.offset + 16 + 6L * segCount,
                glyphIdArrayOffset = cmapOffset + er.offset + 16 + 8L * segCount;

        for (int i = 0; i < segCount; i++) {
            randomAccessFile.seek(endCodeOffset + i * 2);
            randomAccessFile.readFully(bs);

            // endCode
            int endCode = BigUnsignedHelper.readUnsignedShort(bs);

            // 码点比这段的最大值还大，看下一段
            if (codePoint > endCode) {
                continue;
            }

            randomAccessFile.seek(startCodeOffset + i * 2);
            randomAccessFile.readFully(bs);
            // startCode
            int startCode = BigUnsignedHelper.readUnsignedShort(bs);

            // 落在两段之间，说明缺字
            if (codePoint < startCode) {
                return 0;
            }

            randomAccessFile.seek(idDeltaOffset + i * 2);
            randomAccessFile.readFully(bs);
            int idDelta = BigUnsignedHelper.readUnsignedShort(bs);
            long idRangeOffsetAddress = idRangeOffsetOffset + i * 2;
            randomAccessFile.seek(idRangeOffsetAddress);
            randomAccessFile.readFully(bs);
            int idRangeOffset = BigUnsignedHelper.readUnsignedShort(bs);

            if (idRangeOffset == 0) {
                // 简单情况：码点 + 增量 就是字形
                return (codePoint + idDelta) & 0xFFFF;
            }

            // 复杂情况：先在 glyphIdArray 里找到字形，再加增量
            long glyphAddress = idRangeOffsetAddress
                    + idRangeOffset
                    + 2L * (codePoint - startCode);
            randomAccessFile.seek(glyphAddress);
            randomAccessFile.readFully(bs);
            int glyph = BigUnsignedHelper.readUnsignedShort(bs);
            if (glyph == 0) {
                return 0;
            }
            return (glyph + idDelta) & 0xFFFF;
        }

        return 0;
    }

    private static void readCmapSubTable(RandomAccessFile randomAccessFile, EncodingRecord er, long cmapOffset) throws IOException {
        er.cst = new CmapSubTable();
        randomAccessFile.seek(er.offset + cmapOffset);

        byte[] bs = new byte[2];
        randomAccessFile.readFully(bs);
        er.cst.format = BigUnsignedHelper.readUnsignedShort(bs);
    }

    /**
     * 渲染一个大写字母A。
     */
    static void renderA() {
    }

    /**
     * 渲染中文汉字“我”。
     */
    static void renderChineseChar() {
    }

    /**
     * ttf头部的地图信息
     */
    static class HeadMap {

        long scalerType;

        /**
         * 字体表的数量
         */
        long numOfTables;

        long searchRange;

        long entrySelector;

        long rangeShift;
    }

}
