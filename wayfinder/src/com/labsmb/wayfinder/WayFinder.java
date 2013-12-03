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
import java.util.LinkedList;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.video.BackgroundSubtractorMOG;

import processing.core.PApplet;
import processing.core.PVector;

import com.labsmb.util.OpenCvUtil;
import com.labsmb.util.ShapeUtil;
import controlP5.*;

public class WayFinder extends PApplet {
	// In order to suppress serialization warning. 
	private static final long serialVersionUID = 1L;

	// Logging:
	private final static Logger LOGGER = Logger.getLogger(WayFinder.class.getName() + "Logger");

	// Constants:
	private static final int WINDOW_WIDTH = 640;
	private static final int WINDOW_HEIGHT = 360;
	// private static final int WIDTH = 1280;
	// private static final int HEIGHT = 720;
	private static final int FRAME_RATE = 30;
	private static final float DESTINATION_NAME_POSITION_MULTIPLIER = 1.1f;
	private static final int SPOTLIGHT_HISTORY_MAX_LENGTH = (int) FRAME_RATE / 10;
	private static int CP5_SLIDER_HEIGHT = 10;
	private static int CP5_SLIDER_WIDTH = 150;

	// FIXME: Turn these into CP5 controls:
	private float spotlightHistoryMinSpread = 10.0f; // TODO: RANGE!
	private float spotlightHistoryMaxSpread = 500.0f; // TODO: RANGE!
	private int frameCountThreshold = 10;
	private int mogNmixtures = 3;
	
	private ArrayList<Destination> destinations;
	private PVector spotlightCenter3D;
	private LinkedList<PVector> spotlightHistory;
	private float spotlightRadius;
	private float arrowLength;
	private boolean detected;
	private boolean debugView;
	private int frameCount;
	private VideoCapture capture;

	// Detection sample: http://mateuszstankiewicz.eu/?p=189
	// private org.opencv.video.BackgroundSubtractorMOG2 bg; // TODO: Binding doesn't exist due to bug! http://code.opencv.org/issues/3171#note-1
	private BackgroundSubtractorMOG bg;
	private Mat cvFrame;
	//private Mat back;
	private Mat fore;
	ArrayList<MatOfPoint> contours;
	ArrayList<MatOfPoint> debugShowlargestContour;

	// Debug controls.
	public ControlP5 cp5;
	public Slider cp5MinValidContourArea;
	
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
			spotlightRadius = (float) width / 16.0f; // TODO: Need to define range and change this based on CP5 event.
			arrowLength = (float) min(width, height) / 2.0f;
			spotlightCenter3D = new PVector((float) width / 2.0f, (float) height / 2.0f, 0.0f);
			spotlightHistory = new LinkedList<PVector>();
			detected = false;

			// Start the video capture.
			capture = new VideoCapture(0);
			capture.open(0);
		    if(!capture.isOpened()){
		        throw new Exception("Camera Error");
		    }
			
			bg = new BackgroundSubtractorMOG();
			bg.setInt("nmixtures", mogNmixtures); // TODO: Need to define range and change this based on CP5 event.
			// bg.set("bShadowDetection", false);
			// bg.setBool("detectShadows", true);
			cvFrame = new Mat();
			//back = new Mat();
			fore = new Mat();
			contours = new ArrayList<MatOfPoint>();
			debugShowlargestContour = new ArrayList<MatOfPoint>();
			frameCount = 0;
			debugView = true;

