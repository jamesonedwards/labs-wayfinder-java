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
	private static final int CP5_SLIDER_HEIGHT = 10;
	private static final int CP5_SLIDER_WIDTH = 150;
	private static final int CP5_SLIDER_X = 10;
	private static final int CP5_SLIDER_Y_BUFFER = 15;
	private static final int CP5_BACKGROUND_BUFFER = 5;
	private static final int CP5_BACKGROUND_WIDTH = CP5_SLIDER_WIDTH + (CP5_BACKGROUND_BUFFER * 2);
	private static final int MAX_MOG_NMIXTURES = 10;
	private static final int MIN_MOG_NMIXTURES = 1;
	private static final String MOG_PARAM_NAME_HISTORY = "history";
	private static final String MOG_PARAM_NAME_NMIXTURES = "nmixtures";
	private static final String MOG_PARAM_NAME_BACKGROUND_RATIO = "backgroundRatio";
	private static final String MOG_PARAM_NAME_NOISE_SIGMA = "noiseSigma";

	private ArrayList<Destination> destinations;
	private PVector spotlightCenter3D;
	private LinkedList<PVector> spotlightHistory;
	private float arrowLength;
	private boolean detected;
	private boolean debugView;
	private int frameCount;
	private VideoCapture capture;

	// Detection sample: http://mateuszstankiewicz.eu/?p=189
	// private org.opencv.video.BackgroundSubtractorMOG2 bg;
	// TODO: Binding doesn't exist due to bug! http://code.opencv.org/issues/3171#note-1
	private BackgroundSubtractorMOG bg;
	private Mat cvFrame;
	// private Mat back;
	private Mat fore;
	ArrayList<MatOfPoint> contours;
	ArrayList<MatOfPoint> debugShowlargestContour;

	// Debug controls.
	private ArrayList<Controller<?>> cp5Controls;
	public ControlP5 cp5;
	public Slider cp5MinValidContourArea;
	public Range cp5SpotlightHistorySpread;
	public Slider cp5FrameCountThreshold;
	public Slider cp5SpotlightRadius;
	public Slider cp5MogHistory; // Length of the history.
	public Slider cp5MogNmixtures; // Number of Gaussian mixtures.
	public Slider cp5MogBackgroundRatio; // Background ratio.
	public Slider cp5MogNoiseSigma; // Noise strength.

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
			arrowLength = (float) min(width, height) / 2.0f;
			spotlightCenter3D = new PVector((float) width / 2.0f, (float) height / 2.0f, 0.0f);
			spotlightHistory = new LinkedList<PVector>();
			detected = false;

			// Start the video capture.
			capture = new VideoCapture(0);
			capture.open(0);
			if (!capture.isOpened()) {
				throw new Exception("Camera Error");
			}

			bg = new BackgroundSubtractorMOG();
			/**
			 * For some reason you cannot increase the value of nmixtures once it is initially set, you can only decrease it. So initially set it to the max
			 * value, and then set it in the draw method to the actual default value.
			 */
			// bg.set("bShadowDetection", false);
			// bg.setBool("detectShadows", true);
			cvFrame = new Mat();
			// back = new Mat();
			fore = new Mat();
			contours = new ArrayList<MatOfPoint>();
			debugShowlargestContour = new ArrayList<MatOfPoint>();
			frameCount = 0;
			debugView = true;

			cp5 = new ControlP5(this);
			cp5Controls = new ArrayList<Controller<?>>();

			cp5MinValidContourArea = cp5.addSlider("MinValidContourArea").setPosition(10, CP5_SLIDER_Y_BUFFER).setSize(CP5_SLIDER_WIDTH, CP5_SLIDER_HEIGHT)
					.setRange(0, 5000).setValue(800).setVisible(debugView);
			cp5MinValidContourArea.getValueLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5MinValidContourArea.getCaptionLabel().align(ControlP5.RIGHT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5Controls.add(cp5MinValidContourArea);

			cp5SpotlightHistorySpread = cp5.addRange("SpotlightHistorySpread")
					// Disable broadcasting since setRange and setRangeValues will trigger an event.
					.setBroadcast(false).setPosition(CP5_SLIDER_X, CP5_SLIDER_Y_BUFFER * 3).setSize(CP5_SLIDER_WIDTH, CP5_SLIDER_HEIGHT).setHandleSize(10)
					.setRange(0, 2000).setRangeValues(100, 500)
					// After the initialization we turn broadcast back on again.
					.setBroadcast(true).setVisible(debugView);
			cp5SpotlightHistorySpread.getValueLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5SpotlightHistorySpread.getCaptionLabel().align(ControlP5.RIGHT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5Controls.add(cp5SpotlightHistorySpread);

			cp5FrameCountThreshold = cp5.addSlider("FrameCountThreshold").setPosition(CP5_SLIDER_X, CP5_SLIDER_Y_BUFFER * 5)
					.setSize(CP5_SLIDER_WIDTH, CP5_SLIDER_HEIGHT).setRange(1, FRAME_RATE).setValue(10).setVisible(debugView);
			cp5FrameCountThreshold.getValueLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5FrameCountThreshold.getCaptionLabel().align(ControlP5.RIGHT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5Controls.add(cp5FrameCountThreshold);

			cp5SpotlightRadius = cp5.addSlider("SpotlightRadius").setPosition(CP5_SLIDER_X, CP5_SLIDER_Y_BUFFER * 7)
					.setSize(CP5_SLIDER_WIDTH, CP5_SLIDER_HEIGHT).setRange((float) width / 32.0f, (float) width / 2.0f).setValue((float) width / 16.0f)
					.setVisible(debugView);
			cp5SpotlightRadius.getValueLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5SpotlightRadius.getCaptionLabel().align(ControlP5.RIGHT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5Controls.add(cp5SpotlightRadius);

			cp5MogNmixtures = cp5.addSlider("MogNmixtures").setPosition(CP5_SLIDER_X, CP5_SLIDER_Y_BUFFER * 9).setSize(CP5_SLIDER_WIDTH, CP5_SLIDER_HEIGHT)
					.setRange(MIN_MOG_NMIXTURES, MAX_MOG_NMIXTURES).setValue(bg.getInt(MOG_PARAM_NAME_NMIXTURES)).setVisible(debugView);
			cp5MogNmixtures.getValueLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5MogNmixtures.getCaptionLabel().align(ControlP5.RIGHT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5Controls.add(cp5MogNmixtures);

			cp5MogBackgroundRatio = cp5.addSlider("MogBackgroundRatio").setPosition(CP5_SLIDER_X, CP5_SLIDER_Y_BUFFER * 11)
					.setSize(CP5_SLIDER_WIDTH, CP5_SLIDER_HEIGHT).setRange(0, 1).setValue((float) bg.getDouble(MOG_PARAM_NAME_BACKGROUND_RATIO))
					.setVisible(debugView);
			cp5MogBackgroundRatio.getValueLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5MogBackgroundRatio.getCaptionLabel().align(ControlP5.RIGHT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5Controls.add(cp5MogBackgroundRatio);

			cp5MogHistory = cp5.addSlider("MogHistory").setPosition(CP5_SLIDER_X, CP5_SLIDER_Y_BUFFER * 13).setSize(CP5_SLIDER_WIDTH, CP5_SLIDER_HEIGHT)
					.setRange(50, 1000).setValue(bg.getInt(MOG_PARAM_NAME_HISTORY)).setVisible(debugView);
			cp5MogHistory.getValueLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5MogHistory.getCaptionLabel().align(ControlP5.RIGHT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5Controls.add(cp5MogHistory);

			cp5MogNoiseSigma = cp5.addSlider("MogNoiseSigma").setPosition(CP5_SLIDER_X, CP5_SLIDER_Y_BUFFER * 15).setSize(CP5_SLIDER_WIDTH, CP5_SLIDER_HEIGHT)
					.setRange(0, 50).setValue((float) bg.getDouble(MOG_PARAM_NAME_NOISE_SIGMA)).setVisible(debugView);
			cp5MogNoiseSigma.getValueLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5MogNoiseSigma.getCaptionLabel().align(ControlP5.RIGHT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
			cp5Controls.add(cp5MogNoiseSigma);
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

		// Don't detect with every frame.
		if (frameCount % (int) cp5FrameCountThreshold.getValue() == 0) {
			detected = false;

			// Get the current frame.
			capture.retrieve(cvFrame);
			LOGGER.fine("Frame Obtained");

			try {
				// Subtract background.
				// bg.apply(cvFrame, fore, .5);

				// If any of the BackgroundSubtractorMOG parameters have changed, create a new BackgroundSubtractorMOG object with the current values.
				if (bg.getInt(MOG_PARAM_NAME_NMIXTURES) != (int) cp5MogNmixtures.getValue()
						|| bg.getDouble(MOG_PARAM_NAME_BACKGROUND_RATIO) != (double) cp5MogBackgroundRatio.getValue()
						|| bg.getInt(MOG_PARAM_NAME_HISTORY) != (int) cp5MogHistory.getValue()
						|| bg.getDouble(MOG_PARAM_NAME_NOISE_SIGMA) != (double) cp5MogNoiseSigma.getValue()) {
					bg = new BackgroundSubtractorMOG();
					bg.setInt(MOG_PARAM_NAME_NMIXTURES, (int) cp5MogNmixtures.getValue());
					bg.setDouble(MOG_PARAM_NAME_BACKGROUND_RATIO, (double) cp5MogBackgroundRatio.getValue());
					bg.setInt(MOG_PARAM_NAME_HISTORY, (int) cp5MogHistory.getValue());
					bg.setDouble(MOG_PARAM_NAME_NOISE_SIGMA, (double) cp5MogNoiseSigma.getValue());
				}
				bg.apply(cvFrame, fore);

				// Get all contours.
				Imgproc.findContours(fore, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);

				// Get largest contour:
				// http://stackoverflow.com/questions/15012073/opencv-draw-draw-contours-of-2-largest-objects
				int largestIndex = 0;
				double largestContourArea = 0;
				for (int i = 0; i < contours.size(); i++) {
					double contourArea = Imgproc.contourArea(contours.get(i));
					if (contourArea > largestContourArea) {
						largestContourArea = contourArea;
						largestIndex = i;
					}
				}

				if (contours.size() > 0) {
					// Make sure the blog is large enough to be a track-worthy.
					LOGGER.info("largestContourArea = " + largestContourArea);
					if (largestContourArea >= (double) cp5MinValidContourArea.getValue()) {
						// Find the center mass of the contour.
						// Source:
						// http://stackoverflow.com/questions/18345969/how-to-get-the-mass-center-of-a-contour-android-opencv
						Moments moments = Imgproc.moments(contours.get(largestIndex));
						spotlightCenter3D.x = (float) (moments.get_m10() / moments.get_m00());
						spotlightCenter3D.y = (float) (moments.get_m01() / moments.get_m00());

						// Show guide.
						detected = isLost();
					}
				}

				// When debug mode is off, the background should be black.
				if (debugView) {
					debugShowlargestContour.clear();
					if (contours.size() > 0) {
						Imgproc.drawContours(cvFrame, contours, -1, new Scalar(0, 0, 255), 2);
						debugShowlargestContour.add(contours.get(largestIndex));
						Imgproc.drawContours(cvFrame, debugShowlargestContour, -1, new Scalar(255, 0, 0), 2);
					}
				}
			} catch (Exception ex) {
				LOGGER.warning("MOG exception occurred with nmixtures = " + bg.getInt(MOG_PARAM_NAME_NMIXTURES) + " and cp5MogNmixtures.getValue() = "
						+ cp5MogNmixtures.getValue() + ": " + ex.getMessage());
			}

			// Show/hid debug controls:
			for (Controller<?> item : cp5Controls) {
				item.setVisible(debugView);
			}
		}

		if (debugView) {
			image(OpenCvUtil.toPImage(this, cvFrame), 0, 0);
			fill(100);
			rect(CP5_SLIDER_X - CP5_BACKGROUND_BUFFER, CP5_SLIDER_Y_BUFFER - CP5_BACKGROUND_BUFFER, CP5_BACKGROUND_WIDTH, cp5Controls.size()
					* CP5_SLIDER_Y_BUFFER * 2 + (CP5_BACKGROUND_BUFFER * 2));
		}

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
		if (spread > cp5SpotlightHistorySpread.getLowValue() && spread < cp5SpotlightHistorySpread.getHighValue()) {
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
		ShapeUtil.drawCircle(this, spotlightCenter3D.x, spotlightCenter3D.y, cp5SpotlightRadius.getValue(), new int[] { 255, 255, 255 });
		ShapeUtil.drawCircle(this, spotlightCenter3D.x, spotlightCenter3D.y, cp5SpotlightRadius.getValue() * 2 / 3, new int[] { 0, 0, 0 });
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
		// TODO: Need to release other resources - app keeps running after
		// window closes!
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
