package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.OSMTile;
import org.dstadler.ctw.utils.UTMRefWithHash;

import com.github.filosganga.geogson.model.Feature;
import com.google.common.base.Preconditions;

/**
 * Simple tool to create a list of all squares that are part
 * of a "cluster", i.e. squares which have all 4 direct
 * neighbours also covered.
 *
 * Results are stored in a TXT file for easy diffing via version
 * control and a JS file which can be used as overlay layer in a
 * Leaflet-based HTML page.
 */
public class CreateClusterGeoJSON {
	private static final Logger log = LoggerFactory.make();

	public static final String CLUSTER_SQUARES_JSON = "js/ClusterSquares.js";
	public static final String CLUSTER_SQUARES_TXT = "txt/ClusterSquares.txt";

	public static void main(String[] args) throws IOException {
		LoggerFactory.initLogging();

		log.info("Computing all cluster squares");

		Set<UTMRefWithHash> squares = UTMRefWithHash.readSquares(new File(VISITED_SQUARES_TXT));
		Preconditions.checkState(squares.size() > 0,
				"Did not read any squares from " + VISITED_SQUARES_TXT);

		Set<String> clusterSquares = new TreeSet<>();
		List<Feature> features = new ArrayList<>();
		for (UTMRefWithHash ref : squares) {
			if (partOfCluster(ref, squares)) {
				log.fine("Found square in cluster: " + ref + ": " + OSMTile.fromLatLngZoom(
						ref.toLatLng().getLatitude(),
						ref.toLatLng().getLongitude(), 12));
				features.add(GeoJSON.createSquare(ref.getRectangle(), null));
				clusterSquares.add(ref.toString());
			}
		}

		log.info("Found " + clusterSquares.size() + " cluster-squares for " + squares.size() + " squares");

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJSON(CLUSTER_SQUARES_JSON, "cluster", features);

		// create list of latLngBounds for SVG elements to overlay
		try (Writer writer = new BufferedWriter(new FileWriter(CLUSTER_SQUARES_TXT))) {
			for (String square : clusterSquares) {
				writer.write(square);
				writer.write('\n');
			}
		}

		log.info("Wrote " + features.size() + " cluster-squares to " + CLUSTER_SQUARES_JSON);
	}

	private static boolean partOfCluster(UTMRefWithHash ref, Set<UTMRefWithHash> squares) {
		return squares.contains(ref.up()) &&
				squares.contains(ref.down()) &&
				squares.contains(ref.left()) &&
				squares.contains(ref.right());
	}
}
