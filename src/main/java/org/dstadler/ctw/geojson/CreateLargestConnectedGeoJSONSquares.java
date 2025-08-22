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
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.OSMTile;
import org.dstadler.ctw.utils.UTMRefWithHash;

import com.github.filosganga.geogson.model.Feature;
import com.google.common.base.Preconditions;

/**
 * Small application to compute the largest set of connected squares
 * defined as all connected squares where one of the four
 * neighbouring squares are covered as well.
 *
 * This is computed by searching for squares with neighbours
 * and then expanding the cluster out as far as possible.
 *
 * Results are stored in a TXT file for easy diffing via version
 * control and a JS file which can be used as overlay layer in a
 * Leaflet-based HTML page.
 */
public class CreateLargestConnectedGeoJSONSquares {
    private static final Logger log = LoggerFactory.make();

    public static final String LARGEST_CONNECTED_SQUARES_JSON = "js/LargestConnectedSquares.js";
    public static final String LARGEST_CONNECTED_SQUARES_TXT = "txt/LargestConnectedSquares.txt";

    public static void main(String[] args) throws IOException {
        LoggerFactory.initLogging();

		log.info("Computing largest connected squares");

		List<Set<UTMRefWithHash>> connected = computeLargestConnected();

		connected.sort(Comparator.
				comparingInt((Set<UTMRefWithHash> o) -> o.size()).
				thenComparingInt(Set::hashCode).
				reversed());

		log.info("Found " + connected.size() + " connected, top 5: \n" +
				connected.
						// print the top 5
						subList(0, Math.min(connected.size(), 5)).
						stream().
						// convert to string
						map(r -> r.size() + ": " + StringUtils.abbreviate(r.toString(), 256)).
						// print on separate lines
						collect(Collectors.joining("\n")));


		if (connected.isEmpty()) {
			log.info("Did not find any connected for squares");
			GeoJSON.writeGeoJavaScript(LARGEST_CONNECTED_SQUARES_JSON, "largestconnected", Collections.emptyList());
			FileUtils.writeStringToFile(new File(LARGEST_CONNECTED_SQUARES_TXT), "", "UTF-8");
			return;
		}

		// build the GeoJSON features from the larges connected
		List<Feature> features = new ArrayList<>();
		List<UTMRefWithHash> largestConnected = new ArrayList<>(connected.get(0));
		largestConnected.sort(Comparator.naturalOrder());
		for (UTMRefWithHash square : largestConnected) {
			features.add(GeoJSON.createSquare(square.getRectangle(),
					"Largest Connected: " + largestConnected.size() + " squares"));
		}

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJavaScript(LARGEST_CONNECTED_SQUARES_JSON, "largestconnected", features);

		// also write the file in pure JSON for use in later steps
		GeoJSON.writeGeoJSON(GeoJSON.getJSONFileName(LARGEST_CONNECTED_SQUARES_JSON), features);

		// create list of latLngBounds for SVG elements to overlay
		try (Writer writer = new BufferedWriter(new FileWriter(LARGEST_CONNECTED_SQUARES_TXT))) {
			for (UTMRefWithHash square : largestConnected) {
				writer.write(square.toString());
				writer.write('\n');
			}
		}
    }

	private static List<Set<UTMRefWithHash>> computeLargestConnected() throws IOException {
		List<Set<UTMRefWithHash>> connected = new ArrayList<>();

		Set<UTMRefWithHash> squares = UTMRefWithHash.readSquares(new File(VISITED_SQUARES_TXT));
		Preconditions.checkState(squares.size() > 0,
				"Did not read any squares from " + VISITED_SQUARES_TXT);

		Set<UTMRefWithHash> allSquares = new HashSet<>(squares);

		// check each square
		while (squares.size() > 0) {
			Iterator<UTMRefWithHash> it = squares.iterator();
			UTMRefWithHash square = it.next();

			// remove this entry as we either add it to a cluster or discard it
			// if it is not connected
			it.remove();

			// connected on any side?
			if (isConnected(allSquares, square)) {
				// add to a cluster or create a new one
				boolean found = false;
				Set<UTMRefWithHash> foundCluster = null;
				for (Set<UTMRefWithHash> cluster : connected) {
					if (isConnected(cluster, square)) {
						//log.info("Square: " + square + ": cluster: " + cluster);
						cluster.add(square);
						found = true;
						foundCluster = cluster;
						break;
					}
				}

				if (!found) {
					log.info("Found square in new connected: " + square + ": " + OSMTile.fromLatLngZoom(
							square.toLatLng().getLatitude(),
							square.toLatLng().getLongitude(), 12));

					Set<UTMRefWithHash> cluster = new HashSet<>();
					cluster.add(square);
					connected.add(cluster);
					foundCluster = cluster;
				} else {
					log.info("Found square in existing connected: " + square + ": " + OSMTile.fromLatLngZoom(
							square.toLatLng().getLatitude(),
							square.toLatLng().getLongitude(), 12));
				}

				extendConnected(squares, foundCluster);
			}
		}

		return connected;
	}

	private static void extendConnected(Set<UTMRefWithHash> squares, Set<UTMRefWithHash> foundCluster) {
		// extend this connected as far as possible to speed up
		// processing and avoid disconnected clusters
		while (true) {
			int count = 0;
			Iterator<UTMRefWithHash> it = squares.iterator();
			while (it.hasNext()) {
				UTMRefWithHash square = it.next();

				// if this square is adjacent to
				// the current cluster, then add it
				if (isConnected(foundCluster, square)) {
					foundCluster.add(square);
					it.remove();
					count++;
				}
			}

			if (count == 0) {
				break;
			}

			log.info("Added " + count + " additional squares to the connected set, now having " + foundCluster.size() + " connected squares");
		}
	}

	private static boolean isConnected(Set<UTMRefWithHash> squares, UTMRefWithHash ref) {
        return squares.contains(ref.up()) ||
                squares.contains(ref.down()) ||
                squares.contains(ref.right()) ||
                squares.contains(ref.left());
    }
}
