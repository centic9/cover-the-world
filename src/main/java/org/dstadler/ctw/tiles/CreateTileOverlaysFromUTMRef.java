package org.dstadler.ctw.tiles;

import static org.dstadler.ctw.tiles.CreateStaticTiles.TILE_DIR_COMBINED_SQUARES;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_NEW_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.Constants;
import org.dstadler.ctw.utils.LatLonRectangle;
import org.dstadler.ctw.utils.OSMTile;
import org.dstadler.ctw.utils.UTMRefWithHash;

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

	public static final File TILES_SQUARES_DIR = new File("tilesSquares");
	public static final File TILES_SQUARES_DIR_NEW = new File("tilesSquaresNew");

	// for printing stats when writing tiles
	private static final AtomicLong lastLogSquare = new AtomicLong();

	// prevent higher zoom levels to be processed concurrently as this puts a large
	// burden on main memory, by limiting it to 32, we avoid running 16-17 and 18
	// at the same time
	private static final Semaphore SEM_ZOOMS = new Semaphore(33);

	public static void main(String[] args) throws IOException {
		LoggerFactory.initLogging();

		boolean onlyNewTiles = !(args.length > 0 && "all".equals(args[0]));

		File tileDir = onlyNewTiles ? TILES_SQUARES_DIR_NEW : TILES_SQUARES_DIR;
		String squaresFile = onlyNewTiles ? VISITED_SQUARES_NEW_TXT : VISITED_SQUARES_TXT;

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
		Set<OSMTile> newTiles = generateTiles(squares, tilesOverall, tileDir, t -> true);

		log.info(String.format(Locale.US, "Wrote %,d files overall in %,dms",
				tilesOverall.get(), System.currentTimeMillis() - start));

		if (onlyNewTiles) {
			// rerun for normal tiles, but only ones that were touched by the new tiles
			log.info("--------------------------------------------------------------------------");
			log.info(String.format("Write touched full tiles for %d new squares, found %d affected tiles",
					squares.size(), newTiles.size()));

			tilesOverall = new AtomicInteger();
			generateTiles(CreateTileOverlaysHelper.read(VISITED_SQUARES_TXT, "squares"), tilesOverall, TILES_SQUARES_DIR, newTiles::contains);

			log.info(String.format(Locale.US, "Wrote %,d files for changed tiles in %,dms",
					tilesOverall.get(), System.currentTimeMillis() - start));
		}
	}

	private static Set<OSMTile> generateTiles(Set<String> squares, AtomicInteger tilesOverall, File tileDir,
			Predicate<OSMTile> filter) {
		Set<OSMTile> allTiles = ConcurrentHashMap.newKeySet();

		//for (int zoom = MIN_ZOOM; zoom <= MAX_ZOOM; zoom++)
		IntStream.rangeClosed(Constants.MIN_ZOOM, Constants.MAX_ZOOM).
				parallel().
				forEach(zoom -> {
					// indicate that this zoom is started
					CreateTileOverlaysHelper.EXPECTED.add(zoom, 0);
					CreateTileOverlaysHelper.ACTUAL.add(zoom, 0);

					Thread thread = Thread.currentThread();
					thread.setName(thread.getName() + " zoom " + zoom);

					SEM_ZOOMS.acquireUninterruptibly(zoom);
					try {
						log.info("Start processing of " + squares.size() + " squares at zoom " + zoom + CreateTileOverlaysHelper.concatProgress());

						Map<OSMTile, boolean[][]> tiles = new TreeMap<>();

						int squareCount = squares.size();
						int squareNr = 1;
						for (String square : squares) {
							handleSquare(square, zoom, tiles, squareNr, squareCount, filter);
							squareNr++;
						}

						CreateTileOverlaysHelper.EXPECTED.add(zoom, tiles.size());
						log.info("Having " + tiles.size() + " touched tiles for zoom " + zoom + CreateTileOverlaysHelper.concatProgress());

						try {
							CreateTileOverlaysHelper.writeTilesToFiles(TILE_DIR_COMBINED_SQUARES, tiles, tileDir, zoom);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}

						allTiles.addAll(tiles.keySet());
						tilesOverall.addAndGet(tiles.size());

						log.info("Wrote " + tiles.size() + " files for zoom " + zoom + CreateTileOverlaysHelper.concatProgress());
					} finally {
						SEM_ZOOMS.release(zoom);
					}
				}
				);

		return allTiles;
	}

	private static void handleSquare(String square, int zoom, Map<OSMTile,boolean[][]> tiles, int squareNr, int squareCount,
			Predicate<OSMTile> filter) {
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

				boolean[][] pixel = tiles.computeIfAbsent(tile, osmTile -> new boolean[256][256]);
				if (pixel == CreateTileOverlaysHelper.FULL) {
					// already full, nothing to do anymore
					continue;
				}

				LatLonRectangle recTile = tile.getRectangle();

				// compute how much of the square is located in this tile
				// so that we can fill the boolean-buffer accordingly
				LatLonRectangle recResult = recSquare.intersect(recTile);

				//log.info("For '" + square + "', zoom " + zoom + " and xy " + x + "/" + y + ": Had rect " + recResult + " for " + recSquare + " and " + recTile);
				CreateTileOverlaysHelper.fillPixel(square, recResult, pixel, tile);

				// replace a "full" array with a global instance to save main memory
				if (CreateTileOverlaysHelper.isFull(pixel)) {
					tiles.put(tile, CreateTileOverlaysHelper.FULL);
				}
			}
		}

		if (lastLogSquare.get() + TimeUnit.SECONDS.toMillis(5) < System.currentTimeMillis()) {
			log.info(String.format(Locale.US, squareNr + " of " + squareCount + ": %s - zoom %d: %,d",
					square, zoom, tiles.size()));

			lastLogSquare.set(System.currentTimeMillis());
		}

	}
}
