package org.dstadler.ctw.gpx;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dstadler.commons.gpx.GPXTrackpointsParser;
import org.dstadler.commons.gpx.TrackPoint;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.util.ExecutorUtil;
import org.dstadler.ctw.utils.Constants;
import org.dstadler.ctw.utils.OSMTile;
import org.dstadler.ctw.utils.UTMRefWithHash;
import org.xml.sax.SAXException;

import com.google.common.base.Preconditions;

import uk.me.jstott.jcoord.LatLng;

/**
 * Small tool to read all GPX files and produce a list of
 * 1km-squares (based on UTMRef) and a list of OSMTile-tiles
 * which were visited.
 *
 * This is the first step when computing data for visited
 * squares and tiles, the resulting text-files are later used
 * as input when computing things like largest square, rectangle,
 * cluster, ...
 */
public class CreateListOfVisitedSquares {
	private static final Logger log = LoggerFactory.make();

	// location where GPX files are located
	public static final File GPX_DIR = new File("gpx");

	// squares
	public static final String VISITED_SQUARES_TXT = "txt/VisitedSquares.txt";
	public static final String VISITED_SQUARES_NEW_TXT = "txt/VisitedSquaresNew.txt";

	// tiles
	public static final String VISITED_TILES_TXT = "txt/VisitedTiles.txt";
	public static final String VISITED_TILES_NEW_TXT = "txt/VisitedTilesNew.txt";

	public static void main(String[] args) throws IOException, SAXException {
		LoggerFactory.initLogging();

		long start = System.currentTimeMillis();

		// Use a UTMRef/OSMTile as String to avoid double-imprecision affecting the resulting output
		Set<String> visitedSquares = ConcurrentHashMap.newKeySet();
		Set<String> visitedTiles = ConcurrentHashMap.newKeySet();

		final Consumer<TrackPoint> consumer = trackPoint -> {
			// Squares use UTMRef
			LatLng latLng = new LatLng(trackPoint.getLatitude(), trackPoint.getLongitude());
			visitedSquares.add(UTMRefWithHash.getSquareString(latLng));

			// Tiles use OSMTile
			visitedTiles.add(OSMTile.
					fromLatLngZoom(trackPoint.getLatitude(), trackPoint.getLongitude(), Constants.TILE_ZOOM).
					toCoords());
		};

		readVisited(consumer);

		Preconditions.checkState(visitedSquares.size() > 0,
				"Did not read any square from GPX tracks in directory '" + GPX_DIR + "'");
		Preconditions.checkState(visitedTiles.size() > 0,
				"Did not read any tile from GPX tracks in '" + GPX_DIR + "'");

		// Squares
		processVisitedArea(VISITED_SQUARES_TXT, VISITED_SQUARES_NEW_TXT, "squares", visitedSquares);

		// Tiles
		processVisitedArea(VISITED_TILES_TXT, VISITED_TILES_NEW_TXT, "tiles", visitedTiles);

		log.info("Finished reading GPX files after " + (System.currentTimeMillis() - start) + "ms");
	}

	private static void readVisited(Consumer<TrackPoint> toStringFun) throws IOException {
		AtomicInteger count = new AtomicInteger();

		Preconditions.checkState(GPX_DIR.exists() && GPX_DIR.isDirectory(),
				"Directory '%s' does not exist or is not a directory (%s/%s)",
				GPX_DIR, GPX_DIR.exists(), GPX_DIR.isDirectory());

		log.info("Searching directory '" + GPX_DIR + "' for GPX tracks");

		List<Future<?>> futures = new ArrayList<>();
		ExecutorService executor = Executors.newWorkStealingPool();

		try (Stream<Path> walk = Files.walk(GPX_DIR.toPath(), FileVisitOption.FOLLOW_LINKS)) {
			walk.
					parallel().
					forEach(path -> futures.add(executor.submit(() -> {
						File gpxFile = path.toFile();

						if(gpxFile.isDirectory() ||
								!gpxFile.getName().toLowerCase().endsWith(".gpx")) {
							return;
						}

						// only log sometimes to not spam the output
						if (count.incrementAndGet() % 20 == 0) {
							log.info("Move " + count.incrementAndGet() + ": " + gpxFile);
						}
						readTrackPoints(gpxFile, toStringFun);
					})));
		}

		Iterator<Future<?>> it = futures.iterator();
		while (it.hasNext()) {
			Future<?> future = it.next();
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
			it.remove();
		}

		ExecutorUtil.shutdownAndAwaitTermination(executor, 10_000);
	}

	private static void readTrackPoints(File gpxFile, Consumer<TrackPoint> toStringFun) {
		try {
			final SortedMap<Long, TrackPoint> trackPoints = GPXTrackpointsParser.parseContent(gpxFile, false);

			for (TrackPoint trackPoint : trackPoints.values()) {
				toStringFun.accept(trackPoint);
			}
		} catch (IOException e) {
			throw new RuntimeException("While handling " + gpxFile, e);
		}
	}

	private static void processVisitedArea(String visitedFile, String visitedNewFile,
			String title, Set<String> visited) throws IOException {
		long start = System.currentTimeMillis();

		Set<String> previous = readPrevious(visitedFile);

		writeListOfVisited(visited, visitedFile);

		log.info(String.format("Found %,d covered " + title + " after %,dms",
				visited.size(), System.currentTimeMillis() - start));

		// compute newly covered squares by removing all previously known ones
		visited.removeAll(previous);

		// re-write the new-file if we found some this time
		// otherwise the previous "new" entries should stay in place
		if (visited.size() > 0) {
			writeListOfVisited(visited, visitedNewFile);
		}

		log.info(String.format("Having %,d newly covered " + title + " after %,dms",
				visited.size(), System.currentTimeMillis() - start));
	}

	private static Set<String> readPrevious(String visitedFile) throws IOException {
		if (new File(visitedFile).exists()) {
			try (BufferedReader reader = new BufferedReader(new FileReader(visitedFile))) {
				return reader.lines().collect(Collectors.toCollection(TreeSet::new));
			}
		}

		return Collections.emptySet();
	}

	private static void writeListOfVisited(Set<String> visited, String visitedTxtFile) throws IOException {
		// create list of latLngBounds for SVG elements to overlay
		try (Writer writer = new BufferedWriter(new FileWriter(visitedTxtFile))) {
			for (String square : new TreeSet<>(visited)) {
				writer.write(square);
				writer.write('\n');
			}
		}
	}
}
