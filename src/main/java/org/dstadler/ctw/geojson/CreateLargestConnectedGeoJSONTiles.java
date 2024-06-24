package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;

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
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.OSMTile;

import com.github.filosganga.geogson.model.Feature;
import com.google.common.base.Preconditions;

/**
 * Small application to compute the largest set of connected tiles
 * defined as all connected tiles where one of the four
 * neighbouring tiles are covered as well.
 *
 * This is computed by searching for tiles with neighbours
 * and then expanding the cluster out as far as possible.
 *
 * Results are stored in a TXT file for easy diffing via version
 * control and a JS file which can be used as overlay layer in a
 * Leaflet-based HTML page.
 */
public class CreateLargestConnectedGeoJSONTiles {
    private static final Logger log = LoggerFactory.make();

    public static final String LARGEST_CONNECTED_TILES_JSON = "js/LargestConnectedTiles.js";
    public static final String LARGEST_CONNECTED_TILES_TXT = "txt/LargestConnectedTiles.txt";

    public static void main(String[] args) throws IOException {
        LoggerFactory.initLogging();

		log.info("Computing largest connected tiles");

		List<List<OSMTile>> connected = computeLargestConnected();

		connected.sort(Comparator.
				comparingInt((List<OSMTile> o) -> o.size()).
				thenComparingInt(List::hashCode));

		log.info("Found " + connected.size() + " connected, top 5: \n" +
				connected.
						// print the top 5
						subList(connected.size() < 6 ? 0 : connected.size() - 6, connected.size() < 1 ? 0 : connected.size() - 1).
						stream().
						// convert to string
						map(r -> r.size() + ": " + r).
						// print on separate lines
						collect(Collectors.joining("\n")));

		if (connected.isEmpty()) {
			log.info("Did not find any connected for tiles");
			GeoJSON.writeGeoJavaScript(LARGEST_CONNECTED_TILES_JSON, "largestconnectedtiles", Collections.emptyList());
			FileUtils.writeStringToFile(new File(LARGEST_CONNECTED_TILES_TXT), "", "UTF-8");
			return;
		}

		// build the GeoJSON features from the larges cluster
		List<Feature> features = new ArrayList<>();
		List<OSMTile> largestConnected = connected.get(connected.size() - 1);
		largestConnected.sort(Comparator.naturalOrder());
		for (OSMTile tile : largestConnected) {
			features.add(GeoJSON.createSquare(tile.getRectangle(),
					"Largest Connected: " + largestConnected.size() + " tiles"));
		}

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJavaScript(LARGEST_CONNECTED_TILES_JSON, "largestconnectedtiles", features);

		// also write the file in pure JSON for use in later steps
		GeoJSON.writeGeoJSON(GeoJSON.getJSONFileName(LARGEST_CONNECTED_TILES_JSON), features);

		// create list of latLngBounds for SVG elements to overlay
		try (Writer writer = new BufferedWriter(new FileWriter(LARGEST_CONNECTED_TILES_TXT))) {
			for (OSMTile tile : largestConnected) {
				writer.write(tile.toCoords());
				writer.write('\n');
			}
		}
    }

	private static List<List<OSMTile>> computeLargestConnected() throws IOException {
		List<List<OSMTile>> connected = new ArrayList<>();

		Set<OSMTile> tiles = OSMTile.readTiles(new File(VISITED_TILES_TXT));
		Preconditions.checkState(tiles.size() > 0,
				"Did not read any tiles from " + VISITED_TILES_TXT);

		Set<OSMTile> allTiles = new HashSet<>(tiles);

		// check each tile
		while (tiles.size() > 0) {
			Iterator<OSMTile> it = tiles.iterator();
			OSMTile tile = it.next();

			// remove this entry as we either add it to a cluster or discard it
			// if it is not connected 4 times
			it.remove();

			// connected on any side?
			if (isConnected(allTiles, tile)) {
				// add to a cluster or create a new one
				boolean found = false;
				List<OSMTile> foundCluster = null;
				for (List<OSMTile> cluster : connected) {
					if (isAdjacent(cluster, tile)) {
						//log.info("Tile: " + tile + ": cluster: " + cluster);
						cluster.add(tile);
						found = true;
						foundCluster = cluster;
						break;
					}
				}

				if (!found) {
					log.info("Found tile in new connected: " + tile);

					List<OSMTile> cluster = new ArrayList<>();
					cluster.add(tile);
					connected.add(cluster);
					foundCluster = cluster;
				} else {
					log.info("Found tile in existing connected: " + tile);
				}

				extendConnected(tiles, foundCluster);
			}
		}

		return connected;
	}

	private static void extendConnected(Set<OSMTile> tiles, List<OSMTile> foundCluster) {
		// extend this connected as far as possible to speed up
		// processing and avoid disconnected clusters
		while (true) {
			int count = 0;
			Iterator<OSMTile> it = tiles.iterator();
			while (it.hasNext()) {
				OSMTile tile = it.next();

				// if this square is adjacent to
				// the current cluster, then add it
				if (isAdjacent(foundCluster, tile)) {
					foundCluster.add(tile);
					it.remove();
					count++;
				}
			}

			if (count == 0) {
				break;
			}

			log.info("Added " + count + " additional tiles to the cluster");
		}
	}

	private static boolean isAdjacent(List<OSMTile> cluster, OSMTile ref) {
		return cluster.contains(ref.up()) ||
				cluster.contains(ref.down()) ||
				cluster.contains(ref.right()) ||
				cluster.contains(ref.left());
	}

	private static boolean isConnected(Set<OSMTile> tiles, OSMTile ref) {
        return tiles.contains(ref.up()) ||
                tiles.contains(ref.down()) ||
                tiles.contains(ref.right()) ||
                tiles.contains(ref.left());
    }
}
