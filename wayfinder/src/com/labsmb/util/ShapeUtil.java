package com.labsmb.util;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;

import processing.core.PApplet;
import processing.core.PImage;

public class ShapeUtil {
	public static final int DEFAULT_STROKE_WEIGHT = 1;
	public static final int[] DEFAULT_STROKE_COLOR = new int[] { 0, 0, 0 };
	public static final int DEFAULT_ALPHA_MAX = 255;
	private static final float lineSpacingMultiplier = (float) 0.45;

	// Make ctor private so you can only use this class statically.
	private ShapeUtil() {

	}

	/**
	 * Get a point on the circumference of a circle based on the angle in degrees.
	 * 
	 * @param radius
	 * @param angleInDegrees
	 * @param origin
	 * @return
	 */
	public static Point2D.Float getPointOnCircle(float radius, float angleInDegrees, Point2D.Float origin) {
		float x = (float) (radius * Math.cos(angleInDegrees * Math.PI / 180F)) + origin.x;
		float y = (float) (radius * Math.sin(angleInDegrees * Math.PI / 180F)) + origin.y;
		return new Point2D.Float(x, y);
	}

	/**
	 * Get the width and height of a rectangle containing text, based on the font size, the number of lines of text and internal padding.
	 * 
	 * @param lines
	 * @param textSize
	 * @param padLeft
	 * @param padRight
	 * @param padTop
	 * @param padBottom
	 * @return
	 */
	public static float[] getTooltipDimensions(PApplet pApplet, String[] lines, int textSize, float padLeft, float padRight, float padTop,
			float padBottom) {
		// Find longest line.
		float maxLine = 0;
		for (int i = 0; i < lines.length; i++) {
			if (pApplet.textWidth(lines[i]) > maxLine) {
				maxLine = pApplet.textWidth(lines[i]);
			}
		}

		float rectWidth = padLeft + maxLine + padRight;
		float rectHeight = padTop + (textSize * (1 + lineSpacingMultiplier) * lines.length) + padBottom;
		return new float[] { rectWidth, rectHeight };
	}

	/**
	 * Draw a circle on the Processing Applet, based on the Dot object.
	 * 
	 * @param pApplet
	 * @param dot
	 */
	public static void drawDot(PApplet pApplet, Dot dot) {
		drawCircle(pApplet, dot.getCenter().x, dot.getCenter().y, dot.getRadius(), dot.getInnerRgb(), dot.getOuterRgb(), dot.getStrokeWeight(),
				dot.getAlpha());
	}

	/**
	 * Draw a circle on the Processing Applet. Note: stroke == {-1} is a flag that means "no stroke".
	 * 
	 * @param pApplet
	 * @param x
	 * @param y
	 * @param radius
	 * @param fill
	 */
	public static void drawCircle(PApplet pApplet, float x, float y, float radius, int[] fill, int[] stroke, int strokeWeight, float alpha) {
		if (stroke.length == 3) {
			pApplet.strokeWeight(strokeWeight);
			pApplet.stroke(stroke[0], stroke[1], stroke[2]);
		} else {
			pApplet.noStroke();
		}
		pApplet.fill(fill[0], fill[1], fill[2], alpha);
		pApplet.ellipse(x, y, radius * 2, radius * 2);
		pApplet.stroke(DEFAULT_STROKE_COLOR[0], DEFAULT_STROKE_COLOR[1], DEFAULT_STROKE_COLOR[2]);
		pApplet.strokeWeight(DEFAULT_STROKE_WEIGHT);
	}

	public static void drawCircle(PApplet pApplet, float x, float y, float radius, int[] fill) {
		drawCircle(pApplet, x, y, radius, fill, DEFAULT_STROKE_COLOR, DEFAULT_STROKE_WEIGHT, DEFAULT_ALPHA_MAX);
	}

	public static void drawRectangleWithText(PApplet pApplet, float x, float y, int curve, float padLeft, float padRight, float padTop,
			float padBottom, int[] rectRgb, int[] textRgb, int textSize, String[] lines) {
		drawRectangleWithText(pApplet, x, y, curve, padLeft, padRight, padTop, padBottom, rectRgb, textRgb, textSize, lines, false, false);
	}

	/**
	 * Draw a rectangle on the Processing Applet and place text inside it.
	 * 
	 * @param pApplet
	 * @param x
	 * @param y
	 * @param curve
	 * @param padLeft
	 * @param padRight
	 * @param padTop
	 * @param padBottom
	 * @param rectRgb
	 * @param textRgb
	 * @param textSize
	 * @param lines
	 * @param alightRight
	 * @param alightBottom
	 */
	public static void drawRectangleWithText(PApplet pApplet, float x, float y, int curve, float padLeft, float padRight, float padTop,
			float padBottom, int[] rectRgb, int[] textRgb, int textSize, String[] lines, boolean alignRight, boolean alignBottom) {
		// Calculate the rectangle dimensions based on the amount of text and
		// the font size.
		float[] rectDims = ShapeUtil.getTooltipDimensions(pApplet, lines, textSize, padLeft, padRight, padTop, padBottom);
		float rectWidth = rectDims[0];
		float rectHeight = rectDims[1];

		// If the rectangle is right or bottom aligned, adjust the x or y
		// coordinate accordingly.
		float trueX = alignRight ? x - rectWidth - 1 : x; // - 1 accounts for
															// the stroke width.
		float trueY = alignBottom ? y - rectHeight + 1 : y; // + 1 accounts for
															// the
		// stroke width.

		String text = StringUtils.join(lines, "\n");
		pApplet.fill(rectRgb[0], rectRgb[1], rectRgb[2]);
		pApplet.rect(trueX, trueY, rectWidth, rectHeight, curve);
		pApplet.textSize(textSize);
		pApplet.fill(textRgb[0], textRgb[1], textRgb[2]);
		pApplet.text(text, trueX + padLeft, trueY + padTop);
	}

