package org.dstadler.ctw;

import java.io.IOException;

import org.dstadler.ctw.geojson.CreateClusterGeoJSON;
import org.dstadler.ctw.geojson.CreateGeoJSON;
import org.dstadler.ctw.geojson.CreateLargestClusterGeoJSONSquares;
import org.dstadler.ctw.geojson.CreateLargestClusterGeoJSONTiles;
import org.dstadler.ctw.geojson.CreateLargestRectangleGeoJSONSquares;
import org.dstadler.ctw.geojson.CreateLargestRectangleGeoJSONTiles;
import org.dstadler.ctw.geojson.CreateLargestSquareGeoJSONSquares;
import org.dstadler.ctw.geojson.CreateLargestSquareGeoJSONTiles;
import org.dstadler.ctw.gpx.CreateListOfVisitedSquares;
import org.xml.sax.SAXException;

/**
 * Main application to read GPX tracks and produce the GeoJSON
 * files for the Leaflet-based HTML page.
 */
public class CoverTheWorld {

	public static void main(String[] args) throws IOException, SAXException {
		// this needs to run first to compute "Visited*.txt"
		CreateListOfVisitedSquares.main(new String[0]);

		// produce Visited*.js
		CreateGeoJSON.main(new String[0]);

		// produce ClusterSquares.*
		CreateClusterGeoJSON.main(new String[0]);

		// produce "LargestCluster*"
		CreateLargestClusterGeoJSONSquares.main(new String[0]);
		CreateLargestClusterGeoJSONTiles.main(new String[0]);

		// produce "LargestRectangle*"
		CreateLargestRectangleGeoJSONSquares.main(new String[0]);
		CreateLargestRectangleGeoJSONTiles.main(new String[0]);

		// produce "LargestSquare*"
		CreateLargestSquareGeoJSONSquares.main(new String[0]);
		CreateLargestSquareGeoJSONTiles.main(new String[0]);
	}
}
