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
import java.util.Collections;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.dstadler.commons.gpx.GPXTrackpointsParser;
import org.dstadler.commons.gpx.TrackPoint;
import org.dstadler.commons.logging.jdk.LoggerFactory;
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
	}

	private static void readVisited(Consumer<TrackPoint> toStringFun) throws IOException {
		AtomicInteger count = new AtomicInteger();

		Preconditions.checkState(GPX_DIR.exists() && GPX_DIR.isDirectory(),
				"Directory '%s' does not exist or is not a directory (%s/%s)",
				GPX_DIR, GPX_DIR.exists(), GPX_DIR.isDirectory());

		log.info("Searching directory '" + GPX_DIR + "' for GPX tracks");
		try (Stream<Path> walk = Files.walk(GPX_DIR.toPath(), FileVisitOption.FOLLOW_LINKS)) {
			walk.
					parallel().
					forEach(path -> {
						File gpxFile = path.toFile();

						if(gpxFile.isDirectory() ||
								!gpxFile.getName().toLowerCase().endsWith(".gpx")) {
							return;
						}

					if (gpxFile.length() == 0) {
						System.out.println("Skipping empty file " + gpxFile);
						return;
					}

					if (gpxFile.length() == 1048576 ||
							gpxFile.getName().equals("tourenwelt.at_download.php_tourid=206&download=206_fuenfmandling.gpx")) {
						System.out.println("Skipping truncated file " + gpxFile);
						return;
					}

					try {
						String str = FileUtils.readFileToString(gpxFile, "UTF-8").trim();
						if (str.contains("301 Moved Permanently") ||
								str.startsWith("Moved Permanently") ||
								str.startsWith("BCFZ") ||
								str.startsWith("Found") ||
								str.toLowerCase().startsWith("<!doctype html") ||
								str.toLowerCase().startsWith("<html") ||
								str.toUpperCase().startsWith("GEOMETRYCOLLECTION") ||
								StringUtils.isBlank(str)) {
							System.out.println("Skipping file with HTTP error " + gpxFile);
							return;
						}
					} catch (IOException | RuntimeException e) {
						throw new RuntimeException("Failed to process " + gpxFile, e);
					}

					log.info("Move " + count.incrementAndGet() + ": " + gpxFile);
						readTrackPoints(gpxFile, toStringFun);
					});
		}
	}

	private static void readTrackPoints(File gpxFile, Consumer<TrackPoint> toStringFun) {
		try {
			final SortedMap<Long, TrackPoint> trackPoints = GPXTrackpointsParser.parseContent(gpxFile);

			for (TrackPoint trackPoint : trackPoints.values()) {
				toStringFun.accept(trackPoint);
			}
		} catch (IOException | RuntimeException e) {
			// ignore some broken files
			String stackTrace = ExceptionUtils.getStackTrace(e);
			if (stackTrace.contains("Expected to have tag 'lat' and 'lon'") ||
					stackTrace.contains("For input string")) {
				System.out.println("Skipping broken file " + gpxFile);
				return;
			}
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
