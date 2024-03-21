package org.dstadler.ctw.tiles;

import static org.dstadler.ctw.geojson.CreateAdjacent.ADJACENT_TILES_TXT;
import static org.dstadler.ctw.tiles.CreateStaticTiles.TILE_DIR_COMBINED_TILES;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.geotools.GeoTools;
import org.dstadler.ctw.utils.Constants;
import org.dstadler.ctw.utils.OSMTile;
import org.geotools.feature.FeatureCollection;

/**
 * This application takes the list of covered tiles from
 * file "VisitedTiles.txt" and generates a list of OSM
 * tiles with which are "adjacent" to the covered tiles.
 *
 * This is used to aid in planning routes by showing
 * small tile-border lines in areas that can be covered
 * next.
 *
 * Resulting PNGs are stored in directories "tilesTiles"
 * and "tilesTilesNew"
 *
 * The created images consist of red transparent border.
 *
 * This is then used via Leaflet to produce combined map
 * of OSM plus the additional tiles for covered area as overlay.
 */
public class CreateAdjacentTileOverlaysFromTiles {
	private static final Logger log = LoggerFactory.make();

	public static final File ADJACENT_TILES_DIR = new File("tilesTilesAdjacent");
	public static final File ADJACENT_TILES_JSON = new File("js/AdjacentTiles.json");

	// for printing stats when writing tiles
	private static final AtomicLong lastLogTile = new AtomicLong();

	public static void main(String[] args) throws IOException, InterruptedException {
		LoggerFactory.initLogging();

		// as we write many small files, we do not want to use disk-based caching
		ImageIO.setUseCache(false);

		File tileDir = ADJACENT_TILES_DIR;
		String tilesFile = ADJACENT_TILES_TXT;
		File jsonFile = ADJACENT_TILES_JSON;

		log.info("Writing adjacent tiles to directory " + tileDir);

		if (!tileDir.exists() && !tileDir.mkdirs()) {
			throw new IOException("Could not create directory at " + tileDir);
		}

		long start = System.currentTimeMillis();

		Set<String> tiles = CreateTileOverlaysHelper.read(tilesFile, "adjacentTiles");

		AtomicInteger tilesOverall = new AtomicInteger();
		// t.toCoords().equals("17/70647/45300")
		generateTiles(tiles, tilesOverall, tileDir, jsonFile, t -> true);

		log.info(String.format(Locale.US, "Wrote %,d files overall in %,dms",
				tilesOverall.get(), System.currentTimeMillis() - start));
	}

	private static void generateTiles(Set<String> tilesIn, AtomicInteger tilesOverall, File tileDir,
			File jsonFile, Predicate<OSMTile> filter) throws InterruptedException, IOException {
		// read GeoJSON from file to use it for rendering overlay images
		final FeatureCollection<?, ?> features = GeoTools.parseFeatureCollection(jsonFile);

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
						generateTilesForOneZoom(zoom, tilesIn, tilesOverall, tileDir, filter, features, allTiles)));

		customThreadPool.shutdown();
		if (!customThreadPool.awaitTermination(4,TimeUnit.HOURS)) {
			throw new IllegalStateException("Timed out while waiting for all tasks to finish");
		}
	}

	private static void generateTilesForOneZoom(int zoom, Set<String> tilesIn,
			AtomicInteger tilesOverall,
			File tileDir,
			Predicate<OSMTile> filter,
			FeatureCollection<?, ?> features, Set<OSMTile> allTiles) {
		Thread thread = Thread.currentThread();
		thread.setName(thread.getName() + " zoom " + zoom);

		CreateTileOverlaysHelper.ACTUAL.add(zoom, 1);

		log.info("Start processing of " + tilesIn.size() + " tiles at zoom " + zoom + CreateTileOverlaysHelper.concatProgress());

		Set<OSMTile> tilesOut = new HashSet<>();

		int tilesCount = tilesIn.size();
		int tilesNr = 1;
		for (String tileIn : tilesIn) {
			handleTile(tileIn, zoom, tilesOut, filter);

			if (lastLogTile.get() + TimeUnit.SECONDS.toMillis(5) < System.currentTimeMillis()) {
				log.info(String.format(Locale.US, "zoom %d: %,d of %,d: %s - %,d",
						zoom, tilesNr, tilesCount, tileIn, tilesOut.size()));

				lastLogTile.set(System.currentTimeMillis());
			}

			tilesNr++;
		}

		log.info("Having " + tilesOut.size() + " touched tiles for zoom " + zoom + CreateTileOverlaysHelper.concatProgress());
		CreateTileOverlaysHelper.EXPECTED.add(zoom, tilesOut.size());

		allTiles.addAll(tilesOut);
		int tilesOutSize = tilesOut.size();
		tilesOverall.addAndGet(tilesOutSize);

		try {
			CreateTileOverlaysHelper.writeTilesToFiles(TILE_DIR_COMBINED_TILES, tilesOut, tileDir, features, true);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		log.info("Wrote " + tilesOutSize + " files for zoom " + zoom + CreateTileOverlaysHelper.concatProgress());
	}

	private static void handleTile(String tileIn, int zoom, Set<OSMTile> tiles, Predicate<OSMTile> filter) {
		// select starting and ending tile
		OSMTile ref = OSMTile.fromString(tileIn);

		// iterate over all "bounding" tiles at the given zoom
		// for zoom less or equal to TILE_ZOOM, this will be a single tile
		for (OSMTile tile : ref.getTilesAtZoom(zoom)) {
			// check if this tile should be included
			if (!filter.test(tile)) {
				continue;
			}

			tiles.add(tile);
		}
	}
}
