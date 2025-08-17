package org.dstadler.ctw.tiles;

import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.GPX_DIR;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_NEW_TXT;
import static org.dstadler.ctw.utils.Constants.SQUARE_SIZE;
import static org.dstadler.ctw.utils.OSMTileTest.ASSERT_DELTA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.dstadler.commons.gpx.GPXTrackpointsParser;
import org.dstadler.commons.gpx.TrackPoint;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.util.SuppressForbidden;
import org.dstadler.ctw.utils.LatLonRectangle;
import org.dstadler.ctw.utils.OSMTile;
import org.dstadler.ctw.utils.UTMRefWithHash;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

public class CreateTileOverlaysFromUTMRefTest {
	private static final Logger log = LoggerFactory.make();

	@BeforeAll
	public static void beforeClass() throws IOException {
		LoggerFactory.initLogging();
	}

	@Test
	void testComputeSquare() {
		LatLonRectangle recSquare1 = createSquare("33U 425000.0 5366000.0");
		LatLonRectangle recSquare2 = createSquare("33U 426000.0 5366000.0");

		assertEquals(recSquare1.lon2, recSquare2.lon1, ASSERT_DELTA);
	}

	private static LatLonRectangle createSquare(String square) {
		UTMRefWithHash ref1 = UTMRefWithHash.fromString(square);

		// use separate refs for easting/northing to not cause gaps
		// caused by non-matching longitude-values
		UTMRefWithHash ref2East = new UTMRefWithHash(ref1.getLngZone(), ref1.getLatZone(),
				ref1.getEasting() + SQUARE_SIZE, ref1.getNorthing());
		UTMRefWithHash ref2North = new UTMRefWithHash(ref1.getLngZone(), ref1.getLatZone(),
				ref1.getEasting(), ref1.getNorthing() + SQUARE_SIZE);

		LatLng latLng1 = ref1.toLatLng();
		LatLng latLng2 = new LatLng(ref2North.toLatLng().getLatitude(), ref2East.toLatLng().getLongitude());

		//System.out.println(latLng1.getLongitude() + " " + latLng2.getLongitude());

		return new LatLonRectangle(
				latLng2.getLatitude(), latLng1.getLongitude(),
				latLng1.getLatitude(), latLng2.getLongitude());
	}

