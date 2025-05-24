package org.dstadler.ctw.geojson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;

import com.github.filosganga.geogson.model.Feature;

public class GeoJSONTest {
	@Test
	void testCreateSquare() {
		Feature square = GeoJSON.createSquare(UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle(), null);
		assertNotNull(square);
		assertTrue(square.properties().isEmpty());

		square = GeoJSON.createSquare(OSMTile.fromString("12/23/43").getRectangle(), null);
		assertNotNull(square);
		assertTrue(square.properties().isEmpty());
	}

	@Test
	void testCreateSquareWithProperty() {
		Feature square = GeoJSON.createSquare(UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle(),
				"property 1");
		assertNotNull(square);
		assertFalse(square.properties().isEmpty(), "Had: " + square.properties());

		assertNotNull(square.properties().get("popupContent"));
		assertEquals("property 1", square.properties().get("popupContent").getAsString());
	}

	@Test
	void testCreateSquareOSMTile() {
		Feature square = GeoJSON.createSquare(OSMTile.fromString("1/1/1").getRectangle(), null);
		assertNotNull(square);
		assertTrue(square.properties().isEmpty());

		square = GeoJSON.createSquare(OSMTile.fromString("13/1432/2341").getRectangle(), null);
		assertNotNull(square);
		assertTrue(square.properties().isEmpty());
	}

	@Test
	void testCreateLines() {
		Feature lines = GeoJSON.createLines(UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle(), null);
		assertNotNull(lines);
		assertTrue(lines.properties().isEmpty());

		lines = GeoJSON.createLines(OSMTile.fromString("12/23/43").getRectangle(), null);
		assertNotNull(lines);
		assertTrue(lines.properties().isEmpty());
	}

	@Test
	void testCreateLinesWithProperty() {
		Feature lines = GeoJSON.createLines(UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle(),
				"property 1");
		assertNotNull(lines);
		assertFalse(lines.properties().isEmpty(), "Had: " + lines.properties());

		assertNotNull(lines.properties().get("popupContent"));
		assertEquals("property 1", lines.properties().get("popupContent").getAsString());
	}

	@Test
	void testCreateLinesOSMTile() {
		Feature lines = GeoJSON.createLines(OSMTile.fromString("1/1/1").getRectangle(), null);
		assertNotNull(lines);
		assertTrue(lines.properties().isEmpty());

		lines = GeoJSON.createLines(OSMTile.fromString("13/1432/2341").getRectangle(), null);
		assertNotNull(lines);
		assertTrue(lines.properties().isEmpty());
	}

	@Test
	void testFormatDecimal() {
		assertEquals(1.0, GeoJSON.formatDecimal(1.0d), 0.000001);
		assertEquals(1.12345, GeoJSON.formatDecimal(1.1234547890d), 0.000001);
		assertEquals(1.12345, GeoJSON.formatDecimal(1.1234507890d), 0.000001);
		assertEquals(1.12346, GeoJSON.formatDecimal(1.1234567890d), 0.000001);
		assertEquals(89.12346, GeoJSON.formatDecimal(89.1234567890d), 0.000001);
		assertEquals(-89.12346, GeoJSON.formatDecimal(-89.1234567890d), 0.000001);
	}

	@Test
	void testWriteSquare() throws IOException {
		File temp = File.createTempFile("GeoJSONTest", ".js");
		try {
			assertTrue(temp.delete());

			LatLonRectangle rect = UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle();

			List<Feature> features = new ArrayList<>();
			features.add(GeoJSON.createSquare(rect, null));
			GeoJSON.writeGeoJavaScript(temp.getAbsolutePath(), "test", features);

			assertTrue(temp.exists());
			String js = FileUtils.readFileToString(temp, StandardCharsets.UTF_8);
			assertTrue(js.contains("\"features\""), "Had: " + js);

			assertTrue(
					js.contains("[" + GeoJSON.formatDecimal(rect.lon1) + "," + GeoJSON.formatDecimal(rect.lat1) + "]"),
					"Should have only 5 decimal digits for \nrect " + rect + ", but had: \n" + js);
		} finally {
			assertTrue(!temp.exists() || temp.delete(), "Had: " + temp.getAbsolutePath());
		}
	}

	@Test
	void testWriteLines() throws IOException {
		File temp = File.createTempFile("GeoJSONTest", ".js");
		try {
			assertTrue(temp.delete());

			LatLonRectangle rect = UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle();

			List<Feature> features = new ArrayList<>();
			features.add(GeoJSON.createLines(rect, null));
			GeoJSON.writeGeoJavaScript(temp.getAbsolutePath(), "test", features);

			assertTrue(temp.exists());
			String js = FileUtils.readFileToString(temp, StandardCharsets.UTF_8);
			assertTrue(js.contains("\"features\""), "Had: " + js);

			assertTrue(
					js.contains("[" + GeoJSON.formatDecimal(rect.lon1) + "," + GeoJSON.formatDecimal(rect.lat1) + "]"),
					"Should have only 5 decimal digits for \nrect " + rect + ", but had: \n" + js);
		} finally {
			assertTrue(!temp.exists() || temp.delete(), "Had: " + temp.getAbsolutePath());
		}
	}


