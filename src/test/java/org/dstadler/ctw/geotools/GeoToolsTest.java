package org.dstadler.ctw.geotools;

import static org.dstadler.ctw.tiles.CreateAdjacentTileOverlaysFromTiles.ADJACENT_TILES_JSON;
import static org.dstadler.ctw.tiles.CreateTileOverlaysFromTiles.VISITED_TILES_JSON;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.LatLonRectangle;
import org.geotools.feature.FeatureCollection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class GeoToolsTest {
	private static final Logger log = LoggerFactory.make();

	@BeforeAll
	static void init() throws IOException {
		LoggerFactory.initLogging();
	}

	@Test
	void testParse() throws IOException {
		final FeatureCollection<?, ?> features = GeoTools.parseFeatureCollection(VISITED_TILES_JSON);
		assertNotNull(features);

		assertTrue(features.size() > 3,
				"Had: " + features.size() + " for " + VISITED_TILES_JSON);
	}

	@Test
	void testParseInvalidFile() {
		assertThrows(IOException.class,
				() -> GeoTools.parseFeatureCollection(new File("not existing")));
	}

	@Test
	void testWrite() throws IOException {
		final FeatureCollection<?, ?> features = GeoTools.parseFeatureCollection(VISITED_TILES_JSON);
		assertNotNull(features);

		File temp = File.createTempFile("GeoToolsTest", ".png");
		try {
			assertTrue(temp.delete());

			GeoTools.writeImage(features, new LatLonRectangle(1, 0, 0, 1), temp);

			assertTrue(temp.exists());

			// read the image back in
			final BufferedImage image = ImageIO.read(temp);
			assertNotNull(image);
		} finally {
			assertTrue(!temp.exists() || temp.delete());
		}
	}

	@Test
	void testWriteBorder() throws IOException {
		final FeatureCollection<?, ?> features = GeoTools.parseFeatureCollection(ADJACENT_TILES_JSON);
		assertNotNull(features);

		File temp = File.createTempFile("GeoToolsTest", ".png");
		try {
			assertTrue(temp.delete());

			GeoTools.writeBorder(features, new LatLonRectangle(48.30055, 14.25588, 48.28085, 14.28609), temp);

			assertTrue(temp.exists());

			// read the image back in
			final BufferedImage image = ImageIO.read(temp);
			assertNotNull(image);
		} finally {
			assertTrue(!temp.exists() || temp.delete());
		}
	}

	@Disabled("Local micro-benchmark test")
	@Test
	void microBenchmarkImageWrite() throws IOException {
		final FeatureCollection<?, ?> features = GeoTools.parseFeatureCollection(ADJACENT_TILES_JSON);
		assertNotNull(features);

		File temp = File.createTempFile("GeoToolsTest", ".png");

		for (int j = 0; j < 20; j++) {
			long start = System.currentTimeMillis();
			for (int i = 0; i < 300; i++) {
				assertTrue(temp.delete());

				GeoTools.writeBorder(features, new LatLonRectangle(48.30055, 14.25588, 48.28085, 14.28609), temp);

				assertTrue(temp.exists());
			}

			log.info("Took " + (System.currentTimeMillis() - start) + "ms");
		}
		/*
		Took 2506ms
		Took 1617ms
		Took 1448ms
		Took 1383ms
		Took 1349ms
		Took 1327ms
		Took 1593ms
		Took 1652ms
		Took 1460ms
		Took 1366ms
		Took 1258ms
		Took 1298ms
		Took 1463ms
		Took 1830ms
		Took 1326ms
		Took 1292ms
		Took 1228ms
		Took 1261ms
		Took 1461ms
		Took 1253ms
		*/
	}
}