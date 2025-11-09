package org.dstadler.ctw.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.google.common.base.Preconditions;

import jakarta.annotation.Nullable;
import uk.me.jstott.jcoord.LatLng;

/**
 * Helper class to handle information about OSM-based map-tiles.
 *
 * It allows converting from Latitude/Longitude plus Zoom-Level
 * to the OSM tile numbers which are used for naming images in the
 * file-system to use as static resources for web-pages.
 *
 * See <a href="https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames">Slippy map tilenames</a>
 * for details and sources for fromLatLngZoom() and the other
 * conversion methods.
 */
public class OSMTile implements BaseTile<OSMTile>, Comparable<OSMTile> {
	// OSM uses zoom-levels from 0 - the whole world to 19 - very detailed
	protected static final int OSM_MIN_ZOOM = 0;
	protected static final int OSM_MAX_ZOOM = 19;

	private static final int PIXELS = 256;

	private static final Pattern STRING_COORDS = Pattern.compile("(\\d+)/(\\d+)/(\\d+)");

	private final int zoom;
	private final int xTile;
	private final int yTile;

	public OSMTile(int zoom, int xTile, int yTile) {
		// plus one for now to allow using zoom 20 in some places
		Preconditions.checkArgument(zoom >= OSM_MIN_ZOOM && zoom <= OSM_MAX_ZOOM + 1,
				"Invalid zoom %s, needs to be between %s and %s",
				zoom, OSM_MIN_ZOOM, OSM_MAX_ZOOM + 1);

		int max = zoom == 0 ? 1 : 2 << (zoom - 1);
		Preconditions.checkArgument(xTile >= 0,
				"X needs to be non-negative, but had %s", xTile);
		Preconditions.checkArgument(xTile < max,
				"X needs to be lower than %s for zoom %s, but had %s",
				max, zoom, xTile);
		Preconditions.checkArgument(yTile >= 0,
				"Y needs to be non-negative, but had %s", yTile);
		Preconditions.checkArgument(yTile < max,
				"Y needs to be lower than %s for zoom %s, but had %s",
				max, zoom, yTile);

		this.zoom = zoom;
		this.xTile = xTile;
		this.yTile = yTile;
	}

