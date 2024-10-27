package org.dstadler.ctw.utils;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_NEW_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;
import static org.dstadler.ctw.utils.Constants.SQUARE_SIZE;
import static org.dstadler.ctw.utils.OSMTileTest.ASSERT_DELTA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.commons.lang3.RandomUtils;
import org.dstadler.commons.testing.TestHelpers;
import org.dstadler.commons.util.SuppressForbidden;
import org.junit.jupiter.api.Test;

import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.NotDefinedOnUTMGridException;
import uk.me.jstott.jcoord.UTMRef;

public class UTMRefWithHashTest {
    private static final Pattern UTMREF_SQUARE_PATTERN = Pattern.compile("\\d+[C-HJ-NP-X] \\d+000.0 (?:\\d+00)?0.0");

    private final static int MIN_LATITUDE = -80;
    private final static int MAX_LATITUDE = 84;

	// getting closer to 180 causes failures due to rounding errors lead to values > 180
    private final static double MIN_LONGITUDE = -179.9;
    private final static double MAX_LONGITUDE = 179.9;

    @Test
    void test() {
        UTMRefWithHash ref = new UTMRefWithHash(1, 'U', 23423.23, 5234.233);
        assertEquals(1, ref.getLngZone());
        assertEquals('U', ref.getLatZone());
        assertEquals(23423.23, ref.getEasting(), ASSERT_DELTA);
        assertEquals(5234.233, ref.getNorthing(), ASSERT_DELTA);
    }

    @Test
    void testHashAndEquals() {
        UTMRefWithHash ref1 = new UTMRefWithHash(1, 'U', 23423.23, 5234.233);
        UTMRefWithHash ref2 = new UTMRefWithHash(1, 'U', 23423.23, 5234.233);

        TestHelpers.HashCodeTest(ref1, ref2);

        UTMRefWithHash notEqual = new UTMRefWithHash(1, 'U', 23423.23, 5234.232);
        TestHelpers.EqualsTest(ref1, ref2, notEqual);

        notEqual = new UTMRefWithHash(1, 'U', 23424.23, 5234.233);
        TestHelpers.EqualsTest(ref1, ref2, notEqual);

        notEqual = new UTMRefWithHash(1, 'T', 23423.23, 5234.233);
        TestHelpers.EqualsTest(ref1, ref2, notEqual);

        notEqual = new UTMRefWithHash(2, 'U', 23423.23, 5234.233);
        TestHelpers.EqualsTest(ref1, ref2, notEqual);
    }

    @Test
    void testGetSquareString() {
        String strRef = UTMRefWithHash.getSquareString(new LatLng(23.43, 54.23));
        UTMRefWithHash ref = UTMRefWithHash.fromString(strRef);

        assertEquals(40, ref.getLngZone(),
				"Had: " + ref);
        assertEquals('Q', ref.getLatZone(),
				"Had: " + ref);
        assertEquals(216000.0, ref.getEasting(), ASSERT_DELTA,
				"Had: " + ref);
        assertEquals(2593000.0, ref.getNorthing(), ASSERT_DELTA,
				"Had: " + ref);

        // ensure that we get values at km-boundary
        // https://en.wikipedia.org/wiki/Universal_Transverse_Mercator_coordinate_system#Latitude_bands
        assertTrue(UTMREF_SQUARE_PATTERN.matcher(ref.toString()).matches(),
				"Failed for " + ref);
    }

	@Test
	void testSquareStringNormalize() {
		// special case: we would get back different "zone" "T" and "U" for those two coordinates
		// if we do not normalize the zone as well in getSquareString()
		String ref1 = UTMRefWithHash.getSquareString(new LatLng(47.999573, 15.187657));
		String ref2 = UTMRefWithHash.getSquareString(new LatLng(48.002663, 15.187625));

		// first ensure we get the expected string
		assertEquals("33T 513000.0 5316000.0", ref1);

		assertEquals(ref1, ref2,
				"Expecting to get the same square for the two coordinates, but had " + ref1 + " and " + ref2);
	}