			cp5 = new ControlP5(this);
			cp5MinValidContourArea = cp5.addSlider("MinValidContourArea").setPosition(10, 10).setSize(CP5_SLIDER_WIDTH, CP5_SLIDER_HEIGHT).setRange(0, 5000)
					.setValue(800).setVisible(debugView);
			cp5MinValidContourArea.getValueLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5MinValidContourArea.getCaptionLabel().align(ControlP5.RIGHT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
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
		contours.clear();
		
        if(frameCount % frameCountThreshold == 0) { // Don't detect with every frame.
        	detected = false;

        	// Get the current frame.
        	capture.retrieve(cvFrame);
    	    LOGGER.fine("Frame Obtained");
    	    
        	// TODO: Consider converting capture to grayscale or blurring then thresholding to improve performance.
        	//Mat frameGray, frameBlurred, frameThresh, foreGray, backGray;
            //Imgproc.cvtColor(cvFrame, frameGray, CV_BGR2GRAY);
            //int blurAmount = 10;
            //Imgproc.blur(cvFrame, frameBlurred, cv.Size(blurAmount, blurAmount));
            //Imgproc.threshold(frameBlurred, frameThresh, 100, 255, CV_THRESH_BINARY);
    	    //Imgproc.erode(fore, fore, new Mat());
            //Imgproc.dilate(fore, fore, new Mat());

    	    // Subtract background.
            bg.apply(cvFrame, fore);
            
            // Get all contours.
            Imgproc.findContours(fore, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);

            // Get largest contour: http://stackoverflow.com/questions/15012073/opencv-draw-draw-contours-of-2-largest-objects
            int largestIndex = 0;
            double largestContourArea = 0;
            for(int i = 0; i < contours.size(); i++) {
            	double contourArea = Imgproc.contourArea(contours.get(i));
                if(contourArea > largestContourArea) {
                	largestContourArea = contourArea;
                    largestIndex = i;
                }
            }

            if(contours.size() > 0) {
                // Make sure the blog is large enough to be a track-worthy.
            	LOGGER.info("largestContourArea = " + largestContourArea);
                if (largestContourArea >= (double) cp5MinValidContourArea.getValue()) {
                	// Find the center mass of the contour.
                	// Source: http://stackoverflow.com/questions/18345969/how-to-get-the-mass-center-of-a-contour-android-opencv
                    Moments moments = Imgproc.moments(contours.get(largestIndex));
                    spotlightCenter3D.x = (float)(moments.get_m10() / moments.get_m00());
                    spotlightCenter3D.y = (float)(moments.get_m01() / moments.get_m00());
                    //spotlightRadius = (rect.width + rect.y) / 2;
                 
                    // Show guide.
                    //detected = true;
                    detected = isLost();
                }
            }

            // When debug mode is off, the background should be black.
            if(debugView) {
            	debugShowlargestContour.clear();
                if(contours.size() > 0) {
                	Imgproc.drawContours(cvFrame, contours, -1, new Scalar(0, 0, 255), 2);
                	debugShowlargestContour.add(contours.get(largestIndex));
                	Imgproc.drawContours(cvFrame, debugShowlargestContour, -1, new Scalar(255, 0, 0), 2);
                }
            }

            // Debug controls:
            cp5MinValidContourArea.setVisible(debugView);
		}

	    if(debugView)
	    	image(OpenCvUtil.toPImage(this, cvFrame), 0, 0);

	    if (detected) {
	        guide();
	    }
	}
	
	private boolean isLost() {
		// Push current position onto stack.
		spotlightHistory.push(new PVector(spotlightCenter3D.x, spotlightCenter3D.y, spotlightCenter3D.z));
		
		// Trim the history.
		if (spotlightHistory.size() > SPOTLIGHT_HISTORY_MAX_LENGTH)
			spotlightHistory.poll();
		
		// Calculate the change in position though the history.
		float spread = PVector.dist(spotlightHistory.getLast(), spotlightHistory.getFirst());
		LOGGER.info("Spread = " + spread);
		
		// Check if the spread is within the target range.
		if (spread > spotlightHistoryMinSpread && spread < spotlightHistoryMaxSpread) {
			return true;
		} else {
			return false;
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
			// Destination names need to be placed at the end of the arrows.
			PVector namePt = calculateLinePoint(spotlightCenter3D, iter.getVector(), arrowLength * DESTINATION_NAME_POSITION_MULTIPLIER);

			fill(255, 255, 255);
			// Draw a line from the spotlight center to each of the
			// destinations.
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
		// TODO: Need to release other resources - app keeps running after window closes!
		if (capture != null)
			capture.release();
		if (cvFrame != null)
			cvFrame.release();
		if (fore != null)
			fore.release();
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
