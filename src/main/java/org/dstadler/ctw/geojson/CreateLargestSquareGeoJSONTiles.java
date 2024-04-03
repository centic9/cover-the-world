package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.utils.Constants.TILE_ZOOM;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;

import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang3.tuple.Pair;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.MatrixUtils;
import org.dstadler.ctw.utils.OSMTile;

import com.github.filosganga.geogson.model.Feature;
import com.github.filosganga.geogson.model.LinearRing;
import com.github.filosganga.geogson.model.Point;
import com.github.filosganga.geogson.model.Polygon;
import com.google.common.base.Preconditions;
import com.google.gson.JsonPrimitive;

/**
 * This application reads the list of covered tiles and
 * tries to find the largest area covered by a big square.
 *
 * Results are stored in a TXT file for easy diffing via version
 * control and a JS file which can be used as overlay layer in a
 * Leaflet-based HTML page.
 */
public class CreateLargestSquareGeoJSONTiles {
	private static final Logger log = LoggerFactory.make();

	public static final String LARGEST_SQUARE_TILES_JSON = "js/LargestSquareTiles.js";
	public static final String LARGEST_SQUARE_TILES_TXT = "txt/LargestSquareTiles.txt";

	public static void main(String[] args) throws IOException {
		LoggerFactory.initLogging();

		log.info("Computing largest square for tiles");

		Set<OSMTile> tiles = OSMTile.readTiles(new File(VISITED_TILES_TXT));
		Preconditions.checkState(tiles.size() > 0,
				"Did not read any tiles from " + VISITED_TILES_TXT);

		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
				minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		for (OSMTile tile : tiles) {
			if (tile.getXTile() > maxX) {
				maxX = tile.getXTile();
			}
			if (tile.getXTile() < minX) {
				minX = tile.getXTile();
			}

			if (tile.getYTile() > maxY) {
				maxY = tile.getYTile();
			}
			if (tile.getYTile() < minY) {
				minY = tile.getYTile();
			}
		}

		int[][] M = MatrixUtils.populateMatrix(tiles, minX, minY, maxX, maxY);

		Pair<Rectangle,Integer> result = MatrixUtils.maxSubSquare(M);
		Rectangle rect = result.getKey();

		log.info("Area of maximum square " + rect + ": " + rect.width + "x" + rect.height +
				" = " + result.getValue());

		OSMTile squareMin = new OSMTile(TILE_ZOOM, minX + rect.x - rect.width, minY + rect.y - rect.height + 1);
		OSMTile squareMax = new OSMTile(TILE_ZOOM, minX + rect.x, minY + rect.y + 1);

		/*UTMRefWithHash recRefMin = UTMRefWithHash.fromString(ZONE + "U " +
				(minEast + (rect.x - rect.width) * SQUARE_SIZE) + " " + (minNorth + (rect.y - rect.height) * SQUARE_SIZE + SQUARE_SIZE));
		UTMRefWithHash recRefMax = UTMRefWithHash.fromString(ZONE + "U " +
				(minEast + rect.x * SQUARE_SIZE) + " " + (minNorth + rect.y * SQUARE_SIZE + SQUARE_SIZE));*/
		log.info("Found largest square at " + rect.x + "x" + rect.y + " with size " + rect.width + "x" + rect.height
				/*"\n" + recRefMin +
				"\n" + recRefMax +
				"\n" + squareMin.getStartLatLon() +
				"\n" + squareMax.getStartLatLon() +
				"\n" + OSMTile.fromLatLngZoom(squareMin.getStartLatLon().getLatitude(), squareMin.getStartLatLon().getLongitude(), 13) +
				"\n" + OSMTile.fromLatLngZoom(squareMax.getStartLatLon().getLatitude(), squareMax.getStartLatLon().getLongitude(), 13)*/
		);

		// produce the GeoJSON for the rectangle
		List<Feature> features = Collections.singletonList(
				Feature.builder().withGeometry(Polygon.of(LinearRing.of(
				Point.from(squareMin.toLatLng().getLongitude(), squareMin.toLatLng().getLatitude()),
				Point.from(squareMax.toLatLng().getLongitude(), squareMin.toLatLng().getLatitude()),
				Point.from(squareMax.toLatLng().getLongitude(), squareMax.toLatLng().getLatitude()),
				Point.from(squareMin.toLatLng().getLongitude(), squareMax.toLatLng().getLatitude()),
				Point.from(squareMin.toLatLng().getLongitude(), squareMin.toLatLng().getLatitude())
		))).withProperty("popupContent", new JsonPrimitive(
				result.getValue() + " tiles: " + rect.width + "x" + rect.height)).build());

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJavaScript(LARGEST_SQUARE_TILES_JSON, "tilesquare", features);

		// create list of latLngBounds for SVG elements to overlay
		try (Writer writer = new BufferedWriter(new FileWriter(LARGEST_SQUARE_TILES_TXT))) {
			writer.write(squareMin.toCoords());
			writer.write('\n');
			writer.write(squareMax.toCoords());
			writer.write('\n');
			writer.write(rect.x + "x" + rect.y);
			writer.write('\n');
			writer.write(rect.width + "x" + rect.height);
			writer.write('\n');
			writer.write(result.getValue().toString());
			writer.write('\n');
		}
	}
}