	@SuppressForbidden(reason = "Uses System.out on purpose")
	@Test
	void testValues() {
		// select starting and ending tile
		UTMRefWithHash ref1 = UTMRefWithHash.fromString("33U 429000.0 5371000.0");

		// use separate refs for easting/northing to not cause gaps
		// caused by non-matching longitude-values
		UTMRefWithHash ref2East = new UTMRefWithHash(ref1.getLngZone(), ref1.getLatZone(),
				ref1.getEasting() + SQUARE_SIZE, ref1.getNorthing());
		UTMRefWithHash ref2North = new UTMRefWithHash(ref1.getLngZone(), ref1.getLatZone(),
				ref1.getEasting(), ref1.getNorthing() + SQUARE_SIZE);

		UTMRefWithHash ref3 = UTMRefWithHash.fromString("33U 430000.0 5372000.0");

		System.out.println("LatLng1 : " + ref1.toLatLng() + " - " + ref1);
		System.out.println("LatLng2e: " + ref2East.toLatLng() + " - " + ref2East);
		System.out.println("LatLng2n: " + ref2North.toLatLng() + " - " + ref2North);
		System.out.println("LatLng3 : " + ref3.toLatLng() + " - " + ref3);

		assertNotEquals(ref2East.toLatLng().getLongitude(), ref3.toLatLng().getLongitude(),
				"Would be expected, but isn't due to earth being a sphere/ellipsoid");
		assertNotEquals(ref2North.toLatLng().getLatitude(), ref3.toLatLng().getLatitude(),
				"Would be expected, but isn't due to earth being a sphere/ellipsoid");

		System.out.println();

		// use coordinates of the tile as anchor-point for computing lat/lon of the squares
		OSMTile tile = new OSMTile(15, 17661, 11322);
		LatLng latLngTile = tile.toLatLng();
		UTMRef refTile = latLngTile.toUTMRef();

		System.out.println("Tile:     " + latLngTile + " - " + latLngTile.toUTMRef() + " - " + refTile);

		System.out.println();

		// select starting and ending position rooted at tile-lat-long
		UTMRefWithHash ref1East = new UTMRefWithHash(refTile.getLngZone(), refTile.getLatZone(),
				ref1.getEasting(), refTile.getNorthing());
		UTMRefWithHash ref1North = new UTMRefWithHash(refTile.getLngZone(), refTile.getLatZone(),
				refTile.getEasting(), ref1.getNorthing());

		// use separate refs for easting/northing to not cause gaps
		// caused by non-matching longitude-values
		ref2East = new UTMRefWithHash(refTile.getLngZone(), refTile.getLatZone(),
				ref1East.getEasting() + SQUARE_SIZE, refTile.getNorthing());
		ref2North = new UTMRefWithHash(ref1.getLngZone(), ref1.getLatZone(),
				refTile.getEasting(), ref1North.getNorthing() + SQUARE_SIZE);

		UTMRefWithHash ref3East = new UTMRefWithHash(ref1.getLngZone(), ref1.getLatZone(),
				430000.0, refTile.getNorthing());
		UTMRefWithHash ref3North = new UTMRefWithHash(ref1.getLngZone(), ref1.getLatZone(),
				refTile.getEasting(), 5372000.0);

		System.out.println("LatLng1e: " + ref1East.toLatLng() + " - " + ref1East);
		System.out.println("LatLng1n: " + ref1North.toLatLng() + " - " + ref1North);
		System.out.println("LatLng2e: " + ref2East.toLatLng() + " - " + ref2East);
		System.out.println("LatLng2n: " + ref2North.toLatLng() + " - " + ref2North);
		System.out.println("LatLng3e: " + ref3East.toLatLng() + " - " + ref3East);
		System.out.println("LatLng3n: " + ref3North.toLatLng() + " - " + ref3North);

		assertEquals(ref2North.toLatLng().getLatitude(), ref3North.toLatLng().getLatitude(), ASSERT_DELTA,
				"Adjusted lat/lon should match");
		assertEquals(ref2East.toLatLng().getLongitude(), ref3East.toLatLng().getLongitude(), ASSERT_DELTA,
				"Adjusted lat/lon should match");
	}

	@Disabled("Used to find tracks for a given Tile")
	@Test
	void testFromXYZoom() throws IOException {
		OSMTile tile = new OSMTile(11, 1107, 714);

		LatLonRectangle recTile = tile.getRectangle();

		log.info("Filtering visited tiles for " + tile + " and " + tile.getRectangle());

		Set<String> matches = new HashSet<>();
		if (new File(VISITED_SQUARES_NEW_TXT).exists()) {
			Set<String> squares = CreateTileOverlaysHelper.read(VISITED_SQUARES_NEW_TXT, "squares");
			for (String square : squares) {
				LatLonRectangle latLongRect = createSquare(square);

				if (recTile.intersect(latLongRect) != null) {
					log.info("Found: " + square);
					matches.add(square);
				}
			}
		}

		log.info("Found " + matches.size() + " matches for tile " + tile);

		Set<Pair<Double, Double>> points = ConcurrentHashMap.newKeySet();
		try (Stream<Path> walk = Files.walk(GPX_DIR.toPath(), FileVisitOption.FOLLOW_LINKS)) {
			walk.
			parallel().
			forEach(path -> {
				File gpxFile = path.toFile();
				if(gpxFile.isDirectory() ||
						!gpxFile.getName().toLowerCase().endsWith(".gpx")) {
					return;
				}

				try {
					//log.info("Reading GPX trackpoints from " + gpxFile);
					final SortedMap<Long, TrackPoint> trackPoints = GPXTrackpointsParser.parseContent(gpxFile, false);

					for (TrackPoint trackPoint : trackPoints.values()) {
						if (points.add(Pair.of(trackPoint.getLatitude(), trackPoint.getLongitude()))) {
							LatLng latLng = new LatLng(trackPoint.getLatitude(), trackPoint.getLongitude());

							if(matches.contains(UTMRefWithHash.getSquareString(latLng))) {
								log.info("found track: " + gpxFile);
								break;
							}
						}
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}
}
