package com.cell.demo.fonts.helper;

/**
 * 大端字节序组合法。
 *
 * @author zhaokai
 * @since 2026.07.26
 */
public class BigUnsignedHelper {

    public static long readUnsignedInt(byte[] bytes) {
        return ((long) bytes[0] & 0xff) << 24
                | ((long) (bytes[1] & 0xff) << 16)
                | ((long) (bytes[2] & 0xff) << 8)
                | ((long) bytes[3] & 0xff);
    }

    public static int readUnsignedShort(byte[] bytes) {
        return ((int) bytes[0] & 0xff) << 8
                | ((int) bytes[1] & 0xff);
    }

    public static int readUnsignedByte(byte b) {
        return (int) b & 0xff;
    }

}
