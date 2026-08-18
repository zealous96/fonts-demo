package com.cell.demo.fonts.model;

/**
 * @author zhaokai
 * @since 2026.08.17
 */
public class GlyphPoint {

    public double x;

    public double y;

    public boolean onCurve;

    public GlyphPoint() {
    }

    public GlyphPoint(double x, double y, boolean onCurve) {
        this.x = x;
        this.y = y;
        this.onCurve = onCurve;
    }

    @Override
    public String toString() {
        return "GlyphPoint(%.2f, %.2f%s)".formatted(this.x, this.y, this.onCurve ? ", on-curve" : ", off-curve");
    }
}
