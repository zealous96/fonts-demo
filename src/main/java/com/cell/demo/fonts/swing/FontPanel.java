package com.cell.demo.fonts.swing;

import com.cell.demo.fonts.helper.GlyphPointHelper;
import com.cell.demo.fonts.model.GlyphPoint;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhaokai
 * @since 2026.08.17
 */
public class FontPanel extends JPanel {

    static final int OFFSET = 100;
    static final double HEIGHT = 350;

    private final List<List<GlyphPoint>> contours;
    private final float scale;

    static final boolean CLOSE_BEZIER = false;

    public FontPanel(List<List<GlyphPoint>> contours, float scale) {
        this.contours = contours;
        this.scale = scale;
    }

    private List<GlyphPoint> range(GlyphPoint start, GlyphPoint end, GlyphPoint controlPoint) {
        List<GlyphPoint> glyphPoints = GlyphPointHelper.bezierCurve(start, end, controlPoint);
        glyphPoints.removeLast();
        glyphPoints.removeFirst();
        return glyphPoints;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;

        List<List<GlyphPoint>> newContours = new ArrayList<>();

        for (List<GlyphPoint> glyphPoints : contours) {
            List<GlyphPoint> newContour = new ArrayList<>();

            int size = glyphPoints.size();

            for (int i = 0; i < size; ) {

                GlyphPoint point = glyphPoints.get(i);


                if (point.onCurve) {
                    newContour.add(point);
                    i++;
                    continue;
                } else if (CLOSE_BEZIER) {
                    i++;
                    continue;
                }

                boolean first = newContour.isEmpty();
                if (first) {
                    // 第一个是 off-curve，需要看最后一个

                    GlyphPoint last = glyphPoints.getLast();
                    if (last.onCurve) {
                        // 最后一个是 on-curve，那就作为起点
                        newContour.add(last);
                        continue;
                    }

                    // 最后一个是 off-curve，那就取两点的中点作为起点
                    GlyphPoint middlePoint = GlyphPointHelper.getMiddlePoint(point, last);
                    newContour.add(middlePoint);
                    continue;
                }

                // 控制点
                // 拿上一个点去计算
                GlyphPoint prev = newContour.getLast();
                GlyphPoint next = i + 1 < size ? glyphPoints.get(i + 1) : newContour.getFirst();
                GlyphPoint nnext = i + 2 < size ? glyphPoints.get(i + 2) : newContour.getFirst();
                if (next == nnext) {
                    // 都是第一个点
                    List<GlyphPoint> range = range(prev, next, point);
                    newContour.addAll(range);
                    break;
                }

                if (next.onCurve) {
                    List<GlyphPoint> range = range(prev, next, point);
                    newContour.addAll(range);
                    i++;
                    continue;
                }

                // 当前点和下个点都是 off-curve，取中点
                GlyphPoint middlePoint = GlyphPointHelper.getMiddlePoint(point, next);
                List<GlyphPoint> range = range(prev, middlePoint, point);
                newContour.addAll(range);
                newContour.add(middlePoint);
                i++;
            }

            newContours.add(newContour);
        }

        boolean fill = true;

        if (fill) {
            g2d.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );
        }

        Path2D.Double glyphPath = new Path2D.Double(Path2D.WIND_NON_ZERO);
        for (List<GlyphPoint> glyphPoints : newContours) {
            if (fill) {
                moveTo(glyphPath, glyphPoints.getFirst());
            }
            for (int i = 0; i < glyphPoints.size(); i++) {
                if (i == glyphPoints.size() - 1) {
                    break;
                }
                GlyphPoint next = glyphPoints.get(i + 1);
                if (fill) {
                    lineTo(glyphPath, glyphPoints.get(i), next);
                } else {
                    drawLine(g2d, glyphPoints.get(i), next);
                }
            }

            if (fill) {
                glyphPath.closePath();
            }
        }

        if (fill) {
            g2d.setColor(new Color(40, 110, 220));
            g2d.fill(glyphPath);

            g2d.setColor(new Color(25, 45, 85));
            g2d.setStroke(new BasicStroke(1f));
            g2d.draw(glyphPath);
        }
    }

    private void drawCurve(Graphics2D g2d, GlyphPoint curr, GlyphPoint nnext, GlyphPoint next) {
        List<GlyphPoint> points = GlyphPointHelper.bezierCurve(curr, nnext, next);
        int bezierSize = points.size();
        for (int bi = 0; bi < bezierSize - 1; bi++) {

            GlyphPoint bcurr = points.get(bi);
            GlyphPoint bnext = points.get(bi + 1);

            drawLine(g2d, bcurr, bnext);

            if (bi + 1 == bezierSize - 1) {
                drawLine(g2d, bnext, nnext);
            }
        }
    }

    private void moveTo(Path2D.Double glyphPath, GlyphPoint curr) {
        glyphPath.moveTo(curr.x * scale + OFFSET, HEIGHT - curr.y * scale + OFFSET);
    }

    private void lineTo(Path2D.Double glyphPath, GlyphPoint curr, GlyphPoint next) {
        glyphPath.lineTo(next.x * scale + OFFSET, HEIGHT - next.y * scale + OFFSET);
    }

    private void drawLine(Graphics2D g2d, GlyphPoint curr, GlyphPoint next) {
        g2d.draw(new Line2D.Double(
                curr.x * scale + OFFSET,
                HEIGHT - curr.y * scale + OFFSET,
                next.x * scale + OFFSET,
                HEIGHT - next.y * scale + OFFSET
        ));
    }

}
