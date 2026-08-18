package com.cell.demo.fonts.helper;

import com.cell.demo.fonts.model.GlyphPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhaokai
 * @since 2026.08.17
 */
public class GlyphPointHelper {

    static final double THRESHOLD = 0.5d;

    /**
     * 公式：B(t) = (1-t)²·P₀ + 2(1-t)t·P₁ + t²·P₂
     *
     * @param point1       点 1。
     * @param point2       点 2。
     * @param controlPoint 控制点。
     * @return 经过曲线计算的一系列点。
     */
    public static List<GlyphPoint> bezierCurve(GlyphPoint point1, GlyphPoint point2, GlyphPoint controlPoint) {
        List<GlyphPoint> list = new ArrayList<>();
        list.add(point1);
        List<GlyphPoint> splitted = split(point1, point2, controlPoint);
        if (!splitted.isEmpty()) {
            list.addAll(splitted);
        }
        list.add(point2);
        return list;
    }

    private static List<GlyphPoint> split(GlyphPoint point1, GlyphPoint point2, GlyphPoint controlPoint) {
        List<GlyphPoint> list = new ArrayList<>();

        if (continueSplit(point1, point2, controlPoint, THRESHOLD)) {
            // 继续拆分

            // 公式 B(t) = (1-t)²·P₀ + 2(1-t)t·P₁ + t²·P₂

            // 找到 B
            GlyphPoint b = bezierMiddlePoint(point1, point2, controlPoint);


            // 新控制点
            GlyphPoint c0 = getControlPoint(point1, controlPoint);

            List<GlyphPoint> splitted = split(point1, b, c0);

            if (!splitted.isEmpty()) {
                list.addAll(splitted);
            }

            list.add(b);

            // 新控制点
            GlyphPoint c1 = getControlPoint(controlPoint, point2);
            splitted = split(b, point2, c1);
            if (!splitted.isEmpty()) {
                list.addAll(splitted);
            }
        }

        return list;
    }

    public static GlyphPoint getMiddlePoint(GlyphPoint point1, GlyphPoint point2) {
        GlyphPoint controlPoint = getControlPoint(point1, point2);
        controlPoint.onCurve = true;
        return controlPoint;
    }

    private static GlyphPoint bezierMiddlePoint(GlyphPoint point1, GlyphPoint point2, GlyphPoint controlPoint) {
        // 公式 B(t) = (1-t)²·P₀ + 2(1-t)t·P₁ + t²·P₂
        double x1 = point1.x, x2 = point2.x, xc = controlPoint.x,
                y1 = point1.y, y2 = point2.y, yc = controlPoint.y;
        return new GlyphPoint(
                Math.pow(1 - THRESHOLD, 2) * x1
                        + 2 * (1 - THRESHOLD) * THRESHOLD * xc
                        + Math.pow(THRESHOLD, 2) * x2,
                Math.pow(1 - THRESHOLD, 2) * y1
                        + 2 * (1 - THRESHOLD) * THRESHOLD * yc
                        + Math.pow(THRESHOLD, 2) * y2,
                true
        );
    }

    private static GlyphPoint getControlPoint(GlyphPoint point, GlyphPoint controlPoint) {
        double x = point.x, cx = controlPoint.x;
        double y = point.y, cy = controlPoint.y;
        return new GlyphPoint(
                (int) (x + (cx - x) * THRESHOLD),
                (int) (y + (cy - y) * THRESHOLD),
                false
        );
    }

    /**
     * 判断是否需要继续切分
     *
     * @param point1       点 1。
     * @param point2       点 2。
     * @param controlPoint 控制点。
     * @return true：继续切割；false：不切割。
     */
    private static boolean continueSplit(GlyphPoint point1, GlyphPoint point2, GlyphPoint controlPoint, double threshold) {
        // 向量 ab (底边)
        double dx = point2.x - point1.x;
        double dy = point2.y - point1.y;

        // 向量 ap
        double px = controlPoint.x - point1.x;
        double py = controlPoint.y - point1.y;

        // 叉积的绝对值 = 平行四边形面积
        double cross = Math.abs(dx * py - dy * px);

        // 底边长度
        double baseLen = Math.sqrt(dx * dx + dy * dy);

        // 如果 P0 和 P2 重合，线段长度为0，直接返回 P1 到 P0 的距离
        if (baseLen < 1e-10) {
            return Math.sqrt(px * px + py * py) > threshold;
        }

        // 高 = 面积 / 底边
        return cross / baseLen > threshold;
    }

}
