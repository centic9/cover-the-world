package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_NEW_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_NEW_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;
import static org.dstadler.ctw.utils.Constants.SQUARE_SIZE;
import static org.dstadler.ctw.utils.Constants.TILE_ZOOM;
import static org.dstadler.ctw.utils.Constants.ZONE;

import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.BaseTile;
import org.dstadler.ctw.utils.LatLonRectangle;
import org.dstadler.ctw.utils.MatrixUtils;
import org.dstadler.ctw.utils.OSMTile;
import org.dstadler.ctw.utils.UTMRefWithHash;

import com.github.filosganga.geogson.model.Feature;
import com.github.filosganga.geogson.model.LinearRing;
import com.github.filosganga.geogson.model.Point;
import com.github.filosganga.geogson.model.Polygon;
import com.google.common.base.Preconditions;
import com.google.gson.JsonPrimitive;

/**
 * Small application which reads the list of covered squares
 * (twice for all covered and only new squares)
 * from a simple text-file and produces GeoJSON embedded in
 * a JavaScript file which can be loaded in a leaflet.js map
 * to display covered squares.
 *
 * Results are stored in JS files which can be used as overlay
 * layer in a Leaflet-based HTML page.
 */
public class CreateGeoJSON {
	private static final Logger log = LoggerFactory.make();

	// squares
	public static final String VISITED_SQUARES_JS = "js/VisitedSquares.js";
	public static final String VISITED_SQUARES_NEW_JS = "js/VisitedSquaresNew.js";

	// tiles
	public static final String VISITED_TILES_JS = "js/VisitedTiles.js";
	public static final String VISITED_TILES_NEW_JS = "js/VisitedTilesNew.js";

	// for printing stats when writing tiles
	private static final AtomicLong lastLog = new AtomicLong();

	public static void main(String[] args) throws IOException {
		LoggerFactory.initLogging();

		log.info("Computing GeoJSON for visited squares and tiles");

		writeGeoJSON(VISITED_SQUARES_TXT, VISITED_SQUARES_JS, "squares",
				UTMRefWithHash::getRectangle, UTMRefWithHash::fromString, "squares");
		writeGeoJSON(VISITED_SQUARES_NEW_TXT, VISITED_SQUARES_NEW_JS, "squaresnew",
				UTMRefWithHash::getRectangle, UTMRefWithHash::fromString, "new squares");

		writeGeoJSON(VISITED_TILES_TXT, VISITED_TILES_JS, "tiles",
				OSMTile::getRectangle, OSMTile::fromString, "tiles");
		writeGeoJSON(VISITED_TILES_NEW_TXT, VISITED_TILES_NEW_JS, "tilesnew",
				OSMTile::getRectangle, OSMTile::fromString, "new tiles");
	}

