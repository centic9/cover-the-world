package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.geojson.CreateGeoJSON.VISITED_SQUARES_JS;
import static org.dstadler.ctw.geojson.CreateGeoJSON.VISITED_TILES_JS;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.testing.PrivateConstructorCoverage;
import org.dstadler.ctw.utils.OSMTile;
import org.dstadler.ctw.utils.UTMRefWithHash;
import org.junit.jupiter.api.Test;

import com.github.filosganga.geogson.model.Feature;

class CreateGeoJSONTest {
	private static final Logger log = LoggerFactory.make();

	private static final AtomicLong lastLog = new AtomicLong();

	@Test
	void test() throws IOException {
		// for now simply run the application
		CreateGeoJSON.main(new String[0]);
	}

	@Test
	public void testSquares() throws IOException {
		CreateGeoJSON.writeGeoJSON(VISITED_SQUARES_TXT, VISITED_SQUARES_JS, "squares",
				UTMRefWithHash::getRectangle, UTMRefWithHash::fromString, "squares");
	}

	@Test
	public void testSquaresDetail() throws IOException {
		// read list of UTMRefs for covered or new squares
		Set<UTMRefWithHash> squares = CreateGeoJSON.readSquares(new File(VISITED_SQUARES_TXT)).
				stream().
				map(UTMRefWithHash::fromString).
				collect(Collectors.toSet());

		log.info("Squares: Read " + squares.size());

		List<Feature> features = new ArrayList<>();
		while (squares.size() > 0) {
			final Feature rectangle =
					CreateGeoJSON.getSquareRectangle(squares, null, "squares");

			if (rectangle == null) {
				break;
			}

			features.add(rectangle);

			if (lastLog.get() + TimeUnit.SECONDS.toMillis(5) < System.currentTimeMillis()) {
				log.info("Squares: Found " + features.size() + " features, having " + squares.size() + " " +
						"squares" + " remaining, details: " + rectangle);

				lastLog.set(System.currentTimeMillis());
			}
		}
	}

	@Test
	public void testTiles() throws IOException {
		CreateGeoJSON.writeGeoJSON(VISITED_TILES_TXT, VISITED_TILES_JS, "tiles",
				OSMTile::getRectangle, OSMTile::fromString, "tiles");
	}

	@Test
	public void testTilesDetails() throws IOException {
		// read list of UTMRefs for covered or new squares
		Set<OSMTile> squares = CreateGeoJSON.readSquares(new File(VISITED_TILES_TXT)).
				stream().
				map(OSMTile::fromString).
				collect(Collectors.toSet());

		log.info("Squares: Read " + squares.size());

		List<Feature> features = new ArrayList<>();
		while (squares.size() > 0) {
			final Feature rectangle =
					CreateGeoJSON.getTileRectangle(squares, null, "tiles");

			if (rectangle == null) {
				break;
			}

			features.add(rectangle);

			if (lastLog.get() + TimeUnit.SECONDS.toMillis(5) < System.currentTimeMillis()) {
				log.info("Tiles: Found " + features.size() + " features, having " + squares.size() + " " +
						"tiles" + " remaining, details: " + rectangle);

				lastLog.set(System.currentTimeMillis());
			}
		}
	}

	// helper method to get coverage of the unused constructor
	@Test
	void testPrivateConstructor() throws Exception {
		PrivateConstructorCoverage.executePrivateConstructor(CreateGeoJSON.class);
	}
}
