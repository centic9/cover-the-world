package org.dstadler.ctw.utils;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;
import static org.dstadler.ctw.utils.Constants.MAX_ZOOM;
import static org.dstadler.ctw.utils.OSMTile.OSM_MAX_ZOOM;
import static org.dstadler.ctw.utils.OSMTile.OSM_MIN_ZOOM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.dstadler.commons.collections.MappedCounter;
import org.dstadler.commons.collections.MappedCounterImpl;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.testing.TestHelpers;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import uk.me.jstott.jcoord.LatLng;

public class OSMTileTest {
	private static final Logger log = LoggerFactory.make();

	public static final double ASSERT_DELTA = 0.00001;

	private final static int MIN_LATITUDE = -80;
	private final static int MAX_LATITUDE = 84;

	@BeforeAll
	public static void beforeClass() throws IOException {
		LoggerFactory.initLogging();
	}

	@Test
	void test() {
		OSMTile nr = OSMTile.fromLatLngZoom(2, 3, 10);
		assertNotNull(nr);
		assertEquals(520, nr.getXTile(),
				"Having: " + nr.toCoords());
		assertEquals(506, nr.getYTile(),
				"Having: " + nr.toCoords());
		assertEquals(10, nr.getZoom(),
				"Having: " + nr.toCoords());

		assertEquals("10/520", nr.toDirName(),
				"Having: " + nr.toCoords());
		assertEquals("10/520/506", nr.toCoords(),
				"Having: " + nr.toCoords());
		assertEquals(new File("./10/520/506.png"), nr.toFile(new File(".")),
				"Having: " + nr.toCoords());
		assertEquals("10/520", nr.toDirName(),
				"Having: " + nr.toCoords());
	}

	@Test
	void testConstructInvalid() {
		assertNotNull(new OSMTile(10, 23, 23).toCoords());

		assertThrows(IllegalArgumentException.class,
				() -> new OSMTile(-1, 23, 23));
		assertThrows(IllegalArgumentException.class,
				() -> new OSMTile(100, 23, 23));

		assertThrows(IllegalArgumentException.class,
				() -> new OSMTile(10, -1, 23));
		assertThrows(IllegalArgumentException.class,
				() -> new OSMTile(10, 38283823, 23));

		assertThrows(IllegalArgumentException.class,
				() -> new OSMTile(10, 23, -1));
		assertThrows(IllegalArgumentException.class,
				() -> new OSMTile(10, 23, 238374623));
	}