	protected static <T extends BaseTile<T>> void writeGeoJSON(String squaresFile, String jsonOutputFile, String varPrefix,
			Function<T, LatLonRectangle> toRectangle,
			Function<String, T> toObject,
			String title) throws IOException {
		log.info(title + ": Writing from " + squaresFile + " to " + jsonOutputFile +
				" with prefix '" + varPrefix + "'");

		// read list of UTMRefs for covered or new squares
		Set<T> squares = readSquares(new File(squaresFile)).
				stream().
				map(toObject).
				collect(Collectors.toCollection(TreeSet::new));

		log.info(title + ": Read " + squares.size());

		List<Feature> features = new ArrayList<>();

		// first look for single squares/tiles which we cannot combine anyway to
		// make computing the largest rectangles a bit cheaper
		handleSingleAreas(toRectangle, squares, features);

		// build an optimized GeoJSON as including all squares/tiles lead to a fairly large GeoJSON
		// which causes performance issues e.g. on Smartphone-Browsers
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
				minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

		boolean found = false;
		boolean[] isY = null;
		if (squares.size() > 0 && !(squares.iterator().next() instanceof UTMRefWithHash)) {
			for (OSMTile tile : (Set<OSMTile>) squares) {
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

				found = true;
			}

			int[][] M = MatrixUtils.populateMatrix((Set<OSMTile>) squares, minX, minY, maxX, maxY);

			isY = new boolean[M.length];
			MatrixUtils.findPopulatedRows(M, isY);
		}

		// next create as many rectangles as possible to minimize the resulting GeoJSON
		int iterationCount = 0;
		while (squares.size() > 0) {
			final Feature rectangle;
			final BaseTile<T> next = squares.iterator().next();
			if (next instanceof UTMRefWithHash) {
				//noinspection unchecked
				rectangle = getSquareRectangle((Set<UTMRefWithHash>) squares, null, "squares");
			} else {
				// stop if all the remaining tiles are outside the default UTM-zone
				if (!found) {
					break;

				}

				//noinspection unchecked
				rectangle = getTileRectangleInternal((Set<OSMTile>) squares, null, "tiles", minX, minY, maxX, maxY, isY);

				//log.info(title + ": Remaining " + squares.size() + ", found: " + rectangle);
			}

			if (rectangle == null) {
				break;
			}

			features.add(rectangle);

			iterationCount++;
			if (iterationCount % 100 == 0 && next instanceof OSMTile) {
				// recompute optimization from time to time to skip any new single squares
				// which are now there because of found and removed rectangles
				handleSingleAreas(toRectangle, squares, features);

				// re-compute which rows are empty from time to time to speed up processing a bit
				int[][] M = MatrixUtils.populateMatrix((Set<OSMTile>) squares, minX, minY, maxX, maxY);
				isY = new boolean[M.length];
				int count = MatrixUtils.findPopulatedRows(M, isY);
				log.info(title + ": Found " + count + " populated rows of " + isY.length + " overall");
			}

			if (lastLog.get() + TimeUnit.SECONDS.toMillis(5) < System.currentTimeMillis()) {
				log.info(title + ": Found " + features.size() + " features, having " + squares.size() + " " +
						title + " remaining, details: " + rectangle);

				lastLog.set(System.currentTimeMillis());
			}
		}

		log.info(title + ": Found " + features.size() + " rectangles, having " + squares.size() + " single squares remaining");

		// then add all remaining single-squares
		for (T square : squares) {
			features.add(GeoJSON.createSquare(toRectangle.apply(square),
					null
					/*square + "\n" + toRectangle.apply(square)*/));
		}

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJSON(jsonOutputFile, varPrefix, features);

		// also write the file in pure JSON for use in later steps
		FileUtils.copyToFile(GeoJSON.getGeoJSON(features), new File(
				StringUtils.removeEnd(jsonOutputFile, ".js") + ".json"));

		log.info(title + ": Wrote " + features.size() + " features with " + squares.size() + " single " + title + " from " + squaresFile + " to " + jsonOutputFile);
	}

	private static <T extends BaseTile<T>> void handleSingleAreas(Function<T, LatLonRectangle> toRectangle,
			Set<T> squares,
			List<Feature> features) {
		int count = 0;
		Iterator<T> it = squares.iterator();
		while (it.hasNext()) {
			T square = it.next();

			// check if this square is single
			if (!squares.contains(square.up()) &&
					!squares.contains(square.down()) &&
					!squares.contains(square.left()) &&
					!squares.contains(square.right())
			) {
				features.add(GeoJSON.createSquare(toRectangle.apply(square),
						null
						/*square + "\n" + toRectangle.apply(square)*/));
				it.remove();
				count++;
			}
		}

		log.info("Found " + count + " single areas");
	}

	protected static Set<String> readSquares(File file) throws IOException {
		return file.exists() ?
				new TreeSet<>(FileUtils.readLines(file, StandardCharsets.UTF_8)) :
				Collections.emptySet();
	}

	public static Feature getTileRectangle(Set<OSMTile> tiles, String textFile, String title) throws IOException {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
				minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

		boolean found = false;
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

			found = true;
		}

		// stop if all the remaining tiles are outside the default UTM-zone
		if (!found) {
			return null;
		}

