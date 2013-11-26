package com.labsmb.util;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.opencv.core.Mat;

import processing.core.PApplet;
import processing.core.PImage;

public class OpenCvUtil {

	// Make ctor private so you can only use this class statically.
	private OpenCvUtil() {

	}

	/**
	 * SOURCE: https://github.com/atduskgreg/opencv-processing/blob/master/src/gab/opencv/OpenCV.java
	 * 
	 * Convert an OpenCV Mat object into a PImage to be used in other Processing
	 * code. Copies the Mat's pixel data into the PImage's pixel array. Iterates
	 * over each pixel in the Mat, i.e. expensive.
	 * 
	 * (Mainly used internally by OpenCV. Inspired by toCv() from KyleMcDonald's
	 * ofxCv.)
	 * 
	 * @param m
	 *            A Mat you want converted
	 * @param img
	 *            The PImage you want the Mat converted into.
	 */
	public static PImage toPImage(PApplet pApplet, Mat m) {
		int width = m.width();
		int height = m.height();
		PImage img = new PImage(width, height);
		img.loadPixels();

		if (m.channels() == 3) {
			byte[] matPixels = new byte[width * height * 3];
			m.get(0, 0, matPixels);
			for (int i = 0; i < m.width() * m.height() * 3; i += 3) {
				img.pixels[PApplet.floor(i / 3)] = pApplet.color(matPixels[i + 2] & 0xFF, matPixels[i + 1] & 0xFF, matPixels[i] & 0xFF);
			}
		} else if (m.channels() == 1) {
			byte[] matPixels = new byte[width * height];
			m.get(0, 0, matPixels);
			for (int i = 0; i < m.width() * m.height(); i++) {
				img.pixels[i] = pApplet.color(matPixels[i] & 0xFF);
			}
		} else if (m.channels() == 4) {
			byte[] matPixels = new byte[width * height * 4];
			m.get(0, 0, matPixels);
			for (int i = 0; i < m.width() * m.height() * 4; i += 4) {
				img.pixels[PApplet.floor(i / 4)] = pApplet
						.color(matPixels[i + 2] & 0xFF, matPixels[i + 1] & 0xFF, matPixels[i] & 0xFF, matPixels[i + 3] & 0xFF);
			}
		}

		img.updatePixels();
		return img;
	}

	/**
	 * SOURCE: https://github.com/atduskgreg/opencv-processing/blob/master/src/gab/opencv/OpenCV.java
	 * 
	 * Convert a Processing PImage to an OpenCV Mat. (Inspired by Kyle
	 * McDonald's ofxCv's toOf())
	 * 
	 * @param img
	 *            The PImage to convert.
	 * @param m
	 *            The Mat to receive the image data.
	 */
	public static void toCv(PApplet pApplet, PImage img, Mat m) {
		BufferedImage image = (BufferedImage) img.getNative();
		int[] matPixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

		ByteBuffer bb = ByteBuffer.allocate(matPixels.length * 4);
		IntBuffer ib = bb.asIntBuffer();
		ib.put(matPixels);

		byte[] bvals = bb.array();

		m.put(0, 0, bvals);
	}
}
