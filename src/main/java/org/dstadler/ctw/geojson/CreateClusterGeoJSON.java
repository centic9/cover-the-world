package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;

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
import org.dstadler.ctw.utils.BaseTile;
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

	public static final String CLUSTER_TILES_JSON = "js/ClusterTiles.js";
	public static final String CLUSTER_TILES_TXT = "txt/ClusterTiles.txt";

	public static void main(String[] args) throws IOException {
		LoggerFactory.initLogging();

		Set<UTMRefWithHash> refs = UTMRefWithHash.readSquares(new File(VISITED_SQUARES_TXT));
		run("squares", VISITED_SQUARES_TXT, refs, CLUSTER_SQUARES_JSON, CLUSTER_SQUARES_TXT);

		Set<OSMTile> tiles = OSMTile.readTiles(new File(VISITED_TILES_TXT));
		run("tiles", VISITED_TILES_TXT, tiles, CLUSTER_TILES_JSON, CLUSTER_TILES_TXT);
	}

	private static <T> void run(String title, String visitedTxt, Set<? extends BaseTile<T>> squares, String clusterJS,
			String clusterTxt) throws IOException {
		log.info("Computing all cluster " +  title);

		Preconditions.checkState(squares.size() > 0,
				"Did not read any " + title + " from " + visitedTxt);

		Set<String> clusterSquares = new TreeSet<>();
		List<Feature> features = new ArrayList<>();
		for (BaseTile<T> ref : squares) {
			if (partOfCluster(ref, squares)) {
				log.fine("Found square in cluster: " + ref + ": " + OSMTile.fromLatLngZoom(
						ref.toLatLng().getLatitude(),
						ref.toLatLng().getLongitude(), 12));
				features.add(GeoJSON.createSquare(ref.getRectangle(), null));
				clusterSquares.add(ref.toString());
			}
		}

		log.info("Found " + clusterSquares.size() + " cluster-" + title + " for " + squares.size() + " " + title);

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJavaScript(clusterJS, title + "cluster", features);

		// also write the file in pure JSON for use in later steps
		GeoJSON.writeGeoJSON(GeoJSON.getJSONFileName(clusterJS), features);

		// create list of latLngBounds for SVG elements to overlay
		try (Writer writer = new BufferedWriter(new FileWriter(clusterTxt))) {
			for (String square : clusterSquares) {
				writer.write(square);
				writer.write('\n');
			}
		}

		log.info("Wrote " + features.size() + " cluster-" + title + " to " + clusterJS);
	}

	private static <T> boolean partOfCluster(BaseTile<T> ref, Set<? extends BaseTile<T>> squares) {
		return squares.contains(ref.up()) &&
				squares.contains(ref.down()) &&
				squares.contains(ref.left()) &&
				squares.contains(ref.right());
	}
}
