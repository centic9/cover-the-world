package org.dstadler.ctw.geojson;

import static org.dstadler.ctw.geojson.CreateGeoJSON.VISITED_SQUARES_JS;
import static org.dstadler.ctw.geojson.CreateGeoJSON.VISITED_TILES_JS;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.testing.PrivateConstructorCoverage;
import org.dstadler.ctw.utils.OSMTile;
import org.dstadler.ctw.utils.UTMRefWithHash;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.github.filosganga.geogson.model.Feature;

class CreateGeoJSONTest {
	private static final Logger log = LoggerFactory.make();

	private static final AtomicLong lastLog = new AtomicLong();

	@BeforeAll
	static void beforeAll() throws IOException {
		LoggerFactory.initLogging();
	}

	@Test
	void test() throws IOException {
		// for now simply run the application
		CreateGeoJSON.main(new String[0]);
	}

	@Test
	void testEmptyFile() throws IOException {
		File tempTxt = File.createTempFile("CreateGeoJSONTest", ".txt");
		File tempJs = File.createTempFile("CreateGeoJSONTest", ".js");
		try {
			CreateGeoJSON.writeGeoJSON(tempTxt.getAbsolutePath(), tempJs.getAbsolutePath(), "tiles",
					OSMTile::getRectangle, OSMTile::fromString, "tiles");

			assertTrue(tempJs.exists());
			assertTrue(tempJs.length() > 0);
		} finally {
			assertTrue(!tempTxt.exists() || tempTxt.delete());
			assertTrue(!tempJs.exists() || tempJs.delete());

			File tempJson = new File(GeoJSON.getJSONFileName(tempJs.getAbsolutePath()));
			assertTrue(!tempJson.exists() || tempJson.delete());
		}
	}

	@Test
	void testOneTile() throws IOException {
		File tempTxt = File.createTempFile("CreateGeoJSONTest", ".txt");
		File tempJs = File.createTempFile("CreateGeoJSONTest", ".js");
		try {
			FileUtils.writeStringToFile(tempTxt, "14/8846/5677", "UTF-8");
			assertTrue(tempJs.delete());

			CreateGeoJSON.writeGeoJSON(tempTxt.getAbsolutePath(), tempJs.getAbsolutePath(), "tiles",
					OSMTile::getRectangle, OSMTile::fromString, "tiles");

			assertTrue(tempJs.exists());
			assertTrue(tempJs.length() > 0);
		} finally {
			assertTrue(!tempTxt.exists() || tempTxt.delete());
			assertTrue(!tempJs.exists() || tempJs.delete());

			File tempJson = new File(GeoJSON.getJSONFileName(tempJs.getAbsolutePath()));
			assertTrue(!tempJson.exists() || tempJson.delete());
		}
	}

	@Disabled("Only used for local testing, already tested via main above")
	@Test
	void testSquares() throws IOException {
		CreateGeoJSON.writeGeoJSON(VISITED_SQUARES_TXT, VISITED_SQUARES_JS, "squares",
				UTMRefWithHash::getRectangle, UTMRefWithHash::fromString, "squares");
	}

	@Disabled("Only used for local testing, already tested via main above")
	@Test
	void testSquaresDetail() throws IOException {
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

	@Disabled("Only used for local testing, already tested via main above")
	@Test
	void testTiles() throws IOException {
		CreateGeoJSON.writeGeoJSON(VISITED_TILES_TXT, VISITED_TILES_JS, "tiles",
				OSMTile::getRectangle, OSMTile::fromString, "tiles");
	}

	@Disabled("Only used for local testing, already tested via main above")
	@Test
	void testTilesDetails() throws IOException {
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
