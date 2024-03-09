package org.dstadler.ctw.utils;

/**
 * Some constants used when processing GPX tracks
 */
public class Constants {
	// The OSM zoom to use for tiles
	public static final int TILE_ZOOM = 14;

	// Defines which minimum zoom is used when creating
	// overlay PNG files in the tiles*-directories
	public static final int MIN_ZOOM = 0;

	// Defines which maximum zoom is used when creating
	// overlay PNG files in the tiles*-directories
	public static final int MAX_ZOOM = 18;

	// for now only calculate for one UTM-zone as otherwise computing
	// "easting" would need to take the zone into account
	// this would make computing largest square and rectangle rather complex
	// You can adjust this to another UTM-zone to match your
	// main area of squares
	// See also https://en.wikipedia.org/wiki/Universal_Transverse_Mercator_coordinate_system
	public static final int ZONE = 33;
}
