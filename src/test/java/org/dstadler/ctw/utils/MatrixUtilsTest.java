package org.dstadler.ctw.utils;

import static org.dstadler.ctw.geojson.CreateLargestRectangleGeoJSONSquares.CLUSTER_RECTANGLE_TXT;
import static org.dstadler.ctw.geojson.CreateLargestSquareGeoJSONSquares.CLUSTER_SQUARE_TXT;
import static org.dstadler.ctw.geojson.CreateLargestSquareGeoJSONTiles.CLUSTER_TILES_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_SQUARES_TXT;
import static org.dstadler.ctw.gpx.CreateListOfVisitedSquares.VISITED_TILES_TXT;
import static org.dstadler.ctw.utils.Constants.ZONE;
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
		assertThrows(IllegalStateException.class,
				() -> MatrixUtils.populateMatrix(tiles,
						0, 0, 0, 0));
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
                minX, minY, maxX, maxY);
        assertNotNull(matrix);
        assertTrue("Having: " + matrix.length,
				matrix.length > 5);
        assertTrue("Having: " + matrix[0].length,
				matrix[0].length > 3);
    }

	@Test
	public void testMaxSubSquareMinimal() {
		int [][] matrix = new int[1][1];
		assertEquals("(java.awt.Rectangle[x=1,y=0,width=0,height=0],0)",
			MatrixUtils.maxSubSquare(matrix).toString());

		matrix[0][0] = 1;
		assertEquals("(java.awt.Rectangle[x=1,y=0,width=1,height=1],1)",
			MatrixUtils.maxSubSquare(matrix).toString());

		matrix = new int[2][2];
		assertEquals("(java.awt.Rectangle[x=1,y=0,width=0,height=0],0)",
			MatrixUtils.maxSubSquare(matrix).toString());

		matrix[0][0] = 1;
		assertEquals("(java.awt.Rectangle[x=1,y=0,width=1,height=1],1)",
			MatrixUtils.maxSubSquare(matrix).toString());

		matrix[1][0] = 1;
		assertEquals("(java.awt.Rectangle[x=1,y=0,width=1,height=1],1)",
			MatrixUtils.maxSubSquare(matrix).toString());

		matrix[0][1] = 1;
		matrix[1][1] = 1;
		assertEquals("(java.awt.Rectangle[x=2,y=1,width=2,height=2],4)",
			MatrixUtils.maxSubSquare(matrix).toString());

		matrix = new int[5][5];
		matrix[1][2] = 1;
		matrix[2][2] = 1;
		assertEquals("(java.awt.Rectangle[x=3,y=1,width=1,height=1],1)",
			MatrixUtils.maxSubSquare(matrix).toString());

		matrix[1][1] = 1;
		matrix[2][1] = 1;
		assertEquals("(java.awt.Rectangle[x=3,y=2,width=2,height=2],4)",
			MatrixUtils.maxSubSquare(matrix).toString());

		matrix = new int[100][100];
		matrix[5][5] = 1;
		matrix[5][6] = 1;
		matrix[6][5] = 1;
		matrix[6][6] = 1;
		assertEquals("(java.awt.Rectangle[x=7,y=6,width=2,height=2],4)",
			MatrixUtils.maxSubSquare(matrix).toString());

		matrix[6][6] = 1;
		matrix[6][7] = 1;
		matrix[7][6] = 1;
		matrix[7][7] = 1;
		assertEquals("(java.awt.Rectangle[x=7,y=6,width=2,height=2],4)",
			MatrixUtils.maxSubSquare(matrix).toString());

		matrix[6][66] = 1;
		matrix[6][67] = 1;
		matrix[7][66] = 1;
		matrix[7][67] = 1;
		assertEquals("(java.awt.Rectangle[x=7,y=6,width=2,height=2],4)",
			MatrixUtils.maxSubSquare(matrix).toString());
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
				minX, minY, maxX, maxY);

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
	public void testMaxRectangleMinimal() {
		int[][] matrix = new int[1][1];
		assertEquals("(java.awt.Rectangle[x=0,y=0,width=0,height=0],0)",
				MatrixUtils.maxRectangle(matrix).toString());

		matrix[0][0] = 1;
		assertEquals("(java.awt.Rectangle[x=1,y=0,width=1,height=1],1)",
				MatrixUtils.maxRectangle(matrix).toString());

		matrix = new int[2][2];
		assertEquals("(java.awt.Rectangle[x=0,y=0,width=0,height=0],0)",
				MatrixUtils.maxRectangle(matrix).toString());

		matrix[0][0] = 1;
		assertEquals("(java.awt.Rectangle[x=1,y=0,width=1,height=1],1)",
				MatrixUtils.maxRectangle(matrix).toString());

		matrix[1][0] = 1;
		assertEquals("(java.awt.Rectangle[x=1,y=1,width=1,height=2],2)",
				MatrixUtils.maxRectangle(matrix).toString());

		matrix[1][1] = 1;
		assertEquals("(java.awt.Rectangle[x=1,y=1,width=1,height=2],2)",
				MatrixUtils.maxRectangle(matrix).toString());

		matrix[0][1] = 1;
		assertEquals("(java.awt.Rectangle[x=2,y=1,width=2,height=2],4)",
				MatrixUtils.maxRectangle(matrix).toString());


		matrix = new int[5][5];
		assertEquals("(java.awt.Rectangle[x=0,y=0,width=0,height=0],0)",
				MatrixUtils.maxRectangle(matrix).toString());

		matrix[1][0] = 1;
		matrix[2][0] = 1;
		matrix[3][0] = 1;
		matrix[4][0] = 1;
		assertEquals("(java.awt.Rectangle[x=1,y=4,width=1,height=4],4)",
				MatrixUtils.maxRectangle(matrix).toString());

		matrix[0][0] = 1;
		matrix[0][1] = 1;
		matrix[0][2] = 1;
		matrix[0][3] = 1;
		matrix[0][4] = 1;
		assertEquals("(java.awt.Rectangle[x=5,y=0,width=5,height=1],5)",
				MatrixUtils.maxRectangle(matrix).toString());

		matrix = new int[100][100];
		matrix[50][20] = 1;
		matrix[50][21] = 1;
		matrix[50][22] = 1;
		matrix[50][23] = 1;
		matrix[50][24] = 1;
		assertEquals("(java.awt.Rectangle[x=25,y=50,width=5,height=1],5)",
				MatrixUtils.maxRectangle(matrix).toString());

		matrix[51][20] = 1;
		matrix[51][21] = 1;
		matrix[51][22] = 1;
		matrix[51][23] = 1;
		matrix[51][24] = 1;
		assertEquals("(java.awt.Rectangle[x=25,y=51,width=5,height=2],10)",
				MatrixUtils.maxRectangle(matrix).toString());

		matrix[71][20] = 1;
		matrix[72][20] = 1;
		matrix[73][20] = 1;
		matrix[74][20] = 1;
		matrix[75][20] = 1;
		matrix[76][20] = 1;

		matrix[71][21] = 1;
		matrix[72][21] = 1;
		matrix[73][21] = 1;
		matrix[74][21] = 1;
		matrix[75][21] = 1;
		matrix[76][21] = 1;
		assertEquals("(java.awt.Rectangle[x=22,y=76,width=2,height=6],12)",
				MatrixUtils.maxRectangle(matrix).toString());
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
		int[][] matrix = MatrixUtils.populateMatrix(tiles,
				minX, minY, maxX, maxY);


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