		return getTileRectangleInternal(tiles, textFile, title, minX, minY, maxX, maxY, null);
	}

	private static Feature getTileRectangleInternal(Set<OSMTile> tiles, String textFile, String title, int minX, int minY, int maxX, int maxY,
			boolean[] isY)
			throws IOException {
		int[][] M = MatrixUtils.populateMatrix(tiles, minX, minY, maxX, maxY);

		if (isY == null) {
			isY = new boolean[M.length];
			MatrixUtils.findPopulatedRows(M, isY);
		}

		Pair<Rectangle,Integer> result = MatrixUtils.maxRectangle(M, isY);
		Rectangle rect = result.getKey();

		// stop when we do not find any real rectangles any more
		if (rect.width == 1 && rect.height == 1) {
			return null;
		}

		if (log.isLoggable(Level.FINE)) {
			log.fine("Area of maximum rectangle " + rect + ": " + rect.width + "x" + rect.height +
					" = " + result.getValue());
		}

		OSMTile squareMin = new OSMTile(TILE_ZOOM, minX + rect.x - rect.width, minY + rect.y - rect.height + 1);
		OSMTile squareMax = new OSMTile(TILE_ZOOM, minX + rect.x, minY + rect.y + 1);

		for (int y = squareMin.getYTile(); y < squareMax.getYTile(); y++) {
			for (int x = squareMin.getXTile(); x < squareMax.getXTile(); x++) {
				final boolean removed = tiles.remove(new OSMTile(TILE_ZOOM, x, y));
				Preconditions.checkState(removed,
						"Should always remove squares, but did not for \n%s %s",
						x, y);
			}
		}

		/*UTMRefWithHash recRefMin = UTMRefWithHash.fromString(ZONE + "U " +
				(minEast + (rect.x - rect.width) * SQUARE_SIZE) + " " + (minNorth + (rect.y - rect.height) * SQUARE_SIZE + SQUARE_SIZE));
		UTMRefWithHash recRefMax = UTMRefWithHash.fromString(ZONE + "U " +
				(minEast + rect.x * SQUARE_SIZE) + " " + (minNorth + rect.y * SQUARE_SIZE + SQUARE_SIZE));
		log.fine("Found largest square at " + rect.x + "x" + rect.y +
				"\n" + recRefMin +
				"\n" + recRefMax +
				"\n" + squareMin.getStartLatLon() +
				"\n" + squareMax.getStartLatLon() +
				"\n" + OSMTile.fromLatLngZoom(squareMin.getStartLatLon().getLatitude(), squareMin.getStartLatLon().getLongitude(), 13) +
				"\n" + OSMTile.fromLatLngZoom(squareMax.getStartLatLon().getLatitude(), squareMax.getStartLatLon().getLongitude(), 13));*/

		if (textFile != null) {
			// create list of latLngBounds for SVG elements to overlay
			try (Writer writer = new BufferedWriter(new FileWriter(textFile))) {
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

		// produce the GeoJSON for the rectangle
		Feature.Builder builder = Feature.builder().withGeometry(Polygon.of(LinearRing.of(
				Point.from(squareMin.toLatLng().getLongitude(), squareMin.toLatLng().getLatitude()),
				Point.from(squareMax.toLatLng().getLongitude(), squareMin.toLatLng().getLatitude()),
				Point.from(squareMax.toLatLng().getLongitude(), squareMax.toLatLng().getLatitude()),
				Point.from(squareMin.toLatLng().getLongitude(), squareMax.toLatLng().getLatitude()),
				Point.from(squareMin.toLatLng().getLongitude(), squareMin.toLatLng().getLatitude())
		)));
		// only set properties if we also write an overview file to keep file-size of some geo-json files at minimum
		if (textFile != null) {
			return builder.withProperty("popupContent", new JsonPrimitive(
					result.getValue() + " " + title + ": " + rect.width + "x" + rect.height)).build();
		} else {
			return builder.build();
		}
	}

	public static Feature getSquareRectangle(Set<UTMRefWithHash> squares, String textFile, String title) throws IOException {
		double minEast = Double.MAX_VALUE, maxEast = Double.MIN_VALUE,
				minNorth = Double.MAX_VALUE, maxNorth = Double.MIN_VALUE;

		boolean found = false;
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

			found = true;
		}

		// stop if all the remaining squares are outside the default UTM-zone
		if (!found) {
			return null;
		}

		int[][] M = MatrixUtils.populateMatrix(squares, minEast, minNorth, maxEast, maxNorth, ZONE);

		Pair<Rectangle,Integer> result = MatrixUtils.maxRectangle(M);
		Rectangle rect = result.getKey();

		// stop when we do not find any real rectangles any more
		if (rect.width == 1 && rect.height == 1) {
			return null;
		}

		if (log.isLoggable(Level.FINE)) {
			log.fine("Area of maximum rectangle " + rect + ": " + rect.width + "x" + rect.height +
					" = " + result.getValue());
		}

		UTMRefWithHash recRefMinMin = new UTMRefWithHash(ZONE, 'U',
				(minEast + (rect.x - rect.width) * SQUARE_SIZE), (minNorth + (rect.y - rect.height) * SQUARE_SIZE + SQUARE_SIZE));
		UTMRefWithHash recRefMaxMin = new UTMRefWithHash(ZONE, 'U',
				(minEast + (rect.x - rect.width) * SQUARE_SIZE), (minNorth + rect.y * SQUARE_SIZE + SQUARE_SIZE));
		UTMRefWithHash recRefMinMax = new UTMRefWithHash(ZONE, 'U',
				(minEast + rect.x * SQUARE_SIZE), (minNorth + (rect.y - rect.height) * SQUARE_SIZE + SQUARE_SIZE));
		UTMRefWithHash recRefMaxMax = new UTMRefWithHash(ZONE, 'U',
				(minEast + rect.x * SQUARE_SIZE), (minNorth + rect.y * SQUARE_SIZE + SQUARE_SIZE));

		if (log.isLoggable(Level.FINE)) {
			log.fine("Found largest rectangle at " + rect.x + "x" + rect.y +
					"\n" + recRefMinMin +
					"\n" + recRefMaxMin +
					"\n" + recRefMinMax +
					"\n" + recRefMaxMax +
					"\n" + recRefMinMin.toLatLng() +
					"\n" + recRefMaxMin.toLatLng() +
					"\n" + recRefMinMax.toLatLng() +
					"\n" + recRefMaxMax.toLatLng() +
					"\n" + OSMTile.fromLatLngZoom(recRefMinMin.toLatLng().getLatitude(), recRefMinMin.toLatLng().getLongitude(),13) +
					"\n" + OSMTile.fromLatLngZoom(recRefMaxMin.toLatLng().getLatitude(), recRefMaxMin.toLatLng().getLongitude(),13) +
					"\n" + OSMTile.fromLatLngZoom(recRefMinMax.toLatLng().getLatitude(), recRefMinMax.toLatLng().getLongitude(),13) +
					"\n" + OSMTile.fromLatLngZoom(recRefMaxMax.toLatLng().getLatitude(), recRefMaxMax.toLatLng().getLongitude(),13));
		}

		// remove all squares of the rectangle from the list of remaining squares
		for (double easting = recRefMinMin.getEasting(); easting < recRefMinMax.getEasting(); easting+=SQUARE_SIZE) {
			for (double northing = recRefMinMax.getNorthing(); northing < recRefMaxMax.getNorthing(); northing+=SQUARE_SIZE) {
				final UTMRefWithHash ref = new UTMRefWithHash(ZONE, 'U', easting, northing);
				final UTMRefWithHash refFixed = new UTMRefWithHash(ZONE, ref.toLatLng().toUTMRef().getLatZone(), easting, northing);
				final boolean removed = squares.remove(
						// have to fix-up latZone
						refFixed);
				Preconditions.checkState(
						removed,
						"Should always remove squares, but did not for \n%s %s: \n%s and %s",
						easting, northing, ref, refFixed);
			}
		}

		// if specified, write out a text-file with a summary of the found rectangle
		if (textFile != null) {
			try (Writer writer = new BufferedWriter(new FileWriter(textFile))) {
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

		// produce the GeoJSON structure for the rectangle
		Feature.Builder builder = Feature.builder().withGeometry(Polygon.of(LinearRing.of(
				Point.from(recRefMinMin.toLatLng().getLongitude(), recRefMinMin.toLatLng().getLatitude()),
				Point.from(recRefMaxMin.toLatLng().getLongitude(), recRefMaxMin.toLatLng().getLatitude()),
				Point.from(recRefMaxMax.toLatLng().getLongitude(), recRefMaxMax.toLatLng().getLatitude()),
				Point.from(recRefMinMax.toLatLng().getLongitude(), recRefMinMax.toLatLng().getLatitude()),
				Point.from(recRefMinMin.toLatLng().getLongitude(), recRefMinMin.toLatLng().getLatitude())
		)));

		// only set properties if we also write an overview file to keep file-size of some geo-json files at minimum
		if (textFile != null) {
			return builder.withProperty("popupContent", new JsonPrimitive(
					result.getValue() + " " + title + ": " + rect.width + "x" + rect.height)).build();
		} else {
			return builder.build();
		}
	}
}
