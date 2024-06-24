package org.dstadler.ctw.utils;

import static org.dstadler.ctw.utils.OSMTileTest.ASSERT_DELTA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.RandomUtils;
import org.dstadler.commons.collections.MappedCounter;
import org.dstadler.commons.collections.MappedCounterImpl;
import org.dstadler.commons.testing.TestHelpers;
import org.dstadler.commons.util.SuppressForbidden;
import org.junit.jupiter.api.Test;

import uk.me.jstott.jcoord.LatLng;

public class LatLonRectangleTest {
	private final static int MIN_LATITUDE = -80;
	private final static int MAX_LATITUDE = 84;

	@Test
	void testIntersectItself() {
		checkIntersect(
				48.458, 14.150, 48.400, 14.238,
				48.458, 14.150, 48.400, 14.238,
				48.458, 14.150, 48.400, 14.238);
	}

	@Test
	void testIntersect() {
		checkIntersect(
				48.458, 14.150, 48.400, 14.238,
				48.500, 14.200, 48.450, 14.338,
				48.458, 14.200, 48.450, 14.238);
	}

	@Test
	void testIntersect2() {
		checkIntersect(
				48.458, 14.150, 48.400, 14.238,
				48.450, 14.200, 48.300, 14.338,
				48.450, 14.200, 48.400, 14.238);
	}

	@Test
	void testIntersect3() {
		checkIntersect(
				48.458, 14.150, 48.400, 14.238,
				48.450, 14.200, 48.300, 14.230,
				48.450, 14.200, 48.400, 14.230);
	}

	@Test
	void testIntersect4() {
		checkIntersect(
				20, 0, 0, 20,
				2, 2, 1, 3,
				2, 2, 1, 3);
	}

	@Test
	void testIntersect5() {
		checkIntersect(
				1, 1, 1, 1,
				1, 1, 1, 1,
				1, 1, 1, 1);
	}

	@Test
	void testIntersectNegative() {
		checkIntersectEmpty(
				-48.400, -14.238, -48.458, -14.150,
				48.450, 14.200, 48.300, 14.230
		);

		checkIntersect(
				-48.400, -14.238, -48.458, -14.150,
				-48.300, -14.230, -48.450, -14.200,
				-48.400, -14.230, -48.450, -14.200);
	}

	@Test
	void testIntersectEmpty() {
		checkIntersectEmpty(
				48.458, 14.150, 48.400, 14.238,
				48.500, 14.239, 48.450, 14.338
		);
	}

	@Test
	void test() {
		LatLonRectangle rect = new LatLonRectangle(
				48.458, 14.150,
				48.400, 14.238);

		assertEquals(0.058, rect.height(), ASSERT_DELTA);
		assertEquals(0.088, rect.width(), ASSERT_DELTA);

		checkRect(rect,
				48.458, 14.150,
				48.400, 14.238);
	}

	private void checkRect(LatLonRectangle ret, double lat1, double lon1, double lat2, double lon2) {
		assertNotNull(ret, "Did not have an intersection but expected to get "
						+ "(" + lat1 + "," + lon1 + "), (" + lat2 + "," + lon2 + ")");

		assertEquals(lat1, ret.lat1, ASSERT_DELTA);
		assertEquals(lon1, ret.lon1, ASSERT_DELTA);
		assertEquals(lat2, ret.lat2, ASSERT_DELTA);
		assertEquals(lon2, ret.lon2, ASSERT_DELTA);
	}

	private void checkIntersectEmpty(
			double lat1, double lon1, double lat2, double lon2,
			double lat3, double lon3, double lat4, double lon4) {
		LatLonRectangle rect1 = new LatLonRectangle(
				lat1, lon1,
				lat2, lon2);
		LatLonRectangle rect2 = new LatLonRectangle(
				lat3, lon3,
				lat4, lon4);

		LatLonRectangle ret = rect1.intersect(rect2);
		assertNull(ret, "Had: " + ret);

		ret = rect2.intersect(rect1);
		assertNull(ret, "Had: " + ret);
	}

