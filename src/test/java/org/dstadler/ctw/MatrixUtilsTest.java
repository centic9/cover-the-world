package org.dstadler.ctw;

import static org.dstadler.ctw.CreateLargestRectangleGeoJSON.CLUSTER_RECTANGLE_TXT;
import static org.dstadler.ctw.CreateLargestSquareGeoJSON.CLUSTER_SQUARE_TXT;
import static org.dstadler.ctw.CreateLargestSquareGeoJSONTiles.CLUSTER_TILES_TXT;
import static org.dstadler.ctw.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;
import static org.dstadler.ctw.CreateListOfVisitedSquares.VISITED_TILES_TXT;
import static org.dstadler.ctw.MatrixUtils.ZONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;

import uk.me.jstott.jcoord.LatLng;

public class MatrixUtilsTest {
	private Set<UTMRefWithHash> squares;
	private Set<OSMTile> tiles;

	private double minEast = Double.MAX_VALUE, maxEast = Double.MIN_VALUE,
			minNorth = Double.MAX_VALUE, maxNorth = Double.MIN_VALUE;

	private int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
			minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

	@Before
	public void setUp() throws IOException {
		squares = UTMRefWithHash.readSquares(new File(VISITED_SQUARES_TXT));
		tiles = OSMTile.readTiles(new File(VISITED_TILES_TXT));

		for (UTMRefWithHash square : squares) {
			if (square.getLngZone() != ZONE) {
				continue;
			}

			if (square.getEasting() > maxEast) {
				maxEast = square.getEasting();
			}
			if (square.getEasting() < minEast) {
				minEast = square.getEasting();
			}

			if (square.getNorthing() > maxNorth) {
				maxNorth = square.getNorthing();
			}
			if (square.getNorthing() < minNorth) {
				minNorth = square.getNorthing();
			}
		}

		for (OSMTile tile : tiles) {
			LatLng latLng = tile.toLatLng();
			if (latLng.toUTMRef().getLngZone() != ZONE) {
				continue;
			}

			if (tile.getXTile() > maxX) {
				maxX = tile.getXTile();
			}
			if (tile.getXTile() < minX) {
				minX = tile.getXTile();
			}

			if (tile.getYTile() > maxY) {
				maxY = tile.getYTile();
			}
			if (tile.getYTile() < minY) {
				minY = tile.getYTile();
			}
		}
	}

	@Test
	public void testInvalidValue() {
		assertThrows(IllegalStateException.class,
				() -> MatrixUtils.populateMatrix(squares,
						0, 0, 0, 0, ZONE));
	}

	@Test
    public void testPopulateMatrix() {
        int[][] matrix = MatrixUtils.populateMatrix(squares,
                minEast, minNorth, maxEast, maxNorth, ZONE);
        assertNotNull(matrix);
        assertTrue("Having: " + matrix.length,
				matrix.length > 5);
        assertTrue("Having: " + matrix[0].length,
				matrix[0].length > 5);
    }

	@Test
    public void testPopulateMatrixTiles() {
        int[][] matrix = MatrixUtils.populateMatrix(tiles,
                minX, minY, maxX, maxY, ZONE);
        assertNotNull(matrix);
        assertTrue("Having: " + matrix.length,
				matrix.length > 5);
        assertTrue("Having: " + matrix[0].length,
				matrix[0].length > 3);
    }

	@Test
	public void testMaxSubSquare() throws IOException {
		int[][] matrix = MatrixUtils.populateMatrix(squares,
				minEast, minNorth, maxEast, maxNorth, ZONE);

		Pair<Rectangle, Integer> result = MatrixUtils.maxSubSquare(matrix);

		/*
33U 420000.0 5363000.0
33U 435000.0 5378000.0
149x343
15x15
225
		 */
		// try to read current square from text-file
		Rectangle expected = readRectangle(new File(CLUSTER_SQUARE_TXT),
				new Rectangle(5, 2, 3, 3));
		assertEquals(expected, result.getLeft());
		assertEquals((Integer)(expected.width * expected.height), result.getRight());
	}

	@Test
	public void testMaxSubSquareTile() throws IOException {
		int[][] matrix = MatrixUtils.populateMatrix(tiles,
				minX, minY, maxX, maxY, ZONE);

		Pair<Rectangle, Integer> result = MatrixUtils.maxSubSquare(matrix);

		/*
14/8825/5656
14/8835/5666
89x453
10x10
100
		 */
		// try to read current square from text-file
		Rectangle expected = readRectangle(new File(CLUSTER_TILES_TXT),
				new Rectangle(3, 5, 2, 2));
		assertEquals(expected, result.getLeft());
		assertEquals((Integer)(expected.width * expected.height), result.getRight());
	}

	@Test
	public void testMaxRectangle() throws IOException {
		int[][] matrix = MatrixUtils.populateMatrix(squares,
				minEast, minNorth, maxEast, maxNorth, ZONE);


		Pair<Rectangle, Integer> result = MatrixUtils.maxRectangle(matrix);

		/*
33U 417000.0 5364000.0
33U 435000.0 5379000.0
149x344
18x15
270
		 */
		// try to read current square from text-file
		Rectangle expected = readRectangle(new File(CLUSTER_RECTANGLE_TXT),
				new Rectangle(5, 2, 3, 3));
		assertEquals(expected, result.getLeft());
		assertEquals((Integer)(expected.width * expected.height), result.getRight());
	}

	@Test
	public void testMaxRectangleTiles() throws IOException {
		int[][] matrix = MatrixUtils.populateMatrix(squares,
				minEast, minNorth, maxEast, maxNorth, ZONE);


		Pair<Rectangle, Integer> result = MatrixUtils.maxRectangle(matrix);

		/*
14/8822/5657
14/8835/5667
89x454
13x10
130
		 */
		// try to read current square from text-file
		Rectangle expected = readRectangle(new File(CLUSTER_RECTANGLE_TXT),
				new Rectangle(5, 2, 3, 3));
		assertEquals(expected, result.getLeft());
		assertEquals((Integer)(expected.width * expected.height), result.getRight());
	}

	private Rectangle readRectangle(File file, Rectangle def) throws IOException {
		if (!file.exists()) {
			return def;
		}

		List<String> lines = Files.readLines(file, StandardCharsets.UTF_8);

		int x, y, w, h;
		String[] split = lines.get(2).split("x");
		x = Integer.parseInt(split[0]);
		y = Integer.parseInt(split[1]);

		split = lines.get(3).split("x");
		w = Integer.parseInt(split[0]);
		h = Integer.parseInt(split[1]);

		return new Rectangle(x, y, w, h);
	}
}