	@Test
	void testFromLatLngInvalid() {
		// verify a few valid ones
		assertNotNull(OSMTile.fromLatLngZoom(10, 10, 10).toCoords());
		assertNotNull(OSMTile.fromLatLngZoom(90, 180, 0).toCoords());
		assertNotNull(OSMTile.fromLatLngZoom(10, 180, 0).toCoords());
		assertNotNull(OSMTile.fromLatLngZoom(-90, -180, 19).toCoords());
		assertNotNull(OSMTile.fromLatLngZoom(-90, -10, 19).toCoords());
		assertNotNull(OSMTile.fromLatLngZoom(0, -180, 19).toCoords());

		// then use values that are out of range
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromLatLngZoom(10, 10, -1));
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromLatLngZoom(10, 10, 100));

		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromLatLngZoom(-190, 10, 10));
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromLatLngZoom(91, 10, 10));

		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromLatLngZoom(10, -1800, 10));
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromLatLngZoom(10, 181, 10));
	}

	@Test
	void testHashCodeEquals() {
		OSMTile nr1 = OSMTile.fromLatLngZoom(34, 3, 10);
		OSMTile nr2 = OSMTile.fromLatLngZoom(34, 3, 10);
		TestHelpers.HashCodeTest(nr1, nr2);

		assertTrue(new OSMTile(7, 3, 10).hashCode() !=
				new OSMTile(7, 3, 11).hashCode());
		assertTrue(new OSMTile(7, 3, 10).hashCode() !=
				new OSMTile(7, 2, 10).hashCode());
		assertTrue(new OSMTile(7, 3, 10).hashCode() !=
				new OSMTile(8, 3, 10).hashCode());

		OSMTile other = OSMTile.fromLatLngZoom(34, 3, 9);
		TestHelpers.EqualsTest(nr1, nr2, other);

		other = OSMTile.fromLatLngZoom(34, 3, 11);
		TestHelpers.EqualsTest(nr1, nr2, other);

		other = OSMTile.fromLatLngZoom(34, 2, 10);
		TestHelpers.EqualsTest(nr1, nr2, other);
	}

	@Test
	void testComparator() {
		OSMTile nr1 = OSMTile.fromLatLngZoom(34, 3, 10);
		OSMTile nr2 = OSMTile.fromLatLngZoom(34, 3, 10);

		OSMTile other = OSMTile.fromLatLngZoom(34, 3, 9);
		TestHelpers.CompareToTest(nr1, nr2, other, true);

		other = OSMTile.fromLatLngZoom(34, 3, 11);
		TestHelpers.CompareToTest(nr1, nr2, other, false);

		other = OSMTile.fromLatLngZoom(34, 2, 10);
		TestHelpers.CompareToTest(nr1, nr2, other, true);

		other = OSMTile.fromLatLngZoom(35, 3, 10);
		TestHelpers.CompareToTest(nr1, nr2, other, true);
	}

	@Test
	void testInvalidValuesFromLatLngZoom() {
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromLatLngZoom(2432, 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromLatLngZoom(1, 2344, 1));
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromLatLngZoom(1, 1, 1283));
	}

	@Test
	void testInvalidValuesGetPixel() {
		OSMTile tile = OSMTile.fromLatLngZoom(1, 1, 1);
		assertThrows(IllegalArgumentException.class,
				() -> tile.getPixelInTile(2432, 1));
		assertThrows(IllegalArgumentException.class,
				() -> tile.getPixelInTile(1, 2344));
	}

	@Test
	void testGetPixelInTile() {
		//checkPixel(35.234, 3.23423, 10, 51, 202);
		checkPixel(53.09391806811101, -8.223464321282336, 5, 68, 105);
		checkPixel(53.102901220952205, -8.208504993347152, 5, 69, 104);
		checkPixel(53.09391806811101, -8.223464321282336, 6, 137, 210);
		checkPixel(53.102901220952205, -8.208504993347152, 6, 138, 209);
		checkPixel(49.35523703235674, 179.995057521241, 9, 254, 15);
		checkPixel(49.36422018519794, 180.0, 9, 255, 10);

		checkPixel(48.80961073546938, 13.774401458702998, 12, 184, 243);
		checkPixel(48.80686346108518, 13.787799241699187, 12, 223, 0);
	}

	private static void checkPixel(double lat, double lon, int zoom, int pixelX, int pixelY) {
		OSMTile tile = OSMTile.fromLatLngZoom(lat, lon, zoom);
		Pair<Integer, Integer> pixel = tile.getPixelInTile(lat, lon);
		assertNotNull(pixel);

		assertEquals(Integer.valueOf(pixelX), pixel.getKey(),
				"Having: " + pixel);
		assertEquals(Integer.valueOf(pixelY), pixel.getValue(),
				"Having: " + pixel);
	}

	@SuppressWarnings("deprecation")
	@Test
	void testRandom() {
		for (int i = 0; i < 100_000; i++) {
			double lat = RandomUtils.nextDouble(0, (-1) * MIN_LATITUDE + MAX_LATITUDE);
			double lon = RandomUtils.nextDouble(0, 2 * 180);
			lat += MIN_LATITUDE;
			lon -= 180;
			int zoom = RandomUtils.nextInt(OSM_MIN_ZOOM, OSM_MAX_ZOOM + 1);

			OSMTile tile = OSMTile.fromLatLngZoom(lat, lon, zoom);
			TestHelpers.ToStringTest(tile);

			assertEquals(tile.toCoords(), tile.string());
			assertNotEquals(tile.toString(), tile.string());
		}
	}

	@SuppressWarnings("deprecation")
	@Test
	void testRandomHashCode() {
		MappedCounter<Integer> hashes = new MappedCounterImpl<>();
		Set<String> tiles = new HashSet<>();
		for (int i = 0; i < 100_000; i++) {
			int zoom = RandomUtils.nextInt(1, MAX_ZOOM);
			int max = zoom == 0 ? 1 : 2 << (zoom - 1);
			OSMTile tile = new OSMTile(zoom,
					RandomUtils.nextInt(1, max - 1), RandomUtils.nextInt(1, max - 1));
			TestHelpers.ToStringTest(tile);

			assertEquals(tile.toCoords(), tile.string());
			assertNotEquals(tile.toString(), tile.string());

			// only check hash if we did not have this tile yet
			if (tiles.add(tile.toCoords())) {
				hashes.inc(tile.hashCode());
			}
		}

		Optional<Map.Entry<Integer, Long>> max = hashes.sortedMap().
				entrySet().
				stream().
				max(Map.Entry.comparingByValue());
		assertTrue(max.orElseThrow().getValue() <= 15,
				"Did not expect many equal hash-values in random tiles, but failed with " + max);
	}

	@Test
	void testGetLatLon() {
		checkStartEndLatLon(2.0, 3.0, 10,
				2.1088986, 2.8125,
				1.757536811308323, 3.1640625);

		checkStartEndLatLon(48.4087, 14.2201, 12,
				48.45835188280865, 14.150390625,
				48.40003249610685, 14.23828125);
	}

	private static void checkStartEndLatLon(
			double pointLat, double pointLon, int zoom,
			double expectedStartLat, double expectedStartLon,
			double expectedEndLat, double expectedEndLon) {
		OSMTile nr = OSMTile.fromLatLngZoom(pointLat, pointLon, zoom);
		assertNotNull(nr);

		assertEquals(expectedStartLat, nr.toLatLng().getLatitude(), ASSERT_DELTA);
		assertEquals(expectedStartLon, nr.toLatLng().getLongitude(), ASSERT_DELTA);

		assertEquals(nr.toLatLng().getLatitude(), nr.getRectangle().lat1, ASSERT_DELTA);
		assertEquals(nr.toLatLng().getLongitude(), nr.getRectangle().lon1, ASSERT_DELTA);

		assertEquals(expectedEndLat, nr.getRectangle().lat2, ASSERT_DELTA);
		assertEquals(expectedEndLon, nr.getRectangle().lon2, ASSERT_DELTA);
	}

	@Test
	void testKleinzell() {
		for (int zoom = OSM_MIN_ZOOM; zoom < OSM_MAX_ZOOM; zoom++) {
			OSMTile tile = OSMTile.fromLatLngZoom(48.45616, 13.99863, zoom);

			log.info(zoom + ": " + tile + ": " + tile.toCoords());
			LatLng start = tile.toLatLng();
			LatLng latLng = new LatLng(
					start.getLatitude() > 84 ? 84 : start.getLatitude(),
					start.getLongitude() > 180 ? 180 : start.getLongitude());
			log.info(zoom + ": " + "LatLon: " +
					latLng);
			log.info(zoom + ": " + "UTMRef: " + latLng.toUTMRef());
		}
	}

	@Test
	void testFromString() {
		OSMTile tile = OSMTile.fromString("1/1/1");
		assertEquals(1, tile.getZoom());
		assertEquals(1, tile.getXTile());
		assertEquals(1, tile.getYTile());

		tile = OSMTile.fromString("12/4000/2374");
		assertEquals(12, tile.getZoom());
		assertEquals(4000, tile.getXTile());
		assertEquals(2374, tile.getYTile());

		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString(""));

		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString("a/b/c"));

		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString("1/2/3/4"));
	}

	@Test
	void testInvalidValuesFromString() {
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString(""));
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString("1.2.3"));
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString("-1/2/3"));
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString("22/2/2"));
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString("10/-3/1"));
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString("10/3/-1"));
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString("10/3/abc"));

		// there can be at most 2^zoom level x/y
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString("1/3/2"));
		assertEquals("19/524287/524287", OSMTile.fromString("19/524287/524287").toCoords());
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString("19/524287/524288"));
		assertThrows(IllegalArgumentException.class,
				() -> OSMTile.fromString("19/524288/524287"));
	}

	@Test
	void testReadTiles() throws IOException {
		Assumptions.assumeTrue(new File(VISITED_TILES_TXT).exists(),
				"Cannot run test, file " + VISITED_TILES_TXT + " not found");
		OSMTile.readTiles(new File(VISITED_TILES_TXT));
	}

	@Test
	void testUpDownLeftRight() {
		OSMTile tile = new OSMTile(14, 2343, 2343);
		assertEquals("14/2343/2342", tile.up().toCoords());
		assertEquals("14/2343/2344", tile.down().toCoords());
		assertEquals("14/2342/2343", tile.left().toCoords());
		assertEquals("14/2344/2343", tile.right().toCoords());
	}

	@Test
	void testGetTilesAtZoom() {
		assertThrows(IllegalArgumentException.class,
				() -> new OSMTile(0, 0, 0).getTilesAtZoom(-1));

		OSMTile tile = new OSMTile(14, 8837, 5660);

		// lower zoom
		assertEquals("[OSMTile{zoom=14, xTile=8837, yTile=5660}]", tile.getTilesAtZoom(14).toString());
		assertEquals("[OSMTile{zoom=13, xTile=4418, yTile=2830}]", tile.getTilesAtZoom(13).toString());
		assertEquals("[OSMTile{zoom=12, xTile=2209, yTile=1415}]", tile.getTilesAtZoom(12).toString());

		// higher zoom
		assertEquals("["
				+ "OSMTile{zoom=15, xTile=17674, yTile=11320}, "
				+ "OSMTile{zoom=15, xTile=17674, yTile=11321}, "
				+ "OSMTile{zoom=15, xTile=17675, yTile=11320}, "
				+ "OSMTile{zoom=15, xTile=17675, yTile=11321}"
				+ "]", tile.getTilesAtZoom(15).toString());

		assertEquals(4096, tile.getTilesAtZoom(20).size());
	}

	@Test
	public void string() {
		OSMTile tile = new OSMTile(14, 8837, 5660);

		TestHelpers.ToStringTest(tile);

		assertEquals(tile.toCoords(), tile.string());
		assertNotEquals(tile.toString(), tile.string());
	}
}