	private void checkIntersect(
			double lat1, double lon1, double lat2, double lon2,
			double lat3, double lon3, double lat4, double lon4,
			double retLat1, double retLon1, double retLat2, double retLon2) {
		LatLonRectangle rect1 = new LatLonRectangle(
				lat1, lon1,
				lat2, lon2);
		LatLonRectangle rect2 = new LatLonRectangle(
				lat3, lon3,
				lat4, lon4);
		LatLonRectangle ret = rect1.intersect(rect2);

		checkRect(ret,
				retLat1, retLon1,
				retLat2, retLon2);

		assertTrue(ret.width() >= 0);
		assertTrue(ret.height() >= 0);

		LatLonRectangle ret2 = rect2.intersect(rect1);

		checkRect(ret2,
				retLat1, retLon1,
				retLat2, retLon2);

		assertTrue(ret2.width() >= 0);
		assertTrue(ret2.height() >= 0);

		assertEquals(ret, ret2);
	}

	@Test
	void testToString() {
		LatLonRectangle rect = new LatLonRectangle(
				48.458, 14.150,
				48.400, 14.238);

		TestHelpers.ToStringTest(rect);

		rect = new LatLonRectangle(
				90, -180,
				-90, 180);

		TestHelpers.ToStringTest(rect);

		rect = new LatLonRectangle(
				ASSERT_DELTA, 0,
				0, ASSERT_DELTA);

		TestHelpers.ToStringTest(rect);
	}

	@Test
	void testHashCode() {
		LatLonRectangle rect = new LatLonRectangle(
				48.458, 14.150,
				48.400, 14.238);
		LatLonRectangle rectEqu = new LatLonRectangle(
				48.458, 14.150,
				48.400, 14.238);

		TestHelpers.HashCodeTest(rect, rectEqu);

		assertTrue(new LatLonRectangle(
				48.458, 14.150,
				48.400, 14.238).hashCode() !=
				new LatLonRectangle(
						48.458, 14.150,
						48.400, 14.239).hashCode());
	}

	@Test
	void testEquals() {
		LatLonRectangle rect = new LatLonRectangle(
				48.458, 14.150,
				48.400, 14.238);
		LatLonRectangle rectEqu = new LatLonRectangle(
				48.458, 14.150,
				48.400, 14.238);
		LatLonRectangle rectNotEqu = new LatLonRectangle(
				49.458, 14.150,
				48.400, 14.238);

		TestHelpers.EqualsTest(rect, rectEqu, rectNotEqu);

		rectNotEqu = new LatLonRectangle(
				48.459, 14.150,
				48.400, 14.238);
		TestHelpers.EqualsTest(rect, rectEqu, rectNotEqu);

		rectNotEqu = new LatLonRectangle(
				48.458, 14.151,
				48.400, 14.238);
		TestHelpers.EqualsTest(rect, rectEqu, rectNotEqu);

		rectNotEqu = new LatLonRectangle(
				48.458, 14.150,
				48.401, 14.238);
		TestHelpers.EqualsTest(rect, rectEqu, rectNotEqu);

		rectNotEqu = new LatLonRectangle(
				48.458, 14.150,
				48.400, 14.239);
		TestHelpers.EqualsTest(rect, rectEqu, rectNotEqu);
	}

	@Test
	void testLatLng() {
		checkLatLng(45.091711, 7.661622, 45.091711, 7.661622, 0);
		checkLatLng(45.091711, 7.661622, 45.055094, 7.647369, 4.2198080284070425);
		checkLatLng(45, 7, 45.055094, 7.647369, 51.20884014453142);
	}

	private static void checkLatLng(double myLat1, double myLng1, double myLat2, double myLng2,
			double distance) {
		LatLng origin = new LatLng(myLat1, myLng1);
		LatLng dest = new LatLng(myLat2, myLng2);
		assertEquals(distance, origin.distance(dest), ASSERT_DELTA);
		assertEquals(distance, dest.distance(origin), ASSERT_DELTA);
	}

