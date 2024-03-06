package org.dstadler.ctw;

import static org.dstadler.ctw.CreateListOfVisitedSquares.TILE_ZOOM;
import static org.dstadler.ctw.CreateListOfVisitedSquares.VISITED_TILES_TXT;
import static org.dstadler.ctw.MatrixUtils.ZONE;

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

import com.github.filosganga.geogson.model.Feature;
import com.github.filosganga.geogson.model.LinearRing;
import com.github.filosganga.geogson.model.Point;
import com.github.filosganga.geogson.model.Polygon;
import com.google.gson.JsonPrimitive;

import uk.me.jstott.jcoord.LatLng;

/**
 * This application reads the list of covered squares and
 * tries to find the largest area covered by a square.
 *
 * Note: Currently only UTMRef-LonZone "33" is used to make
 * computation easier. If the rectangle should someday span
 * more than one Zone, this tool likely needs a major overhaul!
 */
public class CreateLargestSquareGeoJSONTiles {
	private static final Logger log = LoggerFactory.make();

	public static final String CLUSTER_TILES_JSON = "LargestSquareTiles.js";
	public static final String CLUSTER_TILES_TXT = "LargestSquareTiles.txt";

	public static void main(String[] args) throws IOException {
		LoggerFactory.initLogging();

		Set<OSMTile> tiles = OSMTile.readTiles(new File(VISITED_TILES_TXT));

		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
				minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		for (OSMTile tile : tiles) {
			LatLng latLng = tile.toLatLng();
			if (latLng.toUTMRef().getLngZone() != ZONE) {
				continue;
			}

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

		int[][] M = MatrixUtils.populateMatrix(tiles, minX, minY, maxX, maxY, ZONE);

		Pair<Rectangle,Integer> result = MatrixUtils.maxSubSquare(M);
		Rectangle rect = result.getKey();

		log.info("Area of maximum square " + rect + ": " + rect.width + "x" + rect.height +
				" = " + result.getValue());

		OSMTile squareMin = new OSMTile(TILE_ZOOM, minX + rect.x - rect.width, minY + rect.y - rect.height + 1);
		OSMTile squareMax = new OSMTile(TILE_ZOOM, minX + rect.x, minY + rect.y + 1);

		/*UTMRefWithHash recRefMin = UTMRefWithHash.fromString(ZONE + "U " +
				(minEast + (rect.x - rect.width) * 1000) + " " + (minNorth + (rect.y - rect.height) * 1000 + 1000));
		UTMRefWithHash recRefMax = UTMRefWithHash.fromString(ZONE + "U " +
				(minEast + rect.x * 1000) + " " + (minNorth + rect.y * 1000 + 1000));*/
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
		GeoJSON.writeGeoJSON(CLUSTER_TILES_JSON, "tilesquare", features);

		// create list of latLngBounds for SVG elements to overlay
		try (Writer writer = new BufferedWriter(new FileWriter(CLUSTER_TILES_TXT))) {
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