    @Test
    void testFromString() {
		checkFromString("33U 428000.0 5366000.0", 33, 'U', 428000.0, 5366000.0);
		checkFromString("33U 428001 5366001", 33, 'U', 428001.0, 5366001.0);
		checkFromString("1C 428001 5366001", 1, 'C', 428001.0, 5366001.0);
	}

	private static void checkFromString(String str, int lngZone, char latZone, double easting, double northing) {
		UTMRefWithHash ref = UTMRefWithHash.fromString(str);
		assertEquals(lngZone, ref.getLngZone());
		assertEquals(latZone, ref.getLatZone());
		assertEquals(easting, ref.getEasting(), ASSERT_DELTA);
		assertEquals(northing, ref.getNorthing(), ASSERT_DELTA);
	}

	@Test
	void testFromStringOutOfBounds() {
		assertNotNull(UTMRefWithHash.fromString("33U 428000.0 5366000.0"));

		assertThrows(IllegalArgumentException.class,
				() -> UTMRefWithHash.fromString("99U 428000.0 5366000.0"));
		assertThrows(IllegalArgumentException.class,
				() -> UTMRefWithHash.fromString("33U 123428000.0 5366000.0"));
		assertThrows(IllegalArgumentException.class,
				() -> UTMRefWithHash.fromString("33U 428000.0 23445366000.0"));
	}

	@Test
    void testGetRectangle() {
        UTMRefWithHash ref = UTMRefWithHash.fromString("33U 428000.0 5366000.0");
		LatLonRectangle rec = ref.getRectangle();
		assertEquals(48.45201761091308, rec.lat1, ASSERT_DELTA,
				"Had: " + rec);
		assertEquals(14.026424192067552, rec.lon1, ASSERT_DELTA,
				"Had: " + rec);
		assertEquals(48.44302242806286, rec.lat2, ASSERT_DELTA,
				"Had: " + rec);
		assertEquals(14.039944088630683, rec.lon2, ASSERT_DELTA,
				"Had: " + rec);
	}

    @Test
    void testFromString2() {
        UTMRefWithHash ref1 = UTMRefWithHash.fromString("33U 425000.0 5366000.0");
        assertEquals(33, ref1.getLngZone());
        assertEquals('U', ref1.getLatZone());
        assertEquals(425000.0, ref1.getEasting(), ASSERT_DELTA);
        assertEquals(5366000.0, ref1.getNorthing(), ASSERT_DELTA);

		UTMRefWithHash ref2 = new UTMRefWithHash(ref1.getLngZone(), ref1.getLatZone(),
				ref1.getEasting() + SQUARE_SIZE, ref1.getNorthing() + SQUARE_SIZE);
		assertEquals(33, ref2.getLngZone());
		assertEquals('U', ref2.getLatZone());
		assertEquals(426000.0, ref2.getEasting(), ASSERT_DELTA);
		assertEquals(5367000.0, ref2.getNorthing(), ASSERT_DELTA);
    }

