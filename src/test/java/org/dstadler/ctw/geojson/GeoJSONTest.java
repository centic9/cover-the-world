package org.dstadler.ctw.geojson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.dstadler.ctw.utils.LatLonRectangle;
import org.dstadler.ctw.utils.OSMTile;
import org.dstadler.ctw.utils.UTMRefWithHash;
import org.junit.Test;

import com.github.filosganga.geogson.model.Feature;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GeoJSONTest {
	@Test
	public void testCreateSquare() {
		Feature square = GeoJSON.createSquare(UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle(), null);
		assertNotNull(square);
		assertTrue(square.properties().isEmpty());

		square = GeoJSON.createSquare(OSMTile.fromString("12/23/43").getRectangle(), null);
		assertNotNull(square);
		assertTrue(square.properties().isEmpty());
	}

	@Test
	public void testCreateSquareWithProperty() {
		Feature square = GeoJSON.createSquare(UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle(),
				"property 1");
		assertNotNull(square);
		assertFalse("Had: " + square.properties(),
				square.properties().isEmpty());

		assertNotNull(square.properties().get("popupContent"));
		assertEquals("property 1", square.properties().get("popupContent").getAsString());
	}

	@Test
	public void testCreateSquareOSMTile() {
		Feature square = GeoJSON.createSquare(OSMTile.fromString("1/1/1").getRectangle(), null);
		assertNotNull(square);
		assertTrue(square.properties().isEmpty());

		square = GeoJSON.createSquare(OSMTile.fromString("13/1432/2341").getRectangle(), null);
		assertNotNull(square);
		assertTrue(square.properties().isEmpty());
	}

	@Test
	public void testFormatDecimal() {
		assertEquals(1.0, GeoJSON.formatDecimal(1.0d), 0.000001);
		assertEquals(1.12345, GeoJSON.formatDecimal(1.1234547890d), 0.000001);
		assertEquals(1.12345, GeoJSON.formatDecimal(1.1234507890d), 0.000001);
		assertEquals(1.12346, GeoJSON.formatDecimal(1.1234567890d), 0.000001);
		assertEquals(89.12346, GeoJSON.formatDecimal(89.1234567890d), 0.000001);
		assertEquals(-89.12346, GeoJSON.formatDecimal(-89.1234567890d), 0.000001);
	}

	@Test
	public void testWrite() throws IOException {
		File temp = File.createTempFile("GeoJSONTest", ".js");
		try {
			assertTrue(temp.delete());

			LatLonRectangle rect = UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle();

			List<Feature> features = new ArrayList<>();
			features.add(GeoJSON.createSquare(rect, null));
			GeoJSON.writeGeoJSON(temp.getAbsolutePath(), "test", features);

			assertTrue(temp.exists());
			String js = FileUtils.readFileToString(temp, StandardCharsets.UTF_8);
			assertTrue("Had: " + js,
					js.contains("\"features\""));

			assertTrue("Should have only 5 decimal digits for \nrect " + rect + ", but had: \n" + js,
					js.contains("[" + GeoJSON.formatDecimal(rect.lon1) + "," + GeoJSON.formatDecimal(rect.lat1) + "]"));
		} finally {
			assertTrue("Had: " + temp.getAbsolutePath(),
					!temp.exists() || temp.delete());
		}
	}

	@Test
	public void testGetGeoJSON() throws IOException {
		LatLonRectangle rect = UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle();

		List<Feature> features = new ArrayList<>();
		features.add(GeoJSON.createSquare(rect, null));

		try (InputStream input = GeoJSON.getGeoJSON(features)) {
			String json = IOUtils.toString(input, StandardCharsets.UTF_8);
			assertEquals(
					"{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[6.61158,3.1307],[6.62057,3.1307],[6.62057,3.12166],[6.61158,3.12166],[6.61158,3.1307]]]}}]}",
					json);
		}
	}
}
