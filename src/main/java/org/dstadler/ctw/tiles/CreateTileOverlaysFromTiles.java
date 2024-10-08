package org.dstadler.ctw.tiles;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_NEW_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;
import static org.dstadler.ctw.tiles.CreateStaticTiles.TILE_DIR_COMBINED_TILES;

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
import org.dstadler.ctw.utils.OSMTile;
import org.geotools.feature.FeatureCollection;

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

	public static final File VISITED_TILES_JSON = new File("js/VisitedTiles.json");
	public static final File VISITED_TILES_NEW_JSON = new File("js/VisitedTilesNew.json");

	public static final File TILES_TILES_DIR = new File("tilesTiles");
	public static final File TILES_TILES_DIR_NEW = new File("tilesTilesNew");

	// for printing stats when writing tiles
	private static final AtomicLong lastLogTile = new AtomicLong();

	public static void main(String[] args) throws IOException, InterruptedException {
		LoggerFactory.initLogging();

		// as we write many small files, we do not want to use disk-based caching
		ImageIO.setUseCache(false);

		boolean onlyNewTiles = !(args.length > 0 && "all".equals(args[0]));

		File tileDir = onlyNewTiles ? TILES_TILES_DIR_NEW : TILES_TILES_DIR;
		String tilesFile = onlyNewTiles ? VISITED_TILES_NEW_TXT : VISITED_TILES_TXT;
		File jsonFile = onlyNewTiles ? VISITED_TILES_NEW_JSON : VISITED_TILES_JSON;

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
		Set<OSMTile> newTiles = generateTiles(tiles, tilesOverall, tileDir, jsonFile, t -> true, false);

		log.info(String.format(Locale.US, "Wrote %,d files overall in %,dms",
				tilesOverall.get(), System.currentTimeMillis() - start));

		if (onlyNewTiles) {
			// rerun for normal tiles, but only ones that were touched by the new tiles
			log.info("--------------------------------------------------------------------------");
			log.info(String.format("Write touched full tiles for %d new tiles, found %d affected tiles",
					tiles.size(), newTiles.size()));

			tilesOverall = new AtomicInteger();
			generateTiles(CreateTileOverlaysHelper.read(VISITED_TILES_TXT, "tiles"), tilesOverall, TILES_TILES_DIR,
					VISITED_TILES_JSON, newTiles::contains, false);

			log.info(String.format(Locale.US, "Wrote %,d files for changed tiles in %,dms",
					tilesOverall.get(), System.currentTimeMillis() - start));
		}
	}

	protected static Set<OSMTile> generateTiles(Set<String> tilesIn, AtomicInteger tilesOverall, File tileDir,
			File jsonFile, Predicate<OSMTile> filter, boolean borderOnly) throws InterruptedException, IOException {
		// read GeoJSON from file to use it for rendering overlay images
		final FeatureCollection<?, ?> features = GeoTools.parseFeatureCollection(jsonFile);

		Set<OSMTile> allTiles = ConcurrentHashMap.newKeySet();

		CreateTileOverlaysHelper.forEachZoom(
				zoom -> generateTilesForOneZoom(zoom, tilesIn, tilesOverall, tileDir, filter, features, allTiles, borderOnly));

		return allTiles;
	}

	private static void generateTilesForOneZoom(int zoom, Set<String> tilesIn,
			AtomicInteger tilesOverall,
			File tileDir,
			Predicate<OSMTile> filter,
			FeatureCollection<?, ?> features, Set<OSMTile> allTiles, boolean borderOnly) {
		CreateTileOverlaysHelper.ACTUAL.add(zoom, 1);

		log.info(String.format("%s: Start processing of %d tiles at zoom %d%s",
				tileDir, tilesIn.size(), zoom, CreateTileOverlaysHelper.concatProgress()));

		Set<OSMTile> tilesOut = new HashSet<>();

		int tilesCount = tilesIn.size();
		int tilesNr = 1;
		for (String tileIn : tilesIn) {
			handleTile(tileIn, zoom, tilesOut, filter);

			if (lastLogTile.get() + TimeUnit.SECONDS.toMillis(5) < System.currentTimeMillis()) {
				log.info(String.format(Locale.US, "%s: Zoom %d: %,d of %,d: %s - %,d",
						tileDir, zoom, tilesNr, tilesCount, tileIn, tilesOut.size()));

				lastLogTile.set(System.currentTimeMillis());
			}

			tilesNr++;
		}

		log.info(String.format("%s: Having %d touched tiles for zoom %d%s",
				tileDir, tilesOut.size(), zoom, CreateTileOverlaysHelper.concatProgress()));
		CreateTileOverlaysHelper.EXPECTED.add(zoom, tilesOut.size());

		allTiles.addAll(tilesOut);
		int tilesOutSize = tilesOut.size();
		tilesOverall.addAndGet(tilesOutSize);

		CreateTileOverlaysHelper.writeTilesToFiles(TILE_DIR_COMBINED_TILES, tilesOut, tileDir, features, borderOnly);

		log.info(String.format("%s: Wrote %d files for zoom %d%s",
				tileDir, tilesOutSize, zoom, CreateTileOverlaysHelper.concatProgress()));
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
