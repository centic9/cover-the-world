package org.dstadler.ctw;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.function.IORunnable;
import org.dstadler.commons.util.ExecutorUtil;
import org.dstadler.ctw.geojson.CreateAdjacent;
import org.dstadler.ctw.geojson.CreateClusterGeoJSON;
import org.dstadler.ctw.geojson.CreateGeoJSON;
import org.dstadler.ctw.geojson.CreateLargestClusterGeoJSONSquares;
import org.dstadler.ctw.geojson.CreateLargestClusterGeoJSONTiles;
import org.dstadler.ctw.geojson.CreateLargestConnectedGeoJSONSquares;
import org.dstadler.ctw.geojson.CreateLargestConnectedGeoJSONTiles;
import org.dstadler.ctw.geojson.CreateLargestRectangleGeoJSONSquares;
import org.dstadler.ctw.geojson.CreateLargestRectangleGeoJSONTiles;
import org.dstadler.ctw.geojson.CreateLargestSquareGeoJSONSquares;
import org.dstadler.ctw.geojson.CreateLargestSquareGeoJSONTiles;
import org.dstadler.ctw.gpx.CreateListOfVisitedSquares;

/**
 * Main application to read GPX tracks and produce the GeoJSON
 * files for the Leaflet-based HTML page.
 */
public class CoverTheWorld {

	public static void main(String[] args) throws Throwable {
		// this needs to run first to compute "Visited*.txt"
		CreateListOfVisitedSquares.main(args);

		// read "Visited*.txt"
		// produce "Adjacent*"
		CreateAdjacent.main(args);

		ExecutorService executor = Executors.newWorkStealingPool();
		AtomicReference<Throwable> ex = new AtomicReference<>();

		// read "Visited*.txt"
		// produce "Visited*.js"
		submit(executor, ex, CreateGeoJSON::computeGeoJSONSquares);
		submit(executor, ex, CreateGeoJSON::computeGeoJSONSquaresNew);
		submit(executor, ex, CreateGeoJSON::computeGeoJSONTiles);
		submit(executor, ex, CreateGeoJSON::computeGeoJSONTilesNew);

		// read "Visited*.txt"
		// produce "ClusterSquares.*"
		submit(executor, ex, () -> CreateClusterGeoJSON.main(args));

		// read "Visited*.txt"
		// produce "LargestCluster*"
		submit(executor, ex, () -> CreateLargestClusterGeoJSONSquares.main(args));
		submit(executor, ex, () -> CreateLargestClusterGeoJSONTiles.main(args));

		// read "Visited*.txt"
		// produce "LargestConnected*"
		submit(executor, ex, () -> CreateLargestConnectedGeoJSONSquares.main(args));
		submit(executor, ex, () -> CreateLargestConnectedGeoJSONTiles.main(args));

		// read "Visited*.txt"
		// produce "LargestRectangle*"
		submit(executor, ex, () -> CreateLargestRectangleGeoJSONSquares.main(args));
		submit(executor, ex, () -> CreateLargestRectangleGeoJSONTiles.main(args));

		// read "Visited*.txt"
		// produce "LargestSquare*"
		submit(executor, ex, () -> CreateLargestSquareGeoJSONSquares.main(args));
		submit(executor, ex, () -> CreateLargestSquareGeoJSONTiles.main(args));

		// wait for the tasks to finish
		ExecutorUtil.shutdownAndAwaitTermination(executor, 120_000);

		if (ex.get() != null) {
			throw ex.get();
		}
	}

	private static void submit(ExecutorService executor, AtomicReference<Throwable> ex, IORunnable r) {
		// stop early on exception
		if (ex.get() != null) {
			return;
		}

		// schedule the task, catch any exception and keep it for later reporting
		executor.submit(() -> {
			try {
				r.run();
			} catch (Throwable e) {
				ex.set(e);
			}
		});
	}
}
