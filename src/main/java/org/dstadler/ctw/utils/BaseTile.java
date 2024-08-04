package org.dstadler.ctw.utils;

import uk.me.jstott.jcoord.LatLng;

/**
 * Small interface to allow to handle different
 * types of tiles via common utility code.
 *
 * @param <T> The actual type of tile to allow compile-time type-checks
 */
public interface BaseTile<T> {

	/**
	 * Get the lat-lon-rectangle covering this tile.
	 *
	 * @return The lat-lon coordinates which describe this tile
	 */
	LatLonRectangle getRectangle();

	/**
	 * Get the next tile in direction upwards/north.
	 *
	 * @return The resulting tile
	 * @throws RuntimeException if the tile is not defined anymore
	 */
	BaseTile<T> up();

	/**
	 * Get the next tile in direction downwards/south.
	 *
	 * @return The resulting tile
	 * @throws RuntimeException if the tile is not defined anymore
	 */
	BaseTile<T> down();

	/**
	 * Get the next tile in direction right/east.
	 *
	 * @return The resulting tile
	 * @throws RuntimeException if the tile is not defined anymore
	 */
	BaseTile<T> right();

	/**
	 * Get the next tile in direction left/west.
	 *
	 * @return The resulting tile
	 * @throws RuntimeException if the tile is not defined anymore
	 */
	BaseTile<T> left();

	/**
	 * Get the lat-lon coordinates of this tile.
	 *
	 * @return A LatLng filled with coordinates for this tile.
	 */
	LatLng toLatLng();

	/**
	 * Get a string-representation of this tile.
	 *
	 * Implementations usually provide a means to convert this
	 * back into the full object again, so this should be
	 * usable for serialization, e.g. writing to text-files.
	 *
	 * @return A string-representation of this tile.
	 */
	String string();
}