	public static OSMTile fromString(String str) {
		Matcher matcher = STRING_COORDS.matcher(str);

		Preconditions.checkArgument(matcher.matches(),
				"String for OSM-tile did not match pattern {0}/{1}/{2}: %s", str);

		return new OSMTile(Integer.parseInt(matcher.group(1)),
				Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
	}

	public static OSMTile fromLatLngZoom(final double lat, final double lon, final int zoom) {
		checkParameters(lat, lon, zoom);

		int xtile = (int) Math.floor(computeXTile(lon, zoom));
		Preconditions.checkArgument(xtile >= 0,
				"Had invalid x-tile %s for lon: %s and zoom: %s", xtile, lon, zoom);
		// for lon == 90 we get one more than the max tile
		if (xtile >= (1 << zoom)) {
			xtile = ((1 << zoom) - 1);
		}

		int ytile = (int) Math.floor(computeYTile(lat, zoom));
		// precision seems not high enough to properly compute this in some cases (e.g., lat = -90)
		if (ytile < 0) {
			ytile = 0;
		}
		// for lat == 180 we get one more than the max tile
		if (ytile >= (1 << zoom)) {
			ytile = ((1 << zoom) - 1);
		}

		return new OSMTile(zoom, xtile, ytile);
	}

	/**
	 * Compute the pixel-position of the given lat and lno values.
	 * The number of pixels per tile is defined in the constant PIXELS
	 *
	 * @param lat The latitude to check
	 * @param lon The longitute to check
	 * @return A pair of pixel in x- and y-direction
	 */
	public Pair<Integer, Integer> getPixelInTile(final double lat, final double lon) {
		checkParameters(lat, lon, zoom);

		double lonX = computeXTile(lon, zoom);
		double latY = computeYTile(lat, zoom);

		lonX -= computeXTile(computeLon(xTile, zoom), zoom);
		latY -= computeYTile(computeLat(yTile, zoom), zoom);

		return Pair.of(
				lon == 180.0 ? 255 : Math.min(255, (int)Math.floor(lonX*PIXELS)),
				Math.min(255, (int)Math.floor(latY*PIXELS)));
	}

	private static void checkParameters(double lat, double lon, int zoom) {
		if (lat < -90 || lat > 90) {
			throw new IllegalArgumentException("Latitude needs to be in range [-90, 90], but had: " + lat);
		}
		if (lon < -180 || lon > 180) {
			throw new IllegalArgumentException("Longitude needs to be in range [-180, 180], but had: " + lon);
		}
		if (zoom < OSM_MIN_ZOOM || zoom > OSM_MAX_ZOOM) {
			throw new IllegalArgumentException("Zoom needs to be in range [" + OSM_MIN_ZOOM + "," + OSM_MAX_ZOOM + "], but had: " + zoom);
		}
	}

	private static double computeXTile(double lon, int zoom) {
		return (lon + 180) / 360 * (1 << zoom);
	}

	private static double computeYTile(double lat, int zoom) {
		return (1.0 - Math.log(Math.tan(Math.toRadians(lat)) + 1.0 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom);
	}

	@Override
	public LatLng toLatLng() {
		return new LatLng(computeLat(yTile, zoom), computeLon(xTile, zoom));
	}

	/**
	 * Return the rectangle which represents this tile.
	 *
	 * @return A LatLonRectangle which holds coordinates to represent this
	 * 		   tile on a world-map
	 */
	public LatLonRectangle getRectangle() {
		return new LatLonRectangle(
				computeLat(yTile, zoom), computeLon(xTile, zoom),
				computeLat(yTile+1, zoom), computeLon(xTile+1, zoom));
	}

	private static double computeLon(int x, int zoom) {
		return ((double)x) / Math.pow(2.0, zoom) * 360.0 - 180;
	}

	private static double computeLat(int y, int zoom) {
		double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, zoom);
		return Math.toDegrees(Math.atan(Math.sinh(n)));
	}

	@Override
	public OSMTile up() {
		return new OSMTile(zoom, xTile, yTile - 1);
	}

	@Override
	public OSMTile down() {
		return new OSMTile(zoom, xTile, yTile + 1);
	}

	@Override
	public OSMTile right() {
		return new OSMTile(zoom, xTile + 1, yTile);
	}

	@Override
	public OSMTile left() {
		return new OSMTile(zoom, xTile - 1, yTile);
	}

	/**
	 * Return the zoom-level of this tile.
	 *
	 * @return A zoom-level, usually between 0 and 18
	 */
	public int getZoom() {
		return zoom;
	}

	/**
	 * Returns the x-coordinate of the tile.
	 *
	 * @return A tile-number, higher or equal to zero
	 */
	public int getXTile() {
		return xTile;
	}

	/**
	 * Returns the y-coordinate of the tile.
	 *
	 * @return A tile-number, higher or equal to zero
	 */
	public int getYTile() {
		return yTile;
	}

	/**
	 * Produce the name of the directory for storing this tile,
	 * usually in the form of _zoom_/_xtile_
	 *
	 * @return A string which can be used as directory for storing this tile
	 */
	public String toDirName() {
		return zoom + "/" + xTile;
	}

	/**
	 * Product the full path-name of the file for storing this tile,
	 * usually in the form of _dirname_/_ytile_
	 *
	 * @return A string which can be used as pathname for storing this tile
	 */
	public String toCoords() {
		return toDirName() + "/" + yTile;
	}

	/**
	 * Return the complete name of the .png-file when storing
	 * this tile based on the given tile-dir.
	 *
	 * @param tileDir The base-dir for storing this tile.
	 * @return The full path-name based on toCoords() with ".png" appended
	 */
	public File toFile(File tileDir) {
		return new File(tileDir, toCoords() + ".png");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		OSMTile that = (OSMTile) o;

		if (zoom != that.zoom) {
			return false;
		}
		if (xTile != that.xTile) {
			return false;
		}
		return yTile == that.yTile;
	}

	@Override
	public int hashCode() {
		int result = zoom;
		result = 31 * result + xTile;
		result = 31 * result + yTile;
		return result;
	}

	@Override
	public int compareTo(@Nullable OSMTile o) {
		if (o == null) {
			return 1;
		}

		if (zoom != o.zoom) {
			return zoom < o.zoom ? -1 : 1;
		}

		if (xTile != o.xTile) {
			return xTile < o.xTile ? -1 : 1;
		}

		if (yTile != o.yTile) {
			return yTile < o.yTile ? -1 : 1;
		}

		return 0;
	}

	@Override
	public String toString() {
		return "OSMTile{" +
				"zoom=" + zoom +
				", xTile=" + xTile +
				", yTile=" + yTile +
				'}';
	}

	/**
	 * Read tiles from the given text-file.
	 *
	 * The file should have one string-representation as produced by
	 * string() per line.
	 *
	 * @param file The file to read lines of tile-strings
	 * @return All found tiles in sorted order.
	 * @throws IOException If reading from the file fails.
	 */
	public static Set<OSMTile> readTiles(File file) throws IOException {
		return new TreeSet<>(FileUtils.readLines(file, StandardCharsets.UTF_8)).
				stream().
				map(OSMTile::fromString).
				collect(Collectors.toSet());
	}

	/**
	 * Returns a list of tiles which represent this tile at the given zoom
	 * @param zoom The target zoom
	 * @return A list of OSMTile objects. It contains one element if the given
	 * 		zoom is equal to the zoom of this tile. Otherwise, more than one OSMTile
	 * 		are returned.
	 */
	public List<OSMTile> getTilesAtZoom(int zoom) {
		Preconditions.checkArgument(zoom >= 0, "Zoom cannot be negative, but had %s", zoom);

		// shortcut if the zoom is equal to this tile
		if (zoom == getZoom()) {
			return Collections.singletonList(this);
		}

		// only one tile if we zoom out
		List<OSMTile> tiles = new ArrayList<>();
		if (zoom < getZoom()) {
			int steps = getZoom() - zoom;
			int factor = 1 << steps;
			return Collections.singletonList(new OSMTile(zoom, xTile/factor, yTile/factor));
		}

		// otherwise compute the list of tiles, count is "times 4" for each difference in zoom
		int steps = zoom -getZoom();
		int factor = 1 << steps;
		int startX = xTile*factor;
		int startY = yTile*factor;
		for (int x = 0;x < factor;x++) {
			for (int y = 0;y < factor;y++) {
				tiles.add(new OSMTile(zoom, startX + x, startY + y));
			}
		}

		return tiles;
	}

	@Override
	public String string() {
		return toCoords();
	}
}
