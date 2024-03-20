package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.UTMRefWithHash;

import com.github.filosganga.geogson.model.Feature;
import com.google.common.base.Preconditions;

/**
 * This application reads the list of covered squares and
 * tries to find the largest area covered by a rectangle.
 *
 * Note: Currently only UTMRef-LonZone "33" is used to make
 * computation easier. If the rectangle should someday span
 * more than one Zone, this tool likely needs a major overhaul!
 *
 * Results are stored in a TXT file for easy diffing via version
 * control and a JS file which can be used as overlay layer in a
 * Leaflet-based HTML page.
 */
public class CreateLargestRectangleGeoJSONSquares {
	private static final Logger log = LoggerFactory.make();

	public static final String CLUSTER_RECTANGLE_JSON = "js/LargestRectangleSquares.js";
	public static final String CLUSTER_RECTANGLE_TXT = "txt/LargestRectangleSquares.txt";

	public static void main(String[] args) throws IOException {
		LoggerFactory.initLogging();

		log.info("Computing largest rectangle for squares");

		Set<UTMRefWithHash> squares = UTMRefWithHash.readSquares(new File(VISITED_SQUARES_TXT));
		Preconditions.checkState(squares.size() > 0,
				"Did not read any squares from " + VISITED_SQUARES_TXT);

		// produce the GeoJSON for the rectangle
		Feature rectangle = CreateGeoJSON.getSquareRectangle(squares, CLUSTER_RECTANGLE_TXT, "rectangle");
		log.info("Found largest rectangle for squares: " +
				FileUtils.readFileToString(new File(CLUSTER_RECTANGLE_TXT), "UTF-8"));

		List<Feature> features = Collections.singletonList(rectangle);

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJSON(CLUSTER_RECTANGLE_JSON, "rectangle", features);
	}
}
