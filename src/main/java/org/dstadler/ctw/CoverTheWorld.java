package org.dstadler.ctw;

import java.io.IOException;

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
		CreateLargestClusterGeoJSON.main(new String[0]);
		CreateLargestClusterGeoJSONTiles.main(new String[0]);

		// produce "ClusterRectangle.*" and "ClusterTileRectangle.*"
		CreateLargestRectangleGeoJSON.main(new String[0]);
		CreateLargestRectangleGeoJSONTiles.main(new String[0]);

		// produce "ClusterSquare.*" and "ClusterTileSquare.*"
		CreateLargestRectangleGeoJSON.main(new String[0]);
		CreateLargestRectangleGeoJSONTiles.main(new String[0]);
	}
}
