package org.dstadler.ctw.utils;

import static org.dstadler.ctw.utils.Constants.SQUARE_SIZE;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

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
public class UTMRefWithHash extends UTMRef implements BaseTile<UTMRefWithHash>, Comparable<UTMRefWithHash> {
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
			throw new IllegalArgumentException("For input: " + ref, e);
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
		String square = getSquareStringInternal(latLng);

		final LatLng latLngSquare;
		try {
			latLngSquare = UTMRefWithHash.fromString(square).toLatLng();
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Failed for " + latLng, e);
		}

		// don't try to normalize again if lat would be out of range
		if (latLngSquare.getLatitude() > 80 || latLngSquare.getLatitude() < -80) {
			return square;
		}

		// there may be squares which span two "zones", e.g. "U" and "T" around Scheibbs in Lower Austria
		// We need to ensure that we normalize "zone" as well
		// for now we simply convert the square-ref back and forth once to get
		// this can probably be optimized if we find it too costly at some point
		return getSquareStringInternal(latLngSquare);
	}

	private static String getSquareStringInternal(LatLng latLng) {
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
	 * creating a second UTMRef by adding 1000 (SQUARE_SIZE) meters to
	 * both northing and easting.
	 *
	 * @return A LatLonRectangle based on northing and easting
	 * 		of this UTMRef
	 */
	public LatLonRectangle getRectangle() {
		// use separate refs for easting/northing to not cause gaps
		// caused by non-matching longitude-values
		UTMRefWithHash ref2East = new UTMRefWithHash(getLngZone(), getLatZone(),
				getEasting() + SQUARE_SIZE, getNorthing());
		UTMRefWithHash ref2North = new UTMRefWithHash(getLngZone(), getLatZone(),
				getEasting(), getNorthing() + SQUARE_SIZE);

		LatLng latLng1 = toLatLng();
		LatLng latLng2 = new LatLng(ref2North.toLatLng().getLatitude(), ref2East.toLatLng().getLongitude());

		return new LatLonRectangle(
				latLng2.getLatitude(), latLng1.getLongitude(),
				latLng1.getLatitude(), latLng2.getLongitude());
	}

	private static double normalize(double bearing) {
		// reduce accuracy to 1000 (SQUARE_SIZE) meters
		// use round to avoid strange effects with double being
		// slightly off in some cases
		//noinspection IntegerDivisionInFloatingPointContext
		return (Math.round(bearing) / SQUARE_SIZE) * SQUARE_SIZE;
	}

	private UTMRefWithHash fixupZone() {
		return new UTMRefWithHash(getLngZone(), UTMRef.getUTMLatitudeZoneLetter(toLatLng().getLatitude()), getEasting(), getNorthing());
	}

	public UTMRefWithHash up() {
		return new UTMRefWithHash(getLngZone(), getLatZone(), getEasting(), getNorthing() + SQUARE_SIZE).
				// LatZone can change, so we may need a fixup here
				fixupZone();
	}

	public UTMRefWithHash down() {
		return new UTMRefWithHash(getLngZone(), getLatZone(), getEasting(), getNorthing() - SQUARE_SIZE).
				// LatZone can change, so we may need a fixup here
				fixupZone();
	}

	public UTMRefWithHash right() {
		return new UTMRefWithHash(getLngZone(), getLatZone(), getEasting() + SQUARE_SIZE, getNorthing());
	}

	public UTMRefWithHash left() {
		return new UTMRefWithHash(getLngZone(), getLatZone(), getEasting() - SQUARE_SIZE, getNorthing());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getLatZone(), getLngZone(), getEasting(), getNorthing());
	}

	@Override
	public int compareTo(@Nullable UTMRefWithHash o) {
		if (o == null) {
			return 1;
		}

		return string().compareTo(o.string());
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

	public String toString() {
		// work around a bug in underlying UTMRef when Double.toString()
		// starts to use exponential format, e.g. for "33T 100000.0 10000000.0"
		return String.format(Locale.ROOT, "%d%s %f %f",
				this.getLngZone(), this.getLatZone(), this.getEasting(), this.getNorthing()).
				// cut away trailing zeros in the decimal part, couuld not find how to do this
				// with String.format() itself
				replaceAll("\\.(\\d)0+", ".$1");
	}

	@Override
	public String string() {
		return toString();
	}
}
