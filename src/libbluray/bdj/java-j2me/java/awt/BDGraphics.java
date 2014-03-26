/*
 * This file is part of libbluray
 * Copyright (C) 2012  libbluray
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package java.awt;

import java.lang.reflect.Field;
import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.awt.image.AreaAveragingScaleFilter;
import java.awt.image.BufferedImage;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageObserver;

import org.dvb.ui.DVBBufferedImage;

import sun.awt.ConstrainableGraphics;

import org.videolan.Logger;

class BDGraphics extends Graphics2D implements ConstrainableGraphics {
    private static final Color DEFAULT_COLOR = Color.BLACK;
    private static final Font DEFAULT_FONT = new Font("Dialog", Font.PLAIN, 12);

    private int width;
    private int height;
    private int[] backBuffer;
    private Area dirty;
    private GraphicsConfiguration gc;
    private Color foreground;
    private Color background;
    private Font font;
    private BDFontMetrics fontMetrics;
    private AlphaComposite composite;

    /** The current xor color. If null then we are in paint mode. */
    private Color xorColor;

    /** Translated X, Y offset from native offset. */
    private int originX;
    private int originY;

    /** The actual clip rectangle that is intersection of user clip and constrained rectangle. */
    private Rectangle actualClip;

    /** The current user clip rectangle or null if no clip has been set. This is stored in the
     native coordinate system and not the (possibly) translated Java coordinate system. */
    private Rectangle clip = null;

    /** The rectangle this graphics object has been constrained too. This is stored in the
     native coordinate system and not the (possibly) translated Java coordinate system.
     If it is null then this graphics has not been constrained. The constrained rectangle
     is another layer of clipping independant of the user clip. */
    private Rectangle constrainedRect = null;

    BDGraphics(BDGraphics g) {
        backBuffer = g.backBuffer;
        dirty = g.dirty;
        width = g.width;
        height = g.height;
        gc = g.gc;
        foreground = g.foreground;
        background = g.background;
        composite = g.composite;
        font = g.font;
        fontMetrics = g.fontMetrics;
        originX = g.originX;
        originY = g.originY;
        if (g.clip != null) {
            clip = new Rectangle(g.clip);
        }
        setupClip();
    }

    BDGraphics(BDRootWindow window) {
        width = window.getWidth();
        height = window.getHeight();
        backBuffer = window.getBdBackBuffer();
        dirty = window.getDirtyArea();
        gc = window.getGraphicsConfiguration();
        foreground = window.getForeground();
        background = window.getBackground();
        font = window.getFont();
        if (foreground == null)
            foreground = DEFAULT_COLOR;
        if (background == null)
            background = DEFAULT_COLOR;
        if (font == null)
            font = DEFAULT_FONT;
        fontMetrics = BDFontMetrics.getFontMetrics(font);
        composite = AlphaComposite.SrcOver;
        setupClip();
    }

    BDGraphics(BDImage image) {
        width = image.getWidth();
        height = image.getHeight();
        backBuffer = image.getBdBackBuffer();
        dirty = image.getDirtyArea();

        gc = image.getGraphicsConfiguration();
        Component component = image.getComponent();
        if (component != null) {
            foreground = component.getForeground();
            background = component.getBackground();
            font = component.getFont();
        }
        if (foreground == null)
            foreground = DEFAULT_COLOR;
        if (background == null)
            background = new Color(0, 0, 0, 0);
        if (font == null)
            font = DEFAULT_FONT;
        fontMetrics = BDFontMetrics.getFontMetrics(font);
        composite = AlphaComposite.SrcOver;
        setupClip();
    }

    public Graphics create() {
        return new BDGraphics(this);
    }

    public void translate(int x, int y) {
        originX += x;
        originY += y;
    }

    public void setFont(Font font) {
        if (font != null && !font.equals(this.font)) {
            this.font = font;
            fontMetrics = BDFontMetrics.getFontMetrics(font);
        }
    }

    public Font getFont() {
        return font;
    }

    public FontMetrics getFontMetrics() {
        return fontMetrics;
    }

    public FontMetrics getFontMetrics(Font font) {
        return BDFontMetrics.getFontMetrics(font);
    }

    public void setColor(Color c) {
        if ((c != null) && (c != foreground))
            foreground = c;
    }

    public Color getColor() {
        return foreground;
    }

    public Composite getComposite() {
        return composite;
    }

    public GraphicsConfiguration getDeviceConfiguration() {
        return gc;
    }

    public void setComposite(Composite comp) {
        if ((comp != null) && (comp != composite)) {
            if (!(comp instanceof AlphaComposite))
                throw new IllegalArgumentException("Only AlphaComposite is supported");
            composite = (AlphaComposite) comp;
        }
    }

    public void setPaintMode() {
        xorColor = null;
        composite = AlphaComposite.SrcOver;
    }

    public void setXORMode(Color color) {
        xorColor = color;
    }

    /** Gets the current clipping area. */
    public Rectangle getClipBounds() {
        if (clip != null)
            return new Rectangle (clip.x - originX, clip.y - originY, clip.width, clip.height);
        return null;
    }

    public void constrain(int x, int y, int w, int h) {
        Rectangle rect;
        if (constrainedRect != null)
            rect = constrainedRect;
        else
            rect = new Rectangle(0, 0, width, height);
        constrainedRect = rect.intersection(new Rectangle(rect.x + x, rect.y + y, w, h));
        originX = constrainedRect.x;
        originY = constrainedRect.y;
        setupClip();
    }

    /** Returns a Shape object representing the clip. */
    public Shape getClip() {
        return getClipBounds();
    }

    /** Crops the clipping rectangle. */
    public void clipRect(int x, int y, int w, int h) {
        Rectangle rect = new Rectangle(x + originX, y + originY, w, h);
        if (clip != null)
            clip = clip.intersection(rect);
        else
            clip = rect;
        setupClip();
    }

    /** Sets the clipping rectangle. */
    public void setClip(int x, int y, int w, int h) {
        clip = new Rectangle (x + originX, y + originY, w, h);
        setupClip();
    }

    /** Sets the clip to a Shape (only Rectangle allowed). */
    public void setClip(Shape clip) {
        if (clip == null) {
            this.clip = null;
            setupClip();
        } else if (clip instanceof Rectangle) {
            Rectangle rect = (Rectangle) clip;
            setClip(rect.x, rect.y, rect.width, rect.height);
        } else
            throw new IllegalArgumentException("setClip(Shape) only supports Rectangle objects");
    }

    private void setupClip() {
        Rectangle rect;
        if (constrainedRect != null)
            rect = constrainedRect;
        else
            rect = new Rectangle(0, 0, width, height);
        if (clip != null)
            actualClip = clip.intersection(rect);
        else
            actualClip = rect;
    }

    private int alphaBlend(int dest, int src) {
        int As = src >>> 24;
        if (As == 0)
            return dest;
        if (As == 255)
            return src;
        int Ad = (dest >>> 24);
        if (Ad == 0)
            return src;
        int R, G, B;
        R = ((src >>> 16) & 255) * As * 255;
        G = ((src >>> 8) & 255) * As * 255;
        B = (src & 255) * As * 255;
        Ad = Ad * (255 - As);
        As = As * 255 + Ad;
        R = (R + ((dest >>> 16) & 255) * Ad) / As;
        G = (G + ((dest >>> 8) & 255) * Ad) / As;
        B = (B + (dest & 255) * Ad) / As;
        R = Math.min(255, R);
        G = Math.min(255, G);
        B = Math.min(255, B);
        Ad = As / 255;
        Ad = Math.min(255, Ad);
        return (Ad << 24) | (R << 16) | (G << 8) | B;
    }

    private int applyComposite(int rgb) {
        return ((int)((rgb >>> 24) * composite.getAlpha()) << 24) | (rgb & 0x00FFFFFF);
    }

    private void drawSpanN(int x, int y, int length, int rgb) {

        Rectangle rect = new Rectangle(x, y, length, 1);
        rect = actualClip.intersection(rect);

        if (rect.width <= 0 || rect.height <= 0 || rect.x < 0 || rect.y < 0) {
            return;
        }

        x      = rect.x;
        length = rect.width;

        dirty.add(rect);

        if (xorColor != null) {
            for (int i = 0; i < length; i++) {
                backBuffer[y * width + x + i] ^= xorColor.getRGB() ^ rgb;
            }
            return;
        }

        switch (composite.getRule()) {
            case AlphaComposite.CLEAR:
                for (int i = 0; i < length; i++) {
                    backBuffer[y * width + x + i] = 0;
                }
                break;
            case AlphaComposite.SRC:
                rgb = applyComposite(rgb);
                for (int i = 0; i < length; i++) {
                    backBuffer[y * width + x + i] = rgb;
                }
                break;
            case AlphaComposite.SRC_OVER:
                rgb = applyComposite(rgb);
                for (int i = 0; i < length; i++) {
                    backBuffer[y * width + x + i] = alphaBlend(backBuffer[y * width + x + i], rgb);
                }
                break;
        }
    }

    private void drawSpanN(int x, int y, int length, int src[], int srcOffset) {

        Rectangle rect = new Rectangle(x, y, length, 1);
        rect = actualClip.intersection(rect);

        if (rect.width <= 0 || rect.height <= 0 || rect.x < 0 || rect.y < 0) {
            return;
        }

        srcOffset += rect.x - x;
        x          = rect.x;
        length     = rect.width;

        dirty.add(rect);

        if (xorColor != null) {
            for (int i = 0; i < length; i++) {
                backBuffer[y * width + x + i] ^= xorColor.getRGB() ^ src[srcOffset + i];
            }
            return;
        }

        switch (composite.getRule()) {
            case AlphaComposite.CLEAR:
                for (int i = 0; i < length; i++) {
                    backBuffer[y * width + x + i] = 0;
                }
                break;
            case AlphaComposite.SRC:
                for (int i = 0; i < length; i++) {
                    backBuffer[y * width + x + i] = applyComposite(src[srcOffset + i]);
                }
                break;
            case AlphaComposite.SRC_OVER:
                for (int i = 0; i < length; i++) {
                    backBuffer[y * width + x + i] = alphaBlend(backBuffer[y * width + x + i], applyComposite(src[srcOffset + i]));
                }
                break;
        }
    }

    private void drawSpan(int x, int y, int length, int rgb) {
        x += originX;
        y += originY;
        drawSpanN(x, y, length, rgb);
    }

    private void drawSpan(int x, int y, int length, int src[], int srcOffset) {
        x += originX;
        y += originY;
        drawSpanN(x, y, length, src, srcOffset);
    }

    private void drawPointN(int x, int y, int rgb) {
        drawSpanN(x, y, 1, rgb);
    }

    private void drawGlyph(int[] rgbArray, int x0, int y0, int w, int h) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                drawPoint(x + x0, y + y0, rgbArray[y * w + x]);
    }

    private void drawPoint(int x, int y, int rgb) {
        x += originX;
        y += originY;
        if (actualClip.contains(x, y))
            drawPointN(x, y, rgb);
    }

    public void clearRect(int x, int y, int w, int h) {
        x += originX;
        y += originY;
        Rectangle rect = new Rectangle(x, y, w, h);
        rect = actualClip.intersection(rect);
        if (rect.isEmpty()) {
            return;
        }
        x = rect.x;
        y = rect.y;
        w = rect.width;
        h = rect.height;
        int rgb = background.getRGB();
        for (int i = 0; i < h; i++)
            Arrays.fill(backBuffer, (y + i) * width + x, (y + i) * width + x + w, rgb);

        dirty.add(rect);
    }

    public void fillRect(int x, int y, int w, int h) {
        x += originX;
        y += originY;
        Rectangle rect = new Rectangle(x, y, w, h);
        rect = actualClip.intersection(rect);
        x = rect.x;
        y = rect.y;
        w = rect.width;
        h = rect.height;
        int rgb = foreground.getRGB();
        for (int Y = y; Y < (y + h); Y++)
            drawSpanN(x, Y, w, rgb);
    }

    public void drawRect(int x, int y, int w, int h) {
        x += originX;
        y += originY;

        drawLineN(x, y, x + w, y);
        drawLineN(x, y + h, x + w, y + h);
        drawLineN(x, y, x, y + h);
        drawLineN(x + w, y, x + w, y + h);
    }

    private void drawLineN(int x1, int y1, int x2, int y2) {
        int rgb = foreground.getRGB();
        int dy = y2 - y1;
        int dx = x2 - x1;
        int stepx, stepy;
        int fraction;
        if (dy < 0) {
            dy = -dy;
            stepy = -1;
        } else {
            stepy = 1;
        }
        if (dx < 0) {
            dx = -dx;
            stepx = -1;
        } else {
            stepx = 1;
        }
        dy <<= 1;
        dx <<= 1;

        drawPointN(x1, y1, rgb);

        if (dx > dy) {
            fraction = dy - (dx >> 1);
            while (x1 != x2) {
                if (fraction >= 0) {
                    y1 += stepy;
                    fraction -= dx;
                }
                x1 += stepx;
                fraction += dy;
                drawPointN(x1, y1, rgb);
            }
        } else {
            fraction = dx - (dy >> 1);
            while (y1 != y2) {
                if (fraction >= 0) {
                    x1 += stepx;
                    fraction -= dy;
                }
                y1 += stepy;
                fraction += dx;
                drawPointN(x1, y1, rgb);
            }
        }
    }

    public void drawLine(int x1, int y1, int x2, int y2) {

        x1 += originX;
        y1 += originY;

        x2 += originX;
        y2 += originY;

        drawLineN(x1, y1, x2, y2);
    }

    /**
     * Copies an area of the canvas that this graphics context paints to.
     * @param X the x-coordinate of the source.
     * @param Y the y-coordinate of the source.
     * @param W the width.
     * @param H the height.
     * @param dx the horizontal distance to copy the pixels.
     * @param dy the vertical distance to copy the pixels.
     */
    public void copyArea(int x, int y, int w, int h, int dx, int dy) {

        x += originX;
        y += originY;

        Rectangle rect = new Rectangle(x, y, w, h);
        rect = actualClip.intersection(rect);

        if (rect.width <= 0 || rect.height <= 0) {
            return;
        }

        x = rect.x;
        y = rect.y;
        w = rect.width;
        h = rect.height;

        int subImage[] = new int[w * h];

        // copy back buffer
        for (int i = 0; i < h; i++) {
            System.arraycopy(backBuffer, ((y + i) * width) + x, subImage, w * i, w);
        }

        // draw sub image
        for (int i = 0; i < h; i++) {
            drawSpanN(x + dx, y + i + dy, w, subImage, w * i);
        }
    }

    /** Draws lines defined by an array of x points and y points */
    public void drawPolyline(int xPoints[], int yPoints[], int nPoints) {
        if (nPoints == 1) {
            drawPoint(xPoints[0], yPoints[0], foreground.getRGB());
        } else {
            for (int i = 0; i < (nPoints - 1); i++)
                drawLine(xPoints[i], xPoints[i], xPoints[i + 1], xPoints[i + 1]);
        }
    }

    /** Draws a polygon defined by an array of x points and y points */
    public void drawPolygon(int xPoints[], int yPoints[], int nPoints) {
        if (nPoints == 1) {
            drawPoint(xPoints[0], yPoints[0], foreground.getRGB());
        } else {
            for (int i = 0; i < (nPoints - 1); i++)
                drawLine(xPoints[i], xPoints[i], xPoints[i + 1], xPoints[i + 1]);
            if (nPoints > 2)
                drawLine(xPoints[0], xPoints[0], xPoints[nPoints - 1], xPoints[nPoints - 1]);
        }
    }

    /** Fills a polygon with the current fill mask */
    public void fillPolygon(int xPoints[], int yPoints[], int nPoints) {

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int colour = foreground.getRGB();

        if (nPoints < 3) {
            return;
        }

        for (int i = 0; i < nPoints; i++) {
            if (yPoints[i] > maxY) {
                maxY = yPoints[i];
            }
            if (yPoints[i] < minY) {
                minY = yPoints[i];
            }
        }

        // check the last point to see if its the same as the first
        if (xPoints[0] == xPoints[nPoints - 1] && yPoints[0] == yPoints[nPoints - 1]) {
            nPoints--;
        }

        PolyEdge[] polyEdges = new PolyEdge[nPoints];

        for (int i = 0; i < nPoints - 1; i++) {
            polyEdges[i] = new PolyEdge(xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
        }

        // add the last one
        polyEdges[nPoints - 1] = new PolyEdge(xPoints[nPoints - 1], yPoints[nPoints - 1], xPoints[0], yPoints[0]);
        ArrayList xList = new ArrayList();
        for (int i = minY; i <= maxY; i++) {
            for (int j = 0; j < nPoints; j++) {
                if (polyEdges[j].intersects(i)) {
                    int x = polyEdges[j].intersectionX(i);
                    xList.add(new Integer(x));
                }
            }

            // probably a better way of doing this (removing duplicates);
            HashSet hs = new HashSet();
            hs.addAll(xList);
            xList.clear();
            xList.addAll(hs);

            if (xList.size() % 2 > 0) {
                xList.clear();
                continue;                       // this should be impossible unless the poly is open somewhere
            }

            Collections.sort(xList);

            for (int j = 0; j < xList.size(); j +=2 ) {
                int x1 = ((Integer)xList.get(j)).intValue();
                int x2 = ((Integer)xList.get(j + 1)).intValue();

                drawSpan(x1, i, x2 - x1, colour);
            }

            xList.clear();
        }
    }

    /** Draws an oval to fit in the given rectangle */
    public void drawOval(int x, int y, int w, int h) {

        int     startX;
        int     endX;
        int     offset;
        int[]   xList;
        int[]   yList;
        int     numPoints;
        int     count;
        float   as;
        float   bs;

        if (w <= 0 || h <=0 ) {
            return;
        }

        count = 0;
        numPoints = ((h/2) + (h/2) + 1) * 2;
        numPoints += 1; // to close
        xList = new int[numPoints];
        yList = new int[numPoints];

        as = (w/2.0f) * (w/2.0f);
        bs = (h/2.0f) * (h/2.0f);

        for (int i = -h/2; i <= h/2; i++) {
            offset = (int) Math.sqrt( (1.0 - ((i*i)/bs)) * as );
            startX  = x - offset + w/2;

            xList[count] = startX;
            yList[count] = y + i + h/2;
            count++;
        }

        for (int i = h/2; i >= -h/2; i--) {
            offset = (int) Math.sqrt( (1.0 - ((i*i)/bs)) * as );
            endX    = x + offset + w/2;

            xList[count] = endX;
            yList[count] = y + i + h/2;
            count++;
        }

        xList[count] = xList[0];        // close the loop
        yList[count] = yList[0];        // close the loop

        drawPolyline(xList, yList, numPoints);
    }

    /** Fills an oval to fit in the given rectangle */
    public void fillOval(int x, int y, int w, int h) {

        int     startX;
        int     endX;
        int     offset;
        int     colour;
        float   as;
        float   bs;

        if (w <= 0 || h <= 0) {
            return;
        }

        as = (w/2.0f) * (w/2.0f);
        bs = (h/2.0f) * (h/2.0f);
        colour = foreground.getRGB();

        for(int i=-h/2; i<=h/2; i++) {
            offset  = (int) Math.sqrt( (1.0 - ((i*i)/bs)) * as );
            startX  = x - offset + w/2;
            endX    = x + offset + w/2;

            drawSpan(startX, y + i + h/2, endX - startX + 1, colour);
        }
    }

    /**
     * Draws an arc bounded by the given rectangle from startAngle to
     * endAngle. 0 degrees is a vertical line straight up from the
     * center of the rectangle. Positive start angle indicate clockwise
     * rotations, negative angle are counter-clockwise.
     */
    public void drawArc(int x, int y, int w, int h, int startAngle, int endAngle) {
        logger.unimplemented("drawArc");
    }

    /** fills an arc. arguments are the same as drawArc. */
    public void fillArc(int x, int y, int w, int h, int startAngle, int endAngle) {
        logger.unimplemented("fillArc");
    }

    /** Draws a rounded rectangle. */
    public void drawRoundRect(int x, int y, int w, int h, int arcWidth, int arcHeight) {

        int[]   xList;
        int[]   yList;
        int     numPoints;
        int     count;
        int     startX;
        int     endX;
        int     offset;

        if (w <= 0 || h <= 0) {
            return;
        }

        if (arcWidth == 0 || arcHeight == 0) {
            drawRect(x, y, w, h);
            return;
        }

        if (arcWidth < 0) {                // matches behaviour of normal java version
            arcWidth *= -1;
        }

        if (arcHeight < 0) {
            arcHeight *= -1;
        }

        count = 0;
        numPoints = ((arcHeight/2) + 1) * 2;
        numPoints += ((arcHeight/2) + 1) * 2;
        numPoints += 1; // last point to close the loop

        xList = new int[numPoints];
        yList = new int[numPoints];

        float as = (arcWidth/2.0f)  * (arcWidth/2.0f);
        float bs = (arcHeight/2.0f) * (arcHeight/2.0f);

        // draw top curved half of box

        for (int i = 0; -arcHeight/2 <= i; i--) {
            offset = (int) Math.sqrt( (1.0 - ((i*i)/bs)) * as );
            startX  = x - offset + arcWidth/2;

            xList[count] = startX;
            yList[count] = y+i+(arcHeight/2);
            count++;
        }

        for (int i = -arcHeight / 2; i <= 0; i++) {
            offset = (int) Math.sqrt( (1.0 - ((i*i)/bs)) * as );
            endX    = x + offset + (w-arcWidth) + arcWidth/2;

            xList[count] = endX;
            yList[count] = y + i + (arcHeight/2);
            count++;
        }

        // draw bottom box
        for (int i = 0; i <= arcHeight / 2; i++) {
            offset = (int) Math.sqrt( (1.0 - ((i*i)/bs)) * as );
            startX  = x - offset + arcWidth/2;
            endX    = x + offset + (w - arcWidth) + arcWidth/2;

            xList[count] = endX;
            yList[count] = y + i + h - arcHeight/2;
            count++;
        }

        // draw bottom box
        for (int i = arcHeight / 2; i >= 0; i--) {
            offset = (int) Math.sqrt( (1.0 - ((i*i)/bs)) * as );
            startX  = x - offset + arcWidth/2;
            endX    = x + offset + (w-arcWidth) + arcWidth/2;

            xList[count] = startX;
            yList[count] = y+i+h-arcHeight/2;
            count++;
        }

        xList[count] = xList[0];
        yList[count] = yList[0];

        drawPolyline(xList, yList, numPoints);
    }

    /** Draws a filled rounded rectangle. */
    public void fillRoundRect(int x, int y, int w, int h, int arcWidth, int arcHeight) {

        int startX;
        int endX;
        int offset;
        int colour;

        if (w <= 0 || h <= 0) {
            return;
        }

        if (arcWidth == 0 || arcHeight == 0) {
            fillRect(x,y,w,h);
            return;
        }

        if (arcWidth < 0) {                // matches behaviour of normal java version
            arcWidth *= -1;
        }

        if (arcHeight < 0) {
            arcHeight *= -1;
        }

        float as = (arcWidth/2.0f)  * (arcWidth/2.0f);
        float bs = (arcHeight/2.0f) * (arcHeight/2.0f);

        colour = foreground.getRGB();

        // draw top curved half of box
        for (int i = -arcHeight/2; i < 0; i++) {
            offset = (int) Math.sqrt( (1.0 - ((i*i)/bs)) * as );
            startX  = x - offset + arcWidth/2;
            endX    = x + offset + (w - arcWidth) + arcWidth/2;

            drawSpan(startX, y + i + (arcHeight/2), endX - startX + 1, colour);
        }

        // draw middle section
        for (int i = 0; i < h - arcHeight; i++) {
            drawSpan(x, y + i + arcHeight/2, w, colour);
        }

        // draw bottom box
        for (int i = 0; i <= arcHeight/2; i++) {
            offset = (int) Math.sqrt( (1.0 - ((i*i)/bs)) * as );
            startX  = x - offset + arcWidth/2;
            endX    = x + offset + (w - arcWidth) + arcWidth/2;

            drawSpan(startX, y + i + h - 1 - arcHeight/2, endX - startX + 1, colour);
        }
    }

    protected native void drawStringN(long ftFace, String string, int x, int y, int rgb);

    /** Draws the given string. */
    public void drawString(String string, int x, int y) {
        if (fontMetrics != null) {
            fontMetrics.drawString(this, string, x, y, foreground.getRGB());
        }
    }

    /** Draws the given character array. */
    public void drawChars(char chars[], int offset, int length, int x, int y) {
        drawString(new String(chars, offset, length), x, y);
    }

    public void drawString(AttributedCharacterIterator arg0, int arg1, int arg2) {
        logger.unimplemented("drawString");
    }

    /**
     * Draws an image at x,y in nonblocking mode with a callback object.
     */
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        return drawImage(img, x, y, null, observer);
    }

    /**
     * Draws an image at x,y in nonblocking mode with a solid background
     * color and a callback object.
     */
    public boolean drawImage(Image img, int x, int y, Color bg,
        ImageObserver observer) {
        return drawImageN(img, x, y, -1, -1, 0, 0, -1, -1, bg, observer);
    }

    /**
     * Draws an image scaled to x,y,w,h in nonblocking mode with a
     * callback object.
     */
    public boolean drawImage(Image img, int x, int y, int w, int h,
        ImageObserver observer) {
        return drawImage(img, x, y, w, h, null, observer);
    }

    /**
     * Draws an image scaled to x,y,w,h in nonblocking mode with a
     * solid background color and a callback object.
     */
    public boolean drawImage(Image img, int x, int y, int w, int h,
        Color bg, ImageObserver observer) {
        return drawImageN(img, x, y, w, h, 0, 0, -1, -1, bg, observer);
    }

    /**
     * Draws a subrectangle of an image scaled to a destination rectangle
     * in nonblocking mode with a callback object.
     */
    public boolean drawImage(Image img,
        int dx1, int dy1, int dx2, int dy2,
        int sx1, int sy1, int sx2, int sy2,
        ImageObserver observer) {
        return drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null, observer);
    }

    /**
     * Draws a subrectangle of an image scaled to a destination rectangle in
     * nonblocking mode with a solid background color and a callback object.
     */
    public boolean drawImage(Image img,
        int dx1, int dy1, int dx2, int dy2,
        int sx1, int sy1, int sx2, int sy2,
        Color bg, ImageObserver observer) {

        if (dx1 > dx2) {
            int swap = dx1;
            dx1 = dx2;
            dx2 = swap;
        }

        if (dy1 > dy2) {
            int swap = dy1;
            dy1 = dy2;
            dy2 = swap;
        }

        if (sx1 > sx2) {
            int swap = sx1;
            sx1 = sx2;
            sx2 = swap;
        }

        if (sy1 > sy2) {
            int swap = sy1;
            sy1 = sy2;
            sy2 = swap;
        }

        return drawImageN(img, dx1, dy1, dx2 - dx1, dy2 - dy1,
                          sx1, sy1, sx2 - sx1, sy2 - sy1, bg, observer);
    }

    /**
     * Draws a subrectangle of an image scaled to a destination rectangle in
     * nonblocking mode with a solid background color and a callback object.
     */
    protected boolean drawImageN(Image img,
        int dx, int dy, int dw, int dh,
        int sx, int sy, int sw, int sh,
        Color bg, ImageObserver observer) {
        if ((sx < 0) || (sy < 0) ||
            (sw == 0) || (sh == 0) || (dw == 0) || (dh == 0))
            return false;

        BDImage bdImage;
        if (img instanceof BDImage) {
            bdImage = (BDImage)img;
        } else if (img instanceof DVBBufferedImage) {
            bdImage = (BDImage)getBufferedImagePeer(
                      (BufferedImage)(((DVBBufferedImage)img).getImage()));
        } else if (img instanceof BufferedImage) {
            bdImage = (BDImage)getBufferedImagePeer((BufferedImage)img);
        } else {
            logger.unimplemented("drawImageN: unsupported image type " + img.getClass().getName());
            return false;
        }

        if (bdImage instanceof BDImageConsumer) {
            BDImageConsumer consumer = (BDImageConsumer)bdImage;
            if (!consumer.isComplete(observer)) {
                return false;
            }
        }

        if(sx + sw > bdImage.width || sy + sh > bdImage.height) {
            logger.info("drawImageN: fixing too small src image (src " + sx + "," + sy + " " + sw + "x" + sh + " ; img " + bdImage.width + "x" + bdImage.height + ")");

            BDImage subImage = new BDImage(null, sw, sh, null);
            bdImage.getRGB(sx, sy, Math.min(sw, bdImage.width - sx), Math.min(sh, bdImage.height - sy), subImage.getBdBackBuffer(), 0, sw);
            bdImage = subImage;
            sx = 0;
            sy = 0;
        }
        /*
        if(sx + sw > bdImage.width) {
            int n = sx + sw - bdImage.width;
            dw -= dw * n / sw;
            sw -= n;
        }

        if(sy + sh > bdImage.height) {
            int n = sy + sh - bdImage.height;
            dh -= dh * n / sh;
            sh -= n;
        }
        */

        if ((sw > 0) && (sh > 0) &&
            ((sx != 0) || (sy != 0) || (sw != bdImage.width) || (sh != bdImage.height))) {
            BDImage subImage = new BDImage(null, sw, sh, null);
            bdImage.getRGB(sx, sy, sw, sh, subImage.getBdBackBuffer(), 0, sw);
            bdImage = subImage;
        }
        if ((dw > 0) && (dh > 0) &&
            ((dw != bdImage.width) || (dh != bdImage.height))) {
            BDImageConsumer scaledImage = new BDImageConsumer(null);
            AreaAveragingScaleFilter scaleFilter =
                new AreaAveragingScaleFilter(dw, dh);
            scaleFilter = (AreaAveragingScaleFilter)scaleFilter.getFilterInstance(scaledImage);
            scaleFilter.setDimensions(bdImage.width, bdImage.height);
            scaleFilter.setPixels(0, 0, bdImage.width, bdImage.height,
                                  bdImage.getColorModel(), bdImage.getBdBackBuffer(),
                                  0, bdImage.width);
            scaleFilter.imageComplete(ImageConsumer.STATICIMAGEDONE);
            bdImage = scaledImage;
        }
        int[] rgbArray = bdImage.getBdBackBuffer();
        int bgColor;
        if (bg != null)
            bgColor = bg.getRGB();
        else
            bgColor = 0;

        for (int y = dy; y < (dy + bdImage.height); y++) {

            if (bg != null) {
                drawSpan(dx, y, bdImage.width, bgColor);
            }

            drawSpan(dx, y, bdImage.width, rgbArray, (y - dy) * bdImage.width);
        }

        return true;
    }

    public Stroke getStroke() {
        logger.unimplemented("getStroke");
        throw new Error();
    }

    public void setStroke(Stroke stroke) {
        logger.unimplemented("setStroke");
    }

    public void dispose() {
    }

    public String toString() {
        return getClass().getName() + "[" + originX + "," + originY + "]";
    }

    private static Image getBufferedImagePeer(BufferedImage image) {
        try {
            return (Image)bufferedImagePeer.get(image);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Field bufferedImagePeer;

    static {
        try {
            Class c = Class.forName("java.awt.image.BufferedImage");
            bufferedImagePeer = c.getDeclaredField("peer");
            bufferedImagePeer.setAccessible(true);
        } catch (ClassNotFoundException e) {
            throw new AWTError("java.awt.image.BufferedImage not found");
        } catch (SecurityException e) {
            throw new AWTError("java.awt.image.BufferedImage.peer not accessible");
        } catch (NoSuchFieldException e) {
            throw new AWTError("java.awt.image.BufferedImage.peer not found");
        }
    }

    private static final Logger logger = Logger.getLogger(BDGraphics.class.getName());
}
