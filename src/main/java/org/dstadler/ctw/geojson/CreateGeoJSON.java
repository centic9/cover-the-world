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
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
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

	public static void computeGeoJSONSquares() throws IOException {
		log.info("Computing GeoJSON for visited squares");

		writeGeoJSON(VISITED_SQUARES_TXT, VISITED_SQUARES_JS, "squares",
				UTMRefWithHash::getRectangle, UTMRefWithHash::fromString, "squares");
	}

	public static void computeGeoJSONSquaresNew() throws IOException {
		log.info("Computing GeoJSON for visited new squares");

		writeGeoJSON(VISITED_SQUARES_NEW_TXT, VISITED_SQUARES_NEW_JS, "squaresnew",
				UTMRefWithHash::getRectangle, UTMRefWithHash::fromString, "new squares");
	}

	public static void computeGeoJSONTiles() throws IOException {
		log.info("Computing GeoJSON for visited tiles");

		writeGeoJSON(VISITED_TILES_TXT, VISITED_TILES_JS, "tiles",
				OSMTile::getRectangle, OSMTile::fromString, "tiles");
	}

	public static void computeGeoJSONTilesNew() throws IOException {
		log.info("Computing GeoJSON for visited new tiles");

		writeGeoJSON(VISITED_TILES_NEW_TXT, VISITED_TILES_NEW_JS, "tilesnew",
				OSMTile::getRectangle, OSMTile::fromString, "new tiles");
	}

	@SuppressWarnings({ "unchecked", "SuspiciousMethodCalls" })
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

		// add the largest rectangle
		log.info("Computing largest rectangle");
		if (squares.iterator().next() instanceof UTMRefWithHash) {
			features.add(getSquareRectangle((Set<UTMRefWithHash>)squares, null, title));
		} else {
			features.add(getTileRectangle((Set<OSMTile>)squares, null, title));
		}

		while (!squares.isEmpty()) {
			Iterator<T> it = squares.iterator();
			BaseTile<T> square = it.next();
			it.remove();

			BaseTile<T> left = square.left();
			BaseTile<T> right = square.right();
			if (squares.contains(left) || squares.contains(right)) {
				// do we have another to the left or right? => combine horizontally

				while (squares.contains(left)) {
					final boolean removed = squares.remove(left);
					Preconditions.checkState(removed,
							"Should always remove squares, but did not for \n%s",
							square.toString());

					left = left.left();
				}

				while (squares.contains(right)) {
					final boolean removed = squares.remove(right);
					Preconditions.checkState(removed,
							"Should always remove squares, but did not for \n%s",
							square.toString());

					right = right.right();
				}

				LatLonRectangle rectLeft = left.right().getRectangle();
				LatLonRectangle rectRight = right.left().getRectangle();

				features.add(Feature.builder().withGeometry(Polygon.of(LinearRing.of(
						Point.from(rectLeft.lon1, rectLeft.lat1),
						Point.from(rectRight.lon2, rectLeft.lat1),
						Point.from(rectRight.lon2, rectRight.lat2),
						Point.from(rectLeft.lon1, rectRight.lat2),
						Point.from(rectLeft.lon1, rectLeft.lat1)
				))).build());
			} else {
				BaseTile<T> up = square.up();
				BaseTile<T> down = square.down();
				if (squares.contains(up) || squares.contains(down)) {
					// do we have another to up or down? => combine vertically

					while (squares.contains(up)) {
						final boolean removed = squares.remove(up);
						Preconditions.checkState(removed,
								"Should always remove squares, but did not for \n%s",
								square.toString());

						up = up.up();
					}

					while (squares.contains(down)) {
						final boolean removed = squares.remove(down);
						Preconditions.checkState(removed,
								"Should always remove squares, but did not for \n%s",
								square.toString());

						down = down.down();
					}

					LatLonRectangle rectUp = up.down().getRectangle();
					LatLonRectangle rectDown = down.up().getRectangle();

					features.add(Feature.builder().withGeometry(Polygon.of(LinearRing.of(
							Point.from(rectUp.lon1, rectUp.lat1),
							Point.from(rectDown.lon2, rectUp.lat1),
							Point.from(rectDown.lon2, rectDown.lat2),
							Point.from(rectUp.lon1, rectDown.lat2),
							Point.from(rectUp.lon1, rectUp.lat1)
					))).build());
				} else {
					// otherwise add as "single" square
					//noinspection CastCanBeRemovedNarrowingVariableType
					features.add(GeoJSON.createSquare(toRectangle.apply((T)square),
							null
							/*square + "\n" + toRectangle.apply(square)*/));
				}
			}
		}

		// finally write out JavaScript code with embedded GeoJSON
		GeoJSON.writeGeoJavaScript(jsonOutputFile, varPrefix, features);

		// also write the file in pure JSON for use in later steps
		GeoJSON.writeGeoJSON(GeoJSON.getJSONFileName(jsonOutputFile), features);

		log.info(title + ": Wrote " + features.size() + " features with " + squares.size() + " single " + title + " from " + squaresFile + " to " + jsonOutputFile);
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

		return getTileRectangleInternal(tiles, textFile, title, minX, minY, maxX, maxY);
	}

	private static Feature getTileRectangleInternal(Set<OSMTile> tiles, String textFile, String title, int minX, int minY, int maxX, int maxY)
			throws IOException {
		int[][] M = MatrixUtils.populateMatrix(tiles, minX, minY, maxX, maxY);

		boolean[] isY = new boolean[M.length];
		MatrixUtils.findPopulatedRows(M, isY);

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
