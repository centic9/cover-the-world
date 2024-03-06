package org.dstadler.ctw;

import java.io.IOException;

import org.dstadler.ctw.modules.CreateClusterGeoJSON;
import org.dstadler.ctw.modules.CreateGeoJSON;
import org.dstadler.ctw.modules.CreateLargestClusterGeoJSONSquares;
import org.dstadler.ctw.modules.CreateLargestClusterGeoJSONTiles;
import org.dstadler.ctw.modules.CreateLargestRectangleGeoJSONSquares;
import org.dstadler.ctw.modules.CreateLargestRectangleGeoJSONTiles;
import org.dstadler.ctw.modules.CreateLargestSquareGeoJSONSquares;
import org.dstadler.ctw.modules.CreateLargestSquareGeoJSONTiles;
import org.dstadler.ctw.modules.CreateListOfVisitedSquares;
import org.xml.sax.SAXException;

/**
 * Main application to refresh all resulting files
 */
public class CoverTheWorld {

	public static void main(String[] args) throws IOException, SAXException {
		// this needs to run first to compute "Visited*.txt"
		CreateListOfVisitedSquares.main(new String[0]);

		// produce Visited*.js
		CreateGeoJSON.main(new String[0]);

		// produce ClusterSquares.*
		CreateClusterGeoJSON.main(new String[0]);

		// produce "LargestCluster*"
		CreateLargestClusterGeoJSONSquares.main(new String[0]);
		CreateLargestClusterGeoJSONTiles.main(new String[0]);

		// produce "LargestRectangle*"
		CreateLargestRectangleGeoJSONSquares.main(new String[0]);
		CreateLargestRectangleGeoJSONTiles.main(new String[0]);

		// produce "LargestSquare*"
		CreateLargestSquareGeoJSONSquares.main(new String[0]);
		CreateLargestSquareGeoJSONTiles.main(new String[0]);
	}
}