	/**
	 * Convert a string hex color to RGB values.
	 * 
	 * @param colorStr
	 *            (e.g. "#FFFFFF")
	 * @return
	 */
	public static int[] hexToRgb(String colorStr) throws IllegalArgumentException {
		if (!colorStr.substring(0, 1).equals("#")) {
			throw new IllegalArgumentException("Value passed to hexToRgb() should begin with \"#\". Given: " + colorStr);
		}
		Color c = Color.decode(colorStr);
		return new int[] { c.getRed(), c.getGreen(), c.getBlue() };
	}

	/**
	 * Get the current frame. (A hack for Eclipse from https://forum.processing.org/topic/trying-to-use-processing-in-eclipse).
	 * 
	 * @return
	 */
	public static java.awt.Frame findFrame(PApplet pApplet) {
		java.awt.Container f = pApplet.getParent();
		while (!(f instanceof java.awt.Frame) && f != null)
			f = f.getParent();
		return (java.awt.Frame) f;
	}

	/**
	 * Load all images in a given directory into a PImage array.
	 * 
	 * @param pApplet
	 * @param path
	 * @return
	 * @throws Exception
	 */
	public static PImage[] loadImages(PApplet pApplet, String path) throws Exception {
		// TODO: Add other file extensions here.
		final String[] allowedExtensions = new String[] { "jpg", "jpeg", "gif", "tif", "png" };
		ArrayList<PImage> pImages = new ArrayList<PImage>();

		File[] imagefiles = new File(path).listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				for (final String ext : allowedExtensions) {
					if (name.toLowerCase().endsWith("." + ext)) {
						return (true);
					}
				}
				return (false);
			}
		});

		for (final File fileEntry : imagefiles) {
			if (fileEntry.isFile()) {
				PImage tmpImg = pApplet.loadImage(fileEntry.getAbsolutePath());
				if (tmpImg == null)
					throw new Exception("The PImage for " + fileEntry.getAbsolutePath() + " was null for some reason");
				pImages.add(tmpImg);
			}
		}

		return pImages.toArray(new PImage[pImages.size()]);
	}

	/**
	 * Place an image in the center of the window.
	 * 
	 * @param pApplet
	 * @param image
	 */
	public static void centerImage(PApplet pApplet, PImage image) {
		centerImage(pApplet, image, 1.0f);
	}

	/**
	 * Place an image in the center of the window.
	 * 
	 * @param pApplet
	 * @param image
	 * @param scale
	 */
	public static void centerImage(PApplet pApplet, PImage image, float scale) {
		float effectiveWidth = image.width * scale;
		float effectiveHeight = image.height * scale;
		float x = (pApplet.width - effectiveWidth) / 2;
		float y = (pApplet.height - effectiveHeight) / 2;
		pApplet.image(image, x, y, effectiveWidth, effectiveHeight);
	}
	
	/**
	 * Draws a lines with arrows of the given angles at the ends.
	 * 
	 * SOURCE: http://www.openprocessing.org/sketch/7029
	 * 
	 * @param x0
	 *            starting x-coordinate of line
	 * @param y0
	 *            starting y-coordinate of line
	 * @param x1
	 *            ending x-coordinate of line
	 * @param y1
	 *            ending y-coordinate of line
	 * @param startAngle
	 *            angle of arrow at start of line (in radians)
	 * @param endAngle
	 *            angle of arrow at end of line (in radians)
	 * @param solid
	 *            true for a solid arrow; false for an "open" arrow
	 */
	public static void drawArrowLine(PApplet pApplet, float x0, float y0, float x1, float y1, float startAngle, float endAngle, boolean solid) {
		pApplet.line(x0, y0, x1, y1);
		if (startAngle != 0) {
			ShapeUtil.drawArrowhead(pApplet, x0, y0, PApplet.atan2(y1 - y0, x1 - x0), startAngle, solid);
		}
		if (endAngle != 0) {
			ShapeUtil.drawArrowhead(pApplet, x1, y1, PApplet.atan2(y0 - y1, x0 - x1), endAngle, solid);
		}
	}

	/**
	 * Draws an arrow head at given location.
	 * 
	 * SOURCE: http://www.openprocessing.org/sketch/7029
	 * 
	 * @param x0
	 *            arrow vertex x-coordinate
	 * @param y0
	 *            arrow vertex y-coordinate
	 * @param lineAngle
	 *            angle of line leading to vertex (radians)
	 * @param arrowAngle
	 *            angle between arrow and line (radians)
	 * @param solid
	 *            true for a solid arrow, false for an "open" arrow
	 */
	public static void drawArrowhead(PApplet pApplet, float x0, float y0, float lineAngle, float arrowAngle, boolean solid) {
		float phi;
		float x2;
		float y2;
		float x3;
		float y3;
		final float SIZE = 8;

		x2 = x0 + SIZE * PApplet.cos(lineAngle + arrowAngle);
		y2 = y0 + SIZE * PApplet.sin(lineAngle + arrowAngle);
		x3 = x0 + SIZE * PApplet.cos(lineAngle - arrowAngle);
		y3 = y0 + SIZE * PApplet.sin(lineAngle - arrowAngle);
		if (solid) {
			pApplet.triangle(x0, y0, x2, y2, x3, y3);
		} else {
			pApplet.line(x0, y0, x2, y2);
			pApplet.line(x0, y0, x3, y3);
		}
	}
}
