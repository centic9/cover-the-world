package org.dstadler.ctw.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.NotDefinedOnUTMGridException;
import uk.me.jstott.jcoord.UTMRef;

/**
 * Helper class to provide an extension to {@link uk.me.jstott.jcoord.UTMRef}
 * which can be used in HashMap/HashSet.
 *
 * It also provides support for computing the 1km-"square" into which given
 * {@link uk.me.jstott.jcoord.LatLng} coordinates fall into.
 */
public class UTMRefWithHash extends UTMRef {
	// numerical value between 1 and 60
	private static final String LNG_ZONE_PATTERN = "(\\d{1,2})";
	// Uppercase letter between C and X without I and O
	private static final String LAT_ZONE_PATTERN = "([C-HJ-NP-X])";
	// number of meters
	private static final String EASTING_NORTHING_PATTERN = "(\\d+(?:\\.\\d+)?)";

	private static final Pattern UTMREF_PATTERN = Pattern.compile(
			LNG_ZONE_PATTERN + LAT_ZONE_PATTERN + " " + EASTING_NORTHING_PATTERN + " " + EASTING_NORTHING_PATTERN);

	public UTMRefWithHash(int lngZone, char latZone, double easting, double northing) throws NotDefinedOnUTMGridException {
		super(lngZone, latZone, easting, northing);
	}

	public static UTMRefWithHash fromString(String ref) {
		Matcher matcher = UTMREF_PATTERN.matcher(ref);

		if (!matcher.matches()) {
			throw new IllegalArgumentException("Cannot parse UTM reference " + ref +
					", needs to match pattern " + UTMREF_PATTERN.pattern());
		}

		try {
			return new UTMRefWithHash(Integer.parseInt(matcher.group(1)), matcher.group(2).charAt(0),
					Double.parseDouble(matcher.group(3)), Double.parseDouble(matcher.group(4)));
		} catch (NotDefinedOnUTMGridException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Return the UTMRef string with rounding to the 1km-square.
	 * This is achieved by setting the last 3 digits of northing
	 * and easting to 0.
	 *
	 * This is then the reference that this squares was covered.
	 *
	 * @param latLng The latitude/longitude value to use
	 * @return A string in UTMRef notation where the last three
	 * 			digits of northing and easting are 0,
	 * 			e.g. 33U 428000.0 5366000.0
	 */
	public static String getSquareString(LatLng latLng) {
		UTMRef utmRef = latLng.toUTMRef();
		return "" +
				utmRef.getLngZone() +
				utmRef.getLatZone() + " " +
				normalize(utmRef.getEasting()) + " " +
				normalize(utmRef.getNorthing());
	}

	/**
	 * Return the LatLonRectangle for the current UTMRef.
	 *
	 * This is done by using this UTMRef as one corner and
	 * creating a second UTMRef by adding 1000 meters to
	 * both northing and easting.
	 *
	 * @return A LatLonRectangle based on northing and easting
	 * 		of this UTMRef
	 */
	public LatLonRectangle getRectangle() {
		// use separate refs for easting/northing to not cause gaps
		// caused by non-matching longitude-values
		UTMRefWithHash ref2East = new UTMRefWithHash(getLngZone(), getLatZone(),
				getEasting() + 1000, getNorthing());
		UTMRefWithHash ref2North = new UTMRefWithHash(getLngZone(), getLatZone(),
				getEasting(), getNorthing() + 1000);

		LatLng latLng1 = toLatLng();
		LatLng latLng2 = new LatLng(ref2North.toLatLng().getLatitude(), ref2East.toLatLng().getLongitude());

		return new LatLonRectangle(
				latLng2.getLatitude(), latLng1.getLongitude(),
				latLng1.getLatitude(), latLng2.getLongitude());
	}

	private static double normalize(double bearing) {
		// reduce accuracy to 1000 meters
		return (long)(bearing / 1000) * 1000;
	}

	public UTMRefWithHash up() {
		return new UTMRefWithHash(getLngZone(), getLatZone(), getEasting(), getNorthing() + 1000);
	}

	public UTMRefWithHash down() {
		return new UTMRefWithHash(getLngZone(), getLatZone(), getEasting(), getNorthing() - 1000);
	}

	public UTMRefWithHash right() {
		return new UTMRefWithHash(getLngZone(), getLatZone(), getEasting() + 1000, getNorthing());
	}

	public UTMRefWithHash left() {
		return new UTMRefWithHash(getLngZone(), getLatZone(), getEasting() - 1000, getNorthing());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getLatZone(), getLngZone(), getEasting(), getNorthing());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof UTMRef)) {
			return false;
		}
		UTMRef o = (UTMRef) obj;
		return getLatZone() == o.getLatZone() &&
				getLngZone() == o.getLngZone() &&
				getEasting() == o.getEasting() &&
				getNorthing() == o.getNorthing();
	}

	public static Set<UTMRefWithHash> readSquares(File file) throws IOException {
		return new TreeSet<>(FileUtils.readLines(file, StandardCharsets.UTF_8)).
						stream().
						map(UTMRefWithHash::fromString).
						collect(Collectors.toSet());
	}
}
