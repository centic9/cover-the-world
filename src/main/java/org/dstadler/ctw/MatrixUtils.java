package org.dstadler.ctw;

import static org.dstadler.ctw.CreateListOfVisitedSquares.TILE_ZOOM;

import java.awt.Rectangle;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang3.tuple.Pair;
import org.dstadler.commons.logging.jdk.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * Some helpers to work with matrices for some algorithms,
 * e.g. largest rectangle and largest square.
 */
public class MatrixUtils {
	private static final Logger log = LoggerFactory.make();

	// for now only calculate for my main Zone 33 as otherwise computing
	// "easting" would need to take the zone into account
	public static final int ZONE = 33;

	/**
	 * Create an 2-dimensional matrix in between of minEast/maxEast, minNorth, maxNorth
	 * with "1" for each square that is covered.
	 *
	 * @return The initialized matrix with "0" for not covered and "1" for covered
	 */
	public static int[][] populateMatrix(Set<UTMRefWithHash> squares,
			double minEast,
			double minNorth,
			double maxEast,
			double maxNorth,
			int utmZoneFilter) {
		int xSquares = (int) ((maxEast - minEast) / 1000) + 1;
		int ySquares = (int) ((maxNorth - minNorth) / 1000) + 1;

		log.fine("Having min/max: " +
				"\nEasting: " + minEast + "/" + maxEast +
				"\nNorthing: " + minNorth + "/" + maxNorth +
				"\nx,y: " + xSquares + "," + ySquares +
				"\nlat/lng: " + new UTMRefWithHash(utmZoneFilter, 'T', minEast, minNorth).toLatLng() + " - " +
				new UTMRefWithHash(utmZoneFilter, 'U', maxEast, maxNorth).toLatLng() +
				"\nUTM: " + new UTMRefWithHash(utmZoneFilter, 'T', minEast, minNorth) + " - " +
				new UTMRefWithHash(utmZoneFilter, 'U', maxEast, maxNorth));

		// Min: Ref:  29U 436444.6222519411 5916707.037366929: (53.395620002685064, -9.955800000062068)

		int[][] M = new int[ySquares][xSquares];
		for (UTMRefWithHash square : squares) {
			// for now only calculate for Zone 32 as otherwise computing
			// easting would need to take the zone into account
			if (square.getLngZone() != utmZoneFilter) {
				continue;
			}

			int x = (int) ((square.getEasting() - minEast) / 1000);
			int y = (int) ((square.getNorthing() - minNorth) / 1000);

			Preconditions.checkState(x >= 0 && x <= xSquares,
					"Failed with %s for %s and %s and %s",
					x, square, minEast, xSquares);
			Preconditions.checkState(y >= 0 && y <= ySquares,
					"Failed with %s for %s and %s and %s",
					y, square, minNorth, ySquares);
			Preconditions.checkState(M[y][x] == 0,
					"Failed for %s and %s,%s",
					square, x, y);

			M[y][x] = 1;
		}
		return M;
	}

	/**
	 * Create an 2-dimensional matrix in between of minEast/maxX, minNorth, maxNorth
	 * with "1" for each square that is covered.
	 *
	 * @return The initialized matrix with "0" for not covered and "1" for covered
	 */
	public static int[][] populateMatrix(Set<OSMTile> squares,
			int minX,
			int minY,
			int maxX,
			int maxY,
			int utmZoneFilter) {
		int xSquares = (maxX - minX) + 1;
		int ySquares = (maxY - minY) + 1;

		log.fine("Having min/max: " +
				"\nX: " + minX + "/" + maxX +
				"\nY: " + minY + "/" + maxY +
				"\nx,y: " + xSquares + "," + ySquares +
				"\nlat/lng: " + new OSMTile(TILE_ZOOM, minX, minY).toLatLng() + " - " +
								new OSMTile(TILE_ZOOM, maxX, maxY).toLatLng());

		// Min: Ref:  29U 436444.6222519411 5916707.037366929: (53.395620002685064, -9.955800000062068)

		int[][] M = new int[ySquares][xSquares];
		for (OSMTile square : squares) {
			// for now only calculate for Zone 32 as otherwise computing
			// easting would need to take the zone into account
			if (square.toLatLng().toUTMRef().getLngZone() != utmZoneFilter) {
				continue;
			}

			int x = (square.getXTile() - minX);
			int y = (square.getYTile() - minY);

			Preconditions.checkState(x >= 0 && x <= xSquares,
					"Failed with %s for %s and %s and %s",
					x, square, minX, xSquares);
			Preconditions.checkState(y >= 0 && y <= ySquares,
					"Failed with %s for %s and %s and %s",
					y, square, minY, ySquares);
			Preconditions.checkState(M[y][x] == 0,
					"Failed for %s and %s,%s",
					square, x, y);

			M[y][x] = 1;
		}
		return M;
	}

