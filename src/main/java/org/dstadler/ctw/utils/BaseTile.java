package org.dstadler.ctw.utils;

import uk.me.jstott.jcoord.LatLng;

/**
 * Small interface to allow to handle different
 * types of tiles via common utility code.
 *
 * @param <T>
 */
public interface BaseTile<T> {
	LatLonRectangle getRectangle();

	BaseTile<T> up();

	BaseTile<T> down();

	BaseTile<T> right();

	BaseTile<T> left();

	LatLng toLatLng();

	String string();
}
