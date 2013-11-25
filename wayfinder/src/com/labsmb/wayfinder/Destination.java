package com.labsmb.wayfinder;

import processing.core.*;
import java.util.ArrayList;

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

	public static ArrayList<Destination> getDestinations()
	{
        // FIXME: REMOVE THIS:
        destinations = new ArrayList<Destination>();
        destinations.add(new Destination("test", 0, 0));
		
		// Initialize the destinations vector if not already initialized.
	    if (destinations.size() == 0) {
	        // Load destinations from config file.
	        String configPath = "destinations.json";
	        
	        /*
	        fs::path path(configPath);
	        if (fs::exists(path)) {
	            DataSourceRef dsr = loadFile(path.native());
	            JsonTree tree = ci::JsonTree::JsonTree(dsr);
	            JsonTree::Container jsonDestinations = tree.getChild("destinations").getChildren();
	            for(JsonTree::Iter iter = jsonDestinations.begin(); iter != jsonDestinations.end(); ++iter) {
	                Destination::destinations.push_back(Destination(
	                                                        iter->getChild("name").getValue(),
	                                                        boost::lexical_cast<float>(iter->getChild("x").getValue()),
	                                                        boost::lexical_cast<float>(iter->getChild("y").getValue())));
	            }
	        } else {
	            throw new Exception("Cannot find destinations config file: " + configPath);
	        }*/
	    }
	    return destinations;
	}
}
