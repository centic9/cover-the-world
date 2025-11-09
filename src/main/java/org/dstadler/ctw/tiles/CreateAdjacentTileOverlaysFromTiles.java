package org.dstadler.ctw.tiles;

import static org.dstadler.ctw.geojson.CreateAdjacent.ADJACENT_TILES_NEW_TXT;
import static org.dstadler.ctw.geojson.CreateAdjacent.ADJACENT_TILES_TXT;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.dstadler.commons.logging.jdk.LoggerFactory;

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

	public static void main(String[] args) throws IOException, InterruptedException {
		boolean onlyNewTiles = CreateTileOverlaysHelper.init(args);

		File tileDir = ADJACENT_TILES_DIR;
		String tilesFile = onlyNewTiles ? ADJACENT_TILES_NEW_TXT: ADJACENT_TILES_TXT;

		if (onlyNewTiles) {
			log.info("Writing only new adjacent tiles to directory " + tileDir);
		} else {
			log.info("Writing adjacent tiles to directory " + tileDir);
		}

		/* we write both into the same directory, so do not remove these here
		if (onlyNewTiles) {
			CreateTileOverlaysHelper.cleanTiles(tileDir);
		}
		*/
		if (!tileDir.exists() && !tileDir.mkdirs()) {
			throw new IOException("Could not create directory at " + tileDir);
		}

		long start = System.currentTimeMillis();

		Set<String> tiles = CreateTileOverlaysHelper.read(tilesFile, "adjacentTiles");

		AtomicInteger tilesOverall = new AtomicInteger();
		// t.toCoords().equals("17/70647/45300")
		CreateTileOverlaysHelper.generateTiles(tiles, tilesOverall,
				tileDir, ADJACENT_TILES_JSON, t -> true, true);

		log.info(String.format(Locale.US, "Wrote %,d files overall in %,dms",
				tilesOverall.get(), System.currentTimeMillis() - start));
	}
}