	/**
	 * Method for Maximum size square sub-matrix with all "1"s
	 *
	 * Based on https://www.geeksforgeeks.org/maximum-size-sub-matrix-with-all-1s-in-a-binary-matrix/
	 *
	 * @param matrix The matrix with "1" for covered and "0" for not covered.
	 *
	 * @return A pair with the largest covered square and the number of squares covered by
	 * this rectangle.
	 */
	public static Pair<Rectangle,Integer> maxSubSquare(int[][] matrix) {
		int R = matrix.length; // no of rows in M[][]
		int C = matrix[0].length; // no of columns in M[][]

		int[][] S = new int[R][C];
		/* Set first column of S[][]*/
		for (int i = 0; i < R; i++) {
			S[i][0] = matrix[i][0];
		}

		/* Set first row of S[][]*/
		System.arraycopy(matrix[0], 0, S[0], 0, C);

		/* Construct other entries of S[][]*/
		for (int i = 1; i < R; i++) {
			for (int j = 1; j < C; j++) {
				if (matrix[i][j] == 1) {
					S[i][j] = Math.min(
							S[i][j - 1],
							Math.min(S[i - 1][j],
									S[i - 1][j - 1]))
							+ 1;
				} else {
					S[i][j] = 0;
				}
			}
		}

        /* Find the maximum entry, and indexes of maximum
           entry in S[][] */
		int max_of_s = S[0][0];
		int max_i = 0;
		int max_j = 0;
		for (int i = 0; i < R; i++) {
			for (int j = 0; j < C; j++) {
				if (max_of_s < S[i][j]) {
					max_of_s = S[i][j];
					max_i = i;
					max_j = j;
				}
			}
		}

		return Pair.of(new Rectangle(max_j + 1, max_i, max_of_s, max_of_s), max_of_s * max_of_s);
	}

	// Implementation initially based on
	// https://www.geeksforgeeks.org/maximum-size-rectangle-binary-sub-matrix-1s/
	private static Pair<Integer, Rectangle> maxHist(int[] row) {
		// Create an empty stack. The stack holds indexes of
		// hist[] array/ The bars stored in stack are always
		// in increasing order of their heights.
		IntStack result = new IntStack();

		int max_area = 0; // Initialize max area in current row (or histogram)
		Rectangle max_col = new Rectangle();

		// Run through all bars of given histogram (or row)
		int i = 0;
		while (i < row.length) {
			// If this bar is higher than the bar on top
			// stack, push it to stack
			if (result.isEmpty()
					|| row[result.peek()] <= row[i]) {
				result.push(i++);
			} else {
				// If this bar is lower than top of stack,
				// then calculate area of rectangle with
				// stack top as the smallest (or minimum
				// height) bar. 'i' is 'right index' for the
				// top and element before top in stack is
				// 'left index'
				max_area = getMaxArea(row, result, max_area, max_col, i);
			}
		}

		// Now pop the remaining bars from stack and
		// calculate area with every popped bar as the
		// smallest bar
		while (!result.isEmpty()) {
			max_area = getMaxArea(row, result, max_area, max_col, i);
		}

		return Pair.of(max_area, max_col);
	}

	private static int getMaxArea(int[] row, IntStack result, int max_area, Rectangle max_col, int i) {
		int top_val = row[result.peek()];
		result.pop();

		final int area;
		if (result.isEmpty()) {
			area = top_val * i;
		} else {
			area = top_val * (i - result.peek() - 1);
		}

		if (area > max_area) {
			max_area = area;
			//log.info("New top area " + max_area + ": " + result.peek());
			max_col.width = result.isEmpty() ? i : (i - result.peek() - 1);
			max_col.height = top_val;
			max_col.x = i;
			//max_col = "Top: " + top_val + ", " + i + (result.isEmpty() ? "" : ", " + (i - result.peek() - 1));
		}

		return max_area;
	}

	/**
	 * Returns area of the largest rectangle with all 1s in A[][]
	 *
	 * @param A The matrix with "1" for covered and "0" for not covered
	 * @return The rectangle and the covered area of the rectangle
	 */
	public static Pair<Rectangle, Integer> maxRectangle(int[][] A) {
		// Calculate area for first row and initialize it as
		// result
		Pair<Integer,Rectangle> ret = maxHist(A[0]);
		int result = ret.getKey();
		Rectangle rect = ret.getValue();

		log.fine("Area of first row: " + ret);

		// iterate over row to find maximum rectangular area
		// considering each row as histogram
		for (int i = 1; i < A.length; i++) {
			for (int j = 0; j < A[0].length; j++) {
				// if A[i][j] is 1 then add A[i -1][j]
				if (A[i][j] == 1) {
					A[i][j] += A[i - 1][j];
				}
			}

			// Update result if area with current row (as
			// last row of rectangle) is more
			ret = maxHist(A[i]);
			int newRet = ret.getKey();
			if (newRet > result) {
				result = newRet;
				rect = ret.getValue();
				rect.y = i;
				log.fine("Area after row " + i + ": " + result + ": " + rect);
			}
		}

		return Pair.of(rect, result);
	}
}
