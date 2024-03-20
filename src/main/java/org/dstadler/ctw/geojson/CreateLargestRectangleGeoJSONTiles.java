package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.OSMTile;

import com.github.filosganga.geogson.model.Feature;
import com.google.common.base.Preconditions;

/**
 * This application reads the list of covered tiles and
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
public class CreateLargestRectangleGeoJSONTiles {
	private static final Logger log = LoggerFactory.make();

	public static final String CLUSTER_RECTANGLE_TILE_JSON = "js/LargestRectangleTiles.js";
	public static final String CLUSTER_RECTANGLE_TILE_TXT = "txt/LargestRectangleTiles.txt";

	public static void main(String[] args) throws IOException {
		LoggerFactory.initLogging();

		log.info("Computing largest rectangle for tiles");

		Set<OSMTile> tiles = OSMTile.readTiles(new File(VISITED_TILES_TXT));
		Preconditions.checkState(tiles.size() > 0,
				"Did not read any tiles from " + VISITED_SQUARES_TXT);

		// produce the GeoJSON for the rectangle
		Feature rectangle = CreateGeoJSON.getTileRectangle(tiles, CLUSTER_RECTANGLE_TILE_TXT, "rectangle");
		log.info("Found largest rectangle for tiles: " +
				FileUtils.readFileToString(new File(CLUSTER_RECTANGLE_TILE_TXT), "UTF-8"));

		List<Feature> features = Collections.singletonList(rectangle);

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJSON(CLUSTER_RECTANGLE_TILE_JSON, "tilerectangle", features);
	}
}
