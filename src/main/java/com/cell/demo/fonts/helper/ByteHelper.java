package com.cell.demo.fonts.helper;

/**
 * @author zhaokai
 * @since 2026.07.26
 */
public class ByteHelper {

    public static void prettyPrint(byte[] bytes) {
        if (bytes != null && bytes.length != 0) {
            int count = 0;
            final int total = bytes.length;
            for (byte b : bytes) {
                ++count;
                System.out.printf("%s%s%s", b < 16 ? "0" : "", Integer.toHexString(b), count != total ? " " : "");

                // 16个字节打印一行，再多了就需要换行了。
                // 判断total是：如果total恰好是16的倍数，那么后面会有一个空行，需要避免这个空行。
                if (count % 16 == 0 || (total % 16 != 0 && count == total)) {
                    System.out.println();
                }
            }
        }
    }

}