	@Test
	void testBordersInside() {
		checkBordersInside(
				20, 0, 0, 20,
				2, 2, 1, 3,
				List.of(
						new LatLonRectangle(2,2,2,3),
						new LatLonRectangle(2,2,1,2),
						new LatLonRectangle(1,2,1,3),
						new LatLonRectangle(2, 3, 1, 3)));

		checkBordersInside(
				1, 1, 1, 1,
				1, 1, 1, 1,
				List.of(
					new LatLonRectangle(1.0,1.0,1.0,1.0),
					new LatLonRectangle(1.0,1.0,1.0,1.0),
					new LatLonRectangle(1.0,1.0,1.0,1.0),
					new LatLonRectangle(1.0,1.0,1.0,1.0)));

		checkBordersInside(
				1, 2, 1, 3,
				1, 1, 1, 1,
				Collections.emptyList());
		checkBordersInside(
				1, 2, 1, 3,
				1, 2, 1, 5,
				List.of(
						new LatLonRectangle(1.0,2.0,1.0,3.0),
						new LatLonRectangle(1.0,2.0,1.0,2.0),
						new LatLonRectangle(1.0,2.0,1.0,3.0)));

		checkBordersInside(
				6, 1, 1, 6,
				8, 3, 4, 8,
				List.of(
						new LatLonRectangle(6.0,3.0,4.0,3.0),
						new LatLonRectangle(4.0,3.0,4.0,6.0)));

		checkBordersInside(
				6, 1, 1, 6,
				6, 1, 1, 6,
				List.of(
						new LatLonRectangle(6.0,1.0,6.0,6.0),
						new LatLonRectangle(6.0,1.0,1.0,1.0),
						new LatLonRectangle(1.0,1.0,1.0,6.0),
						new LatLonRectangle(6.0,6.0,1.0,6.0)));

		checkBordersInside(
				48.34164617237459, 14.2822265625, 48.32703913063477, 14.30419921875,
				48.34164617237459, 14.2822265625, 48.312427904071775, 14.326171875,
				List.of(
						new LatLonRectangle(48.34164617237459,14.2822265625,48.34164617237459,14.30419921875),
						new LatLonRectangle(48.34164617237459,14.2822265625,48.32703913063477,14.2822265625)));
	}

	@SuppressForbidden(reason = "Uses System.out on purpose")
	private void checkBordersInside(
			double latA1, double lonA1, double latA2, double lonA2, double latB1, double lonB1, double latB2, double lonB2,
			List<LatLonRectangle> expected) {
		LatLonRectangle rect1 = new LatLonRectangle(latA1, lonA1, latA2, lonA2);
		LatLonRectangle rect2 = new LatLonRectangle(latB1, lonB1, latB2, lonB2);

		try {
			List<LatLonRectangle> bordersInside = rect1.borderInside(rect2);
			System.out.println(bordersInside.size());
			for (LatLonRectangle segment : bordersInside) {
				System.out.println("new LatLonRectangle(" + segment.lat1 + "," + segment.lon1 + "," +
						segment.lat2 + "," + segment.lon2 + "),");
			}

			assertEquals(expected.toString().replace("}, ", "\n"), bordersInside.toString().replace("}, ", "\n"));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Failed for \n" + rect1 + "\n" + rect2, e);
		}
	}

	@Test
	public void testToGeoJSONArray() {
		LatLonRectangle rect = new LatLonRectangle(3, 2, 1,4);
		assertEquals(
				"[2.00000, 3.00000],\n" +
				"[4.00000, 3.00000],\n" +
				"[4.00000, 1.00000],\n" +
				"[2.00000, 1.00000],\n" +
				"[2.00000, 3.00000]",
				rect.toGeoJSONArray());
	}

	@SuppressWarnings("deprecation")
	@Test
	void testRandom() {
		MappedCounter<Integer> hashes = new MappedCounterImpl<>();
		for (int i = 0; i < 100_000; i++) {
			double lat1 = RandomUtils.nextDouble(0, (-1) * MIN_LATITUDE + MAX_LATITUDE);
			double lon1 = RandomUtils.nextDouble(0, 2 * 180);
			double lat2 = RandomUtils.nextDouble(0, (-1) * MIN_LATITUDE + MAX_LATITUDE);
			double lon2 = RandomUtils.nextDouble(0, 2 * 180);
			lat1 += MIN_LATITUDE;
			lon1 -= 180;
			lat2 += MIN_LATITUDE;
			lon2 -= 180;

			LatLonRectangle rect = new LatLonRectangle(
					Math.max(lat1, lat2),
					Math.min(lon1, lon2),
					Math.min(lat1, lat2),
					Math.max(lon1, lon2)
			);

			hashes.inc(rect.hashCode());
		}

		Optional<Map.Entry<Integer, Long>> max = hashes.sortedMap().
				entrySet().
				stream().
				max(Map.Entry.comparingByValue());
		assertTrue(max.orElseThrow().getValue() <= 3,
				"Did not expect many equal hash-values in random rectangles, but failed with " + max);
	}

	@Test
	void testInvalid() {
		assertThrows(IllegalArgumentException.class,
				() -> new LatLonRectangle(1, 1, 2, 1));
		assertThrows(IllegalArgumentException.class,
				() -> new LatLonRectangle(1, 2, 1, 1));
	}
}