    @Test
    void testFromStringInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> UTMRefWithHash.fromString("33I 428000.0 5366000.0"));
        assertThrows(IllegalArgumentException.class,
                () -> UTMRefWithHash.fromString("33O 428000.0 5366000.0"));
        assertThrows(IllegalArgumentException.class,
                () -> UTMRefWithHash.fromString("33B 428000.0 5366000.0"));
        assertThrows(IllegalArgumentException.class,
                () -> UTMRefWithHash.fromString("33Y 428000.0 5366000.0"));
        assertThrows(IllegalArgumentException.class,
                () -> UTMRefWithHash.fromString("3393I 428000.0 5366000.0"));
        assertThrows(IllegalArgumentException.class,
                () -> UTMRefWithHash.fromString("33U 4as28000.0 5366000.0"));
        assertThrows(IllegalArgumentException.class,
                () -> UTMRefWithHash.fromString("33U 428000.0 536sd6000.0"));
        assertThrows(IllegalArgumentException.class,
                () -> UTMRefWithHash.fromString("33U 428000.0 5366000.0 5366000.0"));

		assertThrows(IllegalArgumentException.class,
				() -> UTMRefWithHash.fromString("182U 428000.0 5366000.0"));

		assertThrows(NotDefinedOnUTMGridException.class,
				() -> new UTMRefWithHash(99, '_', -1, -1));
    }

	@Test
	void testDirections() {
		UTMRefWithHash ref = UTMRefWithHash.fromString("33U 428000.0 5366000.0");

		check(ref.up(),    33, 'U', 428000.0, 5367000.0);
		check(ref.down(),  33, 'U', 428000.0, 5365000.0);
		check(ref.right(), 33, 'U', 429000.0, 5366000.0);
		check(ref.left(),  33, 'U', 427000.0, 5366000.0);

		ref = UTMRefWithHash.fromString("17J 596450 6680793");

		check(ref.up(),    17, 'J', 596450.0, 6681793.0);
		check(ref.down(),  17, 'J', 596450.0, 6679793.0);
		check(ref.right(), 17, 'J', 597450.0, 6680793.0);
		check(ref.left(),  17, 'J', 595450.0, 6680793.0);
	}

	private void check(UTMRefWithHash ref, int lngZone, char latZone, double easting, double northing) {
		assertEquals(lngZone, ref.getLngZone());
		assertEquals(latZone, ref.getLatZone());
		assertEquals(easting, ref.getEasting(), ASSERT_DELTA);
		assertEquals(northing, ref.getNorthing(), ASSERT_DELTA);
	}

	@SuppressForbidden(reason = "Uses System.out on purpose")
	@Test
	void testPrintUTMRefs() {
		UTMRef ref = UTMRefWithHash.fromString("32U 0.0 0.0");
		System.out.printf("Ref: %40s: %s: %s%n", ref, ref.toLatLng(), ref.toLatLng().toUTMRef());

		ref = new LatLng(0, 0).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		System.out.println();

		ref = new LatLng(-79, -179).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		ref = new LatLng(-78, -169).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		ref = new LatLng(-50, -100).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		ref = new LatLng(-40, -90).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		ref = new LatLng(-30, -80).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		ref = new LatLng(-0.1, -0.1).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		System.out.println();

		ref = new LatLng(84, 179).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		ref = new LatLng(83, 169).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		ref = new LatLng(50, 100).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		ref = new LatLng(40, 90).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		ref = new LatLng(30, 80).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		ref = new LatLng(0.1, 0.1).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());

		System.out.println();

		ref = new LatLng(53.39562, -9.9558).toUTMRef();
		System.out.printf("Ref: %40s: %s%n", ref, ref.toLatLng());
	}

    @SuppressWarnings("deprecation")
    @Test
    void testRandom() {
        for (int i = 0; i < 100_000; i++) {
            double lat = RandomUtils.nextDouble(0, (-1)*MIN_LATITUDE + MAX_LATITUDE);
            double lon = RandomUtils.nextDouble(0, (-1)*MIN_LONGITUDE + MAX_LONGITUDE);
            lat += MIN_LATITUDE;
            lon += MIN_LONGITUDE;
            String ref = UTMRefWithHash.getSquareString(new LatLng(lat, lon));

            assertTrue(UTMREF_SQUARE_PATTERN.matcher(ref).matches(),
					"Failed for lat " + lat + ", lon " + lon + ": " + ref);

			UTMRefWithHash ref1 = UTMRefWithHash.fromString(ref);
			assertNotNull(ref1);
			LatLonRectangle rec = ref1.getRectangle();
			assertNotNull(rec);

			TestHelpers.ToStringTest(ref1);
		}
    }

	@Test
	void testConvertBackAndForth() {
		// Kansas USA (Consistent):
		checkConvertBackAndForth(39.964463, -99.820180);
		// Rio De Janeiro Brazil
		checkConvertBackAndForth(-22.938733, -43.503667);
		// Leipzig Germany
		checkConvertBackAndForth(51.349262, 12.365591);
		// Kassel Germany
		checkConvertBackAndForth(51.310206, 9.536331);
	}

	private void checkConvertBackAndForth(double lat, double lon) {
		LatLng ll = new LatLng(lat, lon);
		UTMRef ref = ll.toUTMRef();

		UTMRef refBack = UTMRefWithHash.fromString(ref.toString());

		assertEquals(ref.getLngZone(), refBack.getLngZone(),
				"Having " + ref + " and " + refBack);
		assertEquals(ref.getLatZone(), refBack.getLatZone(),
				"Having " + ref + " and " + refBack);
		assertEquals(ref.getEasting(), refBack.getEasting(), ASSERT_DELTA,
				"Having " + ref + " and " + refBack);
		assertEquals(ref.getNorthing(), refBack.getNorthing(), ASSERT_DELTA,
				"Having " + ref + " and " + refBack);
	}

	@Test
	void testReadSquares() throws IOException {
		assertThrows(NoSuchFileException.class,
				() -> UTMRefWithHash.readSquares(new File("not exist")));

		Set<UTMRefWithHash> squares = UTMRefWithHash.readSquares(new File(VISITED_SQUARES_TXT));
		assertNotNull(squares);
		assertTrue(squares.size() > 15, "Had: " + squares.size());

		squares = UTMRefWithHash.readSquares(new File(VISITED_SQUARES_NEW_TXT));
		assertNotNull(squares);
		//noinspection ConstantValue
		assertTrue(squares.size() >= 0, "Had: " + squares.size());
	}

	@Test
	public void string() {
		UTMRefWithHash ref = new UTMRefWithHash(1, 'U', 23423.23, 5234.233);

		TestHelpers.ToStringTest(ref);

		assertEquals(ref.toString(), ref.string());
	}

	@Test
	void testComparator() {
		UTMRefWithHash ref1 = new UTMRefWithHash(1, 'U', 23423.23, 5234.233);
		UTMRefWithHash ref2 = new UTMRefWithHash(1, 'U', 23423.23, 5234.233);


		UTMRefWithHash notEqual = new UTMRefWithHash(1, 'U', 23423.23, 5234.232);
		TestHelpers.CompareToTest(ref1, ref2, notEqual, true);

		notEqual = new UTMRefWithHash(1, 'U', 23424.23, 5234.233);
		TestHelpers.CompareToTest(ref1, ref2, notEqual, false);

		notEqual = new UTMRefWithHash(1, 'T', 23423.23, 5234.233);
		TestHelpers.CompareToTest(ref1, ref2, notEqual, true);

		notEqual = new UTMRefWithHash(2, 'U', 23423.23, 5234.233);
		TestHelpers.CompareToTest(ref1, ref2, notEqual, false);

		Set<UTMRefWithHash> set = new TreeSet<>();
		set.add(ref1);
		set.add(ref2);
		set.add(notEqual);

		Iterator<UTMRefWithHash> it = set.iterator();
		assertEquals(ref1, it.next());
		assertEquals(notEqual, it.next());
		assertFalse(it.hasNext());
	}

	@Test
	void testUpDownAtLatZone() {
		UTMRefWithHash ref1 = UTMRefWithHash.fromString("33T 441000.0 5316000.0");
		UTMRefWithHash ref2 = UTMRefWithHash.fromString("33U 441000.0 5317000.0");

		assertEquals(ref1.up().toString(), ref2.toString());
		assertEquals(ref2.down().toString(), ref1.toString());
	}

	@Test
	void testDoubleFormat() {
		String refStr = "33T 100000.0 10000000.0";
		UTMRefWithHash ref = UTMRefWithHash.fromString(refStr);
		assertNotNull(ref);
		assertEquals(33, ref.getLngZone());
		assertEquals('T', ref.getLatZone());
		assertEquals(100000.0, ref.getEasting());
		assertEquals(10000000.0, ref.getNorthing());

		assertEquals(refStr, ref.string());

		/* This fails due to invalid latitude
		assertEquals(refStr, UTMRefWithHash.getSquareString(ref.toLatLng()));*/
	}
}
