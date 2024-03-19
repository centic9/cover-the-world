package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.OSMTile;
import org.dstadler.ctw.utils.UTMRefWithHash;

import com.github.filosganga.geogson.model.Feature;
import com.google.common.base.Preconditions;

/**
 * Small application to compute the largest cluster of squares
 * defined as all connected squares where each of the four
 * neighbouring squares are covered as well.
 *
 * This is computed by searching for squares with four neighbours
 * and then expanding the cluster out as far as possible.
 *
 * Results are stored in a TXT file for easy diffing via version
 * control and a JS file which can be used as overlay layer in a
 * Leaflet-based HTML page.
 */
public class CreateLargestClusterGeoJSONSquares {
    private static final Logger log = LoggerFactory.make();

    public static final String LARGEST_CLUSTER_SQUARES_JSON = "js/LargestClusterSquares.js";
    public static final String LARGEST_CLUSTER_SQUARES_TXT = "txt/LargestClusterSquares.txt";

    public static void main(String[] args) throws IOException {
        LoggerFactory.initLogging();

		log.info("Computing largest cluster squares");

		List<List<UTMRefWithHash>> clusters = computeLargestCluster();

		clusters.sort(Comparator.
				comparingInt((List<UTMRefWithHash> o) -> o.size()).
				thenComparingInt(List::hashCode));

		log.info("Found " + clusters.size() + " cluster, top 5: \n" +
				clusters.
						// print the top 5
						subList(clusters.size() < 6 ? 0 : clusters.size() - 6, clusters.size() < 1 ? 0 : clusters.size() - 1).
						stream().
						// convert to string
						map(r -> r.size() + ": " + r).
						// print on separate lines
						collect(Collectors.joining("\n")));


		if (clusters.isEmpty()) {
			log.info("Did not find any clusters for squares");
			GeoJSON.writeGeoJSON(LARGEST_CLUSTER_SQUARES_JSON, "largest", Collections.emptyList());
			FileUtils.writeStringToFile(new File(LARGEST_CLUSTER_SQUARES_TXT), "", "UTF-8");
			return;
		}

		// build the GeoJSON features from the larges cluster
		List<Feature> features = new ArrayList<>();
		List<UTMRefWithHash> largestCluster = clusters.get(clusters.size() - 1);
		Set<String> largestClusterStr = new TreeSet<>();
		for (UTMRefWithHash square : largestCluster) {
			features.add(GeoJSON.createSquare(square.getRectangle(),
					"Largest Cluster: " + largestCluster.size() + " squares"));
			largestClusterStr.add(square.toString());
		}

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJSON(LARGEST_CLUSTER_SQUARES_JSON, "largest", features);

		// create list of latLngBounds for SVG elements to overlay
		try (Writer writer = new BufferedWriter(new FileWriter(LARGEST_CLUSTER_SQUARES_TXT))) {
			for (String square : largestClusterStr) {
				writer.write(square);
				writer.write('\n');
			}
		}
    }

	private static List<List<UTMRefWithHash>> computeLargestCluster() throws IOException {
		List<List<UTMRefWithHash>> clusters = new ArrayList<>();

		Set<UTMRefWithHash> squares = UTMRefWithHash.readSquares(new File(VISITED_SQUARES_TXT));
		Preconditions.checkState(squares.size() > 0,
				"Did not read any squares from " + VISITED_SQUARES_TXT);

		Set<UTMRefWithHash> allSquares = new HashSet<>(squares);

		// check each square
		while (squares.size() > 0) {
			Iterator<UTMRefWithHash> it = squares.iterator();
			UTMRefWithHash square = it.next();

			// remove this entry as we either add it to a cluster or discard it
			// if it is not connected 4 times
			it.remove();

			// connected on four sides?
			if (partOfCluster(square, allSquares)) {
				// add to a cluster or create a new one
				boolean found = false;
				List<UTMRefWithHash> foundCluster = null;
				for (List<UTMRefWithHash> cluster : clusters) {
					if (isAdjacent(cluster, square)) {
						//log.info("Square: " + square + ": cluster: " + cluster);
						cluster.add(square);
						found = true;
						foundCluster = cluster;
						break;
					}
				}

				if (!found) {
					log.info("Found square in new cluster: " + square + ": " + OSMTile.fromLatLngZoom(
							square.toLatLng().getLatitude(),
							square.toLatLng().getLongitude(), 12));

					List<UTMRefWithHash> cluster = new ArrayList<>();
					cluster.add(square);
					clusters.add(cluster);
					foundCluster = cluster;
				} else {
					log.info("Found square in existing cluster: " + square + ": " + OSMTile.fromLatLngZoom(
							square.toLatLng().getLatitude(),
							square.toLatLng().getLongitude(), 12));
				}

				extendCluster(squares, allSquares, foundCluster);
			}
		}

		return clusters;
	}

	private static void extendCluster(Set<UTMRefWithHash> squares, Set<UTMRefWithHash> allSquares,
			List<UTMRefWithHash> foundCluster) {
		// extend this cluster as far as possible to speed up
		// processing and avoid disconnected clusters
		while (true) {
			int count = 0;
			Iterator<UTMRefWithHash> it = squares.iterator();
			while (it.hasNext()) {
				UTMRefWithHash square = it.next();

				// if this square has 4 neighbours and is adjacent to
				// the current cluster, then add it
				if (partOfCluster(square, allSquares) && isAdjacent(foundCluster, square)) {
					foundCluster.add(square);
					it.remove();
					count++;
				}
			}

			if (count == 0) {
				break;
			}

			log.info("Added " + count + " additional squares to the cluster");
		}
	}

	private static boolean isAdjacent(List<UTMRefWithHash> cluster, UTMRefWithHash ref) {
		return cluster.contains(ref.up()) ||
				cluster.contains(ref.down()) ||
				cluster.contains(ref.right()) ||
				cluster.contains(ref.left());
	}

	private static boolean partOfCluster(UTMRefWithHash ref, Set<UTMRefWithHash> squares) {
        return squares.contains(ref.up()) &&
                squares.contains(ref.down()) &&
                squares.contains(ref.right()) &&
                squares.contains(ref.left());
    }
}
