package com.labsmb.wayfinder;

import processing.core.*;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class Destination {
	private static ArrayList<Destination> destinations;
	private String name;
	private PVector vector;

	public Destination(String name, float x, float y) {
		this.name = name;
		this.vector = new PVector(x, y);
	}

	public Destination(String name, PVector vector) {
		this.name = name;
		this.setVector(vector);
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public PVector getVector() {
		return this.vector;
	}

	public void setVector(PVector vector) {
		this.vector = vector;
	}

	public static ArrayList<Destination> getDestinations() {
		// Initialize the destinations vector if not already initialized.
		if (destinations == null) {
			// Load destinations from config file.
			String configPath = System.getProperty("user.dir") + File.separator + "config" + File.separator + "destinations.json";
			destinations = new ArrayList<Destination>();
			try (FileReader fr = new FileReader(configPath);) {
				JSONParser parser = new JSONParser();
				JSONObject jsonRoot = (JSONObject) parser.parse(fr);
				JSONArray jsonArr = (JSONArray) jsonRoot.get("destinations");
				for (Object destObj : jsonArr) {
					JSONObject jsonDest = (JSONObject) destObj;
					destinations.add(new Destination((String) jsonDest.get("name"), Float.parseFloat(String.valueOf(jsonDest.get("x"))), Float
							.parseFloat(String.valueOf(jsonDest.get("y")))));
				}
			} catch (Exception ex) {
				System.err.println("Cannot load properties file: " + ex.getMessage());
			} finally {

			}
		}
		return destinations;
	}
}
