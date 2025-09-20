package org.dstadler.ctw.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.google.common.base.Preconditions;

/**
 * Simple rectangle for LatLon which handles the
 * rectangle being "downwards" properly.
 *
 * It allows to compute the "intersection" of two
 * rectangles, i.e. the area where two rectangles
 * overlap.
 */
public class LatLonRectangle {

	// (lat1,lon1) is upper-left, (lat2,lon2) is lower-right
	public double lat1, lon1, lat2, lon2;

	/**
	 * Construct a rectangle
	 *
	 * @param lat1 up
	 * @param lon1 left
	 * @param lat2 down
	 * @param lon2 right
	 */
	public LatLonRectangle(double lat1, double lon1, double lat2, double lon2) {
		Preconditions.checkArgument(lat1 >= lat2,
				"Should have a normalized Rectangle, but had latitudes %s and %s", lat1, lat2);
		Preconditions.checkArgument(lon2 >= lon1,
				"Should have a normalized Rectangle, but had longitudes %s and %s", lon1, lon2);

		this.lat1 = lat1;
		this.lon1 = lon1;
		this.lat2 = lat2;
		this.lon2 = lon2;
	}

	public LatLonRectangle intersect(LatLonRectangle r2) {
		// inspired by https://stackoverflow.com/a/19571902/411846
		// but adjusted to how lat/lon are stored in the rect here
		double lonMin = Math.max(lon1, r2.lon1);
		double lonMax = Math.min(lon2, r2.lon2);
		if (lonMax >= lonMin) {
			// rectangles are downwards
			double latMin = Math.max(lat2, r2.lat2);
			double latMax = Math.min(lat1, r2.lat1);
			if (latMax >= latMin) {
				return new LatLonRectangle(latMax, lonMin, latMin, lonMax);
			}
		}

		// no intersection found
		return null;
	}

	public List<LatLonRectangle> borderInside(LatLonRectangle other) {
		if (intersect(other) == null) {
			return Collections.emptyList();
		}

		double startLon = Math.max(lon1, other.lon1);
		double startLat = Math.min(lat1, other.lat1);
		double endLon = Math.min(lon2, other.lon2);
		double endLat = Math.max(lat2, other.lat2);

		var borders = new ArrayList<LatLonRectangle>();

		// check "inside"? for each of the 4 sides
		if (other.lat1 <= lat1) {
			// line "up"
			borders.add(new LatLonRectangle(other.lat1, startLon, other.lat1, endLon));
		}
		if (other.lon1 >= lon1) {
			// line "left"
			borders.add(new LatLonRectangle(startLat, other.lon1, endLat, other.lon1));
		}
		if (other.lat2 >= lat2) {
			// line "down"
			borders.add(new LatLonRectangle(other.lat2, startLon, other.lat2, endLon));
		}
		if (other.lon2 <= lon2) {
			// line "right"
			borders.add(new LatLonRectangle(startLat, other.lon2, endLat, other.lon2));
		}

		return borders;
	}

	public double width() {
		return lon2 - lon1;
	}

	public double height() {
		return lat1 - lat2;
	}

	@Override
	public String toString() {
		return "LatLonRectangle{" +
				"lat1=" + lat1 +
				", lon1=" + lon1 +
				", lat2=" + lat2 +
				", lon2=" + lon2 +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		LatLonRectangle that = (LatLonRectangle) o;

		if (Double.compare(lat1, that.lat1) != 0) {
			return false;
		}
		if (Double.compare(lon1, that.lon1) != 0) {
			return false;
		}
		if (Double.compare(lat2, that.lat2) != 0) {
			return false;
		}
		return Double.compare(lon2, that.lon2) == 0;
	}

	@Override
	public int hashCode() {
		int result = Double.hashCode(lat1);
		result = 31 * result + Double.hashCode(lon1);
		result = 31 * result + Double.hashCode(lat2);
		result = 31 * result + Double.hashCode(lon2);
		return result;
	}

	public String toGeoJSONArray() {
		/*
                [13.77686, 48.61839],
                [14.32617, 48.61839],
                [14.32617, 48.25394],
                [13.77686, 48.25394],
                [13.77686, 48.61839]
		 */
		return String.format(Locale.ROOT,
				"[%.5f, %.5f],\n" +
				"[%.5f, %.5f],\n" +
				"[%.5f, %.5f],\n" +
				"[%.5f, %.5f],\n" +
				"[%.5f, %.5f]",
				lon1, lat1,
				lon2, lat1,
				lon2, lat2,
				lon1, lat2,
				lon1, lat1);
	}
}
