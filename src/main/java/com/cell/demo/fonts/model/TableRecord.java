package com.cell.demo.fonts.model;

import com.cell.demo.fonts.helper.BigUnsignedHelper;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Locale;

/**
 * 字体表信息
 *
 * @author zhaokai
 * @since 1.0.0
 */
public class TableRecord {
    /**
     * 表名，glyf、head等等字体表名称。
     */
    public String tag;

    public long checksum;

    public long offset;

    public long length;

    // head表专属
    public long xMin, xMax, yMin, yMax;
    public long magicNumber;
    public int indexToLocFormat;
    public int unitsPerEm;

    // cmap
    public List<EncodingRecord> ers;

    public void calcPosition4Head(RandomAccessFile randomAccessFile) throws IOException {
        if (this.tag == null || this.offset == 0L) {
            return;
        }

        byte[] bs = new byte[2];
        randomAccessFile.seek(this.offset + 36);
        randomAccessFile.readFully(bs);
        this.xMin = BigUnsignedHelper.readUnsignedShort(bs);

        randomAccessFile.readFully(bs);
        this.yMin = BigUnsignedHelper.readUnsignedShort(bs);

        randomAccessFile.readFully(bs);
        this.xMax = BigUnsignedHelper.readUnsignedShort(bs);

        randomAccessFile.readFully(bs);
        this.yMax = BigUnsignedHelper.readUnsignedShort(bs);

        bs = new byte[4];
        randomAccessFile.seek(this.offset + 12);
        randomAccessFile.readFully(bs);
        this.magicNumber = BigUnsignedHelper.readUnsignedInt(bs);
        System.out.println("head magic number: 0x" + Long.toHexString(this.magicNumber).toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return "tag: %s\tchecksum: %d\toffset: %d\tlength: %d".formatted(tag, checksum, offset, length);
    }
}