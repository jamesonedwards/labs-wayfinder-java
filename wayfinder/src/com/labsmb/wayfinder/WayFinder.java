/*
#include "cinder/app/AppNative.h"
#include "cinder/gl/gl.h"
#include "cinder/ImageIo.h"
#include "cinder/gl/Texture.h"
#include "cinder/Capture.h"
#include "CinderOpenCv.h"
#include "boost/lexical_cast.hpp"
#include "Destination.h"
#include <vector>
#include<iostream>
#include <opencv2/imgproc/imgproc.hpp>
#include "cinder/app/KeyEvent.h"

using namespace ci;
using namespace ci.app;
using namespace std;
 */

/*
 Reference:
 http://www.codeproject.com/Articles/10248/Motion-Detection-Algorithms
 http://mateuszstankiewicz.eu/?p=189
 http://stackoverflow.com/questions/1800138/given-a-start-and-end-point-and-a-distance-calculate-a-point-along-a-line
 http://math.stackexchange.com/questions/175896/finding-a-point-along-a-line-a-certain-distance-away-from-another-point
 Not used but possibly important: http://www.codeproject.com/Articles/3274/Drawing-Arrows
 */

// TODO: Add sound when guide is shown ("whom" sci-fi door sound, followed  by "warm glow" while shown)
// TODO: Add a subtle undulating effect to circle

package com.labsmb.wayfinder;

import java.util.ArrayList;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.exception.ExceptionUtils;

import processing.core.*;
import processing.opengl.Texture;
import processing.video.*;
import com.labsmb.util.ShapeUtil;

import org.opencv.core.*;
//import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.video.BackgroundSubtractorMOG;

public class WayFinder extends PApplet {
	// Logging:
	private final static Logger LOGGER = Logger.getLogger(WayFinder.class.getName() + "Logger");

	// Constants:
	private static final int WINDOW_WIDTH = 640;
	private static final int WINDOW_HEIGHT = 360;
	// private static final int WIDTH = 1280;
	// private static final int HEIGHT = 720;
	private static final int FRAME_RATE = 30;
	private static final int FRAME_COUNT_THRESHOLD = 10;

	private ArrayList<Destination> destinations;
	// HACK: Need to figure out a better way to maintain center state.
	// private PVector spotlightCenter2D;
	private PVector spotlightCenter3D; // 3D vector is required for drawVector.
	private float spotlightRadius;
	private float arrowLength;
	private boolean detected;
	private boolean debugView;
	private int frameCount;

	private Mat src_img;
	private Capture capture; // OR: org.opencv.highgui.VideoCapture???
	private Texture mTexture;

	// Detection sample: http://mateuszstankiewicz.eu/?p=189
	// private org.opencv.video.BackgroundSubtractorMOG2 bg; // TODO: Binding
	// doesn't exist due to bug! http://code.opencv.org/issues/3171#note-1
	private BackgroundSubtractorMOG bg;
	private Mat frame;
	private Mat back;
	private Mat fore;
	// ArrayList<ArrayList<cv.Point>> contours;
	ArrayList<ArrayList<org.opencv.core.Point>> contours;

