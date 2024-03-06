package org.dstadler.ctw.utils;

import com.google.common.base.Preconditions;

/**
 * Simple rectangle for LatLon which handles the
 * rectangle being "downwards"
 */
public class LatLonRectangle {

	// (lat1,lon1) is upper-left, (lat2,lon2) is lower-right
	double lat1, lon1, lat2, lon2;

	public LatLonRectangle(double lat1, double lon1, double lat2, double lon2) {
		Preconditions.checkArgument(lat1 > lat2,
				"Should have a normalized Rectangle, but had latitudes %s and %s", lat1, lat2);
		Preconditions.checkArgument(lon2 > lon1,
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
		if (lonMax > lonMin) {
			// rectangles are downwards
			double latMin = Math.max(lat2, r2.lat2);
			double latMax = Math.min(lat1, r2.lat1);
			if (latMax > latMin) {
				return new LatLonRectangle(latMax, lonMin, latMin, lonMax);
			}
		}

		// no intersection found
		return null;
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
}
