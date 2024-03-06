package org.dstadler.ctw;

import static org.dstadler.ctw.CreateListOfVisitedSquares.VISITED_SQUARES_NEW_TXT;
import static org.dstadler.ctw.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;
import static org.dstadler.ctw.OSMTileTest.ASSERT_DELTA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.RandomUtils;
import org.dstadler.commons.testing.TestHelpers;
import org.dstadler.commons.util.SuppressForbidden;
import org.junit.Test;

import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

public class UTMRefWithHashTest {
    private static final Pattern UTMREF_SQUARE_PATTERN = Pattern.compile("\\d+[C-HJ-NP-X] \\d+000.0 (?:\\d+00)?0.0");

    private final static int MIN_LATITUDE = -80;
    private final static int MAX_LATITUDE = 84;

	// getting closer to 180 causes failurs due to rounding errors lead to values > 180
    private final static double MIN_LONGITUDE = -179.9;
    private final static double MAX_LONGITUDE = 179.9;

    @Test
    public void test() {
        UTMRefWithHash ref = new UTMRefWithHash(1, 'U', 23423.23, 5234.233);
        assertEquals(1, ref.getLngZone());
        assertEquals('U', ref.getLatZone());
        assertEquals(23423.23, ref.getEasting(), ASSERT_DELTA);
        assertEquals(5234.233, ref.getNorthing(), ASSERT_DELTA);
    }

    @Test
    public void testHashAndEquals() {
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
    public void testGetSquareString() {
        String strRef = UTMRefWithHash.getSquareString(new LatLng(23.43, 54.23));
        UTMRefWithHash ref = UTMRefWithHash.fromString(strRef);

        assertEquals("Had: " + ref,
                40, ref.getLngZone());
        assertEquals("Had: " + ref,
                'Q', ref.getLatZone());
        assertEquals("Had: " + ref,
                216000.0, ref.getEasting(), ASSERT_DELTA);
        assertEquals("Had: " + ref,
                2593000.0, ref.getNorthing(), ASSERT_DELTA);

        // ensure that we get values at km-boundary
        // https://en.wikipedia.org/wiki/Universal_Transverse_Mercator_coordinate_system#Latitude_bands
        assertTrue(UTMREF_SQUARE_PATTERN.matcher(ref.toString()).matches());
    }

    @Test
    public void testFromString() {
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
    public void testGetRectangle() {
        UTMRefWithHash ref = UTMRefWithHash.fromString("33U 428000.0 5366000.0");
		LatLonRectangle rec = ref.getRectangle();
		assertEquals("Had: " + rec,
				48.45201761091308, rec.lat1, ASSERT_DELTA);
		assertEquals("Had: " + rec,
				14.026424192067552, rec.lon1, ASSERT_DELTA);
		assertEquals("Had: " + rec,
				48.44302242806286, rec.lat2, ASSERT_DELTA);
		assertEquals("Had: " + rec,
				14.039944088630683, rec.lon2, ASSERT_DELTA);
	}

    @Test
    public void testFromString2() {
        UTMRefWithHash ref1 = UTMRefWithHash.fromString("33U 425000.0 5366000.0");
        assertEquals(33, ref1.getLngZone());
        assertEquals('U', ref1.getLatZone());
        assertEquals(425000.0, ref1.getEasting(), ASSERT_DELTA);
        assertEquals(5366000.0, ref1.getNorthing(), ASSERT_DELTA);

		UTMRefWithHash ref2 = new UTMRefWithHash(ref1.getLngZone(), ref1.getLatZone(),
				ref1.getEasting() + 1000, ref1.getNorthing() + 1000);
		assertEquals(33, ref2.getLngZone());
		assertEquals('U', ref2.getLatZone());
		assertEquals(426000.0, ref2.getEasting(), ASSERT_DELTA);
		assertEquals(5367000.0, ref2.getNorthing(), ASSERT_DELTA);
    }

    @Test
    public void testFromStringInvalid() {
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
    }

	@Test
	public void testDirections() {
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
	public void testPrintUTMRefs() {
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
    public void testRandom() {
        for (int i = 0; i < 100_000; i++) {
            double lat = RandomUtils.nextDouble(0, (-1)*MIN_LATITUDE + MAX_LATITUDE);
            double lon = RandomUtils.nextDouble(0, (-1)*MIN_LONGITUDE + MAX_LONGITUDE);
            lat += MIN_LATITUDE;
            lon += MIN_LONGITUDE;
            String ref = UTMRefWithHash.getSquareString(new LatLng(lat, lon));

            assertTrue("Failed for lat " + lat + ", lon " + lon + ": " + ref,
                    UTMREF_SQUARE_PATTERN.matcher(ref).matches());

			UTMRefWithHash ref1 = UTMRefWithHash.fromString(ref);
			assertNotNull(ref1);
			LatLonRectangle rec = ref1.getRectangle();
			assertNotNull(rec);
		}
    }

	@Test
	public void testConvertBackAndForth() {
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

		assertEquals("Having " + ref + " and " + refBack,
				ref.getLngZone(), refBack.getLngZone());
		assertEquals("Having " + ref + " and " + refBack,
				ref.getLatZone(), refBack.getLatZone());
		assertEquals("Having " + ref + " and " + refBack,
				ref.getEasting(), refBack.getEasting(), ASSERT_DELTA);
		assertEquals("Having " + ref + " and " + refBack,
				ref.getNorthing(), refBack.getNorthing(), ASSERT_DELTA);
	}

	@Test
	public void testReadSquares() throws IOException {
		assertThrows(NoSuchFileException.class,
				() -> UTMRefWithHash.readSquares(new File("notexist")));

		Set<UTMRefWithHash> squares = UTMRefWithHash.readSquares(new File(VISITED_SQUARES_TXT));
		assertNotNull(squares);
		assertTrue(squares.size() > 3900);

		squares = UTMRefWithHash.readSquares(new File(VISITED_SQUARES_NEW_TXT));
		assertNotNull(squares);
		assertTrue(squares.size() > 0);
	}
}