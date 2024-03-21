package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;
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
 * Small application which reads the list of covered squares
 * from a simple text-file and produces GeoJSON embedded in
 * a JavaScript file which can be loaded in a leaflet.js map
 * to display covered squares.
 *
 * Results are stored in JS files which can be used as overlay
 * layer in a Leaflet-based HTML page.
 */
public class CreateAdjacent {
	private static final Logger log = LoggerFactory.make();

	// squares
	public static final String ADJACENT_SQUARES_JSON = "js/AdjacentSquares.js";
	public static final String ADJACENT_SQUARES_TXT = "txt/AdjacentSquares.txt";

	// tiles
	public static final String ADJACENT_TILES_JSON = "js/AdjacentTiles.js";
	public static final String ADJACENT_TILES_TXT = "txt/AdjacentTiles.txt";

	public static void main(String[] args) throws IOException {
		LoggerFactory.initLogging();

		log.info("Computing GeoJSON for visited squares and tiles");

		writeGeoJSON(VISITED_SQUARES_TXT, ADJACENT_SQUARES_JSON, "adjacentSquares",
				UTMRefWithHash::fromString, "squares", ADJACENT_SQUARES_TXT);

		writeGeoJSON(VISITED_TILES_TXT, ADJACENT_TILES_JSON, "adjacentTiles",
				OSMTile::fromString, "tiles", ADJACENT_TILES_TXT);
	}

	private static <T extends BaseTile<T>> void writeGeoJSON(String squaresFile, String jsonOutputFile, String varPrefix,
			Function<String, T> toObject,
			String title, String adjacentTxtFile) throws IOException {
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
			addAdjacentTiles(squares, adjacentTiles, tile);
			addAdjacentTiles(squares, adjacentTiles, tile.up());
			addAdjacentTiles(squares, adjacentTiles, tile.down());
			addAdjacentTiles(squares, adjacentTiles, tile.left());
			addAdjacentTiles(squares, adjacentTiles, tile.right());
		}

		log.info("Having " + adjacentTiles.size() + " adjacent tiles");

		// add GeoJSON for all squares/tiles
		List<Feature> features = new ArrayList<>();
		for (BaseTile<T> adjacentTile : adjacentTiles) {
			features.add(GeoJSON.createSquare(adjacentTile.getRectangle(),
					null
					/*square + "\n" + toRectangle.apply(square)*/));
		}

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJSON(jsonOutputFile, varPrefix, features);

		// also write the file in pure JSON for use in later steps
		FileUtils.copyToFile(GeoJSON.getGeoJSON(features), new File(
				StringUtils.removeEnd(jsonOutputFile, ".js") + ".json"));

		// write list of adjacent tiles to text-file
		writeListOfAdjacent(
				adjacentTiles.stream().
						map(BaseTile::string).
						collect(Collectors.toSet()),
				adjacentTxtFile);

		log.info("Wrote " + squares.size() + " " + title + " from " + squaresFile + " to " + jsonOutputFile);
	}

	private static Set<String> readFile(File file) throws IOException {
		return file.exists() ?
				new TreeSet<>(FileUtils.readLines(file, StandardCharsets.UTF_8)) :
				Collections.emptySet();
	}

	private static <T> void addAdjacentTiles(Set<BaseTile<T>> tilesIn, Set<BaseTile<T>> adjacentTiles, BaseTile<T> tile) {
		addAdjacentTile(tilesIn, adjacentTiles, tile.up());
		addAdjacentTile(tilesIn, adjacentTiles, tile.down());
		addAdjacentTile(tilesIn, adjacentTiles, tile.left());
		addAdjacentTile(tilesIn, adjacentTiles, tile.right());
	}

	private static <T> void addAdjacentTile(Set<BaseTile<T>> tilesIn, Set<BaseTile<T>> adjacentTiles, BaseTile<T> newTile) {
		if (!tilesIn.contains(newTile)) {
			adjacentTiles.add(newTile);
		}
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
