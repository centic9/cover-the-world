package org.dstadler.ctw.geotools;

import static org.dstadler.ctw.tiles.CreateTileOverlaysFromTiles.VISITED_TILES_JSON;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.dstadler.ctw.utils.LatLonRectangle;
import org.geotools.feature.FeatureCollection;
import org.junit.jupiter.api.Test;

class GeoToolsTest {
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
}