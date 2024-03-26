package org.dstadler.ctw.tiles;

import static org.dstadler.ctw.tiles.CreateStaticTiles.TILE_DIR_COMBINED_SQUARES;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_NEW_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.geotools.GeoTools;
import org.dstadler.ctw.utils.LatLonRectangle;
import org.dstadler.ctw.utils.OSMTile;
import org.dstadler.ctw.utils.UTMRefWithHash;
import org.geotools.feature.FeatureCollection;

/**
 * This application takes the list of covered squares from
 * file "VisitedSquares.txt" and generates OSM tiles with
 * red semi-transparent area for any covered square.
 *
 * Resulting PNGs are stored in directories "tilesSquares"
 * and "tilesSquaresNew"
 *
 * The created images consist of red transparent area for
 * covered squares.
 *
 * This is then used via Leaflet to produce combined map
 * of OSM plus the additional tiles for covered area as overlay.
 */
public class CreateTileOverlaysFromUTMRef {
	private static final Logger log = LoggerFactory.make();

	public static final File VISITED_SQUARES_JSON = new File("js/VisitedSquares.json");
	public static final File VISITED_SQUARES_NEW_JSON = new File("js/VisitedSquaresNew.json");

	public static final File TILES_SQUARES_DIR = new File("tilesSquares");
	public static final File TILES_SQUARES_DIR_NEW = new File("tilesSquaresNew");

	// for printing stats when writing tiles
	private static final AtomicLong lastLogSquare = new AtomicLong();

	public static void main(String[] args) throws IOException, InterruptedException {
		LoggerFactory.initLogging();

		// as we write many small files, we do not want to use disk-based caching
		ImageIO.setUseCache(false);

		boolean onlyNewTiles = !(args.length > 0 && "all".equals(args[0]));

		File tileDir = onlyNewTiles ? TILES_SQUARES_DIR_NEW : TILES_SQUARES_DIR;
		String squaresFile = onlyNewTiles ? VISITED_SQUARES_NEW_TXT : VISITED_SQUARES_TXT;
		File jsonFile = onlyNewTiles ? VISITED_SQUARES_NEW_JSON : VISITED_SQUARES_JSON;

		if (onlyNewTiles) {
			log.info("Writing only new tiles to directory " + tileDir);
		} else {
			log.info("Writing all tiles to directory " + tileDir);
		}

		if (onlyNewTiles) {
			CreateTileOverlaysHelper.cleanTiles(tileDir);
		}
		if (!tileDir.exists() && !tileDir.mkdirs()) {
			throw new IOException("Could not create directory at " + tileDir);
		}

		long start = System.currentTimeMillis();

		Set<String> squares = CreateTileOverlaysHelper.read(squaresFile, "squares");

		AtomicInteger tilesOverall = new AtomicInteger();
		// t.toCoords().equals("17/70647/45300")
		Set<OSMTile> newTiles = generateSquares(squares, tilesOverall, tileDir, jsonFile, t -> true);

		log.info(String.format(Locale.US, "Wrote %,d files overall in %,dms",
				tilesOverall.get(), System.currentTimeMillis() - start));

		if (onlyNewTiles) {
			// rerun for normal tiles, but only ones that were touched by the new tiles
			log.info("--------------------------------------------------------------------------");
			log.info(String.format("Write touched full tiles for %d new squares, found %d affected tiles",
					squares.size(), newTiles.size()));

			tilesOverall = new AtomicInteger();
			generateSquares(CreateTileOverlaysHelper.read(VISITED_SQUARES_TXT, "squares"), tilesOverall, TILES_SQUARES_DIR,
					jsonFile, newTiles::contains);

			log.info(String.format(Locale.US, "Wrote %,d files for changed tiles in %,dms",
					tilesOverall.get(), System.currentTimeMillis() - start));
		}
	}

	private static Set<OSMTile> generateSquares(Set<String> squares, AtomicInteger tilesOverall, File tileDir,
			File jsonFile, Predicate<OSMTile> filter) throws InterruptedException, IOException {
		// read GeoJSON from file to use it for rendering overlay images
		final FeatureCollection<?, ?> features = GeoTools.parseFeatureCollection(jsonFile);

		Set<OSMTile> allTiles = ConcurrentHashMap.newKeySet();

		CreateTileOverlaysHelper.forEachZoom(
				zoom -> generateTilesForOneZoom(zoom, squares, tilesOverall, tileDir, filter, features, allTiles));

		return allTiles;
	}

	private static void generateTilesForOneZoom(int zoom, Set<String> squares,
			AtomicInteger tilesOverall,
			File tileDir,
			Predicate<OSMTile> filter,
			FeatureCollection<?, ?> features, Set<OSMTile> allTiles) {
		Thread thread = Thread.currentThread();
		thread.setName(thread.getName() + " zoom " + zoom);

		CreateTileOverlaysHelper.ACTUAL.add(zoom, 1);

		log.info("Start processing of " + squares.size() + " squares at zoom " + zoom + CreateTileOverlaysHelper.concatProgress());

		Set<OSMTile> tilesOut = new HashSet<>();

		int squareCount = squares.size();
		int squareNr = 1;
		for (String square : squares) {
			handleSquare(square, zoom, tilesOut, filter);

			if (lastLogSquare.get() + TimeUnit.SECONDS.toMillis(5) < System.currentTimeMillis()) {
				log.info(String.format(Locale.US, "zoom %d: %,d of %,d: %s - %,d",
						zoom, squareNr, squareCount, square, tilesOut.size()));

				lastLogSquare.set(System.currentTimeMillis());
			}

			squareNr++;
		}

		log.info("Having " + tilesOut.size() + " touched tiles for zoom " + zoom + CreateTileOverlaysHelper.concatProgress());
		CreateTileOverlaysHelper.EXPECTED.add(zoom, tilesOut.size());

		allTiles.addAll(tilesOut);
		int tilesOutSize = tilesOut.size();
		tilesOverall.addAndGet(tilesOutSize);

		try {
			CreateTileOverlaysHelper.writeTilesToFiles(TILE_DIR_COMBINED_SQUARES, tilesOut, tileDir, features, false);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		log.info("Wrote " + tilesOutSize + " files for zoom " + zoom + CreateTileOverlaysHelper.concatProgress());
	}

	private static void handleSquare(String square, int zoom, Set<OSMTile> tiles, Predicate<OSMTile> filter) {
		// select starting and ending tile
		UTMRefWithHash ref1 = UTMRefWithHash.fromString(square);

		LatLonRectangle recSquare = ref1.getRectangle();

		OSMTile nr1 = OSMTile.fromLatLngZoom(recSquare.lat2, recSquare.lon1, zoom);
		OSMTile nr2 = OSMTile.fromLatLngZoom(recSquare.lat1, recSquare.lon2, zoom);

		for (int x = nr1.getXTile(); x <= nr2.getXTile(); x++) {
			for (int y = nr1.getYTile(); y >= nr2.getYTile(); y--) {
				// construct the tile that we want to process
				OSMTile tile = new OSMTile(zoom, x, y);

				// check if this tile should be included
				if (!filter.test(tile)) {
					continue;
				}

				tiles.add(tile);
			}
		}
	}
}