	/**
	 * HACK: Get this PApplet to run from command line.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		// Load the OpenCV native library.
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		// Start the Processing applet.
		PApplet.main(WayFinder.class.getName());
	}

	public void setup() {
		// This must be the first line of code in setup():
		size(WINDOW_WIDTH, WINDOW_HEIGHT);

		// Set frame rate.
		frameRate(FRAME_RATE);

		// Setup logging.
		LOGGER.setLevel(java.util.logging.Level.INFO);
		LOGGER.addHandler(new ConsoleHandler());

		try {
			LOGGER.info("WayFinder started.");

			// Disable window resizing.
			ShapeUtil.findFrame(this).setResizable(false);

			// Load destinations from config file.
			destinations = Destination.getDestinations();
			if (destinations.size() == 0) {
				throw new Exception("No destinations found, check the config file.");
				// exit();
			}
			LOGGER.info("Destinations loaded.");

			// Initialized state.
			spotlightRadius = (float) width / 16.0f;
			arrowLength = (float) min(width, height) / 2.0f;
			// spotlightCenter2D = Vec2f((float)width / 2.0f, (float)height /
			// 2.0f);
			spotlightCenter3D = new PVector((float) width / 2.0f, (float) height / 2.0f, 0.0f);
			detected = false;

			// Start the video capture.
			capture = new Capture(this, Capture.list()[0]);
			capture.start();

			bg = new BackgroundSubtractorMOG();
			bg.setInt("nmixtures", 3);
			// bg.set("bShadowDetection", false);
			// bg.setBool("detectShadows", true);

			frameCount = 0;
			debugView = false;
		} catch (Exception ex) {
			String msg = "Exception details: \nType: " + ex.getClass().toString() + "\nMessage: " + ex.getMessage() + "\nStack trace: "
					+ ExceptionUtils.getStackTrace(ex) + "\n\n" + "Object state: "
					+ ReflectionToStringBuilder.toString(this, ToStringStyle.MULTI_LINE_STYLE, true, true) + "\n\n";
			LOGGER.severe(msg);
			exit(); // Exit program.
		}
	}

	public void keyPressed() {
		if (key == 'd') {
			// Toggle btwn debug view and normal view.
			background(0.0f);
			debugView = !debugView;
		}
	}

	public void draw() {
		frameCount++;
		background(0.0f);
		
        if (capture.available() == true) {
        	capture.read();
        }
        //image(capture, 0, 0);
        set(0, 0, capture);
        
        if(frameCount % FRAME_COUNT_THRESHOLD == 0) {
        	detected = false;

        	// TODO: Consider converting capture to grayscale or blurring then thresholding to improve performance.
        	// Get the current frame.
        	PImage curFrame = capture.get();
            //image(curFrame, 0, 0);

        	//Mat m = ocv.toCV(pimg);
        	frame = toOcv(curFrame);
            //cv.Mat frameGray, frameBlurred, frameThresh, foreGray, backGray;
            //cvtColor(frame, frameGray, CV_BGR2GRAY);
            //int blurAmount = 10;
            //cv.blur(frame, frameBlurred, cv.Size(blurAmount, blurAmount));
            //threshold(frameBlurred, frameThresh, 100, 255, CV_THRESH_BINARY);

            // Get all contours.
            //bg.operator()(frameThresh,fore);
            bg.operator()(frame, fore);
            bg.getBackgroundImage(back);
            erode(fore, fore, new Mat());
            dilate(fore, fore, new Mat());
            findContours(fore, contours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_NONE);

            // Get largest contour: http://stackoverflow.com/questions/15012073/opencv-draw-draw-contours-of-2-largest-objects
            int largestIndex = 0;
            int largestContour = 0;
            for(int i = 0; i < contours.size(); i++) {
                if(contours[i].size() > largestContour) {
                    largestContour = contours[i].size();
                    largestIndex = i;
                }
            }

            ArrayList<ArrayList<Point>> hack;
            Rect rect;
            Point center;

            if(contours.size() > 0) {
                hack.push_back(contours[largestIndex]);

                // Find bounding rectangle for largest countour.
                rect = boundingRect(contours[largestIndex]);

                // Make sure the blog is large enough to be a track-worthy.
                LOGGER.info("Rext area = " + rect.area());
                if(rect.area() >= 5000) { // TODO: Tweak this value.
                    // Get center of rectangle.
                    center = new Point(
                                 rect.x + (rect.width / 2),
                                 rect.y + (rect.height / 2)
                             );

                    // Show guide.
                    spotlightCenter3D.x = (float)center.x;
                    spotlightCenter3D.y = (float)center.y;
                    //spotlightRadius = (rect.width + rect.y) / 2;
                    detected = true;
                }
            }

            // When debug mode is off, the background should be black.
            if(debugView) {
                if(contours.size() > 0) {
                    drawContours(frame, contours, -1, new Scalar(0, 0, 255), 2);
                    drawContours(frame, hack, -1, new Scalar(255, 0, 0), 2);
                    rectangle(frame, rect, new Scalar(0, 255, 0), 3);
                    circle(frame, center, 10, new Scalar(0, 255, 0), 3);
                }
                mTexture = gl.Texture(fromOcv(frame));
            }

	        // TODO: Create control panel for all inputs.
		}

	    //if(mTexture && debugView)
	    //    gl.draw(mTexture);

	    if (detected) {
	        guide();
	    }
	}

	private PVector calculateLinePoint(PVector start, PVector end, float distance) {
		PVector diff = PVector.sub(end, start);
		PVector normalized = diff.normalize(null); // .safeNormalized();
		return PVector.add(start, PVector.mult(normalized, distance, null));
	}

	private void guide() {
		// Draw the spotlight, centered around the detected location.
		ShapeUtil.drawCircle(this, spotlightCenter3D.x, spotlightCenter3D.y, spotlightRadius, new int[] { 255, 255, 255 });
		ShapeUtil.drawCircle(this, spotlightCenter3D.x, spotlightCenter3D.y, spotlightRadius * 2 / 3, new int[] { 0, 0, 0 });
		// fill(255, 255, 255);

		for (Destination iter : destinations) {
			// Vectors should be of uniform length. Need a point *along* the
			// vector at a predefined distance from the start point.
			PVector endPt = calculateLinePoint(spotlightCenter3D, iter.getVector(), arrowLength);
			// PVector endPt3d = new PVector(endPt.x, endPt.y, 0);
			PVector namePt = calculateLinePoint(spotlightCenter3D, iter.getVector(), arrowLength * 2 / 3);

			fill(255, 255, 255);
			// Draw a line from the spotlight center to each of the
			// destinations.
			// gl.drawVector(spotlightCenter3D, endPt3d, 15.0f, 5.0f);
			ShapeUtil.drawArrowLine(this, spotlightCenter3D.x, spotlightCenter3D.y, endPt.x, endPt.y, 0, radians(30), true);

			// Display the destination name.
			text(iter.getName(), namePt.x, namePt.y);
			fill(0, 0, 0);
		}
	}

	/**
	 * Put all cleanup stuff here.
	 */
	private void cleanup() {

	}

	public void stop() {
		cleanup();
		super.stop();
	}

	public void exit() {
		cleanup();
		super.exit();
	}
}
