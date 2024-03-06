package org.dstadler.ctw;

import static org.dstadler.ctw.OSMTileTest.ASSERT_DELTA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.dstadler.commons.testing.TestHelpers;
import org.junit.Test;

import uk.me.jstott.jcoord.LatLng;

public class LatLonRectangleTest {
	@Test
	public void testIntersectItself() {
		checkIntersect(
				48.458, 14.150, 48.400, 14.238,
				48.458, 14.150, 48.400, 14.238,
				48.458, 14.150, 48.400, 14.238);
	}

	@Test
	public void testIntersect() {
		checkIntersect(
				48.458, 14.150, 48.400, 14.238,
				48.500, 14.200, 48.450, 14.338,
				48.458, 14.200, 48.450, 14.238);
	}

	@Test
	public void testIntersect2() {
		checkIntersect(
				48.458, 14.150, 48.400, 14.238,
				48.450, 14.200, 48.300, 14.338,
				48.450, 14.200, 48.400, 14.238);
	}

	@Test
	public void testIntersect3() {
		checkIntersect(
				48.458, 14.150, 48.400, 14.238,
				48.450, 14.200, 48.300, 14.230,
				48.450, 14.200, 48.400, 14.230);
	}

	@Test
	public void testIntersectNegative() {
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
	public void testIntersectEmpty() {
		checkIntersectEmpty(
				48.458, 14.150, 48.400, 14.238,
				48.500, 14.239, 48.450, 14.338
		);
	}

	@Test
	public void test() {
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
		assertNotNull("Did not have an intersection but expected to get "
						+ "(" + lat1 + "," + lon1 + "), (" + lat2 + "," + lon2 + ")",
				ret);

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
		assertNull("Had: " + ret,
				ret);

		ret = rect2.intersect(rect1);
		assertNull("Had: " + ret,
				ret);
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

		assertTrue(ret.width() > 0);
		assertTrue(ret.height() > 0);

		ret = rect2.intersect(rect1);

		checkRect(ret,
				retLat1, retLon1,
				retLat2, retLon2);

		assertTrue(ret.width() > 0);
		assertTrue(ret.height() > 0);
	}

	@Test
	public void testToString() {
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
	public void testLatLng() {
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
}
