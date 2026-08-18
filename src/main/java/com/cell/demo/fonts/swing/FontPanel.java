package com.cell.demo.fonts.swing;

import com.cell.demo.fonts.helper.GlyphPointHelper;
import com.cell.demo.fonts.model.GlyphPoint;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
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

    public FontPanel(List<List<GlyphPoint>> contours, float scale) {
        this.contours = contours;
        this.scale = scale;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;

        for (List<GlyphPoint> glyphPoints : contours) {
            int size = glyphPoints.size();
            GlyphPoint last = null;
            for (int i = 0; i < size - 1; ) {
                GlyphPoint curr = glyphPoints.get(i);
                GlyphPoint next = glyphPoints.get(i + 1);

                // 判断是否贝塞尔曲线
                if (!next.onCurve) {
                    // 是的

                    // 取下一个
                    GlyphPoint nnext;
                    if (i + 2 < size) {
                        nnext = glyphPoints.get(i + 2);
                    } else {
                        // 取第一个
                        nnext = glyphPoints.getFirst();
                        if (!nnext.onCurve) {
                            // 第一个也是 off，那么就要和最后一个取中点
                            nnext = GlyphPointHelper.getMiddlePoint(nnext, glyphPoints.getLast());
                        }
                    }

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

                    if (i + 1 == size - 1) {
                        last = nnext;
                    }

                    i = i + 2;
                    continue;
                }

                drawLine(g2d, curr, next);

                if (i + 1 == size - 1) {
                    last = next;
                }
                ++i;
            }
            if (last == null) {
                last = glyphPoints.getLast();
            }
            drawLine(g2d, last, glyphPoints.getFirst());
        }

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
