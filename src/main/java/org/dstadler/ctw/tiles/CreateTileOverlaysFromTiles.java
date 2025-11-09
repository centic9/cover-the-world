package org.dstadler.ctw.tiles;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_NEW_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.dstadler.commons.logging.jdk.LoggerFactory;
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

	public static final File VISITED_TILES_JSON = new File("js/VisitedTiles.json");
	public static final File VISITED_TILES_NEW_JSON = new File("js/VisitedTilesNew.json");

	public static final File TILES_TILES_DIR = new File("tilesTiles");
	public static final File TILES_TILES_DIR_NEW = new File("tilesTilesNew");

	public static void main(String[] args) throws IOException, InterruptedException {
		boolean onlyNewTiles = CreateTileOverlaysHelper.init(args);

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
		Set<OSMTile> newTiles = CreateTileOverlaysHelper.generateTiles(tiles, tilesOverall, tileDir, jsonFile, t -> true, false);

		log.info(String.format(Locale.US, "Wrote %,d files overall in %,dms",
				tilesOverall.get(), System.currentTimeMillis() - start));

		if (onlyNewTiles) {
			// rerun for normal tiles, but only ones that were touched by the new tiles
			log.info("--------------------------------------------------------------------------");
			log.info(String.format("Write touched full tiles for %d new tiles, found %d affected tiles",
					tiles.size(), newTiles.size()));

			tilesOverall = new AtomicInteger();
			CreateTileOverlaysHelper.generateTiles(CreateTileOverlaysHelper.read(VISITED_TILES_TXT, "tiles"), tilesOverall, TILES_TILES_DIR,
					VISITED_TILES_JSON, newTiles::contains, false);

			log.info(String.format(Locale.US, "Wrote %,d files for changed tiles in %,dms",
					tilesOverall.get(), System.currentTimeMillis() - start));
		}
	}
}