	@Test
	void testWriteSquareJSON() throws IOException {
		File temp = File.createTempFile("GeoJSONTest", ".js");
		try {
			assertTrue(temp.delete());

			LatLonRectangle rect = UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle();

			List<Feature> features = new ArrayList<>();
			features.add(GeoJSON.createSquare(rect, null));
			GeoJSON.writeGeoJSON(temp.getAbsolutePath(), features);

			assertTrue(temp.exists());
			String js = FileUtils.readFileToString(temp, StandardCharsets.UTF_8);
			assertTrue(js.contains("\"features\""), "Had: " + js);

			assertTrue(
					js.contains("[" + GeoJSON.formatDecimal(rect.lon1) + "," + GeoJSON.formatDecimal(rect.lat1) + "]"),
					"Should have only 5 decimal digits for \nrect " + rect + ", but had: \n" + js);
		} finally {
			assertTrue(!temp.exists() || temp.delete(), "Had: " + temp.getAbsolutePath());
		}
	}

	@Test
	void testWriteLinesJSON() throws IOException {
		File temp = File.createTempFile("GeoJSONTest", ".js");
		try {
			assertTrue(temp.delete());

			LatLonRectangle rect = UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle();

			List<Feature> features = new ArrayList<>();
			features.add(GeoJSON.createLines(rect, null));
			GeoJSON.writeGeoJSON(temp.getAbsolutePath(), features);

			assertTrue(temp.exists());
			String js = FileUtils.readFileToString(temp, StandardCharsets.UTF_8);
			assertTrue(js.contains("\"features\""), "Had: " + js);

			assertTrue(
					js.contains("[" + GeoJSON.formatDecimal(rect.lon1) + "," + GeoJSON.formatDecimal(rect.lat1) + "]"),
					"Should have only 5 decimal digits for \nrect " + rect + ", but had: \n" + js);
		} finally {
			assertTrue(!temp.exists() || temp.delete(), "Had: " + temp.getAbsolutePath());
		}
	}

	@Test
	void testGetGeoJSONSquare() throws IOException {
		LatLonRectangle rect = UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle();

		List<Feature> features = new ArrayList<>();
		features.add(GeoJSON.createSquare(rect, null));

		try (InputStream input = GeoJSON.getGeoJSON(features)) {
			String json = IOUtils.toString(input, StandardCharsets.UTF_8);
			assertEquals(
					"{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[6.61158,3.1307],[6.62057,3.1307],[6.62057,3.12166],[6.61158,3.12166],[6.61158,3.1307]]]}\n"
							+ "  }]}",
					json);
		}
	}

	@Test
	void testGetGeoJSONProperties() throws IOException {
		LatLonRectangle rect = UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle();

		List<Feature> features = new ArrayList<>();
		features.add(GeoJSON.createSquare(rect, "some test property"));

		try (InputStream input = GeoJSON.getGeoJSON(features)) {
			String json = IOUtils.toString(input, StandardCharsets.UTF_8);
			assertEquals(
					"{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"popupContent\":\"some test property\"},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[6.61158,3.1307],[6.62057,3.1307],[6.62057,3.12166],[6.61158,3.12166],[6.61158,3.1307]]]}\n"
							+ "  }]}",
					json);
		}
	}

	@Test
	void testGetGeoJSONLines() throws IOException {
		LatLonRectangle rect = UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle();

		List<Feature> features = new ArrayList<>();
		features.add(GeoJSON.createLines(rect, null));

		try (InputStream input = GeoJSON.getGeoJSON(features)) {
			String json = IOUtils.toString(input, StandardCharsets.UTF_8);
			assertEquals(
					"{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[6.61158,3.1307],[6.62057,3.1307],[6.62057,3.12166],[6.61158,3.12166],[6.61158,3.1307]]}\n"
							+ "  }]}",
					json);
		}
	}

	@Test
	void testGet2GeoJSONLines() throws IOException {
		LatLonRectangle rect = UTMRefWithHash.fromString("32U 234543.0 345342.20").getRectangle();

		List<Feature> features = new ArrayList<>();
		features.add(GeoJSON.createLines(rect, null));
		features.add(GeoJSON.createLines(rect, null));

		try (InputStream input = GeoJSON.getGeoJSON(features)) {
			String json = IOUtils.toString(input, StandardCharsets.UTF_8);
			assertEquals(
					"{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[6.61158,3.1307],[6.62057,3.1307],[6.62057,3.12166],[6.61158,3.12166],[6.61158,3.1307]]}\n"
							+ "  },{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[6.61158,3.1307],[6.62057,3.1307],[6.62057,3.12166],[6.61158,3.12166],[6.61158,3.1307]]}\n"
							+ "  }]}",
					json);
		}
	}

	@Test
	void testGetJsonFileName() {
		assertEquals(".json", GeoJSON.getJSONFileName(""));
		assertEquals("abcdef.json", GeoJSON.getJSONFileName("abcdef"));
		assertEquals("abcdef.json", GeoJSON.getJSONFileName("abcdef.js"));
		assertEquals("abcdef.json.json", GeoJSON.getJSONFileName("abcdef.json"));
		assertEquals("abcdef.json.json", GeoJSON.getJSONFileName("abcdef.json.js"));
	}
}
