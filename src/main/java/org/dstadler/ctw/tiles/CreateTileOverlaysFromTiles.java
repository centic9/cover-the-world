package org.dstadler.ctw.tiles;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_NEW_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;
import static org.dstadler.ctw.tiles.CreateStaticTiles.TILE_DIR_COMBINED_TILES;
import static org.dstadler.ctw.utils.Constants.TILE_ZOOM;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.Constants;
import org.dstadler.ctw.utils.LatLonRectangle;
import org.dstadler.ctw.utils.OSMTile;

/**
 * This application takes the list of covered tiles from
 * file "VisitedTiles.txt" and generates OSM tiles with
 * red semi-transparent area for any covered square.
 *
 * Resulting PNGs are stored in directories "tilesTiles"
 * and "tilesTilesNew"
 *
 * The created images consist of red transparent area for
 * covered squares.
 *
 * This is then used via Leaflet to produce combined map
 * of OSM plus the additional tiles for covered area as overlay.
 */
public class CreateTileOverlaysFromTiles {
	private static final Logger log = LoggerFactory.make();

	public static final File TILES_TILES_DIR = new File("tilesTiles");
	public static final File TILES_TILES_DIR_NEW = new File("tilesTilesNew");

	// for printing stats when writing tiles
	private static final AtomicLong lastLogTile = new AtomicLong();

	// prevent higher zoom levels to be processed concurrently as this puts a large
	// burden on main memory, by limiting it to 32, we avoid running 16-17 and 18
	// at the same time
	private static final Semaphore SEM_ZOOMS = new Semaphore(33);

	public static void main(String[] args) throws IOException, InterruptedException {
		LoggerFactory.initLogging();

		boolean onlyNewTiles = !(args.length > 0 && "all".equals(args[0]));

		File tileDir = onlyNewTiles ? TILES_TILES_DIR_NEW : TILES_TILES_DIR;
		String tilesFile = onlyNewTiles ? VISITED_TILES_NEW_TXT : VISITED_TILES_TXT;

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

		Set<String> tiles = CreateTileOverlaysHelper.read(tilesFile, "tiles");

		AtomicInteger tilesOverall = new AtomicInteger();
		// t.toCoords().equals("17/70647/45300")
		Set<OSMTile> newTiles = generateTiles(tiles, tilesOverall, tileDir, t -> true);

		log.info(String.format(Locale.US, "Wrote %,d files overall in %,dms",
				tilesOverall.get(), System.currentTimeMillis() - start));

		if (onlyNewTiles) {
			// rerun for normal tiles, but only ones that were touched by the new tiles
			log.info("--------------------------------------------------------------------------");
			log.info(String.format("Write touched full tiles for %d new tiles, found %d affected tiles",
					tiles.size(), newTiles.size()));

			tilesOverall = new AtomicInteger();
			generateTiles(CreateTileOverlaysHelper.read(VISITED_TILES_TXT, "tiles"), tilesOverall, TILES_TILES_DIR, newTiles::contains);

			log.info(String.format(Locale.US, "Wrote %,d files for changed tiles in %,dms",
					tilesOverall.get(), System.currentTimeMillis() - start));
		}
	}

	private static Set<OSMTile> generateTiles(Set<String> tilesIn, AtomicInteger tilesOverall, File tileDir,
			Predicate<OSMTile> filter) throws InterruptedException {
		Set<OSMTile> allTiles = ConcurrentHashMap.newKeySet();

		// prepare counters
		IntStream.rangeClosed(Constants.MIN_ZOOM, Constants.MAX_ZOOM).
				forEach(zoom -> {
							// indicate that this zoom is started
							CreateTileOverlaysHelper.EXPECTED.add(zoom, 0);
							CreateTileOverlaysHelper.ACTUAL.add(zoom, -1);
						});

		List<Integer> aList = IntStream.rangeClosed(Constants.MIN_ZOOM, Constants.MAX_ZOOM).boxed()
				.collect(Collectors.toList());

		ForkJoinPool customThreadPool = new ForkJoinPool(Constants.MAX_ZOOM - Constants.MIN_ZOOM);
		aList.forEach(zoom ->
				customThreadPool.submit(() ->
						processTilesForOneZoom(zoom, tilesIn, tilesOverall, tileDir, filter, allTiles)));

		customThreadPool.shutdown();
		if (!customThreadPool.awaitTermination(30,TimeUnit.MINUTES)) {
			throw new IllegalStateException("Timed out while waiting for all tasks to finish");
		}

		return allTiles;
	}

	private static void processTilesForOneZoom(int zoom, Set<String> tilesIn,
			AtomicInteger tilesOverall,
			File tileDir,
			Predicate<OSMTile> filter,
			Set<OSMTile> allTiles) {
		Thread thread = Thread.currentThread();
		thread.setName(thread.getName().
				// remove previous thread-name
						replaceAll("(.*) zoom \\d+(?: - active)?", "$1")
				+ " zoom " + zoom);

		SEM_ZOOMS.acquireUninterruptibly(zoom);
		try {
			thread.setName(thread.getName() + " - active");
			CreateTileOverlaysHelper.ACTUAL.add(zoom, 1);

			log.info("Start processing of " + tilesIn.size() + " tiles at zoom " + zoom + CreateTileOverlaysHelper.concatProgress());

			Map<OSMTile, boolean[][]> tilesOut = new TreeMap<>();

			int tilesCount = tilesIn.size();
			int tilesNr = 1;
			for (String tileIn : tilesIn) {
				handleTile(tileIn, zoom, tilesOut, tilesNr, tilesCount, filter);
				tilesNr++;
			}

			log.info("Having " + tilesOut.size() + " touched tiles for zoom " + zoom + CreateTileOverlaysHelper.concatProgress());
			CreateTileOverlaysHelper.EXPECTED.add(zoom, tilesOut.size());

						allTiles.addAll(tilesOut.keySet());
						int tilesOutSize = tilesOut.size();
						tilesOverall.addAndGet(tilesOutSize);

			try {
				CreateTileOverlaysHelper.writeTilesToFiles(TILE_DIR_COMBINED_TILES, tilesOut, tileDir, zoom);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

						log.info("Wrote " + tilesOutSize + " files for zoom " + zoom + CreateTileOverlaysHelper.concatProgress());
		} finally {
			SEM_ZOOMS.release(zoom);
		}
	}

	private static void handleTile(String tileIn, int zoom, Map<OSMTile,boolean[][]> tiles, int tilesNr, int tilesCount,
			Predicate<OSMTile> filter) {
		// select starting and ending tile
		OSMTile ref = OSMTile.fromString(tileIn);

		LatLonRectangle recTileIn = ref.getRectangle();

		// iterate over all "bounding" tiles at the given zoom
		// for zoom less or equal to TILE_ZOOM, this will be a single tile
		for (OSMTile tile : ref.getTilesAtZoom(zoom)) {
			// check if this tile should be included
			if (!filter.test(tile)) {
				continue;
			}

			// for zoom 14 and higher, we always have full tiles
			if (zoom >= TILE_ZOOM) {
				tiles.put(tile, CreateTileOverlaysHelper.FULL);
				continue;
			}

			CreateTileOverlaysHelper.writePixel(tiles, tile, recTileIn);
		}

		if (lastLogTile.get() + TimeUnit.SECONDS.toMillis(5) < System.currentTimeMillis()) {
			log.info(String.format(Locale.US, tilesNr + " of " + tilesCount + ": %s - zoom %d: %,d",
					tileIn, zoom, tiles.size()));

			lastLogTile.set(System.currentTimeMillis());
		}
	}
}
