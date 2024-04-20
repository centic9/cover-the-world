package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_NEW_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_NEW_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.BaseTile;
import org.dstadler.ctw.utils.OSMTile;
import org.dstadler.ctw.utils.UTMRefWithHash;

import com.github.filosganga.geogson.model.Feature;

/**
 * Small application which reads the list of covered squares/tiles
 * from a simple text-file and produces GeoJSON for so-called
 * "adjacent" squares/tiles embedded in a JavaScript file which
 * can be loaded in a leaflet.js map to display a border around these
 * tiles to help in planning routes.
 *
 * Results are stored in JS files which can be used as overlay
 * layer in a Leaflet-based HTML page.
 */
public class CreateAdjacent {
	private static final Logger log = LoggerFactory.make();

	// squares
	public static final String ADJACENT_SQUARES_JS = "js/AdjacentSquares.js";
	public static final String ADJACENT_SQUARES_NEW_JS = "js/AdjacentSquaresNew.js";
	public static final String ADJACENT_SQUARES_TXT = "txt/AdjacentSquares.txt";
	public static final String ADJACENT_SQUARES_NEW_TXT = "txt/AdjacentSquaresNew.txt";

	// tiles
	public static final String ADJACENT_TILES_JS = "js/AdjacentTiles.js";
	public static final String ADJACENT_TILES_NEW_JS = "js/AdjacentTilesNew.js";
	public static final String ADJACENT_TILES_TXT = "txt/AdjacentTiles.txt";
	public static final String ADJACENT_TILES_NEW_TXT = "txt/AdjacentTilesNew.txt";

	// how many adjacent tiles we create around covered squares/tiles
	private static final int RECURSE_LEVEL = 3;

	public static void main(String[] args) throws IOException {
		LoggerFactory.initLogging();

		final int recurseLevel;
		if (args.length > 0) {
			recurseLevel = Integer.parseInt(args[0]);
		} else {
			recurseLevel = RECURSE_LEVEL;
		}

		log.info("Computing GeoJSON for adjacent squares and tiles with recurseLevel " + recurseLevel);

		writeGeoJSON(VISITED_SQUARES_TXT, ADJACENT_SQUARES_JS, "adjacentSquares",
				UTMRefWithHash::fromString, "squares", ADJACENT_SQUARES_TXT, null, recurseLevel);

		writeGeoJSON(VISITED_SQUARES_NEW_TXT, ADJACENT_SQUARES_NEW_JS, "adjacentSquaresNew",
				UTMRefWithHash::fromString, "squares", ADJACENT_SQUARES_NEW_TXT, VISITED_SQUARES_TXT, recurseLevel);

		writeGeoJSON(VISITED_TILES_TXT, ADJACENT_TILES_JS, "adjacentTiles",
				OSMTile::fromString, "tiles", ADJACENT_TILES_TXT, null, recurseLevel);

		writeGeoJSON(VISITED_TILES_NEW_TXT, ADJACENT_TILES_NEW_JS, "adjacentTilesNew",
				OSMTile::fromString, "tiles", ADJACENT_TILES_NEW_TXT, VISITED_TILES_TXT, recurseLevel);
	}

	private static <T extends BaseTile<T>> void writeGeoJSON(String squaresFile, String jsonOutputFile, String varPrefix,
			Function<String, T> toObject,
			String title, String adjacentTxtFile, String fullTxtFile, int recurseLevel) throws IOException {
		log.info("Writing from " + squaresFile + " to " + jsonOutputFile +
				" with prefix '" + varPrefix + "' and title " + title);

		// read list of UTMRefs for covered or new squares
		Set<BaseTile<T>> squares = readFile(new File(squaresFile)).
				stream().
				map(toObject).
				collect(Collectors.toSet());

		// add adjacent tiles with borders
		// do not generate for zoom 12 and lower
		Set<BaseTile<T>> adjacentTiles = new HashSet<>();
		for (BaseTile<T> tile : squares) {
			addAdjacentTiles(squares, adjacentTiles, tile, recurseLevel);
		}

		log.info("Having " + adjacentTiles.size() + " adjacent tiles");

		// remove adjacent-tiles which are already covered
		if (fullTxtFile != null) {
			Set<BaseTile<T>> fullSquares = readFile(new File(fullTxtFile)).
					stream().
					map(toObject).
					collect(Collectors.toSet());

			adjacentTiles.removeAll(fullSquares);

			log.info("Having " + adjacentTiles.size() + " adjacent tiles after removing already covered ones");
		}

		writeJsFile(adjacentTiles, varPrefix, jsonOutputFile);

		// write list of adjacent tiles to text-file
		writeListOfAdjacent(
				adjacentTiles.stream().
						map(BaseTile::string).
						collect(Collectors.toSet()),
				adjacentTxtFile);

		log.info("Wrote " + adjacentTiles.size() + " adjacent " + title + " from " + squaresFile + " to " + jsonOutputFile);
	}

	private static Set<String> readFile(File file) throws IOException {
		return file.exists() ?
				new TreeSet<>(FileUtils.readLines(file, StandardCharsets.UTF_8)) :
				Collections.emptySet();
	}

	private static <T> void addAdjacentTiles(Set<BaseTile<T>> tilesIn, Set<BaseTile<T>> adjacentTiles, BaseTile<T> tile, int recurse) {
		addAdjacentTile(tilesIn, adjacentTiles, tile);
		if (recurse == 0) {
			return;
		}

		addAdjacentTiles(tilesIn, adjacentTiles, tile.up(), recurse - 1);
		addAdjacentTiles(tilesIn, adjacentTiles, tile.down(), recurse - 1);
		addAdjacentTiles(tilesIn, adjacentTiles, tile.left(), recurse - 1);
		addAdjacentTiles(tilesIn, adjacentTiles, tile.right(), recurse - 1);
	}

	private static <T> void addAdjacentTile(Set<BaseTile<T>> tilesIn, Set<BaseTile<T>> adjacentTiles, BaseTile<T> newTile) {
		if (!tilesIn.contains(newTile)) {
			adjacentTiles.add(newTile);
		}
	}

	private static <T extends BaseTile<T>> void writeJsFile(Set<BaseTile<T>> adjacentTiles,
			String varPrefix,
			String jsonOutputFile)
			throws IOException {
		// add GeoJSON for all squares/tiles
		List<Feature> features = new ArrayList<>();
		for (BaseTile<T> adjacentTile : adjacentTiles) {
			features.add(GeoJSON.createLines(adjacentTile.getRectangle(),
					null
					/*square + "\n" + toRectangle.apply(square)*/));
		}

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJavaScript(jsonOutputFile, varPrefix, features);

		// also write the file in pure JSON for use in later steps
		GeoJSON.writeGeoJSON(StringUtils.removeEnd(jsonOutputFile, ".js") + ".json", features);
	}

	private static void writeListOfAdjacent(Set<String> adjacent, String adjacentTxtFile) throws IOException {
		// create list of latLngBounds for SVG elements to overlay
		try (Writer writer = new BufferedWriter(new FileWriter(adjacentTxtFile))) {
			for (String square : new TreeSet<>(adjacent)) {
				writer.write(square);
				writer.write('\n');
			}
		}
	}
}
