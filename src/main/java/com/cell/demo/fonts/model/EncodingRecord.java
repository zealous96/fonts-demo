package com.cell.demo.fonts.model;

/**
 * cmap 表中的 Encoding Record。
 *
 * @author zhaokai
 * @since 2026.08.13
 */
public class EncodingRecord {

    public int platformId;

    public int encodingId;

    public long offset;

    public CmapSubTable cst;

}
