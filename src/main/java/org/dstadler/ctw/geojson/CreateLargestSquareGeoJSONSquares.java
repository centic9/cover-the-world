package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;
import static org.dstadler.ctw.utils.Constants.SQUARE_SIZE;
import static org.dstadler.ctw.utils.Constants.ZONE;

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
import org.dstadler.ctw.utils.UTMRefWithHash;

import com.github.filosganga.geogson.model.Feature;
import com.github.filosganga.geogson.model.LinearRing;
import com.github.filosganga.geogson.model.Point;
import com.github.filosganga.geogson.model.Polygon;
import com.google.common.base.Preconditions;
import com.google.gson.JsonPrimitive;

/**
 * This application reads the list of covered squares and
 * tries to find the largest area covered by a square.
 *
 * Note: Currently only UTMRef-LonZone "33" is used to make
 * computation easier. If the rectangle should someday span
 * more than one Zone, this tool likely needs a major overhaul!
 *
 * Results are stored in a TXT file for easy diffing via version
 * control and a JS file which can be used as overlay layer in a
 * Leaflet-based HTML page.
 */
public class CreateLargestSquareGeoJSONSquares {
	private static final Logger log = LoggerFactory.make();

	public static final String CLUSTER_SQUARE_JSON = "js/LargestSquareSquares.js";
	public static final String CLUSTER_SQUARE_TXT = "txt/LargestSquareSquares.txt";

	public static void main(String[] args) throws IOException {
		LoggerFactory.initLogging();

		Set<UTMRefWithHash> squares = UTMRefWithHash.readSquares(new File(VISITED_SQUARES_TXT));
		Preconditions.checkState(squares.size() > 0,
				"Did not read any squares from " + VISITED_SQUARES_TXT);

		double minEast = Double.MAX_VALUE, maxEast = Double.MIN_VALUE,
				minNorth = Double.MAX_VALUE, maxNorth = Double.MIN_VALUE;
		for (UTMRefWithHash square : squares) {
			if (square.getLngZone() != ZONE) {
				continue;
			}

			if (square.getEasting() > maxEast) {
				maxEast = square.getEasting();
			}
			if (square.getEasting() < minEast) {
				minEast = square.getEasting();
			}

			if (square.getNorthing() > maxNorth) {
				maxNorth = square.getNorthing();
			}
			if (square.getNorthing() < minNorth) {
				minNorth = square.getNorthing();
			}
		}

		int[][] M = MatrixUtils.populateMatrix(squares, minEast, minNorth, maxEast, maxNorth, ZONE);

		Pair<Rectangle,Integer> result = MatrixUtils.maxSubSquare(M);
		Rectangle rect = result.getKey();

		log.info("Area of maximum square " + rect + ": " + rect.width + "x" + rect.height +
				" = " + result.getValue());

		UTMRefWithHash recRefMinMin = new UTMRefWithHash(ZONE, 'U',
				(minEast + (rect.x - rect.width) * SQUARE_SIZE), (minNorth + (rect.y - rect.height) * SQUARE_SIZE + SQUARE_SIZE));
		UTMRefWithHash recRefMaxMin = new UTMRefWithHash(ZONE, 'U',
				(minEast + (rect.x - rect.width) * SQUARE_SIZE), (minNorth + rect.y * SQUARE_SIZE + SQUARE_SIZE));
		UTMRefWithHash recRefMinMax = new UTMRefWithHash(ZONE, 'U',
				(minEast + rect.x * SQUARE_SIZE), (minNorth + (rect.y - rect.height) * SQUARE_SIZE + SQUARE_SIZE));
		UTMRefWithHash recRefMaxMax = new UTMRefWithHash(ZONE, 'U',
				(minEast + rect.x * SQUARE_SIZE), (minNorth + rect.y * SQUARE_SIZE + SQUARE_SIZE));

		log.info("Found largest square at " + rect.x + "x" + rect.y + " with size " + rect.width + "x" + rect.height
				/*"\n" + recRefMinMin +
				"\n" + recRefMaxMin +
				"\n" + recRefMinMax +
				"\n" + recRefMaxMax +
				"\n" + recRefMinMin.toLatLng() +
				"\n" + recRefMaxMin.toLatLng() +
				"\n" + recRefMinMax.toLatLng() +
				"\n" + recRefMaxMax.toLatLng() +
				"\n" + OSMTile.fromLatLngZoom(recRefMinMin.toLatLng().getLatitude(), recRefMinMin.toLatLng().getLongitude(), 13) +
				"\n" + OSMTile.fromLatLngZoom(recRefMaxMin.toLatLng().getLatitude(), recRefMaxMin.toLatLng().getLongitude(), 13) +
				"\n" + OSMTile.fromLatLngZoom(recRefMinMax.toLatLng().getLatitude(), recRefMinMax.toLatLng().getLongitude(), 13) +
				"\n" + OSMTile.fromLatLngZoom(recRefMaxMax.toLatLng().getLatitude(), recRefMaxMax.toLatLng().getLongitude(), 13)*/
		);

		// produce the GeoJSON for the rectangle
		List<Feature> features = Collections.singletonList(
				Feature.builder().withGeometry(Polygon.of(LinearRing.of(
				Point.from(recRefMinMin.toLatLng().getLongitude(), recRefMinMin.toLatLng().getLatitude()),
				Point.from(recRefMaxMin.toLatLng().getLongitude(), recRefMaxMin.toLatLng().getLatitude()),
				Point.from(recRefMaxMax.toLatLng().getLongitude(), recRefMaxMax.toLatLng().getLatitude()),
				Point.from(recRefMinMax.toLatLng().getLongitude(), recRefMinMax.toLatLng().getLatitude()),
				Point.from(recRefMinMin.toLatLng().getLongitude(), recRefMinMin.toLatLng().getLatitude())
		))).withProperty("popupContent", new JsonPrimitive(
				result.getValue() + " squares: " + rect.width + "x" + rect.height)).build());

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJSON(CLUSTER_SQUARE_JSON, "square", features);

		// create list of latLngBounds for SVG elements to overlay
		try (Writer writer = new BufferedWriter(new FileWriter(CLUSTER_SQUARE_TXT))) {
			writer.write(recRefMinMin.toString());
			writer.write('\n');
			writer.write(recRefMaxMax.toString());
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
